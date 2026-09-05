package local.oss.chronicle.application

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import local.oss.chronicle.util.Event
import local.oss.chronicle.util.postEvent
import timber.log.Timber
import javax.inject.Inject

/**
 * Wear OS has no draggable mini-player / bottom sheet, so the phone-era state machine that used
 * to live here (audiobook/chapter caching for the mini-player, [BottomSheetState] transitions,
 * collections visibility) is gone. What remains is [errorMessage] (surfaced from the playback
 * error broadcast receiver in [MainActivity]) and the [MainActivity.CurrentlyPlayingInterface]
 * implementation, which [MainActivity] still needs a concrete type for.
 */
@ExperimentalCoroutinesApi
class MainActivityViewModel : ViewModel(), MainActivity.CurrentlyPlayingInterface {
    class Factory
        @Inject
        constructor() : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainActivityViewModel::class.java)) {
                    return MainActivityViewModel() as T
                } else {
                    throw IllegalArgumentException(
                        "Cannot instantiate $modelClass from MainActivityViewModel.Factory",
                    )
                }
            }
        }

    /** Retained only so [MainActivity.CurrentlyPlayingInterface] has a concrete type to reference. */
    enum class BottomSheetState {
        COLLAPSED,
        HIDDEN,
        EXPANDED,
    }

    private var _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>>
        get() = _errorMessage

    fun showUserMessage(errorMessage: String) {
        Timber.i("Showing error message: $errorMessage")
        _errorMessage.postEvent(errorMessage)
    }

    override fun setBottomSheetState(state: BottomSheetState) {
        // No-op on Wear: there is no draggable mini-player / bottom sheet UI.
    }
}
