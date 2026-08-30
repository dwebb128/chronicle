package local.oss.chronicle.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import local.oss.chronicle.R
import local.oss.chronicle.data.model.LoadingStatus
import local.oss.chronicle.features.login.ChooseUserViewModel
import local.oss.chronicle.ui.LocalActivityComponent
import local.oss.chronicle.ui.components.ErrorScreen
import local.oss.chronicle.ui.components.LoadingScreen
import local.oss.chronicle.ui.rotaryScrollable

/**
 * Second screen in the real [local.oss.chronicle.data.sources.plex.IPlexLoginRepo.LoginState]
 * chain (user, then server, then library — PLAN.md 5.3). Built against the existing
 * [ChooseUserViewModel] untouched by this wave.
 */
@Composable
fun ChooseUserScreen(navController: NavHostController) {
    val activityComponent = LocalActivityComponent.current
    val viewModel: ChooseUserViewModel = viewModel(factory = activityComponent.chooseUserViewModelFactory())

    val showPin by viewModel.showPin.observeAsState(false)
    if (showPin) {
        PinEntryScreen(viewModel)
        return
    }

    val users by viewModel.users.observeAsState(emptyList())
    val loadingStatus by viewModel.usersLoadingStatus.observeAsState(LoadingStatus.LOADING)
    val listState = rememberScalingLazyListState()

    when (loadingStatus) {
        LoadingStatus.LOADING -> LoadingScreen()
        LoadingStatus.ERROR -> ErrorScreen(message = "Unable to load users", onRetry = viewModel::refresh)
        LoadingStatus.DONE ->
            Box(modifier = Modifier.fillMaxSize()) {
                ScalingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().rotaryScrollable(listState),
                ) {
                    item {
                        Text(text = stringResource(R.string.choose_user), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    items(users) { user ->
                        Chip(
                            onClick = { viewModel.pickUser(user) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = user.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                PositionIndicator(scalingLazyListState = listState)
            }
    }
}

@Composable
private fun PinEntryScreen(viewModel: ChooseUserViewModel) {
    var pin by remember { mutableStateOf("") }
    val pinError by viewModel.pinErrorMessage.observeAsState()
    val pinLoadingStatus by viewModel.pinLoadingStatus.observeAsState(LoadingStatus.DONE)

    fun appendDigit(digit: String) {
        if (pin.length < 8) {
            pin += digit
            viewModel.setPinData(pin)
        }
    }

    fun backspace() {
        if (pin.isNotEmpty()) {
            pin = pin.dropLast(1)
            viewModel.setPinData(pin)
        }
    }

    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScrollable(listState),
        ) {
            item {
                Text(text = "${stringResource(R.string.pin)}: ${"•".repeat(pin.length)}")
            }
            if (pinLoadingStatus == LoadingStatus.LOADING) {
                item { LoadingScreen() }
            }
            pinError?.let { error ->
                item { Text(text = error, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    (1..3).forEach { digit ->
                        CompactChip(
                            onClick = { appendDigit(digit.toString()) },
                            label = { Text(digit.toString()) },
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    (4..6).forEach { digit ->
                        CompactChip(
                            onClick = { appendDigit(digit.toString()) },
                            label = { Text(digit.toString()) },
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    (7..9).forEach { digit ->
                        CompactChip(
                            onClick = { appendDigit(digit.toString()) },
                            label = { Text(digit.toString()) },
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    CompactChip(onClick = ::backspace, label = { Text("Del") }, modifier = Modifier.padding(2.dp))
                    CompactChip(onClick = { appendDigit("0") }, label = { Text("0") }, modifier = Modifier.padding(2.dp))
                    CompactChip(
                        onClick = { viewModel.submitPin() },
                        label = { Text(stringResource(R.string.submit)) },
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
            item {
                CompactChip(
                    onClick = { viewModel.hidePinScreen() },
                    label = { Text(stringResource(R.string.cancel)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        PositionIndicator(scalingLazyListState = listState)
    }
}
