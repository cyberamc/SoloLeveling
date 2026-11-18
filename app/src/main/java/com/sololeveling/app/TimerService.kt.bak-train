package com.sololeveling.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that runs a single countdown timer with a live-updating, pinned
 * (ongoing) notification. When the countdown reaches zero it swaps the ongoing
 * notification for a dismissible "time's up" alert and stops itself. Only one timer
 * runs at a time — starting a new one replaces any running one.
 */
class TimerService : Service() {

    private var countDown: CountDownTimer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Timer"
        val doneText = intent?.getStringExtra(EXTRA_DONE_TEXT) ?: "Time's up"
        val totalSeconds = intent?.getIntExtra(EXTRA_SECONDS, 0) ?: 0
        if (totalSeconds <= 0) {
            stopEverything()
            return START_NOT_STICKY
        }

        createChannel(this)
        // Show the initial pinned notification and enter foreground.
        startForeground(ONGOING_ID, buildOngoing(label, totalSeconds))

        countDown?.cancel()
        countDown = object : CountDownTimer(totalSeconds * 1000L, 1000L) {
            override fun onTick(msLeft: Long) {
                val secLeft = (msLeft / 1000L).toInt()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(ONGOING_ID, buildOngoing(label, secLeft))
            }

            override fun onFinish() {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(DONE_ID, buildDone(label, doneText))
                stopEverything()
            }
        }.start()

        return START_STICKY
    }

    private fun stopEverything() {
        countDown?.cancel()
        countDown = null
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ONGOING_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        countDown?.cancel()
        super.onDestroy()
    }

    // Tapping either notification opens the app (Tasks screen).
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildOngoing(label: String, secondsLeft: Int): android.app.Notification {
        val mm = secondsLeft / 60
        val ss = secondsLeft % 60
        val timeStr = String.format("%d:%02d", mm, ss)

        val stopIntent = Intent(this, TimerService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(label)
            .setContentText(timeStr)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun buildDone(label: String, doneText: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(label)
            .setContentText(doneText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openAppIntent())
            .build()
    }

    companion object {
        const val CHANNEL_ID = "timer_channel"
        const val ONGOING_ID = 5101
        const val DONE_ID = 5102
        const val ACTION_STOP = "com.sololeveling.app.TIMER_STOP"
        const val EXTRA_LABEL = "label"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_DONE_TEXT = "done_text"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Timers", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Meditation and activity timers" }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(ch)
            }
        }

        fun start(context: Context, label: String, seconds: Int, doneText: String) {
            val intent = Intent(context, TimerService::class.java).apply {
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_DONE_TEXT, doneText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
