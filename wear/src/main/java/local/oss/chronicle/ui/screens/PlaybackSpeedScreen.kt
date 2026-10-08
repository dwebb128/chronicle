package local.oss.chronicle.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberPickerState
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel.Companion.PLAYBACK_SPEED_MAX
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel.Companion.PLAYBACK_SPEED_MIN
import local.oss.chronicle.ui.LocalActivityComponent
import local.oss.chronicle.ui.components.LoadingScreen
import kotlin.math.roundToInt

/**
 * Playback speed picker, replacing the phone's `ModalBottomSheetSpeedChooser` bottom sheet with a
 * [Picker] (PLAN.md 5.4). Speeds are offered in 0.1x steps across
 * [CurrentlyPlayingViewModel.PLAYBACK_SPEED_MIN]..[CurrentlyPlayingViewModel.PLAYBACK_SPEED_MAX].
 */
@Composable
fun PlaybackSpeedScreen(navController: NavHostController) {
    val activity = LocalContext.current as ComponentActivity
    val activityComponent = LocalActivityComponent.current
    val viewModel: CurrentlyPlayingViewModel =
        viewModel(
            viewModelStoreOwner = activity,
            factory = activityComponent.currentPlayingViewModelFactory(),
        )

    val speeds =
        remember {
            val minTenths = (PLAYBACK_SPEED_MIN * 10).roundToInt()
            val maxTenths = (PLAYBACK_SPEED_MAX * 10).roundToInt()
            (minTenths..maxTenths).map { it / 10f }
        }
    // Deliberately no default: `speed` is backed by a preference LiveData that only emits once
    // `observeAsState` has subscribed, i.e. after the first composition. Seeding the picker from a
    // placeholder would set `initiallySelectedOption` to 1.0x — and since `rememberPickerState`
    // keeps whatever it was first given, the LaunchedEffect below would then write that 1.0x back
    // over the speed the listener had actually chosen, every time this screen was opened.
    val currentSpeed = viewModel.speed.observeAsState().value
    if (currentSpeed == null) {
        LoadingScreen()
        return
    }

    val pickerState =
        rememberPickerState(
            initialNumberOfOptions = speeds.size,
            initiallySelectedOption =
                speeds
                    .indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }
                    .coerceAtLeast(0),
        )

    LaunchedEffect(pickerState.selectedOption) {
        val selected = speeds[pickerState.selectedOption]
        if (kotlin.math.abs(selected - currentSpeed) >= 0.01f) {
            viewModel.setPlaybackSpeed(selected)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Playback speed",
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurfaceVariant,
            maxLines = 1,
        )
        Picker(
            state = pickerState,
            contentDescription = "Playback speed",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { index ->
            Text(
                text = "%.1fx".format(speeds[index]),
                style = MaterialTheme.typography.title2,
                maxLines = 1,
            )
        }
    }
}
