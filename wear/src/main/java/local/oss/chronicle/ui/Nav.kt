package local.oss.chronicle.ui

/**
 * Route constants for the [androidx.wear.compose.navigation.SwipeDismissableNavHost] built in
 * [ChronicleWearApp].
 *
 * Order mirrors the real [local.oss.chronicle.data.sources.plex.IPlexLoginRepo.LoginState]
 * transition chain — user, then server, then library (see PLAN.md section 5.3) — NOT the order
 * screens are necessarily reachable from Library once logged in.
 */
object Nav {
    const val LINK_ACCOUNT = "link_account"
    const val CHOOSE_USER = "choose_user"
    const val CHOOSE_SERVER = "choose_server"
    const val CHOOSE_LIBRARY = "choose_library"
    const val LIBRARY = "library"
    const val NOW_PLAYING = "now_playing"
    const val PLAYBACK_SPEED = "playback_speed"
    const val SLEEP_TIMER = "sleep_timer"
    const val SETTINGS = "settings"

    /** Argument name embedded in [BOOK_DETAILS_ROUTE]. Never put a title in a route — titles
     * contain slashes. */
    const val ARG_BOOK_ID = "bookId"

    /** Route pattern registered with the [androidx.wear.compose.navigation.SwipeDismissableNavHost]. */
    const val BOOK_DETAILS_ROUTE = "book_details/{$ARG_BOOK_ID}"

    /** Builds a concrete, navigable route for a given [bookId]. */
    fun bookDetails(bookId: String): String = "book_details/$bookId"
}
