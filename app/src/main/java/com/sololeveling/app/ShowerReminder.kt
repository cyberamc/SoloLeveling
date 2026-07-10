package com.sololeveling.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * Fires a "time to shower" notification each day at that day's Shower task time,
 * read live from the routine so it always matches the routine editor.
 *
 * Because the shower time varies by day and isn't known until looked up, scheduling
 * works in two parts:
 *   - scheduleDailyArm(): a fixed daily alarm (~3 AM) that re-runs armToday() each
 *     morning, so today's shower alarm is set from current routine data.
 *   - armToday(): fetches today's routine, finds the "Shower" task, and if its time
 *     hasn't passed yet, arms a one-shot alarm for it today. Also called on app open
 *     and boot so a mid-day launch still arms today's reminder (not just next 3 AM).
 */
object ShowerReminder {
    const val CHANNEL_ID = "shower_reminder"
    private const val NOTIFICATION_ID = 3000

    // Distinct request codes: the daily "arm" trigger vs. the actual shower fire.
    private const val REQ_DAILY_ARM = 3001
    private const val REQ_SHOWER_FIRE = 3002

    // Daily arm runs at 3:00 AM.
    private const val ARM_HOUR_24 = 3
    private const val ARM_MINUTE = 0

    private const val BASE_URL = "https://mysololeveling.us"

    // Action so the receiver can tell "arm today" from "show the notification".
    const val ACTION_ARM = "com.sololeveling.app.SHOWER_ARM"
    const val ACTION_FIRE = "com.sololeveling.app.SHOWER_FIRE"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shower Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Daily reminder at your scheduled shower time" }
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    /** Schedule the fixed daily 3 AM "arm today's shower" alarm. Safe to call repeatedly. */
    fun scheduleDailyArm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, ARM_HOUR_24)
            set(Calendar.MINUTE, ARM_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, ShowerReminderReceiver::class.java).apply { action = ACTION_ARM }
        val pi = PendingIntent.getBroadcast(
            context, REQ_DAILY_ARM, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
        }
    }

    /**
     * Fetch today's Shower task time and, if it hasn't passed, arm a one-shot alarm for it.
     * Must be called off the main thread (does a network request).
     */
    fun armToday(context: Context) {
        val minutes = fetchTodayShowerMinutes() ?: return  // no Shower task or fetch failed → skip
        val now = Calendar.getInstance()
        val fireAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If today's shower time already passed, do nothing (tomorrow's 3 AM arm handles the next one).
        if (fireAt.timeInMillis <= now.timeInMillis) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ShowerReminderReceiver::class.java).apply { action = ACTION_FIRE }
        val pi = PendingIntent.getBroadcast(
            context, REQ_SHOWER_FIRE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt.timeInMillis, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt.timeInMillis, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt.timeInMillis, pi)
        }
    }

    /**
     * Fetch /api/routine/{todayWeekday}, find the task titled "Shower", parse its time.
     * Returns minutes-since-midnight, or null if not found / fetch failed.
     */
    private fun fetchTodayShowerMinutes(): Int? {
        return try {
            val todayWeekday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 // SUNDAY=1 -> 0
            val url = URL("$BASE_URL/api/routine/$todayWeekday")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val obj = JSONObject(text)
            val quests = obj.optJSONArray("quests") ?: return null
            for (i in 0 until quests.length()) {
                val q = quests.getJSONObject(i)
                if (q.optString("title") == "Shower") {
                    return parseTimeToMinutes(q.optString("time"))
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Parse "9:10 AM" / "5:35 PM" -> minutes since midnight. Null if unparseable. */
    private fun parseTimeToMinutes(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val m = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM)$", RegexOption.IGNORE_CASE).find(s.trim())
            ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: 0
        val ap = m.groupValues[3].uppercase()
        if (h < 1 || h > 12 || min > 59) return null
        if (ap == "PM" && h != 12) h += 12
        if (ap == "AM" && h == 12) h = 0
        return h * 60 + min
    }

    fun showNotification(context: Context) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("\uD83D\uDEBF Time to shower")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notification)
    }
}

class ShowerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    ShowerReminder.ACTION_FIRE -> {
                        // Show the shower notification for today.
                        ShowerReminder.showNotification(context)
                    }
                    else -> {
                        // ACTION_ARM (daily 3 AM) or any re-arm: set today's shower alarm.
                        ShowerReminder.armToday(context)
                    }
                }
            } finally {
                // Always keep the daily 3 AM arm alive for tomorrow.
                ShowerReminder.scheduleDailyArm(context)
                pending.finish()
            }
        }.start()
    }
}
