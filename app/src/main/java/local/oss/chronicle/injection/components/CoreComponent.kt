package local.oss.chronicle.injection.components

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.squareup.moshi.Moshi
import com.tonyodev.fetch2.Fetch
import kotlinx.coroutines.CoroutineExceptionHandler
import local.oss.chronicle.data.local.AccountRepository
import local.oss.chronicle.data.local.BookDao
import local.oss.chronicle.data.local.BookRepository
import local.oss.chronicle.data.local.CollectionsDao
import local.oss.chronicle.data.local.CollectionsRepository
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.ITrackRepository
import local.oss.chronicle.data.local.LibraryRepository
import local.oss.chronicle.data.local.LibrarySyncRepository
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.local.TrackDao
import local.oss.chronicle.data.sources.plex.ConnectionRefreshCoordinator
import local.oss.chronicle.data.sources.plex.ICachedFileManager
import local.oss.chronicle.data.sources.plex.IPlexLoginRepo
import local.oss.chronicle.data.sources.plex.PlaybackUrlResolver
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.data.sources.plex.PlexLoginService
import local.oss.chronicle.data.sources.plex.PlexMediaService
import local.oss.chronicle.data.sources.plex.PlexPrefsRepo
import local.oss.chronicle.data.sources.plex.PlexProgressReporter
import local.oss.chronicle.data.sources.plex.ScopedPlexServiceFactory
import local.oss.chronicle.data.sources.plex.ServerConnectionResolver
import local.oss.chronicle.features.account.AccountManager
import local.oss.chronicle.features.account.ActiveLibraryProvider
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlaying
import local.oss.chronicle.features.player.PlaybackStateController
import local.oss.chronicle.injection.qualifiers.AppVersionName
import java.io.File

/**
 * The part of the app's object graph that code in `:core` reaches for, through
 * [local.oss.chronicle.application.Injector] or as [ServiceComponent]'s parent.
 *
 * `:core` can't depend on the app module, so it can't name `AppComponent`. The app's
 * `AppComponent` extends this interface and adds its UI-only provisions.
 */
interface CoreComponent {
    fun applicationContext(): Context

    fun internalFilesDir(): File

    fun externalDeviceDirs(): List<File>

    fun sharedPrefs(): SharedPreferences

    fun trackDao(): TrackDao

    fun bookDao(): BookDao

    fun collectionsDao(): CollectionsDao

    fun moshi(): Moshi

    fun plexLoginRepo(): IPlexLoginRepo

    fun plexPrefs(): PlexPrefsRepo

    fun prefsRepo(): PrefsRepo

    fun trackRepo(): ITrackRepository

    fun librarySyncRepo(): LibrarySyncRepository

    fun collectionsRepo(): CollectionsRepository

    fun bookRepo(): IBookRepository

    fun bookRepos(): BookRepository

    fun workManager(): WorkManager

    fun unhandledExceptionHandler(): CoroutineExceptionHandler

    fun plexConfig(): PlexConfig

    fun plexLoginService(): PlexLoginService

    fun plexMediaService(): PlexMediaService

    fun cachedFileManager(): ICachedFileManager

    fun currentlyPlaying(): CurrentlyPlaying

    fun playbackStateController(): PlaybackStateController

    fun fetch(): Fetch

    fun accountManager(): AccountManager

    fun activeLibraryProvider(): ActiveLibraryProvider

    fun libraryRepository(): LibraryRepository

    fun accountRepository(): AccountRepository

    fun serverConnectionResolver(): ServerConnectionResolver

    fun connectionRefreshCoordinator(): ConnectionRefreshCoordinator

    fun progressReporter(): PlexProgressReporter

    fun scopedPlexServiceFactory(): ScopedPlexServiceFactory

    fun playbackUrlResolver(): PlaybackUrlResolver

    @AppVersionName
    fun appVersionName(): String
}
