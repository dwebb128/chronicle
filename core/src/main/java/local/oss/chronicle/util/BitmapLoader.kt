package local.oss.chronicle.util

import android.graphics.Bitmap
import android.net.Uri

/**
 * Fetches and decodes an image through the app's image pipeline, so `:core` doesn't depend on a
 * particular image library. Returns null if the image couldn't be loaded.
 */
interface BitmapLoader {
    suspend fun loadBitmap(uri: Uri): Bitmap?
}
