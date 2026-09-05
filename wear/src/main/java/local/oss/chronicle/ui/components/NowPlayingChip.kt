package local.oss.chronicle.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Text
import local.oss.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel
import local.oss.chronicle.ui.LocalActivityComponent
import local.oss.chronicle.ui.Nav

/**
 * A "continue listening" style chip shown at the top of Library, BookDetails and Settings
 * (PLAN.md 5.3/D31), so Now Playing is reachable without swiping back to the root. Tapping it
 * navigates to [Nav.NOW_PLAYING].
 *
 * [CurrentlyPlayingViewModel] is Activity-scoped (PLAN.md section 4): it is created here with the
 * host Activity as its `viewModelStoreOwner` so this chip observes the exact same instance
 * `NowPlayingScreen` does, rather than a fresh one scoped to whichever `NavBackStackEntry` happens
 * to host this composable.
 */
@Composable
fun NowPlayingChip(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val activityComponent = LocalActivityComponent.current
    val activity = LocalContext.current as ComponentActivity
    val viewModel: CurrentlyPlayingViewModel =
        viewModel(
            viewModelStoreOwner = activity,
            factory = activityComponent.currentPlayingViewModelFactory(),
        )
    val audiobook by viewModel.audiobook.observeAsState()
    val isPlaying by viewModel.isPlaying.observeAsState(false)

    val book = audiobook
    if (book == null || book.id == NO_AUDIOBOOK_FOUND_ID) {
        return
    }

    Chip(
        onClick = { navController.navigate(Nav.NOW_PLAYING) },
        modifier = modifier.fillMaxWidth(),
        label = {
            Text(text = book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = {
            Text(
                text = if (isPlaying) "Now playing" else "Paused",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
