package local.oss.chronicle.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import local.oss.chronicle.R
import local.oss.chronicle.features.settings.SettingsViewModel
import local.oss.chronicle.ui.LocalActivityComponent
import local.oss.chronicle.ui.components.NowPlayingChip
import local.oss.chronicle.ui.components.OptionsDialog

/**
 * Settings screen against the rewritten [SettingsViewModel] (PLAN.md 5.7). Surviving rows only:
 * offline mode, jump forward/back intervals, auto-rewind, skip-silent-audio,
 * pause-on-interruption, refresh rate, delete downloaded files, log out, version/about. Everything
 * cut in PLAN.md 1.2 (premium, book-cover style, sync location, Android Auto, subreddit/GitHub/
 * licenses, the debug-info Easter egg) has no row here at all.
 */
@Composable
fun SettingsScreen(navController: NavHostController) {
    val activityComponent = LocalActivityComponent.current
    val viewModel: SettingsViewModel = viewModel(factory = activityComponent.settingsViewModelFactory())

    val offlineMode by viewModel.offlineMode.observeAsState(false)
    val skipSilence by viewModel.skipSilence.observeAsState(false)
    val autoRewind by viewModel.autoRewind.observeAsState(false)
    val pauseOnFocusLost by viewModel.pauseOnFocusLost.observeAsState(false)
    val jumpForwardSeconds by viewModel.jumpForwardSeconds.observeAsState(30L)
    val jumpBackwardSeconds by viewModel.jumpBackwardSeconds.observeAsState(10L)
    val bottomChooserState by viewModel.bottomChooserState.observeAsState()

    val listState = rememberScalingLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { NowPlayingChip(navController = navController) }

            item {
                ToggleChip(
                    checked = offlineMode,
                    onCheckedChange = { viewModel.setOfflineMode(it) },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_offline_mode_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(offlineMode),
                            contentDescription = if (offlineMode) "On" else "Off",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ToggleChip(
                    checked = skipSilence,
                    onCheckedChange = { viewModel.setSkipSilence(it) },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_skip_silent_audio),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(skipSilence),
                            contentDescription = if (skipSilence) "On" else "Off",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ToggleChip(
                    checked = autoRewind,
                    onCheckedChange = { viewModel.setAutoRewind(it) },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_auto_rewind),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(autoRewind),
                            contentDescription = if (autoRewind) "On" else "Off",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ToggleChip(
                    checked = pauseOnFocusLost,
                    onCheckedChange = { viewModel.setPauseOnFocusLost(it) },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_pause_on_focus_lost_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(pauseOnFocusLost),
                            contentDescription = if (pauseOnFocusLost) "On" else "Off",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { viewModel.showJumpForwardChooser() },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = stringResource(R.string.settings_jump_forward_title),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    secondaryLabel = { Text(text = "${jumpForwardSeconds}s") },
                )
            }
            item {
                Chip(
                    onClick = { viewModel.showJumpBackwardChooser() },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = stringResource(R.string.settings_jump_backward_title),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    secondaryLabel = { Text(text = "${jumpBackwardSeconds}s") },
                )
            }
            item {
                Chip(
                    onClick = { viewModel.showRefreshRateChooser() },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = stringResource(R.string.settings_refresh_rate_title),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
            item {
                Chip(
                    onClick = { viewModel.confirmDeleteDownloadedFiles() },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = stringResource(R.string.settings_delete_synced_title),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
            item {
                Chip(
                    onClick = { viewModel.confirmLogOut() },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = stringResource(R.string.settings_log_out),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
            item {
                Text(
                    text = "${stringResource(R.string.settings_version_title)}: ${viewModel.versionName}",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PositionIndicator(scalingLazyListState = listState)
        bottomChooserState?.let { OptionsDialog(state = it) }
    }
}
