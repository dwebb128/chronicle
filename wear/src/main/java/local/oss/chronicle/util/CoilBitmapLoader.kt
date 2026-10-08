package local.oss.chronicle.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.Lazy
import javax.inject.Inject

/** [BitmapLoader] backed by the app's Coil [ImageLoader]. */
class CoilBitmapLoader
    @Inject
    constructor(
        private val context: Context,
        // Lazy: the image loader is built on the media OkHttp client, whose interceptor comes from
        // PlexConfig, which in turn needs this loader.
        private val imageLoader: Lazy<ImageLoader>,
    ) : BitmapLoader {
        override suspend fun loadBitmap(uri: Uri): Bitmap? {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(uri)
                    // Notification and media-session artwork must be a software bitmap; hardware
                    // bitmaps can't be parcelled to the system UI process.
                    .allowHardware(false)
                    .build()
            val result = imageLoader.get().execute(request)
            return ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        }
    }
