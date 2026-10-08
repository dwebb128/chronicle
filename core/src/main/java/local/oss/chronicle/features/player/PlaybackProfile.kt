package local.oss.chronicle.features.player

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import kotlin.time.Duration.Companion.seconds

/**
 * Form-factor-specific tuning for the shared [MediaPlayerService]. [DEFAULT] is what the phone app
 * has always used. An app module for another kind of device installs its own profile with
 * [install] from its `Application`, before the service can start.
 */
data class PlaybackProfile(
    val backBufferMs: Int = EXOPLAYER_BACK_BUFFER_DURATION_MILLIS,
    val minBufferMs: Int = EXOPLAYER_MIN_BUFFER_DURATION_MILLIS,
    val maxBufferMs: Int = EXOPLAYER_MAX_BUFFER_DURATION_MILLIS,
    val bufferForPlaybackMs: Int = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
    val bufferForPlaybackAfterRebufferMs: Int =
        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
    /** Byte cap on the buffer; [C.LENGTH_UNSET] lets ExoPlayer derive one from the track types. */
    val targetBufferBytes: Int = DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES,
    /** One of the `C.WAKE_MODE_*` constants, applied with [androidx.media3.exoplayer.ExoPlayer.setWakeMode]. */
    val wakeMode: Int = C.WAKE_MODE_NONE,
    /**
     * Called with the now-playing notification's builder just before it's built, to add
     * form-factor-specific presentation (for example a Wear OS Ongoing Activity).
     */
    val decorateNowPlayingNotification: (
        (context: Context, builder: NotificationCompat.Builder, contentIntent: PendingIntent, title: String) -> Unit
    )? = null,
) {
    companion object {
        /**
         * Exoplayer back-buffer (millis to keep of playback prior to current location)
         *
         * @see DefaultLoadControl.Builder.setBufferDurationsMs
         */
        val EXOPLAYER_BACK_BUFFER_DURATION_MILLIS: Int = 120.seconds.inWholeMilliseconds.toInt()

        /**
         * Exoplayer min-buffer (the minimum millis of buffer which exo will attempt to keep in
         * memory)
         *
         * @see DefaultLoadControl.Builder.setBufferDurationsMs
         */
        val EXOPLAYER_MIN_BUFFER_DURATION_MILLIS: Int = 10.seconds.inWholeMilliseconds.toInt()

        /**
         * Exoplayer max-buffer (the maximum duration of buffer which Exoplayer will store in memory)
         *
         * @see DefaultLoadControl.Builder.setBufferDurationsMs
         */
        val EXOPLAYER_MAX_BUFFER_DURATION_MILLIS: Int = 360.seconds.inWholeMilliseconds.toInt()

        /** What the phone app uses. */
        val DEFAULT = PlaybackProfile()

        @Volatile
        var current: PlaybackProfile = DEFAULT
            private set

        fun install(profile: PlaybackProfile) {
            current = profile
        }
    }
}
