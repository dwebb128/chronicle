package local.oss.chronicle.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import kotlinx.coroutines.launch

/**
 * Wires a [ScalingLazyListState] to the watch's rotating bezel/crown (PLAN.md 5.4: "Every list
 * gets rotary scroll ... wired to the Scaffold's positionIndicator and to rotary scroll").
 *
 * Uses Compose UI's generic rotary input API (`Modifier.onRotaryScrollEvent`, part of plain
 * `androidx.compose.ui:ui` since 1.4) rather than a Wear-Compose-specific rotary helper, per
 * PLAN.md section 0 ("prefer the API you are certain of"): this project cannot compile, and this
 * is the rotary API this conversion is most confident is present at the pinned Compose UI
 * version. [ScalingLazyListState.scrollBy] is assumed available because `ScalingLazyListState`,
 * like `LazyListState`, implements `ScrollableState` — flagged as unverified in the wave report.
 */
@Composable
fun Modifier.rotaryScrollable(state: ScalingLazyListState): Modifier {
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    return this
        .onRotaryScrollEvent { event ->
            coroutineScope.launch {
                state.scrollBy(event.verticalScrollPixels)
            }
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}
