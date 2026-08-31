package local.oss.chronicle.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import local.oss.chronicle.R
import local.oss.chronicle.ui.rotaryScrollable


fun Resources.getString(fs: FormattableString?): String {
    return fs?.format(this) ?: ""
}

interface BottomChooserListener {
    /** Triggers when an item in the chooser is clicked */
    fun onItemClicked(formattableString: FormattableString)

    /**
     * Triggers when the chooser is dismissed without an item being clicked (e.g. tapping outside
     * the dialog or pressing back). [wasBackgroundClicked] mirrors the phone-era name; on Wear it
     * simply means "closed without a selection".
     */
    fun onChooserClosed(wasBackgroundClicked: Boolean = false)

    companion object {
        val emptyListener =
            object : BottomChooserListener {
                override fun onItemClicked(formattableString: FormattableString) {}

                override fun onChooserClosed(wasBackgroundClicked: Boolean) {}
            }
    }
}

/** A [BottomChooserListener] that only cares about item clicks. */
abstract class BottomChooserItemListener : BottomChooserListener {
    abstract override fun onItemClicked(formattableString: FormattableString)

    override fun onChooserClosed(wasBackgroundClicked: Boolean) {}
}

data class BottomChooserState(
    val title: FormattableString,
    val options: List<FormattableString>,
    val listener: BottomChooserListener,
    val shouldShow: Boolean,
) {
    companion object {
        val EMPTY_BOTTOM_CHOOSER =
            BottomChooserState(
                title = FormattableString.EMPTY_STRING,
                options = emptyList(),
                listener = BottomChooserListener.emptyListener,
                shouldShow = false,
            )
    }
}

/**
 * Renders [state] as a full-screen dialog listing [BottomChooserState.options] as tappable rows,
 * replacing the phone's `BottomSheetChooser` view. Tapping an option invokes
 * [BottomChooserListener.onItemClicked]; dismissing without a selection (tap outside, back
 * gesture) invokes [BottomChooserListener.onChooserClosed]. The ViewModel that owns [state] is
 * responsible for flipping [BottomChooserState.shouldShow] back to false in both callbacks — this
 * composable only renders, it never mutates ViewModel state itself.
 */
@Composable
fun OptionsDialog(state: BottomChooserState) {
    if (!state.shouldShow) return
    val resources = LocalContext.current.resources
    val listState = rememberScalingLazyListState()
    Dialog(onDismissRequest = { state.listener.onChooserClosed(true) }) {
        // Must scroll: the refresh-rate chooser passes nine options, and a round 45mm display
        // cannot show that many rows at once. A plain Column would leave the last options
        // rendered off-screen and physically untappable.
        ScalingLazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.surface)
                    .rotaryScrollable(listState),
        ) {
            item {
                Text(
                    text = resources.getString(state.title),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            items(state.options) { option ->
                Chip(
                    onClick = { state.listener.onItemClicked(option) },
                    colors = ChipDefaults.secondaryChipColors(),
                    label = {
                        Text(
                            text = resources.getString(option),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                )
            }
        }
    }
}
