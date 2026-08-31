package local.oss.chronicle.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import local.oss.chronicle.core.R

/**
 * Moved here from the deleted `views/BottomSheetChooser.kt` (PLAN.md 5.6): these types are the
 * shared "pick one of these options" contract every surviving ViewModel (Library, BookDetails,
 * CurrentlyPlaying, Settings) already emits, unchanged, from the phone app. Only the rendering
 * changed — from a View-based bottom sheet to the `OptionsDialog` composable in the app modules.
 */
sealed class FormattableString {
    data class LiteralString(val string: String) : FormattableString() {
        override fun format(resources: Resources): String {
            if (this == EMPTY_STRING) return ""
            return string
        }
    }

    data class ResourceString(
        @StringRes val stringRes: Int,
        val placeHolderStrings: List<String> = emptyList(),
    ) : FormattableString() {
        override fun format(resources: Resources): String {
            return resources.getString(this.stringRes, *this.placeHolderStrings.toTypedArray())
        }
    }

    abstract fun format(resources: Resources): String

    companion object {
        fun from(
            @StringRes stringRes: Int,
        ): FormattableString = ResourceString(stringRes)

        fun from(string: String): FormattableString = LiteralString(string)

        val yes = from(R.string.yes)
        val no = from(R.string.no)

        val EMPTY_STRING = from("")
    }
}
