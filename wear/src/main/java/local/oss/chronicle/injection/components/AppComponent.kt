package local.oss.chronicle.injection.components

import coil.ImageLoader
import dagger.Component
import local.oss.chronicle.application.ChronicleApplication
import local.oss.chronicle.features.player.AudioOutputMonitor
import local.oss.chronicle.injection.modules.AppModule
import javax.inject.Singleton

/** The watch app's graph: everything :core needs, plus the watch UI's own provisions. */
@Singleton
@Component(modules = [AppModule::class])
interface AppComponent : CoreComponent {
    fun imageLoader(): ImageLoader

    fun audioOutputMonitor(): AudioOutputMonitor

    // Inject
    fun inject(chronicleApplication: ChronicleApplication)
}
