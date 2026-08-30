package local.oss.chronicle.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import local.oss.chronicle.R
import local.oss.chronicle.application.Injector
import local.oss.chronicle.data.sources.plex.ICachedFileManager.CacheStatus.CACHED
import local.oss.chronicle.data.sources.plex.ICachedFileManager.CacheStatus.CACHING
import local.oss.chronicle.data.sources.plex.ICachedFileManager.CacheStatus.NOT_CACHED
import local.oss.chronicle.features.bookdetails.AudiobookDetailsViewModel
import local.oss.chronicle.ui.LocalActivityComponent
import local.oss.chronicle.ui.Nav
import local.oss.chronicle.ui.components.ChapterRow
import local.oss.chronicle.ui.components.LoadingScreen
import local.oss.chronicle.ui.components.OptionsDialog
import local.oss.chronicle.ui.rotaryScrollable

/**
 * Route: `book_details/{bookId}` (never the title — titles can contain slashes, PLAN.md 5.3).
 *
 * Follows PLAN.md section 4's per-entity ViewModel scoping exactly: the [AudiobookDetailsViewModel]
 * is not created until [local.oss.chronicle.data.local.IBookRepository.getAudiobook] resolves a
 * non-null [local.oss.chronicle.data.model.Audiobook] (rendering [LoadingScreen] until then), and
 * `viewModel(key = bookId, ...)` is used so navigating from one book to another creates a fresh
 * ViewModel instead of returning the previous book's cached one (`ViewModelProvider` only calls
 * `create()` on a cache miss keyed by class — without `key`, two different books would collide).
 */
@Composable
fun BookDetailsScreen(
    navController: NavHostController,
    bookId: String,
) {
    val bookRepository = remember { Injector.get().bookRepo() }
    val audiobookLiveData = remember(bookId) { bookRepository.getAudiobook(bookId) }
    val audiobook by audiobookLiveData.observeAsState()
    val inputBook =
        audiobook ?: run {
            LoadingScreen()
            return
        }

    val activityComponent = LocalActivityComponent.current
    val factory =
        remember(bookId) {
            activityComponent.audiobookDetailsViewModelFactory().apply {
                inputAudiobook = inputBook
            }
        }
    val viewModel: AudiobookDetailsViewModel = viewModel(key = bookId, factory = factory)

    val book by viewModel.audiobook.observeAsState(inputBook)
    val chapters by viewModel.chapters.observeAsState(emptyList())
    val activeChapter by viewModel.activeChapter.observeAsState()
    val isBookPlaying by viewModel.isBookInViewPlaying.observeAsState(false)
    val cacheStatus by viewModel.cacheStatus.observeAsState(NOT_CACHED)
    val progressString by viewModel.progressString.observeAsState("0:00/0:00")
    val bottomChooserState by viewModel.bottomChooserState.observeAsState()
    val plexConfig = remember { Injector.get().plexConfig() }
    val thumbUri =
        remember(book?.thumb) {
            plexConfig.makeThumbUri(book?.thumb.orEmpty()).toString()
        }

    val listState = rememberScalingLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScrollable(listState),
        ) {
            item {
                AsyncImage(
                    model = thumbUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                )
            }
            item {
                Text(
                    text = book?.title.orEmpty(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item {
                Text(
                    text = book?.author.orEmpty(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item {
                Text(text = progressString, textAlign = TextAlign.Center)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Button(onClick = { viewModel.pausePlayButtonClicked() }) {
                        Image(
                            painter =
                                painterResource(
                                    if (isBookPlaying) {
                                        R.drawable.ic_pause_button_large_colored
                                    } else {
                                        R.drawable.ic_play_button_large_colored
                                    },
                                ),
                            contentDescription = if (isBookPlaying) "Pause" else "Play",
                        )
                    }
                    Button(onClick = { viewModel.onCacheButtonClick() }) {
                        Image(
                            painter =
                                painterResource(
                                    if (cacheStatus == CACHED) {
                                        R.drawable.ic_cloud_done_white
                                    } else {
                                        R.drawable.ic_cloud_download_white
                                    },
                                ),
                            contentDescription =
                                when (cacheStatus) {
                                    CACHED -> "Downloaded"
                                    CACHING -> "Downloading"
                                    NOT_CACHED -> "Download"
                                },
                        )
                    }
                    Button(onClick = { viewModel.toggleWatched() }) {
                        Image(
                            painter =
                                painterResource(
                                    if ((book?.viewCount ?: 0L) == 0L) {
                                        R.drawable.ic_visibility
                                    } else {
                                        R.drawable.ic_visibility_off
                                    },
                                ),
                            contentDescription = "Toggle played",
                        )
                    }
                }
            }
            item {
                Chip(
                    onClick = { navController.navigate(Nav.NOW_PLAYING) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    label = { Text(text = "Now playing", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
            if (chapters.isNotEmpty()) {
                item { Text(text = stringResource(R.string.chapters)) }
            }
            items(chapters) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    isActive =
                        activeChapter?.trackId == chapter.trackId &&
                            activeChapter?.startTimeOffset == chapter.startTimeOffset,
                    onClick = { viewModel.jumpToChapter(chapter.startTimeOffset, chapter.trackId) },
                )
            }
        }
        PositionIndicator(scalingLazyListState = listState)
        bottomChooserState?.let { OptionsDialog(state = it) }
    }
}
