package local.oss.chronicle.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberPickerState
import local.oss.chronicle.R
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel
import local.oss.chronicle.ui.LocalActivityComponent

private val SLEEP_TIMER_MINUTES = listOf(5, 15, 30, 40, 60, 90, 120)

/**
 * Sleep timer picker, replacing the phone's [local.oss.chronicle.ui.components.OptionsDialog]
 * ("append 5 min" / "end of chapter" / preset durations) with a [Picker] (PLAN.md 5.4). Uses
 * [CurrentlyPlayingViewModel.beginSleepTimer]/[CurrentlyPlayingViewModel.cancelSleepTimer], added
 * in this wave alongside the existing [CurrentlyPlayingViewModel.showSleepTimerOptions]
 * [local.oss.chronicle.ui.components.BottomChooserState] flow (kept, unused by Compose, for
 * anything still constructing it directly).
 */
@Composable
fun SleepTimerScreen(navController: NavHostController) {
    val activity = LocalContext.current as ComponentActivity
    val activityComponent = LocalActivityComponent.current
    val viewModel: CurrentlyPlayingViewModel =
        viewModel(
            viewModelStoreOwner = activity,
            factory = activityComponent.currentPlayingViewModelFactory(),
        )

    val isActive by viewModel.isSleepTimerActive.observeAsState(false)
    val remaining by viewModel.sleepTimerTimeRemainingString.observeAsState("0:00")
    val pickerState =
        rememberPickerState(
            initialNumberOfOptions = SLEEP_TIMER_MINUTES.size,
            initiallySelectedOption = 2,
        )

    // The vertical inset keeps the label and the confirm chip clear of the curve at the top and
    // bottom of a round display; the picker takes whatever is left rather than a fixed 100dp,
    // which on a 41mm watch pushed the button off the bottom of the screen.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isActive) {
            Text(
                text = "Sleeping in $remaining",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            CompactChip(
                onClick = { viewModel.cancelSleepTimer() },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text(text = stringResource(R.string.cancel), maxLines = 1) },
            )
        } else {
            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
            Picker(
                state = pickerState,
                contentDescription = "Sleep timer duration",
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { index ->
                Text(
                    text = "${SLEEP_TIMER_MINUTES[index]} min",
                    style = MaterialTheme.typography.title2,
                    maxLines = 1,
                )
            }
            CompactChip(
                onClick = {
                    val minutes = SLEEP_TIMER_MINUTES[pickerState.selectedOption]
                    viewModel.beginSleepTimer(minutes * 60_000L)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Start", maxLines = 1) },
            )
        }
    }
}
