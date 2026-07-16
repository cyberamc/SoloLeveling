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

object DeliveryReminder {
    const val CHANNEL_ID = "delivery_reminder"
    private const val NOTIFICATION_ID = 9401

    // Distinct request codes per day so the two alarms don't overwrite each other.
    private const val REQ_TUESDAY = 1101
    private const val REQ_WEDNESDAY = 1102

    private const val HOUR_24 = 7    // 7 AM
    private const val MINUTE = 35    // :35 -> 7:35 AM

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Delivery Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Delivery-day package cap reminder" }
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    /** Schedule (or reschedule) the Tuesday and Wednesday 7:35 AM reminders. Safe to call repeatedly. */
    fun scheduleAll(context: Context) {
        scheduleForDay(context, Calendar.TUESDAY, REQ_TUESDAY)
        scheduleForDay(context, Calendar.WEDNESDAY, REQ_WEDNESDAY)
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

        val intent = Intent(context, DeliveryReminderReceiver::class.java).apply {
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
            .setContentTitle("\uD83D\uDCE6 Do not accept more than 80 packages today, no matter what.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notification)
    }
}

class DeliveryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DeliveryReminder.showNotification(context)
        // Re-arm this same day for next week.
        DeliveryReminder.scheduleAll(context)
    }
}
