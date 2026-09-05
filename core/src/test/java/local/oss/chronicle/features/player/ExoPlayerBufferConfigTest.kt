package local.oss.chronicle.features.player

import androidx.media3.exoplayer.DefaultLoadControl
import local.oss.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_BACK_BUFFER_DURATION_MILLIS
import local.oss.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
import local.oss.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_BUFFER_FOR_PLAYBACK_MS
import local.oss.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_MAX_BUFFER_DURATION_MILLIS
import local.oss.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_MIN_BUFFER_DURATION_MILLIS
import local.oss.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_TARGET_BUFFER_BYTES
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExoPlayerBufferConfigTest {

    @Test
    fun loadControlBuildsWithoutException() {
        // Must build without throwing IllegalArgumentException
        val loadControl = DefaultLoadControl.Builder()
            .setBackBuffer(EXOPLAYER_BACK_BUFFER_DURATION_MILLIS, true)
            .setBufferDurationsMs(
                EXOPLAYER_MIN_BUFFER_DURATION_MILLIS,
                EXOPLAYER_MAX_BUFFER_DURATION_MILLIS,
                EXOPLAYER_BUFFER_FOR_PLAYBACK_MS,
                EXOPLAYER_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setTargetBufferBytes(EXOPLAYER_TARGET_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        Assert.assertNotNull(loadControl)
    }

    @Test
    fun bufferDurationsSatisfyDefaultLoadControlRequirements() {
        assertTrue(
            "bufferForPlaybackMs cannot be greater than minBufferMs",
            EXOPLAYER_BUFFER_FOR_PLAYBACK_MS <= EXOPLAYER_MIN_BUFFER_DURATION_MILLIS,
        )
        assertTrue(
            "bufferForPlaybackAfterRebufferMs cannot be greater than minBufferMs",
            EXOPLAYER_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS <= EXOPLAYER_MIN_BUFFER_DURATION_MILLIS,
        )
        assertTrue(
            "minBufferMs cannot be greater than maxBufferMs",
            EXOPLAYER_MIN_BUFFER_DURATION_MILLIS <= EXOPLAYER_MAX_BUFFER_DURATION_MILLIS,
        )
    }
}
