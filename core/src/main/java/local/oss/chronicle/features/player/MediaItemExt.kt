package local.oss.chronicle.features.player

import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaDescriptionCompat
import androidx.annotation.DrawableRes
import local.oss.chronicle.core.BuildConfig
import local.oss.chronicle.data.sources.plex.CONTENT_STYLE_LIST_ITEM_HINT_VALUE
import local.oss.chronicle.data.sources.plex.CONTENT_STYLE_PLAYABLE_HINT
import local.oss.chronicle.data.sources.plex.CONTENT_STYLE_SUPPORTED

/**
 * Create a message item for Android Auto error/info display.
 * Uses FLAG_PLAYABLE so Android Auto displays the item (0 flags would be filtered out).
 * When tapped, AudiobookMediaSessionCallback sets an error PlaybackState to show the message.
 *
 * @param title The main message to display
 * @param subtitle Optional subtitle/additional context (shown below title)
 * @param iconRes Icon resource to display
 * @param mediaId Unique identifier for this message item (must start with "__error" or "__message")
 */
fun makeMessageItem(
    title: String,
    subtitle: String = "",
    @DrawableRes iconRes: Int,
    mediaId: String = "__message__",
): MediaItem {
    val extras = Bundle()
    extras.putBoolean(CONTENT_STYLE_SUPPORTED, true)
    extras.putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)

    val mediaDescription =
        MediaDescriptionCompat.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIconUri(
                Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/$iconRes"),
            )
            .setMediaId(mediaId)
            .setExtras(extras)
            .build()
    // FLAG_PLAYABLE ensures Android Auto displays the item (0 flags would be filtered out)
    // When tapped, AudiobookMediaSessionCallback sets an error PlaybackState
    return MediaItem(mediaDescription, FLAG_PLAYABLE)
}
