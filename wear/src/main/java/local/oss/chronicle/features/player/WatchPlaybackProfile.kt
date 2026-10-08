package local.oss.chronicle.features.player

import androidx.media3.common.C
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import local.oss.chronicle.core.R

/**
 * How the shared playback service is tuned on a watch.
 *
 * Buffers are far smaller than the phone's: watches have little memory, and the phone's 6-minute
 * forward / 2-minute back buffer risks OutOfMemoryError there. The byte cap bounds memory even
 * for high-bitrate files. [DefaultLoadControl][androidx.media3.exoplayer.DefaultLoadControl]
 * requires both "buffer for playback" values to be no larger than the minimum buffer, so they're
 * set explicitly rather than left at ExoPlayer's (larger) defaults.
 *
 * Network wake mode keeps streaming going while the watch face is showing and the screen is off.
 *
 * The now-playing notification also becomes an Ongoing Activity, so playback shows on the watch
 * face and in the recents tray.
 */
internal fun watchPlaybackProfile() =
    PlaybackProfile(
        backBufferMs = 5_000,
        minBufferMs = 2_500,
        maxBufferMs = 15_000,
        bufferForPlaybackMs = 1_000,
        bufferForPlaybackAfterRebufferMs = 2_000,
        targetBufferBytes = 2 * 1024 * 1024,
        wakeMode = C.WAKE_MODE_NETWORK,
        decorateNowPlayingNotification = { context, builder, contentIntent, title ->
            // Must run before the builder is built: OngoingActivity.apply() writes its extras
            // into the builder, so applying it to a built Notification silently does nothing.
            OngoingActivity
                .Builder(context, NOW_PLAYING_NOTIFICATION, builder)
                .setStaticIcon(R.drawable.ic_notification_icon_playing)
                .setTouchIntent(contentIntent)
                .setStatus(Status.Builder().addTemplate(title).build())
                .build()
                .apply(context)
        },
    )
