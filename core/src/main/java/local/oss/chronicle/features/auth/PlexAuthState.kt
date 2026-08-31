package local.oss.chronicle.features.auth

/**
 * Immutable state representation for the Plex authentication flow.
 *
 * This sealed class represents all possible states during the plex.tv/link short-code flow:
 * a PIN is created on the watch, its short human-typeable code is shown on screen, the user
 * enters that code at https://plex.tv/link on any other device, and the watch polls until the
 * PIN resolves to an auth token (or times out / errors / is cancelled).
 */
sealed class PlexAuthState {
    /**
     * Initial state before authentication begins.
     */
    object Idle : PlexAuthState()

    /**
     * State while creating a PIN with the Plex API.
     */
    object CreatingPin : PlexAuthState()

    /**
     * State after PIN is created. Waiting for the user to enter [pinCode] at plex.tv/link on
     * another device. The screen showing this state MUST stay on for the duration (no
     * screen-off/ambient) so the user can read the code.
     *
     * @property pinId The PIN identifier for polling
     * @property pinCode The short human-typeable code to display, to be entered at plex.tv/link
     */
    data class WaitingForUser(
        val pinId: Long,
        val pinCode: String,
    ) : PlexAuthState()

    /**
     * State while polling the Plex API for authentication token.
     *
     * @property pinId The PIN identifier being polled
     * @property elapsedMs Time elapsed since authentication started (for timeout detection)
     */
    data class Polling(
        val pinId: Long,
        val elapsedMs: Long,
    ) : PlexAuthState()

    /**
     * Terminal state: Authentication completed successfully.
     *
     * @property authToken The obtained authentication token
     */
    data class Success(
        val authToken: String = "",
    ) : PlexAuthState()

    /**
     * Terminal state: Authentication failed with an error.
     *
     * @property message Human-readable error message
     * @property throwable Optional exception that caused the error
     */
    data class Error(
        val message: String,
        val throwable: Throwable? = null,
    ) : PlexAuthState()

    /**
     * Terminal state: Authentication timed out (5 minutes).
     */
    object Timeout : PlexAuthState()

    /**
     * Terminal state: Authentication was cancelled by the user.
     *
     * @property reason Reason for cancellation (e.g., "User cancelled", "Back button pressed")
     */
    data class Cancelled(
        val reason: String = "User cancelled",
    ) : PlexAuthState()

    /**
     * Returns true if this state is a terminal state (no further transitions expected).
     */
    fun isTerminal(): Boolean =
        when (this) {
            is Success, is Error, is Timeout, is Cancelled -> true
            is Idle, is CreatingPin, is WaitingForUser, is Polling -> false
        }
}
