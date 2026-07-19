package com.sololeveling.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms all scheduled reminders after a device reboot. AlarmManager alarms do not
 * survive a reboot, so without this the sleep, task, and shower reminders would stay
 * cleared until the app is next opened. Listens for BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            // Channels must exist before scheduling/notifying.
            SleepReminder.createChannel(context)
            TaskReminder.createChannel(context)
            ShowerReminder.createChannel(context)
            DeliveryReminder.createChannel(context)
            RoutineReminder.createChannel(context)
            VapeReminder.createChannel(context)
            SleepReminder.scheduleAll(context)
            TaskReminder.schedule(context)
            ShowerReminder.scheduleDailyArm(context)
            DeliveryReminder.scheduleAll(context)
            RoutineReminder.scheduleDailyArm(context)
            VapeReminder.schedule(context)
            // Arm today's shower and routine reminders now (network → background thread) so a
            // reboot mid-day still sets today's reminders, not just the next 3 AM arm.
            Thread {
                ShowerReminder.armToday(context)
                RoutineReminder.armToday(context)
            }.start()
        }
    }
}