package local.oss.chronicle.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
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

@Composable
private fun LinkCode(
    code: String?,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.link_account_instructions),
            textAlign = TextAlign.Center,
            maxLines = 4,
        )
        Text(
            text = code ?: "……",
            style = MaterialTheme.typography.title1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Button(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
            Text(text = stringResource(R.string.cancel))
        }
    }
}
