package local.oss.chronicle.util

import android.graphics.Bitmap
import android.net.Uri
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.request.ImageRequest
import javax.inject.Inject

/** [BitmapLoader] backed by Fresco's image pipeline. */
class FrescoBitmapLoader
    @Inject
    constructor() : BitmapLoader {
        override suspend fun loadBitmap(uri: Uri): Bitmap? {
            val request = ImageRequest.fromUri(uri)
            return Fresco.getImagePipeline().fetchDecodedImage(request, null).getImage()
        }
    }
