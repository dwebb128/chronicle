package local.oss.chronicle.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import local.oss.chronicle.R
import local.oss.chronicle.application.Injector
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_AUTHOR
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_DATE_ADDED
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_DATE_PLAYED
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_DURATION
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_PLAYS
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_TITLE
import local.oss.chronicle.data.model.Audiobook.Companion.SORT_KEY_YEAR
import local.oss.chronicle.features.library.LibraryViewModel

/** The label shown for each key in [LibraryViewModel.SUPPORTED_SORT_KEYS]. */
private fun sortKeyLabel(key: String): Int =
    when (key) {
        SORT_KEY_TITLE -> R.string.sort_by_title
        SORT_KEY_AUTHOR -> R.string.sort_by_author
        SORT_KEY_DATE_ADDED -> R.string.sort_by_date_added
        SORT_KEY_DATE_PLAYED -> R.string.sort_by_date_played
        SORT_KEY_DURATION -> R.string.sort_by_duration
        SORT_KEY_PLAYS -> R.string.sort_by_plays
        SORT_KEY_YEAR -> R.string.sort_by_year
        else -> R.string.sort_by_title
    }

/**
 * Sorting and filtering for the library list. The phone puts these in a bottom sheet over the
 * library; a watch has no room for that, so they get their own screen reached from the top of the
 * list.
 *
 * The state all lives in preferences that [LibraryViewModel] already observes, so every change here
 * reorders the library behind this screen immediately — there is nothing to apply or confirm, and
 * swiping back is the only way out.
 */
@Composable
fun LibraryFilterScreen() {
    val factory =
        remember {
            LibraryViewModel.Factory(
                bookRepository = Injector.get().bookRepo(),
                trackRepository = Injector.get().trackRepo(),
                prefsRepo = Injector.get().prefsRepo(),
                cachedFileManager = Injector.get().cachedFileManager(),
                librarySyncRepository = Injector.get().librarySyncRepo(),
                sharedPreferences = Injector.get().sharedPrefs(),
                plexConfig = Injector.get().plexConfig(),
            )
        }
    val viewModel: LibraryViewModel = viewModel(factory = factory)

    val sortKey by viewModel.sortKey.observeAsState(SORT_KEY_TITLE)
    val isDescending by viewModel.isSortDescending.observeAsState(true)
    val hidePlayed by viewModel.arePlayedAudiobooksHidden.observeAsState(false)

    val listState = rememberScalingLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text(text = stringResource(R.string.library_sort_and_filter)) } }
            item {
                ToggleChip(
                    checked = hidePlayed,
                    onCheckedChange = { viewModel.setHidePlayedAudiobooks(it) },
                    label = {
                        Text(
                            text = stringResource(R.string.library_hide_played),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(hidePlayed),
                            contentDescription = if (hidePlayed) "On" else "Off",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ToggleChip(
                    checked = isDescending,
                    onCheckedChange = { viewModel.setSortDescending(it) },
                    label = {
                        Text(
                            text = stringResource(R.string.library_sort_descending),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(isDescending),
                            contentDescription = if (isDescending) "On" else "Off",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { ListHeader { Text(text = stringResource(R.string.sort_by)) } }
            items(LibraryViewModel.SUPPORTED_SORT_KEYS) { key ->
                val isSelected = key == sortKey
                Chip(
                    onClick = { viewModel.setSortKey(key) },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        if (isSelected) {
                            ChipDefaults.primaryChipColors()
                        } else {
                            ChipDefaults.secondaryChipColors()
                        },
                    label = {
                        Text(
                            text = stringResource(sortKeyLabel(key)),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        PositionIndicator(scalingLazyListState = listState)
    }
}
