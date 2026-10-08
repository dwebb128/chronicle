package local.oss.chronicle.injection.qualifiers

import javax.inject.Qualifier

/**
 * The app's `versionName`, which Plex requests report as `X-Plex-Version`. The app module owns the
 * version (and the release workflow bumps it there), so it provides this to `:core`.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AppVersionName
