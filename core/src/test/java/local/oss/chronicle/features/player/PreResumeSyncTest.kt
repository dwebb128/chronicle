package local.oss.chronicle.features.player

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.ITrackRepository
import local.oss.chronicle.data.model.Audiobook
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resuming playback must first pull the book's tracks from the server, so a position reached on
 * another device is the one this device resumes from.
 */
class PreResumeSyncTest {
    private val bookId = "plex:100"
    private val book = Audiobook(id = bookId, libraryId = "plex:library:1", source = 1L, title = "Book")
    private val bookRepository = mockk<IBookRepository>(relaxed = true)
    private val trackRepository = mockk<ITrackRepository>(relaxed = true)

    private suspend fun refresh(
        offlineMode: Boolean = false,
        timeoutMillis: Long = AudiobookMediaSessionCallback.PRE_RESUME_SYNC_TIMEOUT_MILLIS,
    ) = AudiobookMediaSessionCallback.refreshTracksBeforeResume(
        bookId = bookId,
        offlineMode = offlineMode,
        bookRepository = bookRepository,
        trackRepository = trackRepository,
        timeoutMillis = timeoutMillis,
    )

    @Test
    fun `resuming pulls the book's tracks from the server first`() =
        runTest {
            coEvery { bookRepository.getAudiobookAsync(bookId) } returns book
            coEvery { trackRepository.loadTracksForAudiobook(bookId, book.libraryId, any()) } returns Ok(emptyList())

            assertTrue(refresh())
            coVerify { trackRepository.loadTracksForAudiobook(bookId, book.libraryId, false) }
        }

    @Test
    fun `offline mode resumes from the local position without touching the network`() =
        runTest {
            coEvery { bookRepository.getAudiobookAsync(bookId) } returns book

            assertFalse(refresh(offlineMode = true))
            coVerify(exactly = 0) { trackRepository.loadTracksForAudiobook(any(), any(), any()) }
        }

    @Test
    fun `a slow server falls back to the local position after the timeout`() =
        runTest {
            coEvery { bookRepository.getAudiobookAsync(bookId) } returns book
            coEvery { trackRepository.loadTracksForAudiobook(bookId, book.libraryId, any()) } coAnswers {
                delay(10_000)
                Ok(emptyList())
            }

            assertFalse(refresh(timeoutMillis = 100))
        }

    @Test
    fun `a failed or throwing fetch falls back to the local position`() =
        runTest {
            coEvery { bookRepository.getAudiobookAsync(bookId) } returns book
            coEvery { trackRepository.loadTracksForAudiobook(bookId, book.libraryId, any()) } returns
                Err(IllegalStateException("no connection"))
            assertFalse(refresh())

            coEvery { trackRepository.loadTracksForAudiobook(bookId, book.libraryId, any()) } throws
                RuntimeException("boom")
            assertFalse(refresh())
        }

    @Test
    fun `an unknown book is skipped`() =
        runTest {
            coEvery { bookRepository.getAudiobookAsync(bookId) } returns null

            assertFalse(refresh())
            coVerify(exactly = 0) { trackRepository.loadTracksForAudiobook(any(), any(), any()) }
        }
}
