package local.oss.chronicle.injection.components

import dagger.Component
import local.oss.chronicle.application.ChronicleApplication
import local.oss.chronicle.data.local.*
import local.oss.chronicle.data.sources.plex.*
import local.oss.chronicle.features.account.AccountManager
import local.oss.chronicle.features.account.ActiveLibraryProvider
import local.oss.chronicle.injection.modules.AppModule
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent : CoreComponent {
    // Shared provisions are inherited from CoreComponent; only the watch app's own additions
    // are declared here.

    fun trackDao(): TrackDao

    fun collectionsDao(): CollectionsDao

    fun collectionsRepo(): CollectionsRepository

    fun bookRepos(): BookRepository

    fun accountManager(): AccountManager

    fun activeLibraryProvider(): ActiveLibraryProvider

    fun accountRepository(): AccountRepository

    fun scopedPlexServiceFactory(): ScopedPlexServiceFactory

    // Inject
    fun inject(chronicleApplication: ChronicleApplication)
}
