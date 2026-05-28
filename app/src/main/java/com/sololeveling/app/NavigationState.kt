package com.sololeveling.app

import androidx.compose.runtime.mutableStateOf

object NavigationState {
    val pendingNavigation = mutableStateOf<String?>(null)
}