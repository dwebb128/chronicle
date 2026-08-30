package local.oss.chronicle.features.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hand-rolled (no Horologist) monitor for whether a Bluetooth-family audio output device is
 * currently attached - A2DP, a BLE headset/speaker, or a hearing aid. On a watch this matters
 * because playing an audiobook out loud through the built-in speaker is a much worse default
 * than it is on a phone.
 *
 * This does NOT block playback when no such device is present; it only exposes state so the UI
 * can show a non-blocking prompt ("no headphones connected") while playback continues normally
 * through the speaker.
 *
 * All [AudioDeviceInfo] type constants referenced here (`TYPE_BLUETOOTH_A2DP`,
 * `TYPE_BLE_HEADSET`, `TYPE_BLE_SPEAKER`, `TYPE_HEARING_AID`) were added at API 33 or earlier,
 * so they are safe unconditionally at this app's minSdk 34.
 */
@Singleton
class AudioOutputMonitor
    @Inject
    constructor(
        private val context: Context,
    ) {
        private val audioManager: AudioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private val mainThreadHandler = Handler(Looper.getMainLooper())

        private val _hasBluetoothAudio = MutableStateFlow(false)

        /** Whether a Bluetooth-family audio output (A2DP / BLE headset / BLE speaker / hearing aid) is currently attached. */
        val hasBluetoothAudio: StateFlow<Boolean> = _hasBluetoothAudio.asStateFlow()

        private var registered = false

        private val deviceCallback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    refresh()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                    refresh()
                }
            }

        fun register() {
            if (registered) {
                return
            }
            registered = true
            audioManager.registerAudioDeviceCallback(deviceCallback, mainThreadHandler)
            // registerAudioDeviceCallback does not synchronously invoke the callback with the
            // current device set, so seed the initial state explicitly.
            refresh()
        }

        fun unregister() {
            if (!registered) {
                return
            }
            registered = false
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        }

        private fun refresh() {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasBluetooth =
                outputs.any { device ->
                    when (device.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_BLE_SPEAKER,
                        AudioDeviceInfo.TYPE_HEARING_AID,
                        -> true
                        else -> false
                    }
                }
            Timber.d("[AudioOutputMonitor] hasBluetoothAudio=$hasBluetooth (outputs=${outputs.size})")
            _hasBluetoothAudio.value = hasBluetooth
        }
    }
