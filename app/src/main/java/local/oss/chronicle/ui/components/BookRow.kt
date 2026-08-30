package local.oss.chronicle.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import local.oss.chronicle.application.Injector
import local.oss.chronicle.data.model.Audiobook

/**
 * A single row in [local.oss.chronicle.ui.screens.LibraryScreen], rendered as a [Chip] inside a
 * `ScalingLazyColumn` item. Cover art is loaded via Coil against the current server connection
 * ([PlexConfig.makeThumbUri] — synchronous, unlike the library-aware
 * `makeThumbUriForLibrary`, which is fine here because a Wear session only ever browses one
 * active library at a time).
 */
@Composable
fun BookRow(
    book: Audiobook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plexConfig = remember { Injector.get().plexConfig() }
    val thumbUri = remember(book.thumb) { plexConfig.makeThumbUri(book.thumb).toString() }
    Chip(
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(text = book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = {
            Text(text = book.author, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        icon = {
            AsyncImage(
                model = thumbUri,
                contentDescription = null,
                modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        },
    )
}
