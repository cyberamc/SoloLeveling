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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * User-created one-time reminders, entered on the /reminders web page.
 *
 * The phone learns about new reminders by polling, so delivery works like this:
 *   - A WorkManager periodic job (every POLL_MINUTES) fetches pending reminders and
 *     arms a one-shot exact alarm for any that fall inside the next polling window.
 *   - armAllNow() runs the same fetch immediately on app open and on boot, so a
 *     reminder created moments ago is picked up as soon as the app is opened.
 *
 * Practical consequence: a reminder set more than POLL_MINUTES out fires without any
 * interaction; one set sooner than that should be followed by opening the app. The web
 * page warns when the chosen time falls inside that window.
 *
 * Each reminder's server id doubles as its alarm request code and notification id, so
 * re-arming the same reminder replaces its alarm rather than stacking duplicates.
 */
object UserReminder {
    const val CHANNEL_ID = "user_reminder"

    private const val BASE_URL = "https://mysololeveling.us"
    private const val WORK_NAME = "user_reminder_poll"

    /** How often the background poll runs. WorkManager's floor is 15 minutes. */
    const val POLL_MINUTES = 30L

    // Offset so reminder alarm request codes can't collide with the fixed reminders
    // (which use codes in the 1000–3999 range).
    private const val REQ_BASE = 500000
    private const val NOTIF_BASE = 500000

    const val ACTION_FIRE = "com.sololeveling.app.USER_REMINDER_FIRE"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TITLE = "reminder_title"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Your scheduled one-time reminders" }
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    /** Enqueue the periodic poll. Safe to call repeatedly — keeps the existing schedule. */
    fun enqueuePolling(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ReminderPollWorker>(
            POLL_MINUTES, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Fetch pending reminders and arm alarms for any due soon.
     * Must be called off the main thread (does a network request).
     */
    fun armAllNow(context: Context) {
        val pending = fetchPending() ?: return
        val now = System.currentTimeMillis()
        // Arm anything from now up to a little past the next poll, so nothing slips
        // between polling cycles. Re-arming an already-armed reminder is harmless.
        val horizon = now + TimeUnit.MINUTES.toMillis(POLL_MINUTES * 2)

        for (r in pending) {
            if (r.atMillis <= now) continue          // already passed
            if (r.atMillis > horizon) continue       // too far out; a later poll will get it
            armOne(context, r.id, r.title, r.atMillis)
        }
    }

    private fun armOne(context: Context, id: Int, title: String, atMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, UserReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
        }
        val pi = PendingIntent.getBroadcast(
            context, REQ_BASE + id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    data class Pending(val id: Int, val title: String, val atMillis: Long)

    /** GET /api/reminders → pending list. Returns null if the fetch fails. */
    private fun fetchPending(): List<Pending>? {
        return try {
            val url = URL("$BASE_URL/api/reminders")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val obj = JSONObject(text)
            val arr = obj.optJSONArray("reminders") ?: return emptyList()
            val out = ArrayList<Pending>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optInt("id", -1)
                val title = o.optString("title")
                val at = parseLocalDateTime(o.optString("remind_at"))
                if (id >= 0 && at != null) {
                    out.add(Pending(id, title, at))
                }
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    /** Parse "YYYY-MM-DD HH:MM:SS" (server local time) into epoch millis. */
    private fun parseLocalDateTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            fmt.parse(s)?.time
        } catch (e: Exception) {
            null
        }
    }

    /** Tell the server this reminder has been delivered so it stops appearing as pending. */
    fun markFired(id: Int) {
        try {
            val url = URL("$BASE_URL/api/reminders/$id/fired")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write("{}".toByteArray()) }
            conn.inputStream.bufferedReader().readText()
            conn.disconnect()
        } catch (e: Exception) {
            // Best effort — the server's day-of-grace purge cleans up if this fails.
        }
    }

    fun showNotification(context: Context, id: Int, title: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context, NOTIF_BASE + id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("\u23F0 " + (if (title.isBlank()) "Reminder" else title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_BASE + id, notification)
    }
}

/** Periodic background poll: fetch pending reminders and arm any that are due soon. */
class ReminderPollWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {
    override fun doWork(): Result {
        return try {
            UserReminder.createChannel(applicationContext)
            UserReminder.armAllNow(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class UserReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UserReminder.ACTION_FIRE) return
        val id = intent.getIntExtra(UserReminder.EXTRA_ID, -1)
        val title = intent.getStringExtra(UserReminder.EXTRA_TITLE) ?: ""
        if (id < 0) return

        // Show immediately, then retire the reminder server-side off the main thread.
        UserReminder.showNotification(context, id, title)
        val pending = goAsync()
        Thread {
            try {
                UserReminder.markFired(id)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
