package local.oss.chronicle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import local.oss.chronicle.application.Injector
import local.oss.chronicle.data.sources.plex.IPlexLoginRepo
import local.oss.chronicle.injection.components.ActivityComponent
import local.oss.chronicle.ui.screens.BookDetailsScreen
import local.oss.chronicle.ui.screens.ChooseLibraryScreen
import local.oss.chronicle.ui.screens.ChooseServerScreen
import local.oss.chronicle.ui.screens.ChooseUserScreen
import local.oss.chronicle.ui.screens.LibraryScreen
import local.oss.chronicle.ui.screens.LinkAccountScreen
import local.oss.chronicle.ui.screens.NowPlayingScreen
import local.oss.chronicle.ui.screens.PlaybackSpeedScreen
import local.oss.chronicle.ui.screens.SettingsScreen
import local.oss.chronicle.ui.screens.SleepTimerScreen
import local.oss.chronicle.ui.theme.ChronicleTheme

/**
 * Provides the [ActivityComponent] built by [local.oss.chronicle.application.MainActivity] to
 * every composable below it, mirroring the DI scope the phone app's Activity used to give its
 * Fragments. Screens obtain their ViewModel factories from `LocalActivityComponent.current` — see
 * PLAN.md section 4 ("ViewModel-into-Compose pattern").
 */
val LocalActivityComponent =
    staticCompositionLocalOf<ActivityComponent> {
        error("LocalActivityComponent not provided — must be supplied by MainActivity's setContent {}")
    }

/**
 * Root composable for the whole app, installed by `MainActivity.setContent {}`.
 *
 * This is a WAVE 1 skeleton (see PLAN.md section 12): it wires the Scaffold, theme, and the full
 * [SwipeDismissableNavHost] route table from PLAN.md section 5.3, but the screen composables
 * themselves (the files in `ui/screens`) are built by Wave 2a. Each `composable(...)` block below is a
 * forward reference to a not-yet-created function; expected signatures are documented next to
 * each screen import site below and MUST be treated as a contract, not a suggestion, when Wave 2a
 * lands those files — nav argument extraction (bookId) already happens here and should not be
 * duplicated inside the screen.
 *
 * Login-state-driven auto-navigation (PLAN.md 5.3) is wired below: a `LaunchedEffect` observes
 * [IPlexLoginRepo.loginEvent] directly (via [Injector], not through `LoginViewModel` — the event
 * is a singleton-scoped signal every login step posts to, not something owned by one screen's
 * ViewModel) and navigates with `popUpTo(...) { inclusive = true }`, replicating the old
 * `Navigator.clearBackStack()` behavior. Built against the current, real
 * [IPlexLoginRepo.LoginState] transition chain (user, then server, then library); Wave 2c owns
 * `PlexLoginRepo`/`PlexAuthCoordinator` itself but is not expected to change this enum's shape.
 *
 * @param pendingRoute a one-shot route requested by [local.oss.chronicle.application.MainActivity]
 * from a notification tap or a "play audiobook X" launch intent (its `handleNotificationIntent`).
 * Consumed via [onPendingRouteConsumed] once navigated to, so a configuration change or
 * recomposition doesn't re-fire it.
 */
@Composable
fun ChronicleWearApp(
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
) {
    ChronicleTheme {
        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        ) {
            val navController = rememberSwipeDismissableNavController()

            // MainActivity calls determineLoginState() on every cold start, so a LOGGED_IN_FULLY
            // event lands just after this effect has already honoured a notification tap. Without
            // this flag its popUpTo(inclusive = true) would discard the deep-link destination and
            // drop the listener on the plain library list instead.
            var deepLinkNavigated by remember { mutableStateOf(false) }

            LaunchedEffect(pendingRoute) {
                if (pendingRoute != null) {
                    navController.navigate(pendingRoute)
                    deepLinkNavigated = true
                    onPendingRouteConsumed()
                }
            }

            // Login-state-driven auto-navigation (PLAN.md 5.3): PlexLoginRepo transitions
            // LOGGED_IN_NO_USER_CHOSEN -> NO_SERVER_CHOSEN -> NO_LIBRARY_CHOSEN, i.e. user, then
            // server, then library. popUpTo(...) { inclusive = true } replicates the old
            // Navigator.clearBackStack() so the back gesture never returns to a login step the
            // user has already completed.
            val plexLoginRepo = remember { Injector.get().plexLoginRepo() }
            val loginEvent by plexLoginRepo.loginEvent.observeAsState()
            LaunchedEffect(loginEvent) {
                val loginState = loginEvent?.getContentIfNotHandled() ?: return@LaunchedEffect
                val route =
                    when (loginState) {
                        IPlexLoginRepo.LoginState.NOT_LOGGED_IN,
                        IPlexLoginRepo.LoginState.FAILED_TO_LOG_IN,
                        IPlexLoginRepo.LoginState.AWAITING_LOGIN_RESULTS,
                        -> Nav.LINK_ACCOUNT
                        IPlexLoginRepo.LoginState.LOGGED_IN_NO_USER_CHOSEN -> Nav.CHOOSE_USER
                        IPlexLoginRepo.LoginState.LOGGED_IN_NO_SERVER_CHOSEN -> Nav.CHOOSE_SERVER
                        IPlexLoginRepo.LoginState.LOGGED_IN_NO_LIBRARY_CHOSEN -> Nav.CHOOSE_LIBRARY
                        IPlexLoginRepo.LoginState.LOGGED_IN_FULLY -> Nav.LIBRARY
                    }
                // LOGGED_IN_FULLY is the only state a deep link can outrank: every other one
                // means the requested screen cannot be shown yet, so the login redirect wins.
                if (route == Nav.LIBRARY && deepLinkNavigated) return@LaunchedEffect
                navController.navigate(route) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }

            SwipeDismissableNavHost(
                navController = navController,
                startDestination = Nav.LINK_ACCOUNT,
            ) {
                // fun LinkAccountScreen(navController: NavHostController)
                composable(Nav.LINK_ACCOUNT) { LinkAccountScreen(navController) }

                // fun ChooseUserScreen(navController: NavHostController)
                composable(Nav.CHOOSE_USER) { ChooseUserScreen(navController) }

                // fun ChooseServerScreen(navController: NavHostController)
                composable(Nav.CHOOSE_SERVER) { ChooseServerScreen(navController) }

                // fun ChooseLibraryScreen(navController: NavHostController)
                composable(Nav.CHOOSE_LIBRARY) { ChooseLibraryScreen(navController) }

                // fun LibraryScreen(navController: NavHostController)
                composable(Nav.LIBRARY) { LibraryScreen(navController) }

                // fun BookDetailsScreen(navController: NavHostController, bookId: String)
                composable(
                    route = Nav.BOOK_DETAILS_ROUTE,
                    arguments = listOf(navArgument(Nav.ARG_BOOK_ID) { type = NavType.StringType }),
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString(Nav.ARG_BOOK_ID).orEmpty()
                    BookDetailsScreen(navController, bookId)
                }

                // fun NowPlayingScreen(navController: NavHostController)
                composable(Nav.NOW_PLAYING) { NowPlayingScreen(navController) }

                // fun PlaybackSpeedScreen(navController: NavHostController)
                composable(Nav.PLAYBACK_SPEED) { PlaybackSpeedScreen(navController) }

                // fun SleepTimerScreen(navController: NavHostController)
                composable(Nav.SLEEP_TIMER) { SleepTimerScreen(navController) }

                // fun SettingsScreen(navController: NavHostController)
                composable(Nav.SETTINGS) { SettingsScreen(navController) }
            }
        }
    }
}
