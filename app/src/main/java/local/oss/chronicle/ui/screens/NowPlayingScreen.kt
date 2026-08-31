package local.oss.chronicle.ui.screens

import android.content.Context
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import local.oss.chronicle.R
import local.oss.chronicle.application.Injector
import local.oss.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel
import local.oss.chronicle.ui.LocalActivityComponent
import local.oss.chronicle.ui.Nav
import local.oss.chronicle.ui.components.LoadingScreen
import local.oss.chronicle.ui.components.OptionsDialog

/**
 * The now-playing / transport-controls screen. [CurrentlyPlayingViewModel] is Activity-scoped
 * (PLAN.md section 4) so this screen and [local.oss.chronicle.ui.components.NowPlayingChip] share
 * the exact same instance rather than each getting their own.
 *
 * Rotary crown input controls system media volume here (PLAN.md 5.4/D29), via
 * [AudioManager.adjustStreamVolume] — deliberately *not* wired to a `ScalingLazyColumn`/scroll
 * (this screen has no scrolling list), unlike every other list screen in this app.
 */
@Composable
fun NowPlayingScreen(navController: NavHostController) {
    val activity = LocalContext.current as ComponentActivity
    val activityComponent = LocalActivityComponent.current
    val viewModel: CurrentlyPlayingViewModel =
        viewModel(
            viewModelStoreOwner = activity,
            factory = activityComponent.currentPlayingViewModelFactory(),
        )

    val book by viewModel.audiobook.observeAsState()
    val activeChapter by viewModel.activeChapter.observeAsState()
    val isPlaying by viewModel.isPlaying.observeAsState(false)
    val chapterProgressString by viewModel.chapterProgressString.observeAsState("0:00")
    val chapterDurationString by viewModel.chapterDurationString.observeAsState("0:00")
    val jumpForwardsIcon by viewModel.jumpForwardsIcon.observeAsState(local.oss.chronicle.core.R.drawable.ic_forward_30_white)
    val jumpBackwardsIcon by viewModel.jumpBackwardsIcon.observeAsState(local.oss.chronicle.core.R.drawable.ic_replay_10_white)
    val bottomChooserState by viewModel.bottomChooserState.observeAsState()
    val hasBluetoothAudio by Injector.get().audioOutputMonitor().hasBluetoothAudio.collectAsState()

    val currentBook = book
    if (currentBook == null || currentBook.id == NO_AUDIOBOOK_FOUND_ID) {
        LoadingScreen()
        return
    }

    val audioManager =
        remember { activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    val direction =
                        if (event.verticalScrollPixels > 0) {
                            AudioManager.ADJUST_RAISE
                        } else {
                            AudioManager.ADJUST_LOWER
                        }
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        direction,
                        AudioManager.FLAG_SHOW_UI,
                    )
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Non-blocking: playback through the watch speaker still works, but on a wrist
            // that is rarely what the listener wants, so say so rather than silently doing it.
            if (!hasBluetoothAudio) {
                Text(
                    text = stringResource(R.string.no_bluetooth_output_warning),
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(text = currentBook.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = activeChapter?.title.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = "$chapterProgressString / $chapterDurationString")
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { viewModel.skipBackwards() }) {
                    Image(painter = painterResource(jumpBackwardsIcon), contentDescription = "Jump back")
                }
                Button(onClick = { viewModel.play() }) {
                    Image(
                        painter =
                            painterResource(
                                if (isPlaying) {
                                    R.drawable.ic_pause_button_large_colored
                                } else {
                                    R.drawable.ic_play_button_large_colored
                                },
                            ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                    )
                }
                Button(onClick = { viewModel.skipForwards() }) {
                    Image(painter = painterResource(jumpForwardsIcon), contentDescription = "Jump forward")
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { viewModel.skipToPrevious() }) {
                    Image(
                        painter = painterResource(local.oss.chronicle.core.R.drawable.ic_skip_previous_white),
                        contentDescription = "Previous chapter",
                    )
                }
                Button(onClick = { viewModel.skipToNext() }) {
                    Image(
                        painter = painterResource(local.oss.chronicle.core.R.drawable.ic_skip_next_white),
                        contentDescription = "Next chapter",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                CompactChip(
                    onClick = { navController.navigate(Nav.PLAYBACK_SPEED) },
                    label = { Text("Speed") },
                )
                CompactChip(
                    onClick = { navController.navigate(Nav.SLEEP_TIMER) },
                    label = { Text("Sleep") },
                )
            }
        }
        bottomChooserState?.let { OptionsDialog(state = it) }
    }
}
