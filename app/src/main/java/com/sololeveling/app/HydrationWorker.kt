package com.sololeveling.app

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class HydrationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val questTitle = inputData.getString("questTitle") ?: "Hydration Quest"
        
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(applicationContext, "hydration_channel")
            .setContentTitle("Hydrate")
            .setContentText("Complete your hydration quest")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(questTitle.hashCode(), notification)
        
        return Result.success()
    }
}
