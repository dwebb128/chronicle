package local.oss.chronicle.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import local.oss.chronicle.R
import local.oss.chronicle.features.auth.PlexAuthState
import local.oss.chronicle.features.login.LoginViewModel
import local.oss.chronicle.ui.LocalActivityComponent
import local.oss.chronicle.ui.components.ErrorScreen
import local.oss.chronicle.ui.components.LoadingScreen

/**
 * The plex.tv/link short-code sign-in flow (PLAN.md section 7 / D11): shows the human-typeable
 * code the user enters at plex.tv/link on any other device with a browser, since a watch has no
 * Custom Tabs / browser of its own.
 *
 * Renders every terminal state explicitly with a visible "Try again", and keeps the screen on for
 * the whole non-terminal flow so the user has time to type the code on another device.
 */
@Composable
fun LinkAccountScreen(navController: NavHostController) {
    val activityComponent = LocalActivityComponent.current
    val viewModel: LoginViewModel = viewModel(factory = activityComponent.loginViewModelFactory())

    val authState by viewModel.authState.collectAsState()
    var lastCode by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    val view = LocalView.current
    DisposableEffect(authState) {
        view.keepScreenOn = !authState.isTerminal()
        onDispose { view.keepScreenOn = false }
    }

    // Hold on to the last code we saw: the coordinator moves from WaitingForUser to Polling
    // while the user is still typing it in elsewhere, and the code must stay on screen.
    LaunchedEffect(authState) {
        (authState as? PlexAuthState.WaitingForUser)?.let { lastCode = it.pinCode }
    }

    LaunchedEffect(retryKey) {
        viewModel.startLinkAccountAuth()
    }

    fun retry() {
        viewModel.resetAuth()
        lastCode = null
        retryKey++
    }

    when (val state = authState) {
        is PlexAuthState.Idle, is PlexAuthState.CreatingPin -> LoadingScreen()
        is PlexAuthState.WaitingForUser, is PlexAuthState.Polling ->
            LinkCode(
                code = lastCode,
                onCancel = { viewModel.cancelAuth() },
            )
        is PlexAuthState.Success -> LoadingScreen()
        is PlexAuthState.Timeout ->
            ErrorScreen(
                message = "Sign-in timed out.",
                onRetry = ::retry,
            )
        is PlexAuthState.Error ->
            ErrorScreen(
                message = state.message,
                onRetry = ::retry,
            )
        is PlexAuthState.Cancelled ->
            ErrorScreen(
                message = "Sign-in cancelled.",
                onRetry = ::retry,
            )
    }
}

/**
 * A list rather than a centred column: the code, the instructions and the cancel action together
 * are taller than a 41mm round display, so a column clipped the instructions behind the bezel. The
 * code leads so it is the thing on screen when the list opens centred on its first item.
 */
@Composable
private fun LinkCode(
    code: String?,
    onCancel: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    text = code ?: "……",
                    style = MaterialTheme.typography.title1,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.link_account_instructions),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                CompactChip(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.cancel), maxLines = 1) },
                )
            }
        }
        PositionIndicator(scalingLazyListState = listState)
    }
}
