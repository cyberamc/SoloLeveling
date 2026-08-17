package com.sololeveling.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms user reminders after a device reboot. AlarmManager alarms do not survive a
 * reboot, so without this the website-created reminders would stay cleared until the
 * app is next opened. Listens for BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            // Channels must exist before scheduling/notifying.
            UserReminder.createChannel(context)
            UserReminder.enqueuePolling(context)
            // User reminders are re-armed here, since alarms don't survive a reboot.
            Thread {
                UserReminder.armAllNow(context)
            }.start()
        }
    }
}