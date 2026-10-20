package com.sololeveling.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the Morning and Bedtime alarms from their saved settings. Android clears an app's alarms on
 * reinstall/update, force-stop and reboot, and these two were only scheduled when Save was pressed in
 * the Alarms dialog, so they could be "enabled" with nothing scheduled.
 */
object AlarmRearm {
    fun rearmAll(context: Context) {
        val app = context.applicationContext
        val m = app.getSharedPreferences("morning_brief", Context.MODE_PRIVATE)
        if (m.getBoolean("alarm_enabled", true)) MorningBrief.schedule(app, m.getInt("alarm_hour", 5), m.getInt("alarm_minute", 0))
        val b = app.getSharedPreferences("bedtime_alarm", Context.MODE_PRIVATE)
        if (b.getBoolean("alarm_enabled", true)) BedtimeAlarm.schedule(app, b.getInt("alarm_hour", 21), b.getInt("alarm_minute", 30))
    }
}

class AlarmRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> AlarmRearm.rearmAll(context)
        }
    }
}
