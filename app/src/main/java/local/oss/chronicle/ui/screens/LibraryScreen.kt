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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import local.oss.chronicle.R
import local.oss.chronicle.application.Injector
import local.oss.chronicle.features.library.LibraryViewModel
import local.oss.chronicle.ui.Nav
import local.oss.chronicle.ui.components.BookRow
import local.oss.chronicle.ui.components.LoadingScreen
import local.oss.chronicle.ui.components.NowPlayingChip
import local.oss.chronicle.ui.rotaryScrollable

/**
 * The library list. [LibraryViewModel]'s dependencies (PLAN.md section 4) are all
 * [local.oss.chronicle.injection.components.AppComponent]-scoped singletons, so — unlike
 * [BookDetailsScreen]/[NowPlayingScreen]/[SettingsScreen] — its `Factory` is constructed directly
 * from [Injector] rather than via an `ActivityComponent` accessor; no Activity-scoped dependency
 * justifies adding one, and doing so would mean touching the `injection` package, which this wave does not
 * own.
 */
@Composable
fun LibraryScreen(navController: NavHostController) {
    val factory =
        remember {
            LibraryViewModel.Factory(
                bookRepository = Injector.get().bookRepo(),
                trackRepository = Injector.get().trackRepo(),
                prefsRepo = Injector.get().prefsRepo(),
                cachedFileManager = Injector.get().cachedFileManager(),
                librarySyncRepository = Injector.get().librarySyncRepo(),
                sharedPreferences = Injector.get().sharedPrefs(),
            )
        }
    val viewModel: LibraryViewModel = viewModel(factory = factory)

    val books by viewModel.books.observeAsState(emptyList())
    val isRefreshing by viewModel.isRefreshing.observeAsState(false)
    val listState = rememberScalingLazyListState()

    if (books.isEmpty() && isRefreshing) {
        LoadingScreen()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScrollable(listState),
        ) {
            item {
                NowPlayingChip(navController = navController)
            }
            if (books.isEmpty()) {
                item { Text(text = "Your library is empty") }
            } else {
                items(books) { book ->
                    BookRow(
                        book = book,
                        onClick = { navController.navigate(Nav.bookDetails(book.id)) },
                    )
                }
            }
            // The only entry point to Settings. The phone reached it from the bottom nav bar,
            // which has no Wear equivalent, so it lives at the end of the library list instead.
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.settings)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = { navController.navigate(Nav.SETTINGS) },
                )
            }
        }
        PositionIndicator(scalingLazyListState = listState)
    }
}
