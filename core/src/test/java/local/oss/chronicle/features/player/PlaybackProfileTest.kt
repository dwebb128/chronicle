package local.oss.chronicle.features.player

import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The phone app never installs a [PlaybackProfile], so [PlaybackProfile.DEFAULT] must stay exactly
 * the player configuration the service used before profiles existed.
 */
class PlaybackProfileTest {
    @After
    fun tearDown() {
        PlaybackProfile.install(PlaybackProfile.DEFAULT)
    }

    @Test
    fun `default profile is the phone's existing player configuration`() {
        val profile = PlaybackProfile.DEFAULT

        assertEquals(120_000, profile.backBufferMs)
        assertEquals(10_000, profile.minBufferMs)
        assertEquals(360_000, profile.maxBufferMs)
        assertEquals(DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS, profile.bufferForPlaybackMs)
        assertEquals(
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            profile.bufferForPlaybackAfterRebufferMs,
        )
        assertEquals(C.LENGTH_UNSET, profile.targetBufferBytes)
        assertEquals(C.WAKE_MODE_NONE, profile.wakeMode)
        assertNull(profile.decorateNowPlayingNotification)
    }

    @Test
    fun `the default is current until an app installs another profile`() {
        assertSame(PlaybackProfile.DEFAULT, PlaybackProfile.current)

        val watch = PlaybackProfile(maxBufferMs = 15_000)
        PlaybackProfile.install(watch)

        assertSame(watch, PlaybackProfile.current)
    }
}
