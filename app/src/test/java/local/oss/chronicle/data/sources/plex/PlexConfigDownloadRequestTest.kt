package local.oss.chronicle.data.sources.plex

import com.tonyodev.fetch2.EnqueueAction
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A download cut short by the app being killed leaves a row in Fetch's own database. Re-requesting
 * that file must replace the row and start from zero, not resume from the stale byte offset into a
 * file that has since been deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlexConfigDownloadRequestTest {
    private val plexConfig = PlexConfig(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), "1.0.0")

    @Test
    fun `download requests replace any download Fetch already has for the file`() {
        val request =
            plexConfig.makeDownloadRequest(
                trackSource = "/library/parts/1/file.mp3",
                uniqueBookId = 100,
                bookTitle = "Book",
                downloadLoc = "/cache/plex:1.mp3",
            )

        assertEquals(EnqueueAction.REPLACE_EXISTING, request.enqueueAction)
        assertEquals(100, request.groupId)
    }
}
