package local.oss.chronicle.features.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import local.oss.chronicle.application.Injector
import local.oss.chronicle.data.sources.plex.IPlexLoginRepo
import local.oss.chronicle.data.sources.plex.PlexLoginService
import local.oss.chronicle.data.sources.plex.PlexPrefsRepo
import local.oss.chronicle.features.auth.PlexAuthCoordinator
import local.oss.chronicle.features.auth.PlexAuthState
import javax.inject.Inject

/**
 * Drives the plex.tv/link short-code login flow (see PLAN.md section 7).
 *
 * [LinkAccountScreen][local.oss.chronicle.ui.screens] is expected to:
 * - call [startLinkAccountAuth] once (e.g. from a `LaunchedEffect(Unit)`, or from a "Try again"
 *   button after a terminal state)
 * - render [authState] via `collectAsState()`, showing the PIN code on [PlexAuthState.WaitingForUser]
 *   and keeping the screen on for as long as that state persists
 * - show a visible "Try again" action on [PlexAuthState.Error], [PlexAuthState.Timeout], and
 *   [PlexAuthState.Cancelled] that calls [resetAuth] followed by [startLinkAccountAuth]
 * - call [cancelAuth] if the user backs out of the screen while waiting/polling
 *
 * On [PlexAuthState.Success], [IPlexLoginRepo.loginEvent] (observed higher up, in
 * `ChronicleWearApp`) will have already advanced past `AWAITING_LOGIN_RESULTS`, driving navigation
 * to the next login-state screen (choose user/server/library) — this ViewModel does not navigate.
 */
class LoginViewModel(
    plexLoginRepo: IPlexLoginRepo,
    plexLoginService: PlexLoginService,
    plexPrefsRepo: PlexPrefsRepo,
) : ViewModel() {
    class Factory
        @Inject
        constructor(
            private val plexLoginRepo: IPlexLoginRepo,
            private val plexLoginService: PlexLoginService,
            private val plexPrefsRepo: PlexPrefsRepo,
        ) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                    return LoginViewModel(plexLoginRepo, plexLoginService, plexPrefsRepo) as T
                }
                throw IllegalArgumentException("Unknown ViewHolder class")
            }
        }

    private val authCoordinator =
        PlexAuthCoordinator(plexLoginRepo, plexLoginService, plexPrefsRepo, viewModelScope)

    /** plex.tv/link auth state — observe with `collectAsState()`. */
    val authState: StateFlow<PlexAuthState> = authCoordinator.state

    /**
     * Starts (or, after a terminal state and [resetAuth], restarts) the plex.tv/link flow.
     * A no-op if a flow is already in progress (anything but [PlexAuthState.Idle]).
     */
    fun startLinkAccountAuth() {
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            authCoordinator.startAuth()
        }
    }

    /** Cancels the in-progress authentication flow (e.g. the user backed out of the screen). */
    fun cancelAuth() {
        authCoordinator.cancelAuth()
    }

    /** Resets a terminal state (Error/Timeout/Cancelled) back to Idle so [startLinkAccountAuth] can run again. */
    fun resetAuth() {
        authCoordinator.reset()
    }

    override fun onCleared() {
        authCoordinator.dispose()
        super.onCleared()
    }
}
