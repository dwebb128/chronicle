package local.oss.chronicle.data.model

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [MediaItemTrack.cached] is a database flag; the file it describes can be missing or only partly
 * written. [MediaItemTrack.getTrackSource] must only hand ExoPlayer a local file that is whole.
 */
class MediaItemTrackCachedFileTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun track(size: Long) =
        MediaItemTrack(
            id = "plex:1",
            parentKey = "plex:100",
            title = "Track 1",
            media = "/library/parts/1/file.mp3",
            size = size,
            cached = true,
        )

    private fun writeCachedFile(
        track: MediaItemTrack,
        bytes: Int,
    ) = File(tempFolder.root, track.getCachedFileName()).writeBytes(ByteArray(bytes))

    @Test
    fun `a file of the reported size is complete`() {
        val track = track(size = 100L)
        writeCachedFile(track, 100)

        assertThat(track.isCachedFileComplete(tempFolder.root), `is`(true))
    }

    @Test
    fun `a file shorter than the reported size is not complete`() {
        val track = track(size = 100L)
        writeCachedFile(track, 50)

        assertThat(track.isCachedFileComplete(tempFolder.root), `is`(false))
    }

    @Test
    fun `a missing file is not complete`() {
        assertThat(track(size = 100L).isCachedFileComplete(tempFolder.root), `is`(false))
    }

    @Test
    fun `with no reported size, an existing file is trusted`() {
        val track = track(size = 0L)
        writeCachedFile(track, 10)

        assertThat(track.isCachedFileComplete(tempFolder.root), `is`(true))
    }
}
