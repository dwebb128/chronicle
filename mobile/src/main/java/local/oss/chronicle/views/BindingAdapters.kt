package local.oss.chronicle.views

import android.app.Activity
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.databinding.BindingAdapter
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import local.oss.chronicle.R
import local.oss.chronicle.application.Injector
import local.oss.chronicle.data.sources.plex.PlexConfig
import timber.log.Timber

/**
 * Loads Plex artwork into a plain [ImageView].
 *
 * This used to drive a Fresco `DraweeView`. Coil replaced Fresco across the project, so the
 * layouts now hold ordinary ImageViews and the load goes through the shared, auth-aware
 * [coil.ImageLoader] the Application installs — the same one the notification artwork uses.
 */
@BindingAdapter(value = ["srcRounded", "serverConnected", "libraryId"], requireAll = false)
fun bindImageRounded(
    imageView: ImageView,
    src: String?,
    serverConnected: Boolean,
    libraryId: String?,
) {
    val context = imageView.context
    if (context is Activity && context.isDestroyed) {
        return
    }

    val imageSize =
        imageView.resources.getDimension(R.dimen.currently_playing_artwork_max_size).toInt()
    val config = Injector.get().plexConfig()

    // A library-scoped thumbnail URL has to be resolved against that library's server, which is
    // a suspending lookup; everything else can go straight through the global config.
    if (!libraryId.isNullOrEmpty()) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                imageView.load(config.makeThumbUriForLibrary(src ?: "", libraryId))
            } catch (e: Exception) {
                Timber.e(e, "Failed to load library-aware thumbnail for libraryId=$libraryId")
                loadWithGlobalConfig(imageView, src, imageSize, config)
            }
        }
    } else {
        loadWithGlobalConfig(imageView, src, imageSize, config)
    }
}

private fun loadWithGlobalConfig(
    imageView: ImageView,
    src: String?,
    imageSize: Int,
    config: PlexConfig,
) {
    imageView.load(
        config.toServerString("photo/:/transcode?width=$imageSize&height=$imageSize&url=$src")
            .toUri(),
    )
}

// NOTE: this will not work for Android versions HoneyComb and below, and DataBinding overrides the
// tag set on all outermost layouts in a data bound layout xml
@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
@BindingAdapter("specialTag")
fun bindTag(
    view: View,
    o: Any,
) {
    view.tag = o
}
