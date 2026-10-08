package local.oss.chronicle.features.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import local.oss.chronicle.data.sources.plex.IPlexLoginRepo
import local.oss.chronicle.data.sources.plex.PlexLoginService
import local.oss.chronicle.data.sources.plex.PlexPrefsRepo
import timber.log.Timber
import javax.inject.Inject

/**
 * State machine coordinator for the plex.tv/link short-code auth flow.
 *
 * This coordinator manages the entire authentication lifecycle:
 * 1. Creates a plain (non-`strong`) PIN via [PlexLoginService.postLinkPin], which returns a short
 *    human-typeable code
 * 2. Signals the UI to display that code so the user can enter it at https://plex.tv/link on any
 *    other device
 * 3. Polls Plex (via [IPlexLoginRepo.checkForOAuthAccessToken]) for the resulting auth token
 * 4. Enforces a 5-minute timeout — long enough to read a code off a watch, walk to another
 *    device, and type it in
 *
 * **State Machine Transitions:**
 * ```
 * Idle → CreatingPin → WaitingForUser → Polling → Success/Error/Timeout/Cancelled
 * ```
 *
 * **Why this bypasses [IPlexLoginRepo.postOAuthPin]:** that method (kept verbatim, since it is
 * still used by the wider account/server/user/library state machine) always requests a `strong`
 * PIN via [PlexLoginService.postAuthPin], intended to be embedded in a browser-redirect URL. The
 * plex.tv/link flow needs the plain, short code instead, so this coordinator calls
 * [PlexLoginService.postLinkPin] directly and mirrors the one side effect [IPlexLoginRepo] callers
 * rely on — stashing the new PIN's `id` in [PlexPrefsRepo.oAuthTempId] — so that the existing,
 * unmodified [IPlexLoginRepo.checkForOAuthAccessToken] polls the right PIN.
 *
 * **Threading:** All API calls and state updates happen on the provided [scope].
 *
 * **Lifecycle:**
 * - Create coordinator (typically in a ViewModel)
 * - Call [startAuth] to begin flow
 * - Observe [state] for UI updates
 * - Call [dispose] when done (cleanup)
 *
 * @param plexLoginRepo Repository for the post-PIN account/server/user/library state machine and
 *   for polling ([IPlexLoginRepo.checkForOAuthAccessToken])
 * @param plexLoginService Used directly to create the plain, human-typeable PIN
 * @param plexPrefsRepo Used directly to stash the new PIN's id, matching what
 *   [IPlexLoginRepo.postOAuthPin] would have done for the strong-PIN flow
 * @param scope Coroutine scope for async operations (use viewModelScope for ViewModels)
 */
class PlexAuthCoordinator
    @Inject
    constructor(
        private val plexLoginRepo: IPlexLoginRepo,
        private val plexLoginService: PlexLoginService,
        private val plexPrefsRepo: PlexPrefsRepo,
        private val scope: CoroutineScope,
    ) {
        companion object {
            /** Default polling interval in milliseconds */
            const val POLLING_INTERVAL_MS = 1500L

            /**
             * Authentication timeout (5 minutes in milliseconds). Reading a short code off a
             * watch face, walking to another device, and typing it in at plex.tv/link does not
             * comfortably fit in the old 2-minute Chrome Custom Tabs timeout.
             */
            const val TIMEOUT_MS = 300_000L
        }

        private val _state = MutableStateFlow<PlexAuthState>(PlexAuthState.Idle)

        /**
         * Current authentication state as a StateFlow.
         *
         * Collectors will receive updates as the auth flow progresses through:
         * Idle → CreatingPin → WaitingForUser → Polling → (Success|Error|Timeout|Cancelled)
         */
        val state: StateFlow<PlexAuthState> = _state.asStateFlow()

        private var pollingJob: Job? = null
        private var startTime: Long = 0

        /**
         * Starts the plex.tv/link authentication flow.
         *
         * **State Transitions:**
         * 1. Idle → CreatingPin (while calling Plex API)
         * 2. CreatingPin → WaitingForUser (PIN created, short code ready to display)
         * 3. WaitingForUser → Polling (automatically starts polling)
         * 4. Polling → Success/Error/Timeout (based on polling results)
         *
         * @return The StateFlow that emits auth state changes
         */
        suspend fun startAuth(): StateFlow<PlexAuthState> {
            if (_state.value !is PlexAuthState.Idle) {
                Timber.w("Auth already in progress, current state: ${_state.value}")
                return state
            }

            _state.value = PlexAuthState.CreatingPin

            try {
                val oAuthResponse = plexLoginService.postLinkPin()

                Timber.i(
                    "Link PIN created: id=${oAuthResponse.id}, code=${oAuthResponse.code}",
                )

                // Mirrors IPlexLoginRepo.postOAuthPin()'s side effect so the unmodified
                // checkForOAuthAccessToken() polls this PIN.
                plexPrefsRepo.oAuthTempId = oAuthResponse.id

                _state.value =
                    PlexAuthState.WaitingForUser(
                        pinId = oAuthResponse.id,
                        pinCode = oAuthResponse.code,
                    )

                startPolling(oAuthResponse.id)
            } catch (e: Exception) {
                Timber.e(e, "Error creating link PIN")
                _state.value =
                    PlexAuthState.Error(
                        "Failed to start authentication: ${e.message}",
                        e,
                    )
            }

            return state
        }

        /**
         * Starts polling the Plex API for authentication token.
         *
         * Polls at [POLLING_INTERVAL_MS] intervals (1.5 seconds).
         *
         * **Termination Conditions:**
         * - Token received (Success state)
         * - Timeout after [TIMEOUT_MS] (5 minutes)
         * - Job cancelled via [cancelAuth] or [dispose]
         *
         * @param pinId The PIN identifier being polled
         */
        private fun startPolling(pinId: Long) {
            startTime = System.currentTimeMillis()

            pollingJob =
                scope.launch {
                    delay(POLLING_INTERVAL_MS)

                    while (isActive) {
                        val elapsed = System.currentTimeMillis() - startTime

                        if (elapsed > TIMEOUT_MS) {
                            Timber.w("Link authentication timed out after ${elapsed}ms")
                            _state.value = PlexAuthState.Timeout
                            break
                        }

                        _state.value = PlexAuthState.Polling(pinId, elapsed)

                        try {
                            plexLoginRepo.checkForOAuthAccessToken()

                            val loginState = plexLoginRepo.loginEvent.value?.peekContent()
                            if (loginState != null &&
                                loginState != IPlexLoginRepo.LoginState.NOT_LOGGED_IN &&
                                loginState != IPlexLoginRepo.LoginState.AWAITING_LOGIN_RESULTS &&
                                loginState != IPlexLoginRepo.LoginState.FAILED_TO_LOG_IN
                            ) {
                                Timber.i("Link auth token obtained successfully, login state: $loginState")
                                _state.value = PlexAuthState.Success()
                                break
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error during link auth polling (continuing...)")
                            // Don't fail immediately - network errors might be transient
                            // Continue polling until timeout or success
                        }

                        delay(POLLING_INTERVAL_MS)
                    }
                }
        }

        /**
         * Cancels the ongoing authentication flow.
         *
         * **Use Cases:**
         * - User presses back/cancel button
         * - User navigates away from login screen
         * - App needs to abort authentication for any reason
         *
         * **State Transition:** Current state → Cancelled
         */
        fun cancelAuth() {
            Timber.i("Authentication cancelled by user")
            pollingJob?.cancel()
            pollingJob = null
            _state.value = PlexAuthState.Cancelled()
        }

        /**
         * Resets the coordinator to Idle state.
         *
         * Cancels any ongoing polling and clears state.
         * Useful for retrying authentication after failure/timeout.
         *
         * **State Transition:** Any state → Idle
         */
        fun reset() {
            Timber.d("Resetting auth coordinator to Idle state")
            pollingJob?.cancel()
            pollingJob = null
            _state.value = PlexAuthState.Idle
        }

        /**
         * Disposes of the coordinator and cleans up resources.
         *
         * **Must be called** when the coordinator is no longer needed (e.g. in
         * ViewModel.onCleared()).
         *
         * **Cleanup Actions:**
         * - Cancels polling job
         * - Does NOT reset state (preserves terminal state for observation)
         */
        fun dispose() {
            Timber.d("Disposing PlexAuthCoordinator")
            pollingJob?.cancel()
            pollingJob = null
        }
    }
