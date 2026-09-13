package local.oss.chronicle.data.model

import android.support.v4.media.MediaMetadataCompat
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import local.oss.chronicle.application.Injector
import local.oss.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import local.oss.chronicle.data.model.MediaItemTrack.Companion.EMPTY_TRACK
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.data.sources.plex.model.PlexDirectory
import local.oss.chronicle.data.sources.plex.model.getDuration
import local.oss.chronicle.features.player.*
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * A model for an audio track (i.e. a song)
 */
@Entity(indices = [Index(value = ["libraryId"]), Index(value = ["parentKey"])])
data class MediaItemTrack(
    @PrimaryKey
    val id: String = TRACK_NOT_FOUND,
    val parentKey: String = "-1",
    val libraryId: String = "",
    val title: String = "",
    val playQueueItemID: Long = -1,
    val thumb: String? = null,
    val index: Int = 0,
    val discNumber: Int = 1,
    /** The duration of the track in milliseconds */
    val duration: Long = 0L,
    /** Path to the media file in the form "/library/parts/[id]/SOME_NUMBER/file.mp3" */
    val media: String = "",
    val album: String = "",
    val artist: String = "",
    val genre: String = "",
    val cached: Boolean = false,
    val artwork: String? = "",
    val viewCount: Long = 0L,
    val progress: Long = 0L,
    val lastViewedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val size: Long = 0L,
    val source: Long = 0L,
) : Comparable<MediaItemTrack> {
    companion object {
        /**
         * Cache for pre-resolved streaming URLs from Plex's /transcode/universal/decision endpoint.
         * This allows bandwidth-aware playback. Key is track ID, value is the resolved streaming URL.
         *
         * Populated by PlaybackUrlResolver before playback starts.
         * Falls back to direct file URLs if not populated.
         */
        @JvmStatic
        val streamingUrlCache = ConcurrentHashMap<String, String>()

        fun from(
            metadata: MediaMetadataCompat,
            libraryId: String = "",
        ): MediaItemTrack {
            return MediaItemTrack(
                id = metadata.id ?: "unknown",
                libraryId = libraryId,
                title = metadata.title ?: "",
                playQueueItemID = metadata.trackNumber,
                thumb = metadata.artUri.toString(),
                media = metadata.mediaUri.toString(),
                index = metadata.trackNumber.toInt(),
                duration = metadata.duration,
                album = metadata.album ?: "",
                artist = metadata.artist ?: "",
                genre = metadata.genre ?: "",
                artwork = metadata.artUri.toString(),
            )
        }

        val EMPTY_TRACK = MediaItemTrack(TRACK_NOT_FOUND)

        /** The pattern representing a downloaded track on the file system - now format is "plex:123.mp3" */
        val cachedFilePattern = Regex("[^.]+\\..+")

        fun getTrackIdFromFileName(fileName: String): String {
            return fileName.substringBefore('.')
        }

        /**
         * Merges a network copy of a track with the local one. Network metadata wins throughout;
         * [progress] and [lastViewedAt] go to whichever side listened last, so a position set on
         * a phone reaches the watch and vice versa. [cached] is always local — it describes this
         * device's filesystem and does not exist on the server.
         *
         * Both sides' [lastViewedAt] are milliseconds; see [PlexDirectory.lastViewedAtMillis] for
         * why that needs saying.
         *
         * One safety net on top of the timestamp comparison: a passive sync (this function called
         * with [forceUseNetwork] false, e.g. from opening the book details screen) never adopts a
         * network position that erases most of an already-substantial local one, even when the
         * network side compares as newer. That comparison assumes both devices' clocks and the
         * server's are in agreement, and a device that has drifted — plausible on a standalone
         * watch that is not always paired to sync its clock — always looks "newer" than a server
         * it is in fact behind, silently reverting real progress to whatever the server last had on
         * file. [isSuspiciousRegression] catches that shape of failure without touching the
         * explicit "force sync from server" action, which must still be able to move progress
         * backward on purpose.
         */
        fun merge(
            network: MediaItemTrack,
            local: MediaItemTrack,
            forceUseNetwork: Boolean = false,
        ) = if (forceUseNetwork) {
            network.copy(cached = local.cached)
        } else if (network.lastViewedAt > local.lastViewedAt &&
            !isSuspiciousRegression(network, local)
        ) {
            // The server holds a listen this device has not seen — from a phone, or another watch.
            // Taking its position is the whole point of syncing progress through Plex.
            network.copy(cached = local.cached)
        } else {
            // Either this device listened more recently than the server knows about, or the
            // network side looked newer but its position doesn't hold up — see
            // [isSuspiciousRegression]. Either way, keep the local position: while playback is
            // running `ProgressUpdater` writes it every second, so a sync landing mid-playback must
            // not roll it back to whatever was last scrobbled.
            network.copy(
                cached = local.cached,
                lastViewedAt = local.lastViewedAt,
                progress = local.progress,
            )
        }

        /**
         * True when [network]'s progress is small enough, relative to [local]'s, that adopting it
         * would look to a listener like the book jumped back to (near) the start — the "lost my
         * place" failure — while [local] already represents a real amount of listening.
         *
         * The thresholds are deliberately generous: a genuine intentional rewind on another device
         * of a minute or two must still cross the watch normally, so this only refuses a network
         * position that gives back more than half of at least a minute of local progress.
         */
        private fun isSuspiciousRegression(
            network: MediaItemTrack,
            local: MediaItemTrack,
        ): Boolean {
            val regressionGuardMinMs = 60_000L
            return local.progress > regressionGuardMinMs && network.progress < local.progress / 2
        }

        /** Create a [MediaItemTrack] from a Plex model and an index */
        fun fromPlexModel(
            networkTrack: PlexDirectory,
            libraryId: String,
        ): MediaItemTrack {
            return MediaItemTrack(
                id = "plex:${networkTrack.ratingKey}",
                parentKey = "plex:${networkTrack.parentRatingKey}",
                libraryId = libraryId,
                title = networkTrack.title,
                artist = networkTrack.grandparentTitle,
                thumb = networkTrack.thumb,
                index = networkTrack.index,
                discNumber = networkTrack.parentIndex,
                duration = networkTrack.duration,
                progress = networkTrack.viewOffset,
                media = networkTrack.media[0].part[0].key,
                album = networkTrack.parentTitle,
                lastViewedAt = networkTrack.lastViewedAtMillis,
                updatedAt = networkTrack.updatedAt,
                size = networkTrack.media[0].part[0].size,
            )
        }

        const val PARENT_KEY_PREFIX = "/library/metadata/"
    }

    /** The name of the track when it is written to the file system */
    fun getCachedFileName(): String {
        return "$id.${File(media).extension}"
    }

    /**
     * Returns the playback URL for this track.
     *
     * **BANDWIDTH-AWARE PLAYBACK**:
     * This function now checks for pre-resolved streaming URLs from Plex's
     * `/transcode/universal/decision` endpoint which:
     * 1. Negotiates direct play vs transcode based on bandwidth limits
     * 2. Creates proper playback sessions with permissions
     * 3. Adapts to bandwidth constraints by transcoding to lower bitrates
     *
     * **How it works**:
     * - If a URL exists in [streamingUrlCache], use that (bandwidth-aware)
     * - Otherwise fall back to direct file URL (may hit bandwidth limits)
     *
     * The cache is populated by PlaybackUrlResolver before playback starts.
     * This allows Plex to handle bandwidth limits gracefully by transcoding when needed
     * instead of rejecting the request.
     *
     * @see local.oss.chronicle.data.sources.plex.PlaybackUrlResolver
     * @see <a href="https://github.com/[repo]/blob/main/docs/ARCHITECTURE.md#bandwidth-handling">ARCHITECTURE.md - Bandwidth Handling</a>
     */
    fun getTrackSource(): String {
        return if (cached && isCachedFileComplete()) {
            // Use local file if downloaded
            File(Injector.get().prefsRepo().cachedMediaDir, getCachedFileName()).toURI().toString()
        } else {
            // Check for pre-resolved streaming URL (bandwidth-aware)
            streamingUrlCache[id]?.let { resolvedUrl ->
                Timber.d("Using pre-resolved streaming URL for track $id")
                return resolvedUrl
            }

            // Fall back to direct file URL with library-aware server resolution
            // Note: This may trigger bandwidth errors if server has limits
            Timber.d("No pre-resolved URL for track $id, using direct file path from library $libraryId")

            // Use library-specific server URL if available
            val serverUrl =
                try {
                    kotlinx.coroutines.runBlocking {
                        Injector.get().serverConnectionResolver().resolve(libraryId).serverUrl
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to resolve server for library $libraryId, using global PlexConfig")
                    null
                } ?: Injector.get().plexConfig().url

            // Build full URL with library-specific server
            buildServerUrl(serverUrl, media)
        }
    }

    /**
     * Whether this track's downloaded file is actually on disk and whole.
     *
     * [cached] is a database column, and the file it describes can be gone or half-written without
     * it: a download interrupted partway leaves a short file at the final path, and files deleted
     * outside the app leave none at all. Either way, handing ExoPlayer a `file://` URI it cannot
     * fully read surfaces as a source error, or as playback that starts and then stalls on a seek
     * past what was written — with nothing pointing at the download as the cause. Streaming
     * instead is always recoverable.
     *
     * [size] is what Plex reported for the file; it can be absent (0), in which case existence is
     * the most that can be checked.
     */
    private fun isCachedFileComplete(): Boolean {
        val file = File(Injector.get().prefsRepo().cachedMediaDir, getCachedFileName())
        if (!file.exists()) {
            Timber.w("Track $id is marked cached but its file is missing; streaming instead")
            return false
        }
        if (size > 0L && file.length() < size) {
            Timber.w(
                "Track $id is marked cached but only ${file.length()} of $size bytes " +
                    "are on disk; streaming instead",
            )
            return false
        }
        return true
    }

    /**
     * Builds a full server URL from base and relative path.
     * Accounts for trailing/leading slashes like PlexConfig.toServerString().
     */
    private fun buildServerUrl(
        baseUrl: String,
        relativePath: String,
    ): String {
        val baseEndsWith = baseUrl.endsWith('/')
        val pathStartsWith = relativePath.startsWith('/')
        return if (baseEndsWith && pathStartsWith) {
            "$baseUrl${relativePath.substring(1)}"
        } else if (!baseEndsWith && !pathStartsWith) {
            "$baseUrl/$relativePath"
        } else {
            "$baseUrl$relativePath"
        }
    }

    /** A string representing the index but padded to [length] characters with zeroes */
    fun paddedIndex(length: Int): String {
        return index.toString().padStart(length, '0')
    }

    override fun compareTo(other: MediaItemTrack): Int {
        val discCompare = discNumber.compareTo(other.discNumber)
        if (discCompare != 0) {
            return discCompare
        }
        return index.compareTo(other.index)
    }
}

/**
 * Returns the timestamp (in ms) corresponding to the start of [track] with respect to the
 * entire playlist
 *
 * IMPORTANT: [MediaItemTrack.duration] is not guaranteed to perfectly match the duration of the
 * track represented, as we don't trust the server and are unable to verify this ourselves, so
 * use with caution
 */
fun List<MediaItemTrack>.getTrackStartTime(track: MediaItemTrack): Long {
    if (isEmpty()) {
        return 0
    }
    // There's a possibility [track] has been edited and [this] has not, so find it again
    val trackInList = find { it.id == track.id } ?: return 0
    val previousTracks = this.subList(0, indexOf(trackInList))
    return previousTracks.map { it.duration }.sum()
}

/**
 * Returns the timestamp (in ms) corresponding to the progress of [track] with respect to the
 * entire playlist
 */
fun List<MediaItemTrack>.getTrackProgressInAudiobook(track: MediaItemTrack): Long {
    if (isEmpty()) {
        return 0
    }
    val previousTracks = this.subList(0, indexOf(track))
    return previousTracks.map { it.duration }.sum() + track.progress
}

/** Returns the track containing the timestamp (as offset from the start of the [List] provided */
fun List<MediaItemTrack>?.getTrackContainingOffset(offset: Long): MediaItemTrack {
    if (isNullOrEmpty()) {
        return EMPTY_TRACK
    }
    this.fold(offset) { acc: Long, track: MediaItemTrack ->
        val tempAcc: Long = acc - track.duration
        if (tempAcc <= 0) {
            return track
        }
        return@fold tempAcc
    }
    return EMPTY_TRACK
}

/**
 * @return the progress of the current track plus the duration of all previous tracks
 */
fun List<MediaItemTrack>.getProgress(): Long {
    if (isEmpty()) {
        return 0
    }
    val currentTrackProgress = getActiveTrack().progress
    val previousTracksDuration = getTrackStartTime(getActiveTrack())
    return currentTrackProgress + previousTracksDuration
}

/**
 * @return progress as percent
 */
fun List<MediaItemTrack>.getProgressPercentage(): Int {
    if (isEmpty() || getDuration() == 0L) {
        return 0
    }
    return ((getProgress() / getDuration().toDouble()) * 100).roundToInt()
}

/**
 * Find the next song in the [List] which has not been completed. Returns the first element
 */
fun List<MediaItemTrack>.getActiveTrack(): MediaItemTrack {
    check(this.isNotEmpty()) { "Cannot get active track of empty list!" }
    return maxByOrNull { it.lastViewedAt } ?: get(0)
}

/** Converts the metadata of a [MediaItemTrack] to a [MediaMetadataCompat]. */
fun MediaItemTrack.toMediaMetadata(plexConfig: PlexConfig): MediaMetadataCompat {
    val metadataBuilder = MediaMetadataCompat.Builder()
    metadataBuilder.id = this.id
    metadataBuilder.title = this.title
    metadataBuilder.displayTitle = this.album
    metadataBuilder.displaySubtitle = this.artist
    metadataBuilder.trackNumber = this.playQueueItemID
    metadataBuilder.mediaUri = getTrackSource()
    metadataBuilder.albumArtUri = plexConfig.makeThumbUri(this.thumb ?: "").toString()
    metadataBuilder.trackNumber = this.index.toLong()
    metadataBuilder.duration = this.duration
    metadataBuilder.album = this.album
    metadataBuilder.artist = this.artist
    metadataBuilder.genre = this.genre
    return metadataBuilder.build()
}

fun List<MediaItemTrack>.asChapterList(bookId: String = NO_AUDIOBOOK_FOUND_ID): List<Chapter> {
    val outList = mutableListOf<Chapter>()
    var cumStartOffset = 0L
    for (track in this) {
        outList.add(track.asChapter(cumStartOffset, bookId))
        cumStartOffset += track.duration
    }
    return outList
}

fun MediaItemTrack.asChapter(
    startOffset: Long,
    bookId: String = NO_AUDIOBOOK_FOUND_ID,
): Chapter {
    return Chapter(
        uid = 0L, // Auto-generated by Room
        title = title,
        id = id.hashCode().toLong(),
        index = index.toLong(),
        discNumber = discNumber,
        startTimeOffset = startOffset,
        endTimeOffset = startOffset + duration,
        downloaded = cached,
        trackId = id,
        bookId = bookId,
    )
}

// Produces an ID unique to a track and source
fun MediaItemTrack.uniqueId(): Int {
    return id.hashCode()
}

val EMPTY_TRACK = MediaItemTrack(id = TRACK_NOT_FOUND)
