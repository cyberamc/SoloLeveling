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

object TaskReminder {
    const val CHANNEL_ID = "task_reminder"
    private const val NOTIFICATION_ID = 2000
    private const val REQ_DAILY = 2001

    private const val HOUR_24 = 20   // 8 PM
    private const val MINUTE = 0     // :00 -> 8:00 PM

    private const val BASE_URL = "https://mysololeveling.us"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Evening reminder to finish remaining tasks" }
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    /** Schedule (or reschedule) the daily 8 PM reminder. Safe to call repeatedly. */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR_24)
            set(Calendar.MINUTE, MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If 8 PM already passed today, push to tomorrow.
        if (next.timeInMillis <= now.timeInMillis) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, TaskReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_DAILY,
            intent,
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
     * Fetch today's remaining task count (incomplete dailies + today's incomplete required quests).
     * Returns null if the fetch fails for any reason.
     */
    private fun fetchRemainingCount(): Int? {
        return try {
            val url = URL("$BASE_URL/api/quests")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val obj = JSONObject(text)

            val totalDailies = obj.optInt("totalDailies", 0)
            val dailiesCompleted = obj.optInt("dailiesCompleted", 0)
            val remainingDailies = maxOf(0, totalDailies - dailiesCompleted)

            // Today's required (non-optional) weekly quests that aren't completed
            val todayWeekday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 // Calendar.SUNDAY=1 -> 0
            var remainingWeekly = 0
            val weekly = obj.optJSONArray("weeklyQuests")
            if (weekly != null) {
                for (i in 0 until weekly.length()) {
                    val q = weekly.getJSONObject(i)
                    val optional = q.optInt("optional", 0)
                    val completed = q.optInt("completed", 0)
                    val weekday = q.optInt("weekday", -1)
                    if (optional == 0 && completed == 0 && weekday == todayWeekday) {
                        remainingWeekly++
                    }
                }
            }
            remainingDailies + remainingWeekly
        } catch (e: Exception) {
            null
        }
    }

    fun showNotification(context: Context) {
        val count = fetchRemainingCount()

        val text = when {
            count == null -> "Final stretch — clear your remaining quests"
            count == 0 -> "All quests cleared. Nice work."
            count == 1 -> "Final stretch — 1 quest remaining"
            else -> "Final stretch — $count quests remaining"
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notification)
    }
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Network call must run off the main thread; use a background thread within the
        // receiver's goAsync() window so onReceive returns promptly.
        val pending = goAsync()
        Thread {
            try {
                TaskReminder.showNotification(context)
            } finally {
                // Re-arm for tomorrow, then release.
                TaskReminder.schedule(context)
                pending.finish()
            }
        }.start()
    }
}
