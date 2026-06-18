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
import java.util.Calendar

object SleepReminder {
    const val CHANNEL_ID = "sleep_reminder"
    private const val NOTIFICATION_ID = 9301

    // Distinct request codes per day so the two alarms don't overwrite each other.
    private const val REQ_MONDAY = 1001
    private const val REQ_TUESDAY = 1002

    private const val HOUR_24 = 21   // 9 PM
    private const val MINUTE = 30    // :30  -> 9:30 PM

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Nightly wind-down reminder" }
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    /** Schedule (or reschedule) the Monday and Tuesday 9:30 PM reminders. Safe to call repeatedly. */
    fun scheduleAll(context: Context) {
        scheduleForDay(context, Calendar.MONDAY, REQ_MONDAY)
        scheduleForDay(context, Calendar.TUESDAY, REQ_TUESDAY)
    }

    private fun scheduleForDay(context: Context, dayOfWeek: Int, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, HOUR_24)
            set(Calendar.MINUTE, MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If that moment already passed this week, push to next week.
        if (next.timeInMillis <= now.timeInMillis) {
            next.add(Calendar.WEEK_OF_YEAR, 1)
        }

        val intent = Intent(context, SleepReminderReceiver::class.java).apply {
            putExtra("request_code", requestCode)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Exact alarm if allowed, else fall back to a non-exact alarm (still fires, just less precise).
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

    fun showNotification(context: Context) {
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
            .setContentTitle("\uD83D\uDE34 Time to wind down \u2014 sleep at 10 PM")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notification)
    }
}

class SleepReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SleepReminder.showNotification(context)
        // Re-arm this same day for next week.
        val requestCode = intent.getIntExtra("request_code", -1)
        when (requestCode) {
            1001, 1002 -> SleepReminder.scheduleAll(context)
            else -> SleepReminder.scheduleAll(context)
        }
    }
}
