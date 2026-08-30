package local.oss.chronicle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
val LocalActivityComponent = staticCompositionLocalOf<ActivityComponent> {
    error("LocalActivityComponent not provided — must be supplied by MainActivity's setContent {}")
}

/**
 * Root composable for the whole app, installed by `MainActivity.setContent {}`.
 *
 * This is a WAVE 1 skeleton (see PLAN.md section 12): it wires the Scaffold, theme, and the full
 * [SwipeDismissableNavHost] route table from PLAN.md section 5.3, but the screen composables
 * themselves (`ui/screens/*.kt`) are built by Wave 2a. Each `composable(...)` block below is a
 * forward reference to a not-yet-created function; expected signatures are documented next to
 * each screen import site below and MUST be treated as a contract, not a suggestion, when Wave 2a
 * lands those files — nav argument extraction (bookId) already happens here and should not be
 * duplicated inside the screen.
 *
 * Login-state-driven auto-navigation (LoginState -> start destination / auto-advance as the user
 * logs in — PLAN.md 5.3) is intentionally NOT wired here: it depends on the real `LoginViewModel`
 * shape, which Wave 2c is revising for the plex.tv/link flow (PLAN.md section 7). Wave 2a/2c
 * should add a `LaunchedEffect` here (or in a thin wrapper) observing
 * `IPlexLoginRepo.loginEvent`/`LoginViewModel` and calling
 * `navController.navigate(route) { popUpTo(navController.graph.id) { inclusive = true } }` to
 * replicate the old `Navigator.clearBackStack()` behavior described there.
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

            LaunchedEffect(pendingRoute) {
                if (pendingRoute != null) {
                    navController.navigate(pendingRoute)
                    onPendingRouteConsumed()
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
