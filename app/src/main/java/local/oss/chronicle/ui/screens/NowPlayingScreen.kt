package local.oss.chronicle.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
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
import timber.log.Timber

/**
 * Opens the system Bluetooth/audio-output screen so the listener can pair or connect a headset
 * (Pixel Buds and the like) without leaving the watch.
 *
 * Wear OS ships its own settings app, and which activity answers depends on the OEM and the
 * platform version, so this tries the Clockwork-specific screen first and falls back to the
 * AOSP-standard [Settings.ACTION_BLUETOOTH_SETTINGS]. Wear cannot *initiate* a connection to a
 * specific device from a third-party app — `BluetoothDevice.connect` is a system API — so handing
 * the listener the picker is as close to "auto-connect to my Pixel Buds" as an app can get.
 */
private fun launchAudioOutputSettings(context: Context) {
    val intents =
        listOf(
            Intent("com.google.android.clockwork.settings.BLUETOOTH_SETTINGS"),
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
        )
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            Timber.w(e, "Could not launch Bluetooth intent: ${intent.action}")
        }
    }
}

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

    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    // The slider is not the only thing that moves this stream: the rotary crown below, the
    // hardware buttons and the system volume panel all do too. Without this the slider would show
    // a stale value the moment any of them is used.
    DisposableEffect(audioManager) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    val stream =
                        intent?.getIntExtra(
                            "android.media.EXTRA_VOLUME_STREAM_TYPE",
                            -1,
                        ) ?: -1
                    if (stream == AudioManager.STREAM_MUSIC) {
                        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    }
                }
            }
        // Not exported: only the system sends VOLUME_CHANGED_ACTION, and :app is minSdk 34, so
        // the flag is always available here.
        activity.registerReceiver(
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            Context.RECEIVER_NOT_EXPORTED,
        )
        // Re-read on (re)attach in case the volume moved while this screen was away.
        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        onDispose { activity.unregisterReceiver(receiver) }
    }

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
                    // No FLAG_SHOW_UI: the on-screen slider below already shows the level, and the
                    // system volume overlay would sit on top of it.
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                    volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
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
                CompactChip(
                    onClick = { launchAudioOutputSettings(activity) },
                    label = {
                        Text(
                            text = stringResource(R.string.no_bluetooth_output_warning),
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.error,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
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
            // Media volume. The rotary crown drives the same stream (see the Box modifier above);
            // this is the touch equivalent, for listeners who reach for the screen instead.
            InlineSlider(
                value = volume,
                onValueChange = { newVolume ->
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                    volume = newVolume
                },
                valueProgression = 0..maxVolume,
                decreaseIcon = {
                    Icon(
                        imageVector = InlineSliderDefaults.Decrease,
                        contentDescription = "Lower volume",
                    )
                },
                increaseIcon = {
                    Icon(
                        imageVector = InlineSliderDefaults.Increase,
                        contentDescription = "Raise volume",
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                CompactChip(
                    onClick = { navController.navigate(Nav.PLAYBACK_SPEED) },
                    label = { Text("Speed") },
                )
                CompactChip(
                    onClick = { navController.navigate(Nav.SLEEP_TIMER) },
                    label = { Text("Sleep") },
                )
                CompactChip(
                    onClick = { launchAudioOutputSettings(activity) },
                    label = { Text("Audio") },
                )
            }
        }
        bottomChooserState?.let { OptionsDialog(state = it) }
    }
}
