package local.oss.chronicle.injection.components

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import coil.ImageLoader
import com.squareup.moshi.Moshi
import com.tonyodev.fetch2.Fetch
import kotlinx.coroutines.CoroutineExceptionHandler
import local.oss.chronicle.data.local.BookDao
import local.oss.chronicle.data.local.ChapterDao
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.IChapterRepository
import local.oss.chronicle.data.local.ITrackRepository
import local.oss.chronicle.data.local.LibraryDao
import local.oss.chronicle.data.local.LibraryRepository
import local.oss.chronicle.data.local.LibrarySyncRepository
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.sources.plex.ICachedFileManager
import local.oss.chronicle.data.sources.plex.IPlexLoginRepo
import local.oss.chronicle.data.sources.plex.ConnectionRefreshCoordinator
import local.oss.chronicle.data.sources.plex.PlaybackUrlResolver
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.data.sources.plex.PlexLoginService
import local.oss.chronicle.data.sources.plex.PlexMediaService
import local.oss.chronicle.data.sources.plex.PlexPrefsRepo
import local.oss.chronicle.data.sources.plex.PlexProgressReporter
import local.oss.chronicle.data.sources.plex.ServerConnectionResolver
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlaying
import local.oss.chronicle.features.player.AudioOutputMonitor
import local.oss.chronicle.features.player.PlaybackStateController
import java.io.File

/**
 * The slice of the object graph that `:core` itself reaches for through
 * [local.oss.chronicle.application.Injector].
 *
 * `:core` is shared by the phone and watch apps, and each of those owns its own `@Singleton`
 * `AppComponent` — with its own UI-facing provisions, which differ. Depending on either concrete
 * component from here would make the shared layer depend on one of its own consumers, so this
 * interface names exactly the provisions the shared code needs and each app's `AppComponent`
 * extends it. Add a method here only when code inside `:core` genuinely needs it; anything
 * UI-facing belongs on the app's own component instead.
 */
interface CoreComponent {
    fun applicationContext(): Context

    fun internalFilesDir(): File

    fun externalDeviceDirs(): List<File>

    fun moshi(): Moshi

    fun plexPrefs(): PlexPrefsRepo

    fun prefsRepo(): PrefsRepo

    fun trackRepo(): ITrackRepository

    fun bookRepo(): IBookRepository

    fun plexConfig(): PlexConfig

    fun plexMediaService(): PlexMediaService

    fun plexLoginService(): PlexLoginService

    fun fetch(): Fetch

    fun imageLoader(): ImageLoader

    fun serverConnectionResolver(): ServerConnectionResolver

    fun progressReporter(): PlexProgressReporter

    fun sharedPrefs(): SharedPreferences

    fun plexLoginRepo(): IPlexLoginRepo

    fun librarySyncRepo(): LibrarySyncRepository

    fun cachedFileManager(): ICachedFileManager

    fun audioOutputMonitor(): AudioOutputMonitor

    fun unhandledExceptionHandler(): CoroutineExceptionHandler

    fun workManager(): WorkManager

    // Exposed for ServiceComponent, which depends on this component rather than on either app's.
    fun bookDao(): BookDao

    fun chapterDao(): ChapterDao

    fun libraryDao(): LibraryDao

    fun libraryRepository(): LibraryRepository

    fun chapterRepo(): IChapterRepository

    fun currentlyPlaying(): CurrentlyPlaying

    fun playbackStateController(): PlaybackStateController

    fun connectionRefreshCoordinator(): ConnectionRefreshCoordinator

    fun playbackUrlResolver(): PlaybackUrlResolver
}
