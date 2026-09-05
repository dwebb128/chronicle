package local.oss.chronicle.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * Seeded from the values in res/values/colors.xml so the Compose UI matches the app's existing
 * palette rather than the Wear Compose defaults.
 *
 * NOTE (least-verified piece per PLAN.md 1.3/13.2): the exact parameter names of
 * `androidx.wear.compose.material.Colors` are stated here from memory of the 1.x API surface and
 * have not been checked against wear-compose-material 1.4.0's actual source. Named arguments are
 * used throughout so a renamed/reordered parameter fails loudly at compile time rather than
 * silently mismatching. Verify against the library sources on the first real build.
 */
private val ChroniclePrimary = Color(0xFF2D3043) // colorPrimary
private val ChroniclePrimaryDark = Color(0xFF191A2A) // colorPrimaryDark
private val ChronicleAccent = Color(0xFF00B8D4) // colorAccent
private val ChronicleTextPrimary = Color(0xFFFFFFFF) // textPrimary (alpha dropped; see note below)
private val ChronicleTextSecondary = Color(0xFFE3D5EB) // textSecondary (alpha dropped; see note below)
private val ChronicleError = Color(0xFFFF4444) // textError

/**
 * [textPrimary]/[textSecondary] in colors.xml carry alpha in their ARGB hex (#D8FFFFFF,
 * #9EE3D5EB). [Colors] fields are opaque in every other Wear Compose usage, so the alpha is
 * dropped here rather than baked into a theme-wide color that composables can't selectively
 * re-apply; a composable that needs the translucent variant should apply `Color.copy(alpha = ...)`
 * itself rather than relying on the theme color already being translucent.
 */
private val ChronicleColors =
    Colors(
        primary = ChronicleAccent,
        primaryVariant = ChroniclePrimary,
        secondary = ChronicleAccent,
        secondaryVariant = ChroniclePrimaryDark,
        background = ChroniclePrimaryDark,
        surface = ChroniclePrimary,
        error = ChronicleError,
        onPrimary = ChroniclePrimaryDark,
        onSecondary = ChroniclePrimaryDark,
        onBackground = ChronicleTextPrimary,
        onSurface = ChronicleTextPrimary,
        onSurfaceVariant = ChronicleTextSecondary,
        onError = ChronicleTextPrimary,
    )

@Composable
fun ChronicleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = ChronicleColors,
        content = content,
    )
}
