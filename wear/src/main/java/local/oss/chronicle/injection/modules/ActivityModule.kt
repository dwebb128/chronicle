package local.oss.chronicle.injection.modules

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import local.oss.chronicle.features.player.MediaPlayerService
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.features.player.ProgressUpdater
import local.oss.chronicle.features.player.SimpleProgressUpdater
import local.oss.chronicle.injection.scopes.ActivityScope
import local.oss.chronicle.util.ServiceUtils
import timber.log.Timber

@Module
class ActivityModule(private val activity: ComponentActivity) {
    @Provides
    @ActivityScope
    fun activity(): ComponentActivity = activity

    @Provides
    @ActivityScope
    fun coroutineScope(): CoroutineScope = activity.lifecycleScope

    @Provides
    @ActivityScope
    fun provideProgressUpdater(progressUpdater: SimpleProgressUpdater): ProgressUpdater = progressUpdater

    @Provides
    @ActivityScope
    fun provideBroadcastManager(): LocalBroadcastManager =
        LocalBroadcastManager.getInstance(
            activity,
        )

    @Provides
    @ActivityScope
    fun mediaServiceConnection(): MediaServiceConnection {
        val conn =
            MediaServiceConnection(
                activity.applicationContext,
                ComponentName(activity.applicationContext, MediaPlayerService::class.java),
            )
        val doesServiceExist =
            ServiceUtils.isServiceRunning(
                activity.applicationContext,
                MediaPlayerService::class.java,
            )
        Timber.i("Connecting to existing service? $doesServiceExist")
        if (doesServiceExist) {
            conn.connect()
        }
        return conn
    }
}
