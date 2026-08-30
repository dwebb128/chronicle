package local.oss.chronicle.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberPickerState
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel.Companion.PLAYBACK_SPEED_DEFAULT
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel.Companion.PLAYBACK_SPEED_MAX
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel.Companion.PLAYBACK_SPEED_MIN
import local.oss.chronicle.ui.LocalActivityComponent
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
    val currentSpeed by viewModel.speed.observeAsState(PLAYBACK_SPEED_DEFAULT)
    val initialIndex =
        remember(currentSpeed) {
            speeds.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }.coerceAtLeast(0)
        }
    val pickerState =
        rememberPickerState(
            initialNumberOfOptions = speeds.size,
            initiallySelectedOption = initialIndex,
        )

    LaunchedEffect(pickerState.selectedOption) {
        viewModel.setPlaybackSpeed(speeds[pickerState.selectedOption])
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Playback speed")
        Picker(
            state = pickerState,
            modifier = Modifier.size(80.dp, 100.dp),
        ) { index ->
            Text(text = "%.1fx".format(speeds[index]))
        }
    }
}
