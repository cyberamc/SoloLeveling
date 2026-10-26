package com.sololeveling.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class MorningAlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_START = "com.sololeveling.app.ALARM_START"
        const val ACTION_DISMISS = "com.sololeveling.app.ALARM_DISMISS"
        const val ACTION_REPOST = "com.sololeveling.app.ALARM_REPOST"
        private const val NOTIF_ID = 9002
        private const val CHANNEL_ID = "morning_alarm_service"

        const val EXTRA_TYPE = "alarm_type"
        const val TYPE_MORNING = "morning_brief"
        const val TYPE_EVENING = "evening_brief"

        fun start(context: Context, type: String = TYPE_MORNING) {
            val intent = Intent(context, MorningAlarmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TYPE, type)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun dismiss(context: Context) {
            val intent = Intent(context, MorningAlarmService::class.java).apply {
                action = ACTION_DISMISS
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAlarm(intent?.getStringExtra(EXTRA_TYPE) ?: TYPE_MORNING)
            ACTION_DISMISS -> stopAlarm()
            ACTION_REPOST -> repostNotification()
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Morning Alarm", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Keeps the morning alarm running"
                setSound(null, null) // sound is handled by MediaPlayer
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private var alarmType: String = TYPE_MORNING
    private var notifBuilder: NotificationCompat.Builder? = null

    private fun startAlarm(type: String = TYPE_MORNING) {
        alarmType = type
        // Acquire wake lock to keep CPU running
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "SoloLeveling::MorningAlarm"
        ).also { it.acquire(10 * 60 * 1000L) } // 10 min max

        // Build dismiss intent — opens MainActivity with morning brief overlay and stops alarm
        val dismissIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", alarmType)
        }
        val dismissPi = PendingIntent.getActivity(
            this, 0, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open app intent for notification tap
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", alarmType)
        }
        val openPi = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenPi = PendingIntent.getActivity(
            this, 2, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Swiping the notification away re-posts it (see repostNotification) instead of leaving the sound running with no way to stop it.
        val repostIntent = Intent(this, MorningAlarmService::class.java).apply { action = ACTION_REPOST }
        val repostPi = PendingIntent.getService(
            this, 3, repostIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🌅 Break the pattern today")
            .setContentText("or the loop will repeat tomorrow.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setDeleteIntent(repostPi)
            .setAutoCancel(false)
            .setContentIntent(openPi)
            .setFullScreenIntent(fullScreenPi, true)
            .addAction(android.R.drawable.ic_delete, "Dismiss", dismissPi)
            .also { notifBuilder = it }
            .build()

        startForeground(NOTIF_ID, notification)

        // Play audio at max volume, looping
        try {
            val afd = resources.openRawResourceFd(R.raw.morning_alarm)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                // Set volume to max
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            stopSelf()
        }
    }

    // The user swiped the alarm notification away while the alarm is still sounding: put it back so it can be dismissed.
    private fun repostNotification() {
        if (mediaPlayer == null) { stopSelf(); return }
        val b = notifBuilder ?: return
        startForeground(NOTIF_ID, b.setOnlyAlertOnce(true).build())
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
        try {
            wakeLock?.release()
            wakeLock = null
        } catch (e: Exception) {}
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
        try { wakeLock?.release() } catch (e: Exception) {}
    }
}
