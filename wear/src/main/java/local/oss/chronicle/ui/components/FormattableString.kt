package local.oss.chronicle.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import local.oss.chronicle.R

/**
 * The Wear app's own copy of the phone UI's `BottomSheetChooser.FormattableString` (in `:app`).
 * These types are the "pick one of these options" contract the Wear ViewModels (Library,
 * BookDetails, CurrentlyPlaying, Settings) emit, same as their phone counterparts. Only the
 * rendering differs: the `OptionsDialog` composable instead of a View-based bottom sheet.
 */
sealed class FormattableString {
    data class LiteralString(
        val string: String,
    ) : FormattableString() {
        override fun format(resources: Resources): String {
            if (this == EMPTY_STRING) return ""
            return string
        }
    }

    data class ResourceString(
        @StringRes val stringRes: Int,
        val placeHolderStrings: List<String> = emptyList(),
    ) : FormattableString() {
        override fun format(resources: Resources): String = resources.getString(this.stringRes, *this.placeHolderStrings.toTypedArray())
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
