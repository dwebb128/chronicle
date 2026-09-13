package local.oss.chronicle.data.model

import local.oss.chronicle.data.sources.plex.model.Media
import local.oss.chronicle.data.sources.plex.model.Part
import local.oss.chronicle.data.sources.plex.model.PlexDirectory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test

/**
 * Progress syncs between devices through Plex, so which side of a merge owns [MediaItemTrack.progress]
 * decides whether a book picked up on the watch resumes where the phone left it.
 */
class MediaItemTrackMergeTest {
    private fun track(
        progress: Long,
        lastViewedAt: Long,
        cached: Boolean = false,
    ) = MediaItemTrack(
        id = "plex:1",
        parentKey = "plex:100",
        libraryId = "lib",
        title = "Track 1",
        duration = 600_000L,
        progress = progress,
        lastViewedAt = lastViewedAt,
        cached = cached,
    )

    @Test
    fun `network wins when the server saw a more recent listen`() {
        val merged =
            MediaItemTrack.merge(
                network = track(progress = 300_000L, lastViewedAt = 2_000L),
                local = track(progress = 10_000L, lastViewedAt = 1_000L),
            )

        assertThat(merged.progress, `is`(300_000L))
        assertThat(merged.lastViewedAt, `is`(2_000L))
    }

    @Test
    fun `local wins when this device listened more recently`() {
        val merged =
            MediaItemTrack.merge(
                network = track(progress = 300_000L, lastViewedAt = 1_000L),
                local = track(progress = 10_000L, lastViewedAt = 2_000L),
            )

        assertThat(merged.progress, `is`(10_000L))
        assertThat(merged.lastViewedAt, `is`(2_000L))
    }

    @Test
    fun `local wins on a tie, so a sync mid-playback cannot roll progress back`() {
        val merged =
            MediaItemTrack.merge(
                network = track(progress = 300_000L, lastViewedAt = 1_000L),
                local = track(progress = 10_000L, lastViewedAt = 1_000L),
            )

        assertThat(merged.progress, `is`(10_000L))
    }

    @Test
    fun `forceUseNetwork takes the server position even when local is newer`() {
        val merged =
            MediaItemTrack.merge(
                network = track(progress = 300_000L, lastViewedAt = 1_000L),
                local = track(progress = 10_000L, lastViewedAt = 2_000L),
                forceUseNetwork = true,
            )

        assertThat(merged.progress, `is`(300_000L))
    }

    @Test
    fun `cached is always local, since it describes this device's filesystem`() {
        val merged =
            MediaItemTrack.merge(
                network = track(progress = 300_000L, lastViewedAt = 2_000L, cached = false),
                local = track(progress = 10_000L, lastViewedAt = 1_000L, cached = true),
            )

        assertThat(merged.cached, `is`(true))
    }

    @Test
    fun `plex seconds become millis, so a server timestamp is comparable to a local one`() {
        val plexTrack =
            PlexDirectory(
                ratingKey = "1",
                parentRatingKey = 100,
                title = "Track 1",
                duration = 600_000L,
                viewOffset = 300_000L,
                lastViewedAt = 1_700_000_000L,
                media = listOf(Media(part = listOf(Part(key = "/library/parts/1", size = 1L)))),
            )

        val converted = MediaItemTrack.fromPlexModel(plexTrack, libraryId = "lib")

        assertThat(converted.lastViewedAt, `is`(1_700_000_000_000L))
    }

    @Test
    fun `never viewed stays never viewed rather than becoming the epoch`() {
        val plexTrack =
            PlexDirectory(
                ratingKey = "1",
                parentRatingKey = 100,
                title = "Track 1",
                duration = 600_000L,
                lastViewedAt = 0L,
                media = listOf(Media(part = listOf(Part(key = "/library/parts/1", size = 1L)))),
            )

        val converted = MediaItemTrack.fromPlexModel(plexTrack, libraryId = "lib")

        assertThat(converted.lastViewedAt, `is`(0L))
    }

    @Test
    fun `a newer network timestamp with far less progress does not erase local progress`() {
        // Simulates a clock-skewed or stale-view-state network read: the server compares as
        // "newer" but reports almost no progress against a local position that is well underway.
        val merged =
            MediaItemTrack.merge(
                network = track(progress = 5_000L, lastViewedAt = 5_000L),
                local = track(progress = 300_000L, lastViewedAt = 1_000L),
            )

        assertThat(merged.progress, `is`(300_000L))
    }

    @Test
    fun `forceUseNetwork still applies a genuine regression, since that is an explicit user action`() {
        val merged =
            MediaItemTrack.merge(
                network = track(progress = 5_000L, lastViewedAt = 5_000L),
                local = track(progress = 300_000L, lastViewedAt = 1_000L),
                forceUseNetwork = true,
            )

        assertThat(merged.progress, `is`(5_000L))
    }

    @Test
    fun `a moderate intentional rewind on another device still crosses normally`() {
        // The regression guard is deliberately generous: giving back less than half of a modest
        // local position is treated as a real rewind, not a lost-place failure.
        val merged =
            MediaItemTrack.merge(
                network = track(progress = 40_000L, lastViewedAt = 5_000L),
                local = track(progress = 60_000L, lastViewedAt = 1_000L),
            )

        assertThat(merged.progress, `is`(40_000L))
    }

    @Test
    fun `small local progress is not protected, so an early legitimate reset still applies`() {
        val merged =
            MediaItemTrack.merge(
                network = track(progress = 0L, lastViewedAt = 5_000L),
                local = track(progress = 10_000L, lastViewedAt = 1_000L),
            )

        assertThat(merged.progress, `is`(0L))
    }
}
