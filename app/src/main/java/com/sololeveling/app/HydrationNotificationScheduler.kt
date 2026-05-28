package com.sololeveling.app

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class NotificationSchedule(
    val time: String,
    val questTitle: String,
    val notificationTitle: String,
    val notificationText: String,
    val notificationId: Int
)

object HydrationNotificationScheduler {

    private val HOME_DAY_QUESTS = listOf(
        NotificationSchedule("11:00", "Hydrate @ 11 AM", "Hydrate", "Complete your hydration quest", 1000),
        NotificationSchedule("15:00", "Hydrate @ 3 PM", "Hydrate", "Complete your hydration quest", 1000),
        NotificationSchedule("19:00", "Hydrate @ 7 PM", "Hydrate", "Complete your hydration quest", 1000),
        NotificationSchedule("20:45", "Complete Daily Hydration @ 8:45 PM", "Hydrate", "Complete your hydration quest", 1000),
        NotificationSchedule("21:00", "Quest Reminder @ 9 PM", "Quest Reminder", "Do your best to finish your quests", 1001),
        NotificationSchedule("21:20", "Prepare Tomorrow's Hydration @ 9:20 PM", "Hydrate", "Complete your hydration quest", 1000)
    )

    private val DELIVERY_DAY_QUESTS = listOf(
        NotificationSchedule("10:00", "Hydrate @ 10 AM", "Hydrate", "Complete your hydration quest", 1000),
        NotificationSchedule("12:00", "Hydrate @ 12 PM", "Hydrate", "Complete your hydration quest", 1000),
        NotificationSchedule("14:00", "Hydrate @ 2 PM", "Hydrate", "Complete your hydration quest", 1000),
        NotificationSchedule("18:00", "Complete Daily Hydration @ 6 PM", "Hydrate", "Complete your hydration quest", 1000),
        NotificationSchedule("18:30", "Prepare Tomorrow's Hydration @ 6:30 PM", "Hydrate", "Complete your hydration quest", 1000),
        NotificationSchedule("21:00", "Quest Reminder @ 9 PM", "Quest Reminder", "Do your best to finish your quests", 1001)
    )

    fun scheduleHydrationNotifications(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val calendar = Calendar.getInstance()
        val todayDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val isDeliveryDay = todayDayOfWeek == 3 || todayDayOfWeek == 4

        val questsToSchedule = if (isDeliveryDay) DELIVERY_DAY_QUESTS else HOME_DAY_QUESTS

        for (schedule in questsToSchedule) {
            val (hour, minute) = schedule.time.split(":").map { it.toInt() }
            val workName = "hydration_${schedule.questTitle.hashCode()}"

            val inputData = Data.Builder()
                .putString("questTitle", schedule.questTitle)
                .putString("notificationTitle", schedule.notificationTitle)
                .putString("notificationText", schedule.notificationText)
                .putInt("notificationId", schedule.notificationId)
                .build()

            val initialDelay = calculateDelayToTime(hour, minute)

            val workRequest = PeriodicWorkRequestBuilder<HydrationWorker>(
                1, TimeUnit.DAYS
            )
                .setInputData(inputData)
                .setInitialDelay(initialDelay, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    private fun calculateDelayToTime(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        val delayMillis = target.timeInMillis - now.timeInMillis
        return delayMillis / (1000 * 60)
    }
}