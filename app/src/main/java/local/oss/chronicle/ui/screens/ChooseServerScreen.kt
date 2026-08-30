package local.oss.chronicle.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import local.oss.chronicle.R
import local.oss.chronicle.data.model.LoadingStatus
import local.oss.chronicle.features.login.ChooseServerViewModel
import local.oss.chronicle.ui.LocalActivityComponent
import local.oss.chronicle.ui.components.ErrorScreen
import local.oss.chronicle.ui.components.LoadingScreen
import local.oss.chronicle.ui.rotaryScrollable

/** Third screen in the login chain: user, then server, then library (PLAN.md 5.3). */
@Composable
fun ChooseServerScreen(navController: NavHostController) {
    val activityComponent = LocalActivityComponent.current
    val viewModel: ChooseServerViewModel =
        viewModel(factory = activityComponent.chooseServerViewModelFactory())

    val servers by viewModel.servers.observeAsState(emptyList())
    val loadingStatus by viewModel.loadingStatus.observeAsState(LoadingStatus.LOADING)
    val listState = rememberScalingLazyListState()

    when (loadingStatus) {
        LoadingStatus.LOADING -> LoadingScreen()
        LoadingStatus.ERROR -> ErrorScreen(message = "Unable to load servers", onRetry = viewModel::refresh)
        LoadingStatus.DONE ->
            Box(modifier = Modifier.fillMaxSize()) {
                ScalingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().rotaryScrollable(listState),
                ) {
                    item {
                        Text(text = stringResource(R.string.choose_server), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    items(servers) { server ->
                        Chip(
                            onClick = { viewModel.chooseServer(server) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = server.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                PositionIndicator(scalingLazyListState = listState)
            }
    }
}
