package local.oss.chronicle.application

import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.oss.chronicle.R
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.model.EMPTY_AUDIOBOOK
import local.oss.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import local.oss.chronicle.data.sources.plex.IPlexLoginRepo
import local.oss.chronicle.features.player.MediaPlayerService.Companion.ACTION_PLAYBACK_ERROR
import local.oss.chronicle.features.player.MediaPlayerService.Companion.PLAYBACK_ERROR_MESSAGE
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.injection.components.ActivityComponent
import local.oss.chronicle.injection.components.DaggerActivityComponent
import local.oss.chronicle.injection.modules.ActivityModule
import local.oss.chronicle.injection.scopes.ActivityScope
import local.oss.chronicle.ui.ChronicleWearApp
import local.oss.chronicle.ui.LocalActivityComponent
import local.oss.chronicle.ui.Nav
import local.oss.chronicle.util.observeEvent
import timber.log.Timber
import javax.inject.Inject

/**
 * Thin Wear OS host Activity. Builds the [ActivityComponent] exactly as the phone app did, injects
 * itself, then hands the whole UI over to Compose ([ChronicleWearApp]). All the phone-era
 * bottom-nav / draggable mini-player / [android.view.GestureDetector] /
 * [androidx.activity.OnBackPressedCallback] logic is gone — [androidx.wear.compose.navigation]'s
 * `SwipeDismissableNavHost` handles back gestures natively.
 */
@ActivityScope
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var localBroadcastManager: LocalBroadcastManager

    @Inject
    lateinit var mainActivityViewModelFactory: MainActivityViewModel.Factory

    private val viewModel: MainActivityViewModel by lazy {
        ViewModelProvider(this, mainActivityViewModelFactory)[MainActivityViewModel::class.java]
    }

    @Inject
    lateinit var plexLoginRepo: IPlexLoginRepo

    @Inject
    lateinit var bookRepository: IBookRepository

    @Inject
    lateinit var mediaServiceConnection: MediaServiceConnection

    var activityComponent: ActivityComponent? = null

    /**
     * A route [ChronicleWearApp] should navigate to once its NavHost exists, resolved from a
     * notification tap or a "play audiobook X" launch intent (see [handleNotificationIntent]).
     * Backed by Compose state so recomposition picks it up without any manual observer wiring;
     * [ChronicleWearApp] is expected to `LaunchedEffect(pendingRoute)`-navigate and then call
     * [consumePendingRoute].
     */
    var pendingRoute by mutableStateOf<String?>(null)
        private set

    fun consumePendingRoute() {
        pendingRoute = null
    }

    override fun onDestroy() {
        activityComponent = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("MainActivity onCreate()")
        activityComponent =
            DaggerActivityComponent.builder()
                .appComponent((application as ChronicleApplication).appComponent)
                .activityModule(ActivityModule(this))
                .build()
        activityComponent!!.inject(this)

        super.onCreate(savedInstanceState)

        viewModel.errorMessage.observeEvent(this) { errorMessage ->
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }

        if (savedInstanceState == null) {
            // Re-post a fresh login state event for this new activity instance. ChronicleWearApp
            // (Wave 2a/2c) observes IPlexLoginRepo.loginEvent to route between the
            // login/choose-user/choose-server/choose-library/library screens — see PLAN.md 5.3.
            plexLoginRepo.determineLoginState()
        }

        // If the app is being launched by voice assistant with a query
        val query = intent.getStringExtra(SearchManager.QUERY)
        if (!query.isNullOrEmpty()) {
            mediaServiceConnection.connect {
                mediaServiceConnection.transportControls?.playFromSearch(query, Bundle())
            }
        }

        handleNotificationIntent(intent)

        setContent {
            CompositionLocalProvider(LocalActivityComponent provides activityComponent!!) {
                ChronicleWearApp(
                    pendingRoute = pendingRoute,
                    onPendingRouteConsumed = ::consumePendingRoute,
                )
            }
        }
    }

    interface CurrentlyPlayingInterface {
        fun setBottomSheetState(state: MainActivityViewModel.BottomSheetState)
    }

    fun getCurrentlyPlayingInterface(): CurrentlyPlayingInterface {
        return viewModel
    }

    override fun onStart() {
        super.onStart()
        Timber.i("MainActivity onStart()")
        localBroadcastManager.registerReceiver(onPlaybackError, IntentFilter(ACTION_PLAYBACK_ERROR))
    }

    override fun onStop() {
        Timber.i("MainActivity onStop()")
        localBroadcastManager.unregisterReceiver(onPlaybackError)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val openCurrentlyPlaying =
            intent?.extras?.getBoolean(
                FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING, false,
            ) == true
        if (openCurrentlyPlaying) {
            pendingRoute = Nav.NOW_PLAYING
        }

        val openAudiobookWithId =
            intent?.extras?.getString(
                FLAG_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID,
            ) ?: NO_AUDIOBOOK_FOUND_ID
        if (openAudiobookWithId != NO_AUDIOBOOK_FOUND_ID) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val audiobook = bookRepository.getAudiobookAsync(openAudiobookWithId)
                    if (audiobook != null && audiobook != EMPTY_AUDIOBOOK) {
                        pendingRoute = Nav.bookDetails(audiobook.id)
                    }
                }
            }
        }
    }

    private val onPlaybackError =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_PLAYBACK_ERROR -> {
                        val errorMessage =
                            intent.getStringExtra(PLAYBACK_ERROR_MESSAGE)
                                ?: getString(R.string.playback_error_unknown)
                        val userMessage =
                            when {
                                errorMessage.contains(
                                    "404",
                                ) -> getString(R.string.playback_error_404)
                                errorMessage.contains(
                                    "503",
                                ) -> getString(R.string.playback_error_503)
                                errorMessage.contains(
                                    "401",
                                ) -> getString(R.string.playback_error_401)
                                else -> errorMessage
                            }
                        viewModel.showUserMessage(userMessage)
                    }
                    else -> throw NoWhenBranchMatchedException(
                        getString(R.string.playback_error_unknown),
                    )
                }
            }
        }

    companion object {
        // Aliases for the shared launch contract in :core, which the media notification writes.
        const val FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING =
            LaunchFlags.FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING
        const val FLAG_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID =
            LaunchFlags.FLAG_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID
    }
}
