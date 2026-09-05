package local.oss.chronicle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text

/**
 * A centered error message with an optional "Try again" action. Used both for plain load
 * failures and, on [local.oss.chronicle.ui.screens.LinkAccountScreen], for the
 * Timeout/Error/Cancelled auth states (PLAN.md 7 — a visible "Try again" is required there, not
 * just the happy path).
 */
@Composable
fun ErrorScreen(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String = "Try again",
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                Text(text = retryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
