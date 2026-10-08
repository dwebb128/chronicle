package local.oss.chronicle.application

/**
 * Intent extras and `PendingIntent` request codes for opening the app at a particular screen.
 * Notifications built in `:core` write them; the app's `MainActivity` reads them.
 */
object LaunchFlags {
    const val FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING = "OPEN_ACTIVITY_TO_AUDIOBOOK"
    const val REQUEST_CODE_OPEN_APP_TO_CURRENTLY_PLAYING = -12
    const val FLAG_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID = "OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID"

    // add audiobook id to this number to avoid repeats
    const val REQUEST_CODE_PREFIX_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID = -1001110
}
