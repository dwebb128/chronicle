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
import androidx.compose.ui.Alignment
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
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
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
import local.oss.chronicle.ui.components.NowPlayingChip
import local.oss.chronicle.ui.components.OptionsDialog

/** Sized to sit just outside the 52dp download [Button] rather than on top of its icon. */
private val DOWNLOAD_RING_SIZE = 56.dp
private val DOWNLOAD_RING_STROKE = 3.dp

/**
 * Route: `book_details/{bookId}` (never the title — titles can contain slashes).
 *
 * Uses per-entity ViewModel scoping: the [AudiobookDetailsViewModel]
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
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                NowPlayingChip(navController = navController)
            }
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
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item {
                Text(
                    text = book?.author.orEmpty(),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item { BookProgress(viewModel) }
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
                    Box(contentAlignment = Alignment.Center) {
                        Button(onClick = { viewModel.onCacheButtonClick() }) {
                            Image(
                                painter =
                                    painterResource(
                                        if (cacheStatus == CACHED) {
                                            local.oss.chronicle.core.R.drawable.ic_cloud_done_white
                                        } else {
                                            local.oss.chronicle.core.R.drawable.ic_cloud_download_white
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
                        if (cacheStatus == CACHING) {
                            DownloadProgressRing(viewModel)
                        }
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
            if (cacheStatus == CACHING) {
                item { DownloadProgressLabel(viewModel) }
            }
            item {
                Chip(
                    onClick = { navController.navigate(Nav.NOW_PLAYING) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    label = { Text(text = "Now playing", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
            if (chapters.isNotEmpty()) {
                item { ListHeader { Text(text = stringResource(R.string.chapters)) } }
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

/**
 * Its own composable so the progress readout — which re-emits every time the player writes a new
 * position to the DB, a few times a minute while playing — invalidates one [Text] instead of
 * [BookDetailsScreen]'s whole restart scope, which re-ran the entire `ScalingLazyColumn` content
 * lambda and every visible row with it.
 */
@Composable
private fun BookProgress(viewModel: AudiobookDetailsViewModel) {
    val progressString by viewModel.progressString.observeAsState("0:00/0:00")
    Text(
        text = progressString,
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/**
 * A ring around the download button tracking how much of the book is on disk. Its own composable so
 * the progress ticks invalidate the ring rather than the whole screen.
 *
 * Indeterminate until the first percentage arrives, so the control never reads "0%" while the
 * download is in fact starting.
 */
@Composable
private fun DownloadProgressRing(viewModel: AudiobookDetailsViewModel) {
    val progress by viewModel.downloadProgress.observeAsState()
    val percent = progress
    if (percent == null) {
        CircularProgressIndicator(
            modifier = Modifier.size(DOWNLOAD_RING_SIZE),
            strokeWidth = DOWNLOAD_RING_STROKE,
        )
    } else {
        CircularProgressIndicator(
            progress = percent / 100f,
            modifier = Modifier.size(DOWNLOAD_RING_SIZE),
            strokeWidth = DOWNLOAD_RING_STROKE,
        )
    }
}

/** The same figure in words, for when a ring on a 48dp button is too small to read precisely. */
@Composable
private fun DownloadProgressLabel(viewModel: AudiobookDetailsViewModel) {
    val progress by viewModel.downloadProgress.observeAsState()
    Text(
        text =
            progress
                ?.let { stringResource(R.string.downloading_percent, it) }
                ?: stringResource(R.string.downloading),
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}
