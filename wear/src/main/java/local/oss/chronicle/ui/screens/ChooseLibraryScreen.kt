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
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import local.oss.chronicle.R
import local.oss.chronicle.application.Injector
import local.oss.chronicle.data.model.LoadingStatus
import local.oss.chronicle.features.login.ChooseLibraryViewModel
import local.oss.chronicle.ui.LocalActivityComponent
import local.oss.chronicle.ui.components.ErrorScreen
import local.oss.chronicle.ui.components.LoadingScreen

/**
 * Last screen in the login chain: user, then server, then library (PLAN.md 5.3).
 *
 * NOTE: unlike [ChooseUserViewModel.pickUser]/[ChooseServerViewModel.chooseServer],
 * [ChooseLibraryViewModel] never wraps [local.oss.chronicle.data.sources.plex.IPlexLoginRepo.chooseLibrary] —
 * the phone's `ChooseLibraryFragment` called it directly on an injected `IPlexLoginRepo` instead
 * of through the ViewModel. This screen does the same via [Injector] rather than adding a method
 * to [ChooseLibraryViewModel], which would mean touching a file this wave does not own.
 */
@Composable
fun ChooseLibraryScreen(navController: NavHostController) {
    val activityComponent = LocalActivityComponent.current
    val viewModel: ChooseLibraryViewModel =
        viewModel(factory = activityComponent.chooseLibraryViewModelFactory())
    val plexLoginRepo = remember { Injector.get().plexLoginRepo() }

    val libraries by viewModel.libraries.observeAsState(emptyList())
    val loadingStatus by viewModel.loadingStatus.observeAsState(LoadingStatus.LOADING)
    val listState = rememberScalingLazyListState()

    when (loadingStatus) {
        LoadingStatus.LOADING -> LoadingScreen()
        LoadingStatus.ERROR -> ErrorScreen(message = "Unable to load libraries", onRetry = viewModel::refresh)
        LoadingStatus.DONE ->
            Box(modifier = Modifier.fillMaxSize()) {
                ScalingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        ListHeader {
                            Text(
                                text = stringResource(R.string.choose_library),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    items(libraries) { library ->
                        Chip(
                            onClick = { plexLoginRepo.chooseLibrary(library) },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    text = library.name,
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
}
