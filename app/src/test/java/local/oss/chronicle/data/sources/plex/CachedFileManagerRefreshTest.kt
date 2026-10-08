package local.oss.chronicle.data.sources.plex

import android.content.Context
import com.tonyodev.fetch2.Fetch
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import local.oss.chronicle.application.Injector
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.ITrackRepository
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.model.MediaItemTrack
import local.oss.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [CachedFileManager.refreshTrackDownloadedStatus] reconciles the database's cached flags with the
 * files on disk. Fetch downloads in place, so a file at the final path can be partly written; that
 * must not be marked cached.
 *
 * Robolectric because the constructor builds a real [android.content.IntentFilter].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CachedFileManagerRefreshTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val trackRepository = mockk<ITrackRepository>(relaxed = true)
    private val prefsRepo = mockk<PrefsRepo>(relaxed = true)
    private lateinit var cachedFileManager: CachedFileManager

    @Before
    fun setup() {
        mockkObject(Injector)
        every { Injector.get() } returns mockk(relaxed = true)
        every { prefsRepo.cachedMediaDir } returns tempFolder.root
        coEvery { trackRepository.updateCachedStatus(any(), any()) } returns 1
        coEvery { trackRepository.getBookIdForTrack(any()) } returns NO_AUDIOBOOK_FOUND_ID

        cachedFileManager =
            CachedFileManager(
                fetch = mockk<Fetch>(relaxed = true),
                prefsRepo = prefsRepo,
                trackRepository = trackRepository,
                bookRepository = mockk<IBookRepository>(relaxed = true),
                plexConfig = mockk<PlexConfig>(relaxed = true),
                applicationContext = mockk<Context>(relaxed = true),
            )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun track(
        id: String,
        size: Long,
        cached: Boolean = false,
    ) = MediaItemTrack(
        id = id,
        parentKey = "plex:100",
        title = id,
        media = "/library/parts/$id/file.mp3",
        size = size,
        cached = cached,
    )

    private fun writeFile(
        track: MediaItemTrack,
        bytes: Int,
    ) = File(tempFolder.root, track.getCachedFileName()).writeBytes(ByteArray(bytes))

    @Test
    fun `a whole file is marked cached and a partial one is not`() =
        runTest {
            val whole = track("plex:1", size = 100L)
            val partial = track("plex:2", size = 100L)
            writeFile(whole, 100)
            writeFile(partial, 40)
            coEvery { trackRepository.getAllTracksAsync() } returns listOf(whole, partial)
            coEvery { trackRepository.getCachedTracks() } returns emptyList()

            cachedFileManager.refreshTrackDownloadedStatus()

            coVerify { trackRepository.updateCachedStatus("plex:1", true) }
            coVerify(exactly = 0) { trackRepository.updateCachedStatus("plex:2", true) }
        }

    @Test
    fun `a track marked cached whose file is partial is unmarked`() =
        runTest {
            val partial = track("plex:2", size = 100L, cached = true)
            writeFile(partial, 40)
            coEvery { trackRepository.getAllTracksAsync() } returns listOf(partial)
            coEvery { trackRepository.getCachedTracks() } returns listOf(partial)

            cachedFileManager.refreshTrackDownloadedStatus()

            coVerify { trackRepository.updateCachedStatus("plex:2", false) }
        }

    @Test
    fun `a file whose expected size is unknown is still trusted`() =
        runTest {
            val unknownSize = track("plex:3", size = 0L)
            writeFile(unknownSize, 10)
            coEvery { trackRepository.getAllTracksAsync() } returns listOf(unknownSize)
            coEvery { trackRepository.getCachedTracks() } returns emptyList()

            cachedFileManager.refreshTrackDownloadedStatus()

            coVerify { trackRepository.updateCachedStatus("plex:3", true) }
        }
}
