package local.oss.chronicle.application

import local.oss.chronicle.injection.components.CoreComponent

/**
 * Service-locator access to the object graph, for code that can't take constructor injection
 * (Room converters, WorkManager workers, companion-object helpers).
 *
 * Shared (`:core`) code mustn't depend on the app's `Application` or `AppComponent`, so the app
 * installs a supplier of its component here at startup and shared code sees it as [CoreComponent].
 */
class Injector private constructor() {
    companion object {
        @Volatile
        private var componentSupplier: (() -> CoreComponent)? = null

        /** Called by the app's `Application` before anything can reach [get]. */
        fun install(supplier: () -> CoreComponent) {
            componentSupplier = supplier
        }

        fun get(): CoreComponent =
            componentSupplier?.invoke()
                ?: error("Injector.get() was called before the Application installed its component")
    }
}
