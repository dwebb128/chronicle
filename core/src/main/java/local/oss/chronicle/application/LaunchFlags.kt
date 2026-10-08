package local.oss.chronicle.application

/**
 * Intent extras and `PendingIntent` request codes used to launch an app module's entry Activity at
 * a particular screen.
 *
 * These are written by shared code — [local.oss.chronicle.features.player.NotificationBuilder]
 * builds the media notification's content intent — and read by each app's own launcher Activity,
 * so they cannot live on either `MainActivity`. The phone and watch Activities both honour them.
 */
object LaunchFlags {
    const val FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING = "OPEN_ACTIVITY_TO_AUDIOBOOK"
    const val REQUEST_CODE_OPEN_APP_TO_CURRENTLY_PLAYING = -12
    const val FLAG_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID = "OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID"

    // add audiobook id to this number to avoid repeats
    const val REQUEST_CODE_PREFIX_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID = -1001110
}
