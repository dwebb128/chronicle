package local.oss.chronicle.application

import local.oss.chronicle.injection.components.CoreComponent

/**
 * Service-locator access to the shared object graph, for the handful of places that cannot take
 * constructor injection — Room converters, WorkManager workers, and companion-object helpers.
 *
 * This used to read `ChronicleApplication.get().appComponent` directly. `:core` is now shared by
 * the phone and watch apps, which each own a different `Application` and a different
 * `AppComponent`, so instead each app installs its component here on startup and the shared code
 * sees only the [CoreComponent] slice.
 */
object Injector {
    @Volatile
    private var component: CoreComponent? = null

    /** Called by each app's `Application.onCreate` before anything can reach [get]. */
    fun install(component: CoreComponent) {
        this.component = component
    }

    fun get(): CoreComponent =
        component
            ?: error(
                "Injector was read before an Application installed its component. Every app " +
                    "module must call Injector.install(appComponent) in Application.onCreate.",
            )
}
