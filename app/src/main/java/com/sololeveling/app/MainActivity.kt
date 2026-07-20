package com.sololeveling.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Intent

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNavigationIntent(intent)

        // Sleep reminder: channel + permission + schedule Mon/Tue 9:30 PM
        SleepReminder.createChannel(this)
        ensureNotificationPermission()
        SleepReminder.scheduleAll(this)

        // Task reminder: channel + daily 3 AM arm + arm today now (network → background thread)
        TaskReminder.createChannel(this)
        TaskReminder.scheduleDailyArm(this)
        Thread { TaskReminder.armToday(this@MainActivity) }.start()

        // Shower reminder: channel + daily 3 AM arm + arm today now (network → background thread)
        ShowerReminder.createChannel(this)
        ShowerReminder.scheduleDailyArm(this)
        Thread { ShowerReminder.armToday(this@MainActivity) }.start()

        // Delivery reminder: channel + schedule Tue/Wed 7:35 AM package cap
        DeliveryReminder.createChannel(this)
        DeliveryReminder.scheduleAll(this)

        // Routine reminder: channel + daily 3 AM arm + arm today now (network → background thread)
        RoutineReminder.createChannel(this)
        RoutineReminder.scheduleDailyArm(this)
        Thread { RoutineReminder.armToday(this@MainActivity) }.start()

        // Vape reminder: channel + schedule daily 8:30 PM
        VapeReminder.createChannel(this)
        VapeReminder.schedule(this)

        // User reminders: channel + periodic poll + arm anything due soon right now
        UserReminder.createChannel(this)
        UserReminder.enqueuePolling(this)
        Thread { UserReminder.armAllNow(this@MainActivity) }.start()

        setContent {
            MainTabScreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo != null) {
            NavigationState.pendingNavigation.value = navigateTo
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}