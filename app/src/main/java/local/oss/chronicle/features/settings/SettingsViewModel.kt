package local.oss.chronicle.features.settings

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.oss.chronicle.BuildConfig
import local.oss.chronicle.R
import local.oss.chronicle.data.local.CollectionsRepository
import local.oss.chronicle.data.local.IBookRepository
import local.oss.chronicle.data.local.ITrackRepository
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.data.sources.plex.ICachedFileManager
import local.oss.chronicle.data.sources.plex.IPlexLoginRepo
import local.oss.chronicle.data.sources.plex.PlexConfig
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.ui.components.BottomChooserItemListener
import local.oss.chronicle.ui.components.BottomChooserListener
import local.oss.chronicle.ui.components.BottomChooserState
import local.oss.chronicle.ui.components.BottomChooserState.Companion.EMPTY_BOTTOM_CHOOSER
import local.oss.chronicle.ui.components.FormattableString
import local.oss.chronicle.util.Event
import local.oss.chronicle.util.postEvent
import timber.log.Timber
import javax.inject.Inject

/**
 * Wear-native rewrite of the settings screen's ViewModel (PLAN.md 5.7). The phone version built a
 * ~40-row generic [PreferenceModel] list (much of it for cut features — premium, book cover style,
 * sync location, Android Auto, subreddit/GitHub/licenses, the debug-info Easter egg) rendered by a
 * RecyclerView. `SettingsScreen` instead renders a small, fixed set of Wear-appropriate rows
 * directly against the typed [LiveData] exposed here, so this ViewModel now exposes preferences
 * individually rather than as a generic list.
 *
 * Surviving rows (PLAN.md 5.7): offline mode, jump forward/back intervals, auto-rewind,
 * skip-silent-audio, pause-on-interruption, refresh rate, delete downloaded files, log out,
 * version/about. [bottomChooserState] is reused (via [OptionsDialog]) for the two rows that are
 * still "pick one of several options" (jump interval, refresh rate) and for confirmations
 * (delete downloaded files, log out) — the same [BottomChooserState] contract every other
 * surviving ViewModel already emits.
 */
class SettingsViewModel(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val mediaServiceConnection: MediaServiceConnection,
    private val prefsRepo: PrefsRepo,
    private val plexLoginRepo: IPlexLoginRepo,
    private val cachedFileManager: ICachedFileManager,
    private val plexConfig: PlexConfig,
    private val collectionsRepository: CollectionsRepository,
) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    class Factory
        @Inject
        constructor(
            private val bookRepository: IBookRepository,
            private val trackRepository: ITrackRepository,
            private val mediaServiceConnection: MediaServiceConnection,
            private val prefsRepo: PrefsRepo,
            private val plexLoginRepo: IPlexLoginRepo,
            private val cachedFileManager: ICachedFileManager,
            private val plexConfig: PlexConfig,
            private val collectionsRepository: CollectionsRepository,
        ) : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                    return SettingsViewModel(
                        bookRepository = bookRepository,
                        trackRepository = trackRepository,
                        mediaServiceConnection = mediaServiceConnection,
                        prefsRepo = prefsRepo,
                        plexLoginRepo = plexLoginRepo,
                        cachedFileManager = cachedFileManager,
                        plexConfig = plexConfig,
                        collectionsRepository = collectionsRepository,
                    ) as T
                } else {
                    throw IllegalArgumentException(
                        "Cannot instantiate $modelClass from SettingsViewModel.Factory",
                    )
                }
            }
        }

    /** Bumped on every SharedPreferences change so the `.map {}` values below refresh. */
    private val prefsVersion = MutableLiveData(Unit)

    val offlineMode: LiveData<Boolean> = prefsVersion.map { prefsRepo.offlineMode }
    val skipSilence: LiveData<Boolean> = prefsVersion.map { prefsRepo.skipSilence }
    val autoRewind: LiveData<Boolean> = prefsVersion.map { prefsRepo.autoRewind }
    val pauseOnFocusLost: LiveData<Boolean> = prefsVersion.map { prefsRepo.pauseOnFocusLost }
    val jumpForwardSeconds: LiveData<Long> = prefsVersion.map { prefsRepo.jumpForwardSeconds }
    val jumpBackwardSeconds: LiveData<Long> = prefsVersion.map { prefsRepo.jumpBackwardSeconds }
    val refreshRateMinutes: LiveData<Long> = prefsVersion.map { prefsRepo.refreshRateMinutes }

    val versionName: String = BuildConfig.VERSION_NAME

    private var _messageForUser = MutableLiveData<Event<FormattableString>>()
    val messageForUser: LiveData<Event<FormattableString>>
        get() = _messageForUser

    private var _bottomChooserState = MutableLiveData(EMPTY_BOTTOM_CHOOSER)
    val bottomChooserState: LiveData<BottomChooserState>
        get() = _bottomChooserState

    fun setBottomSheetVisibility(shouldShow: Boolean) {
        bottomChooserState.value?.let {
            _bottomChooserState.postValue(it.copy(shouldShow = shouldShow))
        }
    }

    private fun hideBottomSheet() {
        _bottomChooserState.postValue(
            _bottomChooserState.value?.copy(shouldShow = false) ?: EMPTY_BOTTOM_CHOOSER,
        )
    }

    private fun showOptionsMenu(
        title: FormattableString,
        options: List<FormattableString>,
        listener: BottomChooserListener,
    ) {
        _bottomChooserState.postValue(
            BottomChooserState(
                title = title,
                options = options,
                listener = listener,
                shouldShow = true,
            ),
        )
    }

    private val prefsListener =
        OnSharedPreferenceChangeListener { _, _ ->
            prefsVersion.postValue(Unit)
        }

    init {
        prefsRepo.registerPrefsListener(prefsListener)
    }

    override fun onCleared() {
        prefsRepo.unregisterPrefsListener(prefsListener)
    }

    fun setOfflineMode(enabled: Boolean) {
        prefsRepo.offlineMode = enabled
    }

    fun setSkipSilence(enabled: Boolean) {
        prefsRepo.skipSilence = enabled
    }

    fun setAutoRewind(enabled: Boolean) {
        prefsRepo.autoRewind = enabled
    }

    fun setPauseOnFocusLost(enabled: Boolean) {
        prefsRepo.pauseOnFocusLost = enabled
    }

    /** Shows a chooser of jump-forward-seconds presets, mirroring the phone's row of the same name. */
    fun showJumpForwardChooser() {
        showOptionsMenu(
            title = FormattableString.from(R.string.settings_jump_forward_title),
            options = jumpSecondsOptions(),
            listener =
                object : BottomChooserItemListener() {
                    override fun onItemClicked(formattableString: FormattableString) {
                        check(formattableString is FormattableString.ResourceString)
                        prefsRepo.jumpForwardSeconds = jumpSecondsFor(formattableString.stringRes, 30L)
                        hideBottomSheet()
                    }
                },
        )
    }

    fun showJumpBackwardChooser() {
        showOptionsMenu(
            title = FormattableString.from(R.string.settings_jump_backward_title),
            options = jumpSecondsOptions(),
            listener =
                object : BottomChooserItemListener() {
                    override fun onItemClicked(formattableString: FormattableString) {
                        check(formattableString is FormattableString.ResourceString)
                        prefsRepo.jumpBackwardSeconds = jumpSecondsFor(formattableString.stringRes, 10L)
                        hideBottomSheet()
                    }
                },
        )
    }

    private fun jumpSecondsOptions() =
        listOf(
            FormattableString.from(R.string.settings_jump_10_seconds),
            FormattableString.from(R.string.settings_jump_15_seconds),
            FormattableString.from(R.string.settings_jump_20_seconds),
            FormattableString.from(R.string.settings_jump_30_seconds),
            FormattableString.from(R.string.settings_jump_60_seconds),
            FormattableString.from(R.string.settings_jump_90_seconds),
        )

    private fun jumpSecondsFor(
        stringRes: Int,
        default: Long,
    ): Long =
        when (stringRes) {
            R.string.settings_jump_10_seconds -> 10L
            R.string.settings_jump_15_seconds -> 15L
            R.string.settings_jump_20_seconds -> 20L
            R.string.settings_jump_30_seconds -> 30L
            R.string.settings_jump_60_seconds -> 60L
            R.string.settings_jump_90_seconds -> 90L
            else -> default
        }

    /** Shows a chooser of refresh-rate presets, mirroring the phone's row of the same name. */
    fun showRefreshRateChooser() {
        showOptionsMenu(
            title = FormattableString.from(R.string.settings_refresh_rate_title),
            options =
                listOf(
                    FormattableString.from(R.string.settings_refresh_rate_always),
                    FormattableString.from(R.string.settings_refresh_rate_15_minutes),
                    FormattableString.from(R.string.settings_refresh_rate_1_hour),
                    FormattableString.from(R.string.settings_refresh_rate_3_hours),
                    FormattableString.from(R.string.settings_refresh_rate_6_hours),
                    FormattableString.from(R.string.settings_refresh_rate_1_day),
                    FormattableString.from(R.string.settings_refresh_rate_3_days),
                    FormattableString.from(R.string.settings_refresh_rate_1_week),
                    FormattableString.from(R.string.settings_refresh_rate_manual),
                ),
            listener =
                object : BottomChooserItemListener() {
                    override fun onItemClicked(formattableString: FormattableString) {
                        check(formattableString is FormattableString.ResourceString)
                        prefsRepo.refreshRateMinutes =
                            when (formattableString.stringRes) {
                                R.string.settings_refresh_rate_always -> 0L
                                R.string.settings_refresh_rate_15_minutes -> 15L
                                R.string.settings_refresh_rate_1_hour -> 60L
                                R.string.settings_refresh_rate_3_hours -> 180L
                                R.string.settings_refresh_rate_6_hours -> 360L
                                R.string.settings_refresh_rate_1_day -> 60L * 24
                                R.string.settings_refresh_rate_3_days -> 60L * 24 * 3
                                R.string.settings_refresh_rate_1_week -> 60L * 24 * 7
                                R.string.settings_refresh_rate_manual -> Long.MAX_VALUE
                                else -> throw NoWhenBranchMatchedException(
                                    "Unknown refresh rate option",
                                )
                            }
                        hideBottomSheet()
                    }
                },
        )
    }

    fun confirmDeleteDownloadedFiles() {
        showOptionsMenu(
            title = FormattableString.from(R.string.settings_clear_downloads_warning),
            options = listOf(FormattableString.yes, FormattableString.no),
            listener =
                object : BottomChooserItemListener() {
                    override fun onItemClicked(formattableString: FormattableString) {
                        if (formattableString == FormattableString.yes) {
                            viewModelScope.launch {
                                val deletedFileCount = cachedFileManager.uncacheAllInLibrary()
                                showUserMessage(
                                    FormattableString.ResourceString(
                                        R.string.settings_delete_synced_response,
                                        placeHolderStrings = listOf(deletedFileCount.toString()),
                                    ),
                                )
                            }
                        }
                        hideBottomSheet()
                    }
                },
        )
    }

    fun confirmLogOut() {
        viewModelScope.launch {
            if (!cachedFileManager.hasUserCachedTracks()) {
                logOut()
                return@launch
            }
            showOptionsMenu(
                title = FormattableString.from(R.string.settings_clear_downloads_warning),
                options = listOf(FormattableString.yes, FormattableString.no),
                listener =
                    object : BottomChooserItemListener() {
                        override fun onItemClicked(formattableString: FormattableString) {
                            if (formattableString == FormattableString.yes) {
                                logOut()
                            }
                            hideBottomSheet()
                        }
                    },
            )
        }
    }

    private fun logOut() {
        Timber.i("Logging out")
        viewModelScope.launch {
            cachedFileManager.uncacheAllInLibrary()
            withContext(Dispatchers.IO) {
                bookRepository.clear()
                trackRepository.clear()
                collectionsRepository.clear()
            }
            mediaServiceConnection.transportControls?.stop()
            plexConfig.clear()
            plexLoginRepo.determineLoginState()
        }
    }

    fun showUserMessage(formattableString: FormattableString) {
        _messageForUser.postEvent(formattableString)
    }
}
