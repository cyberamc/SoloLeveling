package com.sololeveling.app

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object HydrationNotificationScheduler {

    private val HOME_DAY_QUESTS = listOf(
        "11:00" to "Hydrate @ 11 AM",
        "15:00" to "Hydrate @ 3 PM",
        "19:00" to "Hydrate @ 7 PM",
        "20:45" to "Complete Daily Hydration @ 8:45 PM",
        "21:20" to "Prepare Tomorrow's Hydration @ 9:20 PM"
    )

    private val DELIVERY_DAY_QUESTS = listOf(
        "10:00" to "Hydrate @ 10 AM",
        "12:00" to "Hydrate @ 12 PM",
        "14:00" to "Hydrate @ 2 PM",
        "18:00" to "Complete Daily Hydration @ 6 PM",
        "18:30" to "Prepare Tomorrow's Hydration @ 6:30 PM"
    )
    
    fun scheduleHydrationNotifications(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val calendar = Calendar.getInstance()
        val todayDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // 1 = Sunday, 2 = Monday, 3 = Tuesday, etc.
        val isDeliveryDay = todayDayOfWeek == 3 || todayDayOfWeek == 4 // Tuesday or Wednesday
        
        val questsToSchedule = if (isDeliveryDay) DELIVERY_DAY_QUESTS else HOME_DAY_QUESTS
        
        for ((time, questTitle) in questsToSchedule) {
            val (hour, minute) = time.split(":").map { it.toInt() }
            val workName = "hydration_${questTitle.hashCode()}"
            
            val inputData = Data.Builder()
                .putString("questTitle", questTitle)
                .build()
            
            val initialDelay = calculateDelayToTime(hour, minute)
            
            val hydrationWorkRequest = PeriodicWorkRequestBuilder<HydrationWorker>(
                1, TimeUnit.DAYS
            )
                .setInputData(inputData)
                .setInitialDelay(initialDelay, TimeUnit.MINUTES)
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.KEEP,
                hydrationWorkRequest
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
        
        // If target time has passed today, schedule for tomorrow
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        val delayMillis = target.timeInMillis - now.timeInMillis
        return delayMillis / (1000 * 60) // Convert to minutes
    }
}
