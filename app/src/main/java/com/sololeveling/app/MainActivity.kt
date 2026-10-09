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

        ensureNotificationPermission()

        // User reminders: channel + periodic poll + arm anything due soon right now
        UserReminder.createChannel(this)
        UserReminder.enqueuePolling(this)
        Thread { UserReminder.armAllNow(this@MainActivity) }.start()

        // Morning brief: create channel and schedule daily alarm
        MorningBrief.createChannel(this)
        val mbPrefs = getSharedPreferences("morning_brief", android.content.Context.MODE_PRIVATE)
        val mbHour = mbPrefs.getInt("alarm_hour", 5)
        val mbMinute = mbPrefs.getInt("alarm_minute", 0)
        MorningBrief.schedule(this, mbHour, mbMinute)

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
            if (navigateTo == "morning_brief") {
                MorningAlarmService.dismiss(this)
                markDismissedToday(this)
            }
            if (navigateTo == "evening_brief") {
                MorningAlarmService.dismiss(this)
            }
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