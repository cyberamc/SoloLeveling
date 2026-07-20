package com.sololeveling.app

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import okhttp3.RequestBody
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.systemBarsPadding
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import okhttp3.OkHttpClient
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap

private fun createUnsafeHttpClient(): OkHttpClient {
    val trustAllCerts = arrayOf<X509TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, java.security.SecureRandom())

    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0])
        .hostnameVerifier { _, _ -> true }
        .build()
}

// Application-level scope for fire-and-refresh network calls (quest toggles, protocol
// activation). Using a scope tied to a composable causes "coroutine scope left the
// composition" when a completion toggle triggers a reload that disposes the item mid-call.
val appNetworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun PlayerScreen() {
    TasksScreen()
}

@Composable
fun TasksScreen() {
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var dailyQuestsState by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var allWeeklyQuestsState by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var thisWeekExpanded by remember { mutableStateOf(false) }
    var nofapStreak by remember { mutableIntStateOf(0) }
    var showRoutine by remember { mutableStateOf(false) }
    var showNotepad by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    var showNofapNotepad by remember { mutableStateOf(false) }
    var showGoingOut by remember { mutableStateOf(false) }
    var showNoRouteConfirm by remember { mutableStateOf(false) }
    var noRouteWorking by remember { mutableStateOf(false) }
    var protocolBanner by remember { mutableStateOf<String?>(null) }

    if (showRoutine) {
        RoutineScreen(onBack = { showRoutine = false })
        return
    }
    if (showNotepad) {
        NotepadScreen(onBack = { showNotepad = false })
        return
    }
    if (showReminders) {
        RemindersScreen(onBack = { showReminders = false })
        return
    }
    if (showGoingOut) {
        GoingOutScreen(onBack = { showGoingOut = false })
        return
    }
    if (showNofapNotepad) {
        NofapNotepadScreen(onBack = { showNofapNotepad = false })
        return
    }

    if (showNoRouteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!noRouteWorking) showNoRouteConfirm = false },
            containerColor = Color(0xFF1a1a1a),
            title = { Text("Activate No Route Protocol?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Today's routine will be replaced with the No Route routine. Tasks before 10 AM will be marked complete. This resets today's progress.",
                    color = Color(0xFFB0B0B0), fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !noRouteWorking,
                    onClick = {
                        noRouteWorking = true
                        appNetworkScope.launch {
                            try {
                                postToApi("/api/protocol/noroute/activate")
                                showNoRouteConfirm = false
                                refreshTrigger++
                            } catch (e: Exception) {
                                // leave dialog open on error
                            } finally {
                                noRouteWorking = false
                            }
                        }
                    }
                ) { Text(if (noRouteWorking) "Activating..." else "Activate", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !noRouteWorking, onClick = { showNoRouteConfirm = false }) {
                    Text("Cancel", color = Color(0xFF888899))
                }
            }
        )
    }

    // Load player data once on first launch only
    LaunchedEffect(Unit) {
        try {
            val playerResponse = fetchFromApi("/api/player")
            val playerData = Gson().fromJson(playerResponse, Map::class.java)
            nofapStreak = (playerData["nofapStreak"] as? Number)?.toInt() ?: 0
        } catch (e: Exception) {
            // streak stays 0
        }
    }

    // Load tasks on first launch and on toggle refresh
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger == 0) loading = true
        try {
            loadAllQuests(
                onQuestsLoaded = { daily, weekly, _, _, _, banner ->
                    dailyQuestsState = daily
                    allWeeklyQuestsState = weekly
                    protocolBanner = banner
                    loading = false
                },
                onError = { err -> error = err; loading = false }
            )
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }

    LaunchedEffect(NavigationState.pendingNavigation.value) {
        val pending = NavigationState.pendingNavigation.value
        if (pending != null) {
            NavigationState.pendingNavigation.value = null
        }
    }

    val calendar = java.util.Calendar.getInstance()
    val todayWeekday = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
    val fullDayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val shortDayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    val todaysWeekly = allWeeklyQuestsState.filter { it.weekday == todayWeekday }
    val combinedToday = sortQuestsByTime(dailyQuestsState + todaysWeekly)
    val overdueQuests = allWeeklyQuestsState.filter { it.weekday != todayWeekday && it.isOverdue && !it.completed && !it.optional }
    val thisWeekQuests = allWeeklyQuestsState.filter { it.weekday != todayWeekday }.filter { !(it.isOverdue && !it.completed && !it.optional) }
    val thisWeekOrder = (1..6).map { (todayWeekday + it) % 7 }
    val overdueOrder = (1..6).map { ((todayWeekday - it) + 7) % 7 }
    val activeOverdueDays = overdueOrder.filter { wd -> overdueQuests.any { it.weekday == wd } }

    val requiredDailies = dailyQuestsState.filter { !it.optional }
    val requiredDailiesCompleted = requiredDailies.count { it.completed }
    val requiredTodayWeekly = todaysWeekly.filter { !it.optional }
    val requiredTodayWeeklyCompleted = requiredTodayWeekly.count { it.completed }
    val overdueText = if (overdueQuests.isNotEmpty()) " · ${overdueQuests.size} overdue" else ""

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1a1a1a))
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(text = "Tasks", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = "View Routine",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier
                        .background(Color(0xFF2a2a2a), shape = RoundedCornerShape(8.dp))
                        .clickable { showRoutine = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Text(
                    text = "Notepad",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier
                        .background(Color(0xFF2a2a2a), shape = RoundedCornerShape(8.dp))
                        .clickable { showNotepad = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Text(
                    text = "Reminders",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier
                        .background(Color(0xFF2a2a2a), shape = RoundedCornerShape(8.dp))
                        .clickable { showReminders = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                // Going Out — only Friday (5) or Saturday (6)
                if (todayWeekday == 5 || todayWeekday == 6) {
                    Text(
                        text = "Going Out",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier
                            .background(Color(0x26F59E0B), shape = RoundedCornerShape(8.dp))
                            .clickable { showGoingOut = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                // No Route — only Tuesday (2) or Wednesday (3)
                if (todayWeekday == 2 || todayWeekday == 3) {
                    Text(
                        text = "No Route",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier
                            .background(Color(0x26F59E0B), shape = RoundedCornerShape(8.dp))
                            .clickable { showNoRouteConfirm = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\"The Pain Of Discipline Or The Pain Of Regret.\"",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$requiredDailiesCompleted/${requiredDailies.size} Daily · $requiredTodayWeeklyCompleted/${requiredTodayWeekly.size} Required$overdueText",
                    fontSize = 13.sp,
                    color = Color(0xFFFFD700)
                )
                Text(
                    text = "  ·  ",
                    fontSize = 13.sp,
                    color = Color(0xFFFFD700)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showNofapNotepad = true })
                    }
                ) {
                    Text(text = "🔥", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "$nofapStreak Days", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                }
            }
            if (protocolBanner != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = protocolBanner!!,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x26F59E0B), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD700))
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $error", color = Color.Red)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "today-header") {
                    SectionHeader(label = "TODAY", subtitle = fullDayNames[todayWeekday], color = Color(0xFFFFD700))
                }
                items(combinedToday, key = { "${it.type}${it.id}" }) { quest ->
                    QuestItem(
                        quest = quest,
                        onCompleteToggle = { refreshTrigger++ },
                        questId = quest.id,
                        isCompleted = quest.completed,
                        isWeekly = quest.type == "weekly",
                        showDaySuffix = false
                    )
                }

                if (overdueQuests.isNotEmpty()) {
                    item(key = "overdue-header") {
                        SectionHeader(
                            label = "⚠ OVERDUE",
                            subtitle = "${overdueQuests.size} task${if (overdueQuests.size == 1) "" else "s"}",
                            color = Color(0xFFFF6B6B)
                        )
                    }
                    activeOverdueDays.forEach { weekday ->
                        val dayQuests = overdueQuests.filter { it.weekday == weekday }
                        item(key = "overdue-sub-$weekday") {
                            DaySubheader(label = shortDayNames[weekday], color = Color(0xFFFF6B6B))
                        }
                        items(dayQuests, key = { "w${it.id}" }) { quest ->
                            QuestItem(
                                quest = quest,
                                onCompleteToggle = { refreshTrigger++ },
                                questId = quest.id,
                                isCompleted = quest.completed,
                                isWeekly = true,
                                isOverdue = true
                            )
                        }
                    }
                }

                if (thisWeekQuests.isNotEmpty()) {
                    item(key = "thisweek-header") {
                        ThisWeekHeader(
                            expanded = thisWeekExpanded,
                            onToggle = { thisWeekExpanded = !thisWeekExpanded }
                        )
                    }
                    if (thisWeekExpanded) {
                        val orderedThisWeek = thisWeekOrder.flatMap { weekday ->
                            thisWeekQuests
                                .filter { it.weekday == weekday }
                                .sortedWith(compareBy({ it.optional }, { it.completed }))
                        }
                        items(orderedThisWeek, key = { "w${it.id}" }) { quest ->
                            QuestItem(
                                quest = quest,
                                onCompleteToggle = { refreshTrigger++ },
                                questId = quest.id,
                                isCompleted = quest.completed,
                                isWeekly = true
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

data class RoutineQuest(
    val id: Int,
    val title: String,
    val time: String?,
    val category: String,
    val xpReward: Int,
    val optional: Boolean,
    val kind: String, // "daily" or "required"
    val important: Boolean = false,
    val monthly: Boolean = false
)

@Composable
fun RoutineScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val fullDayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val shortDayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val todayWeekday = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1

    var selectedDay by remember { mutableIntStateOf(todayWeekday) }
    var quests by remember { mutableStateOf<List<RoutineQuest>>(emptyList()) }
    var dailyCount by remember { mutableIntStateOf(0) }
    var requiredCount by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedDay) {
        loading = true
        error = null
        try {
            val response = fetchFromApi("/api/routine/$selectedDay")
            val obj = Gson().fromJson(response, Map::class.java)
            dailyCount = (obj["dailyCount"] as? Number)?.toInt() ?: 0
            requiredCount = (obj["requiredCount"] as? Number)?.toInt() ?: 0
            @Suppress("UNCHECKED_CAST")
            val list = (obj["quests"] as? List<Map<String, Any?>>) ?: emptyList()
            quests = list.map { q ->
                RoutineQuest(
                    id = (q["id"] as? Number)?.toInt() ?: 0,
                    title = q["title"] as? String ?: "",
                    time = q["time"] as? String,
                    category = q["category"] as? String ?: "STR",
                    xpReward = (q["xp_reward"] as? Number)?.toInt() ?: 0,
                    optional = ((q["optional"] as? Number)?.toInt() ?: 0) == 1,
                    kind = q["kind"] as? String ?: "daily",
                    important = ((q["important"] as? Number)?.toInt() ?: 0) == 1,
                    monthly = ((q["monthly"] as? Number)?.toInt() ?: 0) == 1
                )
            }
            loading = false
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a))
                .systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Routine", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text = "Back",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier.clickable { onBack() }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$dailyCount Daily · $requiredCount Required",
                fontSize = 13.sp,
                color = Color(0xFFFFD700)
            )
        }

        // Day picker
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items((0..6).toList()) { day ->
                val isSelected = day == selectedDay
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFFFFD700) else Color(0xFF1a1a1a))
                        .clickable { selectedDay = day }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = shortDayNames[day],
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF1a1a1a) else Color(0xFFB0B0B0)
                    )
                }
            }
        }

        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD700))
            }
            error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $error", color = Color.Red)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "day-header") {
                        SectionHeader(label = fullDayNames[selectedDay].uppercase(), color = Color(0xFFFFD700))
                    }
                    items(quests, key = { "${it.kind}${it.id}" }) { q ->
                        RoutineQuestItem(q)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
fun RoutineQuestItem(q: RoutineQuest) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (q.time != null) "${q.title} @ ${q.time}" else q.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            if (q.kind == "required" || q.optional || q.important) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (q.kind == "required") {
                        if (q.monthly) {
                            Text(
                                text = "Monthly",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2DD4BF),
                                modifier = Modifier
                                    .background(Color(0x262DD4BF), shape = RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        } else {
                            Text(
                                text = "Required",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFa78bfa),
                                modifier = Modifier
                                    .background(Color(0x26a78bfa), shape = RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (q.optional) {
                        Text(
                            text = "Optional",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB0B0B0),
                            modifier = Modifier
                                .background(Color(0xFF2a2a2a), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (q.important) {
                        Text(
                            text = "Don't Skip",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B),
                            modifier = Modifier
                                .background(Color(0x26F59E0B), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotepadScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var noteText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Saved") }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var lastSaved by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val response = fetchFromApi("/api/notepad")
            val obj = Gson().fromJson(response, Map::class.java)
            noteText = obj["content"] as? String ?: ""
            lastSaved = noteText
            loaded = true
            loading = false
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }

    // Debounced autosave: 1.5s after the last edit
    LaunchedEffect(noteText) {
        if (!loaded) return@LaunchedEffect
        if (noteText == lastSaved) return@LaunchedEffect
        status = "Editing..."
        kotlinx.coroutines.delay(1500)
        status = "Saving..."
        try {
            kotlinx.coroutines.withContext(Dispatchers.IO) { saveNotepad(noteText) }
            lastSaved = noteText
            status = "Saved"
        } catch (e: Exception) {
            status = "Save failed"
        }
    }

    // Save on exit
    DisposableEffect(Unit) {
        onDispose {
            if (loaded && noteText != lastSaved) {
                val toSave = noteText
                scope.launch(Dispatchers.IO) { try { saveNotepad(toSave) } catch (e: Exception) {} }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a))
                .systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Notepad", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = status, fontSize = 12.sp, color = Color(0xFF4CAF50))
                }
                Text(
                    text = "Back",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier.clickable { onBack() }
                )
            }
        }

        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD700))
            }
            error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $error", color = Color.Red)
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        placeholder = { Text("Type your notes here...", color = Color(0xFF555577)) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color(0xFF2a2a3a),
                            cursorColor = Color(0xFFFFD700)
                        )
                    )
                }
            }
        }
    }
}

fun saveNotepad(content: String) {
    val url = URL("https://mysololeveling.us/api/notepad")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "PATCH"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    try {
        val payload = Gson().toJson(mapOf("content" to content))
        conn.outputStream.use { it.write(payload.toByteArray()) }
        conn.responseCode
    } finally { conn.disconnect() }
}


@Composable
fun GoingOutScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var armable by remember { mutableStateOf<String?>(null) }   // "SAT" | "SUN" | null
    var armedDay by remember { mutableStateOf<String?>(null) }  // currently armed target
    var armedDate by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Checklist items. The funnel item only applies when arming for Saturday.
    // checkedState keyed by item index.
    val checked = remember { mutableStateMapOf<Int, Boolean>() }

    suspend fun refresh() {
        try {
            val resp = fetchFromApi("/api/protocol")
            val obj = Gson().fromJson(resp, Map::class.java)
            armable = obj["armable"] as? String
            armedDay = obj["armedDay"] as? String
            armedDate = obj["armedDate"] as? String
            loading = false
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Build the applicable checklist for the day we'd arm for.
    // Funnel item (index 0) is Saturday-only.
    val items = remember(armable) {
        buildList {
            if (armable == "SAT") add("Prepare funnel container with rest day pre-workout & supplements")
            add("Prepare hydration & soda (if needed)")
            add("Prepare green for ride home")
        }
    }
    val allChecked = items.indices.all { checked[it] == true }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a))
                .systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Going Out", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text = "Back",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier.clickable { onBack() }
                )
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFF59E0B))
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $error", color = Color.Red)
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Armed banner
                    if (armedDay != null) {
                        val dayLabel = if (armedDay == "SAT") "Saturday" else "Sunday"
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color(0x26F59E0B), shape = RoundedCornerShape(10.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("✓ Going-Out Protocol armed for $dayLabel",
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                            Text("Tomorrow's routine will be replaced with the recovery routine.",
                                fontSize = 13.sp, color = Color(0xFFB0B0B0))
                            Text(
                                text = if (working) "Working..." else "Cancel protocol",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFCA5A5),
                                modifier = Modifier.clickable(enabled = !working) {
                                    working = true
                                    scope.launch {
                                        try { postToApi("/api/protocol/disarm"); refresh() }
                                        catch (e: Exception) { error = e.message }
                                        finally { working = false }
                                    }
                                }
                            )
                        }
                    } else if (armable == null) {
                        // Not Fri or Sat night — can't arm
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(10.dp))
                                .padding(16.dp)
                        ) {
                            Text("Protocol can only be armed on Friday or Saturday night.",
                                fontSize = 14.sp, color = Color(0xFFB0B0B0))
                        }
                    } else {
                        // Armable: show checklist + arm button
                        val dayLabel = if (armable == "SAT") "Saturday" else "Sunday"
                        Text("Prep checklist for $dayLabel",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Complete all items to arm the protocol.",
                            fontSize = 13.sp, color = Color(0xFFB0B0B0))

                        items.forEachIndexed { i, label ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp))
                                    .clickable { checked[i] = !(checked[i] ?: false) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (checked[i] == true) "☑" else "☐",
                                    fontSize = 20.sp,
                                    color = if (checked[i] == true) Color(0xFF4CAF50) else Color(0xFF888899)
                                )
                                Text(label, fontSize = 14.sp, color = Color.White,
                                    modifier = Modifier.weight(1f))
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        val armEnabled = allChecked && !working
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(
                                    if (armEnabled) Color(0xFFF59E0B) else Color(0xFF2a2a2a),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable(enabled = armEnabled) {
                                    working = true
                                    scope.launch {
                                        try { postToApi("/api/protocol/arm"); refresh() }
                                        catch (e: Exception) { error = e.message }
                                        finally { working = false }
                                    }
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (working) "Arming..." else "Arm Going-Out Protocol",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = if (armEnabled) Color(0xFF1a1a1a) else Color(0xFF666677)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NofapNotepadScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var noteText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Saved") }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var lastSaved by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val response = fetchFromApi("/api/nofap-notepad")
            val obj = Gson().fromJson(response, Map::class.java)
            noteText = obj["content"] as? String ?: ""
            lastSaved = noteText
            loaded = true
            loading = false
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }

    LaunchedEffect(noteText) {
        if (!loaded) return@LaunchedEffect
        if (noteText == lastSaved) return@LaunchedEffect
        status = "Editing..."
        kotlinx.coroutines.delay(1500)
        status = "Saving..."
        try {
            kotlinx.coroutines.withContext(Dispatchers.IO) { saveNofapNotepad(noteText) }
            lastSaved = noteText
            status = "Saved"
        } catch (e: Exception) {
            status = "Save failed"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (loaded && noteText != lastSaved) {
                val toSave = noteText
                scope.launch(Dispatchers.IO) { try { saveNofapNotepad(toSave) } catch (e: Exception) {} }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a))
                .systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Notes", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = status, fontSize = 12.sp, color = Color(0xFF4CAF50))
                }
                Text(
                    text = "Back",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier.clickable { onBack() }
                )
            }
        }

        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD700))
            }
            error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $error", color = Color.Red)
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        placeholder = { Text("Type your notes here...", color = Color(0xFF555577)) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color(0xFF2a2a3a),
                            cursorColor = Color(0xFFFFD700)
                        )
                    )
                }
            }
        }
    }
}

fun saveNofapNotepad(content: String) {
    val url = URL("https://mysololeveling.us/api/nofap-notepad")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "PATCH"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    try {
        val payload = Gson().toJson(mapOf("content" to content))
        conn.outputStream.use { it.write(payload.toByteArray()) }
        conn.responseCode
    } finally { conn.disconnect() }
}

fun sortQuestsByTime(quests: List<Quest>): List<Quest> {
    return quests.sortedBy { quest ->
        val timeRegex = Regex("@ (\\d{1,2}):(\\d{2}) ([AP]M)|@ (\\d{1,2}) ([AP]M)")
        val match = timeRegex.find(quest.title)
        if (match != null) {
            val hour = (match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[4]).toInt()
            val minute = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            val period = (match.groupValues[3].takeIf { it.isNotEmpty() } ?: match.groupValues[5])
            val adjustedHour = if (period == "PM" && hour != 12) hour + 12 else if (period == "AM" && hour == 12) 0 else hour
            adjustedHour * 60 + minute
        } else {
            Int.MAX_VALUE
        }
    }
}

@Composable
fun SectionHeader(label: String, subtitle: String? = null, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 2.sp)
        if (subtitle != null) {
            Text(text = subtitle, fontSize = 11.sp, color = Color(0xFFB0B0B0))
        }
    }
}

@Composable
fun DaySubheader(label: String, color: Color) {
    Text(
        text = label.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 0.dp, start = 8.dp)
    )
}

@Composable
fun ThisWeekHeader(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "THIS WEEK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), letterSpacing = 2.sp)
        Text(text = if (expanded) "▲" else "▼", fontSize = 14.sp, color = Color(0xFFFFD700))
    }
}

@Composable
fun QuestItem(
    quest: Quest,
    onCompleteToggle: () -> Unit,
    questId: Int,
    isCompleted: Boolean,
    isWeekly: Boolean = false,
    showDaySuffix: Boolean = true,
    isOverdue: Boolean = false
) {
    var checked by remember(isCompleted) { mutableStateOf(isCompleted) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isCompleted) {
        checked = isCompleted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1a1a1a))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = checked,
            enabled = !isLoading,
            onCheckedChange = { newValue ->
                if (!isLoading) {
                    isLoading = true
                    checked = newValue
                    appNetworkScope.launch {
                        try {
                            val endpoint = if (newValue) "/complete" else "/uncomplete"
                            val apiPath = if (isWeekly) "/api/weekly-quests/$questId$endpoint" else "/api/quests/$questId$endpoint"

                            android.util.Log.d("QUEST_TOGGLE", "Calling: $apiPath")
                            val response = postToApi(apiPath)
                            android.util.Log.d("QUEST_TOGGLE", "Response: $response")

                            if (response.contains("success")) {
                                android.util.Log.d("QUEST_TOGGLE", "Success! Quest $questId toggled to $newValue")
                                onCompleteToggle()
                            } else {
                                android.util.Log.e("QUEST_TOGGLE", "API error: $response")
                                checked = isCompleted
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("QUEST_TOGGLE", "Exception: ${e.message}", e)
                            checked = isCompleted
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            val displayTitle = when {
                isWeekly && quest.weekday >= 0 && showDaySuffix -> "${quest.title} - ${dayNames[quest.weekday]}"
                isWeekly -> quest.title
                else -> quest.title.replaceFirst(Regex(" - (\\d+:\\d+|\\d{1,2}:\\d{2} [AP]M)"), " @ $1").replace("daily ", "")
            }
            val titleColor = when {
                checked -> Color.Gray
                isOverdue -> Color(0xFFFCA5A5)
                else -> Color.White
            }
            Text(
                text = displayTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isWeekly) {
                    if (quest.monthly) {
                        Text(
                            text = "Monthly",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2DD4BF),
                            modifier = Modifier
                                .background(Color(0x262DD4BF), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = "Required",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFa78bfa),
                            modifier = Modifier
                                .background(Color(0x26a78bfa), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (quest.optional) {
                    Text(
                        text = "Optional",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB0B0B0),
                        modifier = Modifier
                            .background(Color(0xFF2a2a2a), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (quest.important) {
                    Text(
                        text = "Don't Skip",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier
                            .background(Color(0x26F59E0B), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

suspend fun loadAllQuests(onQuestsLoaded: (List<Quest>, List<Quest>, Int, Int, Boolean, String?) -> Unit, onError: (String) -> Unit) {
    try {
        val response = fetchFromApi("/api/quests")
        val questData = Gson().fromJson(response, Map::class.java)

        val dailyQuestsList = (questData["dailyQuests"] as? List<*>)?.mapNotNull {
            if (it is Map<*, *>) {
                Quest(
                    id = (it["id"] as? Number)?.toInt() ?: 0,
                    title = (it["title"] as? String) ?: "",
                    type = (it["type"] as? String) ?: "",
                    category = (it["category"] as? String) ?: "",
                    xpReward = (it["xp_reward"] as? Number)?.toInt() ?: 0,
                    goldReward = (it["gold_reward"] as? Number)?.toInt() ?: 0,
                    completed = ((it["completed"] as? Number)?.toInt() ?: 0) == 1,
                    streak = (it["streak"] as? Number)?.toInt() ?: 0,
                    optional = ((it["optional"] as? Number)?.toInt() ?: 0) == 1,
                    important = ((it["important"] as? Number)?.toInt() ?: 0) == 1
                )
            } else null
        } ?: emptyList()

        val weeklyQuestsList = (questData["weeklyQuests"] as? List<*>)?.mapNotNull {
            if (it is Map<*, *>) {
                Quest(
                    id = (it["id"] as? Number)?.toInt() ?: 0,
                    title = (it["title"] as? String) ?: "",
                    type = (it["type"] as? String) ?: "weekly",
                    category = (it["category"] as? String) ?: "",
                    xpReward = (it["xp_reward"] as? Number)?.toInt() ?: 0,
                    goldReward = (it["gold_reward"] as? Number)?.toInt() ?: 0,
                    completed = ((it["completed"] as? Number)?.toInt() ?: 0) == 1,
                    streak = (it["streak"] as? Number)?.toInt() ?: 0,
                    optional = ((it["optional"] as? Number)?.toInt() ?: 0) == 1
                )
            } else null
        } ?: emptyList()

        val allWeeklyResponse = try {
            fetchFromApi("/api/weekly-quests/all")
        } catch (e: Exception) {
            "[]"
        }

        val allWeeklyQuestsList = try {
            val allWeeklyData = Gson().fromJson(allWeeklyResponse, List::class.java)
            allWeeklyData.mapNotNull {
                if (it is Map<*, *>) {
                    Quest(
                        id = (it["id"] as? Number)?.toInt() ?: 0,
                        title = (it["title"] as? String) ?: "",
                        type = "weekly",
                        category = (it["category"] as? String) ?: "",
                        xpReward = (it["xp_reward"] as? Number)?.toInt() ?: 0,
                        goldReward = 0,
                        completed = ((it["completed"] as? Number)?.toInt() ?: 0) == 1,
                        streak = 0,
                        weekday = (it["weekday"] as? Number)?.toInt() ?: -1,
                        optional = ((it["optional"] as? Number)?.toInt() ?: 0) == 1,
                        isOverdue = ((it["isOverdue"] as? Number)?.toInt() ?: 0) == 1,
                        monthly = ((it["monthly"] as? Number)?.toInt() ?: 0) == 1
                    )
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }

        val dailiesCompleted = (questData["dailiesCompleted"] as? Number)?.toInt() ?: 0
        val weekliesCompleted = (questData["weekliesCompleted"] as? Number)?.toInt() ?: 0
        val hasWeekly = (questData["hasWeeklyQuests"] as? Boolean) ?: false

        // Protocol banner: prioritize "active today" (recovery routine live) over "armed for later".
        val proto = questData["protocol"] as? Map<*, *>
        val activeToday = proto?.get("activeToday") as? String
        val armedDay = proto?.get("armedDay") as? String
        val noRouteActive = (proto?.get("noRouteActive") as? Boolean) ?: false
        fun dayLabel(d: String?) = if (d == "SAT") "Saturday" else if (d == "SUN") "Sunday" else null
        val protocolBannerText: String? = when {
            noRouteActive -> "No Route Protocol Active"
            activeToday != null -> "Recovery Routine Active"
            armedDay != null -> "Going-Out Protocol armed for ${dayLabel(armedDay)}"
            else -> null
        }

        android.util.Log.d("API_DEBUG", "Daily quests: ${dailyQuestsList.size}, Weekly quests due today: ${weeklyQuestsList.size}, All weekly quests: ${allWeeklyQuestsList.size}")
        android.util.Log.d("QUEST_COMPLETED", "All weekly quests: ${allWeeklyQuestsList.map { "${it.title}: ${it.completed}" }.joinToString(", ")}")

        onQuestsLoaded(dailyQuestsList, allWeeklyQuestsList, dailiesCompleted, weekliesCompleted, hasWeekly, protocolBannerText)
    } catch (e: Exception) {
        android.util.Log.e("API_ERROR", "Error loading quests: ${e.message}", e)
        onError(e.message ?: "Unknown error")
    }
}

@Composable
fun MainTabScreen() {
    var selectedTab by remember { mutableStateOf(TabType.QUESTS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a1a))
            .systemBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                TabType.QUESTS -> PlayerScreen()
                TabType.SUPPLEMENTS -> SupplementsScreen()
                TabType.DIET -> DietScreen()
                TabType.GYM -> GymScreen()
                TabType.FOOD -> SuppliesScreen(baseUrl = "https://mysololeveling.us")
                TabType.FINANCE -> FinanceScreen(baseUrl = "https://mysololeveling.us")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1a1a1a))
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TabButton(
                label = "Tasks",
                isSelected = selectedTab == TabType.QUESTS,
                onClick = { selectedTab = TabType.QUESTS },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                label = "Supps",
                isSelected = selectedTab == TabType.SUPPLEMENTS,
                onClick = { selectedTab = TabType.SUPPLEMENTS },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                label = "Diet",
                isSelected = selectedTab == TabType.DIET,
                onClick = { selectedTab = TabType.DIET },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                label = "Gym",
                isSelected = selectedTab == TabType.GYM,
                onClick = { selectedTab = TabType.GYM },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                label = "Supplies",
                isSelected = selectedTab == TabType.FOOD,
                onClick = { selectedTab = TabType.FOOD },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                label = "Finance",
                isSelected = selectedTab == TabType.FINANCE,
                onClick = { selectedTab = TabType.FINANCE },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF2a2a2a) else Color.Transparent
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White
        )
    }
}

enum class TabType {
    QUESTS, SUPPLEMENTS, DIET, GYM, FOOD, FINANCE
}

data class Meal(
    val name: String,
    val mealType: String,
    val macros: Macros,
    val ingredients: List<String>,
    val steps: List<MealStep>
)

data class Macros(
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val netCarbs: String
)

data class MealStep(
    val title: String,
    val detail: String
)

val BREAKFAST_MEALS = listOf(
    Meal(
        name = "Protein Shake",
        mealType = "Breakfast",
        macros = Macros(calories = 410, protein = 60, fat = 13, netCarbs = "13"),
        ingredients = listOf(
            "2 scoops body fortress whey protein powder",
            "8 to 12 oz cold water",
            "7g pecans (about 1 tbsp chopped, ~10 halves)",
            "2 to 3 ice cubes (optional)"
        ),
        steps = listOf(
            MealStep(
                "1. Measure the Base",
                "Pour 8 to 12 oz of cold water into the Ninja blender jar.\n\n(Thicker shake: stick to 8 oz. More fluid: go up to 12 oz. For an ice-cold texture, add 2 to 3 ice cubes.)"
            ),
            MealStep(
                "2. Add Powder & Pecans",
                "Add 2 level scoops of Body Fortress whey protein and 7g pecans."
            ),
            MealStep(
                "3. Blend",
                "Secure the lid and blend 20–30 seconds until fully smooth and the pecans are broken down and incorporated."
            ),
            MealStep(
                "4. Finish",
                "Let it sit 30 seconds for the foam to settle, then pour and drink.\n\nNote: Pecans add ~50 cal and 5g fat over the original — blend rather than shake so they fully break down."
            )
        )
    )
)

val LUNCH_MEALS = listOf(
    Meal(
        name = "Loaded Beef & Bacon Melt",
        mealType = "Lunch",
        macros = Macros(calories = 420, protein = 36, fat = 28, netCarbs = "3"),
        ingredients = listOf(
            "3 slices Member's Mark bacon",
            "2.5 oz 96/4 extra-lean ground beef",
            "40g diced white onion",
            "1 tsp minced garlic",
            "1 oz (approx. 4 tbsp) shredded cheddar or Monterey jack",
            "sea salt",
            "black pepper"
        ),
        steps = listOf(
            MealStep(
                "1. Fry the Bacon (Nonstick Griddle)",
                "Lay the bacon slices flat on the griddle (cut in half first if needed to fit) and turn heat to medium. Fry 8–10 min, flipping occasionally, until perfectly crispy — keep heat at medium so the fat renders slowly and doesn't pool to the edges. Transfer to a paper towel, let cool, then chop into bite-sized pieces."
            ),
            MealStep(
                "2. Cook the Beef, Onions & Garlic (Cast Iron)",
                "Heat the cast iron skillet over medium with a touch of oil (96/4 beef renders almost no fat, so this keeps it from sticking). Add 40g diced onion and sauté 1–2 min until translucent. Add 1 tsp minced garlic and stir 30 sec until fragrant. Add 2.5 oz ground beef, season generously with salt and pepper, and break apart with a spatula. Cook until fully browned and sizzling. Drain off excess liquid, then return the beef mixture to the pan."
            ),
            MealStep(
                "3. Melt the Cheese",
                "Turn heat to low. Spread the beef mixture evenly across the bottom of the skillet. Sprinkle 1 oz shredded cheese flat over the top, then scatter the chopped bacon into the cheese."
            ),
            MealStep(
                "4. Plate & Serve",
                "Turn the burner OFF (cast iron holds enough residual heat to melt the cheese without scorching). Cover with a lid for 60 seconds to let the cheese melt and coat the beef. Slide onto a plate and enjoy fresh and hot.\n\nNote: Bacon is now the biggest fat/calorie source — trim it if you want it leaner. For 50g+ protein, bump beef back toward 4 oz."
            )
        )
    )
)

val DINNER_MEALS = listOf(
    Meal(
        name = "Garlic-Herb Braised Pork & Rice",
        mealType = "Dinner",
        macros = Macros(calories = 640, protein = 47, fat = 24, netCarbs = "32"),
        ingredients = listOf(
            "8 oz pork stew meat (boneless)",
            "1 tbsp butter (split)",
            "3/4 cup cooked jasmine rice",
            "1 tsp minced garlic",
            "1/2 tsp dried thyme",
            "1/3 cup water + 1/2 beef bouillon cube (for braising)",
            "sea salt",
            "freshly cracked black pepper"
        ),
        steps = listOf(
            MealStep(
                "1. Season the Cubes",
                "Pat pork stew meat dry with paper towels. Season on all sides with salt and pepper (go light — bouillon adds salt later)."
            ),
            MealStep(
                "2. Hard Sear",
                "Get skillet smoking hot over high heat. Add 1/2 tbsp butter. Sear cubes in a single layer (don't crowd — work in batches), 60–90 sec per side until deeply browned."
            ),
            MealStep(
                "3. Braise Until Tender",
                "Lower heat to medium-low. Add 1/3 cup water with 1/2 beef bouillon cube dissolved in. Cover and simmer 20–30 min until fork-tender, adding a splash more liquid if it dries out. Cook down to a few spoonfuls of concentrated jus."
            ),
            MealStep(
                "4. Bloom Garlic & Thyme",
                "Push pork aside. Add remaining 1/2 tbsp butter, 1 tsp garlic, 1/2 tsp thyme. Stir 30 sec until fragrant and golden."
            ),
            MealStep(
                "5. Toss & Serve",
                "Add rice to the garlic-thyme butter, stir 30 sec to coat. Turn off heat. Fold pork and all pan juices back in, toss, and taste before adding any salt — you may not need any.\n\nNote: Trim visible fat and drop to 6 oz to lower fat further. Braise adds ~20–25 min — good for batch prep. Sodium runs higher with bouillon."
            )
        )
    )
)

val SNACK_MEALS = listOf(
    Meal(
        name = "Pecan Halves",
        mealType = "Snack",
        macros = Macros(calories = 210, protein = 3, fat = 21, netCarbs = "1"),
        ingredients = listOf("Member's Mark Natural Pecan Halves — 30g"),
        steps = emptyList()
    )
)

val DESSERT_MEALS = listOf(
    Meal(
        name = "Pecan Mousse",
        mealType = "Dessert",
        macros = Macros(calories = 295, protein = 4, fat = 29, netCarbs = "3.5"),
        ingredients = listOf(
            "2 oz heavy whipping cream",
            "1 tbsp cocoa powder",
            "1 tbsp sweetener",
            "7g pecan halves"
        ),
        steps = listOf(
            MealStep(
                "1. Chill and Prep Your Tools: 1 minute",
                "For the fastest results, use a small metal or glass bowl. If you have an extra 60 seconds, drop the bowl and your whisk (or hand mixer beaters) into the freezer to get them cold. Cold tools make heavy cream whip up twice as fast."
            ),
            MealStep(
                "2. Combine the Base: 2 minutes",
                "Pour the 2 oz of heavy whipping cream straight into the chilled bowl. Sift or dump in the 1 tbsp of cocoa powder and 1 tbsp of sweetener."
            ),
            MealStep(
                "3. Whip to Thick Peaks: 2 minutes",
                "Using a hand whisk, beat the mixture vigorously.\n\nMove your wrist in a rapid, circular motion to incorporate air. Watch it closely—after about 90 to 120 seconds, the liquid cream will suddenly transform into a thick, velvety, scoopable mousse that holds its shape. Stop beating immediately once it reaches this point so it doesn't turn into chocolate butter."
            ),
            MealStep(
                "4. The Pecan Crunch Finish: 1 minute",
                "Spoon the mousse into a small bowl or glass. Take your 7g of pecan halves, give them a quick chop with a kitchen knife, and scatter them generously over the top."
            )
        )
    )
)

@Composable
fun DietScreen() {
    var selectedMeal by remember { mutableStateOf<Meal?>(null) }

    BackHandler(enabled = selectedMeal != null) {
        selectedMeal = null
    }

    val meal = selectedMeal
    if (meal == null) {
        DietListScreen(onMealSelected = { selectedMeal = it })
    } else {
        MealDetailScreen(meal = meal, onBack = { selectedMeal = null })
    }
}

@Composable
fun MacroBox(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp)).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, fontSize = 10.sp, color = Color(0xFFB0B0B0), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun DietListScreen(onMealSelected: (Meal) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0a0a0a))
            .padding(16.dp)
    ) {
        Text(
            text = "Diet",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Daily macro summary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = "Daily Targets",
                    fontSize = 11.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroBox("Calories", "1,975", Modifier.weight(1f))
                    MacroBox("Protein", "150g", Modifier.weight(1f))
                    MacroBox("Fat", "115g", Modifier.weight(1f))
                    MacroBox("Net Carbs", "52.5g", Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Breakfast",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(BREAKFAST_MEALS, key = { it.name }) { meal ->
                Button(
                    onClick = { onMealSelected(meal) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1a1a1a)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = meal.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
            item {
                Text(
                    text = "Lunch",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(LUNCH_MEALS, key = { it.name }) { meal ->
                Button(
                    onClick = { onMealSelected(meal) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1a1a1a)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = meal.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
            item {
                Text(
                    text = "Dinner",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(DINNER_MEALS, key = { it.name }) { meal ->
                Button(
                    onClick = { onMealSelected(meal) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1a1a1a)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = meal.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
            item {
                Text(
                    text = "Snack",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(SNACK_MEALS, key = { it.name }) { meal ->
                Button(
                    onClick = { onMealSelected(meal) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1a1a1a)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = meal.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            item {
                Text(
                    text = "Dessert",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(DESSERT_MEALS, key = { it.name }) { meal ->
                Button(
                    onClick = { onMealSelected(meal) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1a1a1a)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = meal.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun MealDetailScreen(meal: Meal, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = meal.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = meal.mealType, fontSize = 13.sp, color = Color(0xFFFFD700))
            }
            Text(
                text = "Back",
                color = Color(0xFFFFD700),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onBack() }
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroBox("Calories", "${meal.macros.calories}", Modifier.weight(1f))
                    MacroBox("Protein", "${meal.macros.protein}g", Modifier.weight(1f))
                    MacroBox("Fat", "${meal.macros.fat}g", Modifier.weight(1f))
                    MacroBox("Net Carbs", "${meal.macros.netCarbs}g", Modifier.weight(1f))
                }
            }
            if (meal.ingredients.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp)).padding(16.dp)) {
                        Column {
                            Text(text = "Ingredients", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), modifier = Modifier.padding(bottom = 8.dp))
                            meal.ingredients.forEach { ingredient ->
                                Text(text = "• $ingredient", fontSize = 14.sp, color = Color.White, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
            if (meal.steps.isNotEmpty()) {
                item {
                    Text(text = "Instructions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                }
                items(meal.steps, key = { it.title }) { step ->
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp)).padding(16.dp)) {
                        Column {
                            Text(text = step.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(bottom = 6.dp))
                            Text(text = step.detail, fontSize = 14.sp, color = Color(0xFFB0B0B0), lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupplementsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a1a))
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SupplementSection(
                    title = "Gym day",
                    items = listOf(
                        SupplementGroup(
                            category = "Capsule",
                            supplements = listOf(
                                "Caffeine" to "400 mg",
                                "L-Tyrosine" to "1000 mg",
                                "Alpha-GPC" to "600 mg",
                                "One A Day Multivitamin for Men" to "1 tablet",
                                "Allergy Relief" to "1 tablet"
                            )
                        ),
                        SupplementGroup(
                            category = "Powder",
                            supplements = listOf(
                                "L-Citrulline" to "6g",
                                "Beta-Alanine" to "3.2g",
                                "Betaine Anhydrous" to "3g",
                                "Creatine" to "5g",
                                "L-Theanine" to "400 mg"
                            )
                        )
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                SupplementSection(
                    title = "Rest day",
                    items = listOf(
                        SupplementGroup(
                            category = "Capsule",
                            supplements = listOf(
                                "One A Day Multivitamin for Men" to "1 tablet",
                                "Allergy Relief" to "1 tablet"
                            )
                        ),
                        SupplementGroup(
                            category = "Powder",
                            supplements = listOf(
                                "Creatine" to "5g",
                                "Beta-Alanine" to "3g",
                                "Betaine Anhydrous" to "1.5g",
                                "L-Theanine" to "200 mg"
                            )
                        )
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                SupplementSection(
                    title = "Evening",
                    items = listOf(
                        SupplementGroup(
                            category = "Capsule",
                            supplements = listOf(
                                "Magnesium Glycinate" to "210 mg",
                                "Ashwagandha" to "600 mg",
                                "Chamomile" to "750 mg",
                                "Valerian Root" to "500 mg"
                            )
                        ),
                        SupplementGroup(
                            category = "Powder",
                            supplements = listOf(
                                "L-Theanine" to "200 mg"
                            )
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun SupplementSection(
    title: String,
    items: List<SupplementGroup>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        items.forEach { group ->
            SupplementGroupCard(group)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SupplementGroupCard(group: SupplementGroup) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2a2a2a), shape = RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = group.category,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF999999),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            group.supplements.forEach { (name, dose) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = dose,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF999999)
                    )
                }
            }
        }
    }
}


// ─── Gym Tab ──────────────────────────────────────────────────────────────────

data class GymExercise(
    val exerciseTemplateId: String,
    val title: String,
    val sessionCount: Int,
    val bestWeightLbs: Int,
    val bestReps: Int,
    val estimated1RMLbs: Int,
    val isPlateaued: Boolean,
    val sessionsAtCurrentWeight: Int,
    val lastPrDate: String,
    val recentGainLbs: Int,
    val strengthLevel: String?,
    val strengthPercentile: Int?
)

data class ExerciseSession(
    val date: String,
    val weightLbs: Int,
    val reps: Int,
    val estimated1RMLbs: Int
)

data class GymRoutine(
    val routineId: String,
    val title: String,
    val exercises: List<GymExercise>
)

@Composable
fun GymScreen() {
    var selectedExercise by remember { mutableStateOf<GymExercise?>(null) }
    var showStandards by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedExercise != null || showStandards) {
        if (selectedExercise != null) selectedExercise = null
        else showStandards = false
    }

    when {
        selectedExercise != null -> GymDetailScreen(exercise = selectedExercise!!, onBack = { selectedExercise = null })
        showStandards -> GymStandardsScreen(onBack = { showStandards = false })
        else -> GymListScreen(onExerciseSelected = { selectedExercise = it }, onViewStandards = { showStandards = true })
    }
}

@Composable
fun GymListScreen(onExerciseSelected: (GymExercise) -> Unit, onViewStandards: () -> Unit) {
    var routines by remember { mutableStateOf<List<GymRoutine>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val response = fetchFromApi("/api/gym/routines")
            val data = Gson().fromJson(response, List::class.java)
            routines = data.mapNotNull {
                if (it is Map<*, *>) {
                    val exList = (it["exercises"] as? List<*>)?.mapNotNull { ex ->
                        if (ex is Map<*, *>) GymExercise(
                            exerciseTemplateId = (ex["exercise_template_id"] as? String) ?: "",
                            title = (ex["title"] as? String) ?: "",
                            sessionCount = (ex["session_count"] as? Number)?.toInt() ?: 0,
                            bestWeightLbs = (ex["best_weight_lbs"] as? Number)?.toInt() ?: 0,
                            bestReps = (ex["best_reps"] as? Number)?.toInt() ?: 0,
                            estimated1RMLbs = (ex["estimated_1rm_lbs"] as? Number)?.toInt() ?: 0,
                            isPlateaued = (ex["is_plateaued"] as? Boolean) ?: false,
                            sessionsAtCurrentWeight = (ex["sessions_at_current_weight"] as? Number)?.toInt() ?: 0,
                            lastPrDate = (ex["last_pr_date"] as? String) ?: "",
                            recentGainLbs = (ex["recent_gain_lbs"] as? Number)?.toInt() ?: 0,
                            strengthLevel = ex["strength_level"] as? String,
                            strengthPercentile = (ex["strength_percentile"] as? Number)?.toInt()
                        ) else null
                    } ?: emptyList()
                    GymRoutine(
                        routineId = (it["routine_id"] as? String) ?: "",
                        title = (it["title"] as? String) ?: "",
                        exercises = exList
                    )
                } else null
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    val totalPlateaued = routines.sumOf { r -> r.exercises.count { it.isPlateaued } }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).systemBarsPadding().padding(16.dp)
        ) {
            Text("Gym", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (routines.isNotEmpty()) {
                Text(
                    text = "${routines.size} routines" + if (totalPlateaued > 0) " · $totalPlateaued plateaued" else "",
                    fontSize = 13.sp, color = Color(0xFFFFD700)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "📊 View Strength Standards",
                fontSize = 12.sp,
                color = Color(0xFF7B8CDE),
                modifier = Modifier.clickable { onViewStandards() }
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD700))
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $error", color = Color.Red)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(routines, key = { it.routineId }) { routine ->
                    GymRoutineSection(routine = routine, onExerciseSelected = onExerciseSelected)
                }
            }
        }
    }
}

@Composable
fun GymExerciseCard(exercise: GymExercise, onClick: () -> Unit) {
    val borderColor = when {
        exercise.isPlateaued -> Color(0xFFf87171)
        exercise.recentGainLbs > 0 -> Color(0xFF4FB3D9)
        else -> Color(0xFFFFD700)
    }
    val levelColor = gymLevelColor(exercise.strengthLevel)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111728))
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(borderColor))
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = exercise.title,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (exercise.isPlateaued) Color(0xFFfca5a5) else Color(0xFFcdd6e2),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (exercise.isPlateaued) {
                    Text(
                        text = "Plateau", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFf87171),
                        modifier = Modifier
                            .background(Color(0x26f87171), RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                } else if (exercise.recentGainLbs > 0) {
                    Text(
                        text = "+${exercise.recentGainLbs} lbs", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF4FB3D9),
                        modifier = Modifier
                            .background(Color(0x264FB3D9), RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            val subtitle = if (exercise.isPlateaued)
                "${exercise.bestWeightLbs} lbs × ${exercise.bestReps} · stuck ${exercise.sessionsAtCurrentWeight} sessions"
            else
                "${exercise.bestWeightLbs} lbs × ${exercise.bestReps} · ${exercise.sessionCount} sessions"
            Text(text = subtitle, fontSize = 12.sp, color = Color(0xFF6b7689), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Est. 1RM:", fontSize = 11.sp, color = Color(0xFF6b7689))
                Text("${exercise.estimated1RMLbs} lbs", fontSize = 11.sp, color = Color(0xFFFFD700), fontWeight = FontWeight.SemiBold)
                if (exercise.strengthLevel != null && exercise.strengthPercentile != null) {
                    val pct = exercise.strengthPercentile / 100f
                    Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF1f2a3f))) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct).background(levelColor, RoundedCornerShape(2.dp)))
                    }
                    Text(exercise.strengthLevel, fontSize = 10.sp, color = levelColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun GymRoutineSection(routine: GymRoutine, onExerciseSelected: (GymExercise) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val plateauCount = routine.exercises.count { it.isPlateaued }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111728))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(routine.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFcdd6e2))
                Text(
                    text = "${routine.exercises.size} exercises" + if (plateauCount > 0) " · $plateauCount plateaued" else "",
                    fontSize = 11.sp, color = Color(0xFF6b7689), modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(if (expanded) "▲" else "▼", fontSize = 13.sp, color = Color(0xFFFFD700))
        }

        if (expanded) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1f2937)))
            routine.exercises.forEachIndexed { index, exercise ->
                GymExerciseRow(exercise = exercise, onClick = { onExerciseSelected(exercise) })
                if (index < routine.exercises.size - 1) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF0e1422)))
                }
            }
        }
    }
}

@Composable
fun GymExerciseRow(exercise: GymExercise, onClick: () -> Unit) {
    val borderColor = when {
        exercise.isPlateaued -> Color(0xFFf87171)
        exercise.recentGainLbs > 0 -> Color(0xFF4FB3D9)
        else -> Color(0xFFFFD700)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(borderColor))
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exercise.title,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (exercise.isPlateaued) Color(0xFFfca5a5) else Color(0xFFcdd6e2),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (exercise.isPlateaued) {
                    Text(
                        "Plateau", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFf87171),
                        modifier = Modifier.background(Color(0x26f87171), RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                } else if (exercise.recentGainLbs > 0) {
                    Text(
                        "+${exercise.recentGainLbs} lbs", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4FB3D9),
                        modifier = Modifier.background(Color(0x264FB3D9), RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            if (exercise.sessionCount > 0) {
                Text(
                    "${exercise.bestWeightLbs} lbs × ${exercise.bestReps} · Est. 1RM: ${exercise.estimated1RMLbs} lbs",
                    fontSize = 11.sp, color = Color(0xFF6b7689), modifier = Modifier.padding(top = 3.dp)
                )
            } else {
                Text("No data yet", fontSize = 11.sp, color = Color(0xFF6b7689), modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
fun GymStandardsScreen(onBack: () -> Unit) {
    var exercises by remember { mutableStateOf<List<GymExercise>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val standards = mapOf(
        "Bench Press (Barbell)" to listOf(96, 143, 191, 239, 287),
        "Squat (Barbell)" to listOf(143, 239, 287, 382, 478),
        "Deadlift (Barbell)" to listOf(191, 287, 334, 430, 525),
        "Overhead Press (Barbell)" to listOf(67, 96, 124, 162, 210),
        "Bent Over Row (Barbell)" to listOf(86, 124, 172, 229, 300),
        "Romanian Deadlift" to listOf(120, 182, 258, 354, 460),
        "Incline Bench Press (Barbell)" to listOf(84, 120, 167, 229, 300),
        "Supine Press" to listOf(96, 143, 191, 239, 287),
        "Shoulder Press (Dumbbell)" to listOf(44, 66, 88, 110, 132),
        "Incline Bench Press (Dumbbell)" to listOf(44, 66, 88, 110, 132),
        "Skullcrusher (Barbell)" to listOf(44, 66, 88, 110, 132),
        "Triceps Pushdown" to listOf(33, 55, 77, 99, 121),
        "Triceps Overhead Extension" to listOf(44, 66, 88, 110, 132),
        "Lean-Back Lat Pulldown" to listOf(77, 121, 165, 209, 253),
        "Lat Pulldown (Band)" to listOf(77, 121, 165, 209, 253),
        "Chest Supported Incline Row (Dumbbell)" to listOf(55, 88, 121, 154, 187),
        "Bent Over Row (Smith Machine)" to listOf(86, 124, 172, 229, 300),
        "Hammer Curl (Cable)" to listOf(33, 55, 77, 99, 121),
        "Single Arm Preacher Curl" to listOf(22, 44, 66, 88, 110),
        "Bayesian Cable Curl" to listOf(22, 44, 66, 88, 110),
        "Hack Squat (Machine)" to listOf(121, 198, 275, 352, 440),
        "Split Squat (Smith Machine)" to listOf(55, 99, 143, 187, 231),
        "Lunge (Dumbbell)" to listOf(44, 77, 110, 143, 176),
        "Lying Leg Curl (Machine)" to listOf(77, 121, 165, 209, 253),
        "Seated Leg Curl (Machine)" to listOf(77, 121, 165, 209, 253),
        "Leg Extension (Machine)" to listOf(99, 154, 209, 264, 319),
        "Back Extension (Weighted Hyperextension)" to listOf(33, 55, 88, 121, 154),
        "Calf Press (Machine)" to listOf(165, 253, 341, 429, 517),
        "Calf Extension (Machine)" to listOf(165, 253, 341, 429, 517),
        "Hip Abduction (Machine)" to listOf(77, 121, 165, 209, 253),
        "Ab Crunch (Machine)" to listOf(55, 99, 143, 187, 231),
        "Low-To-High Cable Crossover" to listOf(22, 33, 44, 66, 88),
        "Single Arm Lateral Raise (Cable)" to listOf(11, 22, 33, 44, 55),
        "Single Arm Rear Delt Flye (Cable)" to listOf(11, 22, 33, 44, 55),
        "Paused Shrug-In (Cable)" to listOf(99, 154, 209, 264, 319),
        "Ab Wheel" to listOf(0, 0, 0, 0, 0),
    )

    val tiers = listOf("Beginner", "Novice", "Intermediate", "Advanced", "Elite")
    val tierColors = listOf(
        Color(0xFF4B5563), Color(0xFFca8a04), Color(0xFF4FB3D9), Color(0xFFa78bfa), Color(0xFFFB923C)
    )

    LaunchedEffect(Unit) {
        try {
            val response = fetchFromApi("/api/gym/summary")
            val data = Gson().fromJson(response, List::class.java)
            exercises = data.mapNotNull {
                if (it is Map<*, *>) GymExercise(
                    exerciseTemplateId = (it["exercise_template_id"] as? String) ?: "",
                    title = (it["title"] as? String) ?: "",
                    sessionCount = (it["session_count"] as? Number)?.toInt() ?: 0,
                    bestWeightLbs = (it["best_weight_lbs"] as? Number)?.toInt() ?: 0,
                    bestReps = (it["best_reps"] as? Number)?.toInt() ?: 0,
                    estimated1RMLbs = (it["estimated_1rm_lbs"] as? Number)?.toInt() ?: 0,
                    isPlateaued = (it["is_plateaued"] as? Boolean) ?: false,
                    sessionsAtCurrentWeight = (it["sessions_at_current_weight"] as? Number)?.toInt() ?: 0,
                    lastPrDate = (it["last_pr_date"] as? String) ?: "",
                    recentGainLbs = (it["recent_gain_lbs"] as? Number)?.toInt() ?: 0,
                    strengthLevel = it["strength_level"] as? String,
                    strengthPercentile = (it["strength_percentile"] as? Number)?.toInt()
                ) else null
            }.filter { standards.containsKey(it.title) && it.estimated1RMLbs > 0 }
            isLoading = false
        } catch (e: Exception) {
            error = e.message
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).systemBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Strength Comparison", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("35yr male · 191 lbs · Est. 1RM", fontSize = 12.sp, color = Color(0xFF6b7689))
            }
            Text("Back", color = Color(0xFFFFD700), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onBack() })
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD700))
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $error", color = Color.Red)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(exercises, key = { it.exerciseTemplateId }) { exercise ->
                        val tiers5 = standards[exercise.title] ?: return@items
                        val oneRM = exercise.estimated1RMLbs
                        val levelIdx = when {
                            oneRM >= tiers5[4] -> 4
                            oneRM >= tiers5[3] -> 3
                            oneRM >= tiers5[2] -> 2
                            oneRM >= tiers5[1] -> 1
                            else -> 0
                        }
                        val levelColor = tierColors[levelIdx]

                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF111728)).padding(12.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(exercise.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFcdd6e2), modifier = Modifier.weight(1f).padding(end = 8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${oneRM} lbs", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFD700))
                                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                        .background(levelColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text(tiers[levelIdx], fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            color = levelColor)
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            // Progress bar across tiers
                            Row(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                tiers5.forEachIndexed { i, _ ->
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()
                                        .background(if (i <= levelIdx) tierColors[i] else tierColors[i].copy(alpha = 0.15f)))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                tiers5.forEachIndexed { i, lbs ->
                                    Text("${lbs}lb", fontSize = 8.sp, color = if (i <= levelIdx) tierColors[i] else Color(0xFF444455),
                                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun GymDetailScreen(exercise: GymExercise, onBack: () -> Unit) {
    var sessions by remember { mutableStateOf<List<ExerciseSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(exercise.exerciseTemplateId) {
        try {
            val response = fetchFromApi("/api/gym/history/${exercise.exerciseTemplateId}")
            val data = Gson().fromJson(response, List::class.java)
            sessions = data.mapNotNull {
                if (it is Map<*, *>) ExerciseSession(
                    date = (it["date"] as? String) ?: "",
                    weightLbs = (it["weight_lbs"] as? Number)?.toInt() ?: 0,
                    reps = (it["reps"] as? Number)?.toInt() ?: 0,
                    estimated1RMLbs = (it["estimated_1rm_lbs"] as? Number)?.toInt() ?: 0
                ) else null
            }
        } catch (e: Exception) {
            android.util.Log.e("GYM_DETAIL", "History error: ${e.message}")
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).systemBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    exercise.title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = if (exercise.isPlateaued) Color(0xFFfca5a5) else Color.White
                )
                if (exercise.isPlateaued) {
                    Text(
                        "Stuck ${exercise.sessionsAtCurrentWeight} sessions at ${exercise.bestWeightLbs} lbs",
                        fontSize = 12.sp, color = Color(0xFFf87171), modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                "Back", color = Color(0xFFFFD700), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp).clickable { onBack() }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GymStatBox("Best set", "${exercise.bestWeightLbs} × ${exercise.bestReps}", Modifier.weight(1f))
                    GymStatBox("Est. 1RM", "${exercise.estimated1RMLbs} lbs", Modifier.weight(1f), Color(0xFFFFD700))
                    GymStatBox("Sessions", "${exercise.sessionCount}", Modifier.weight(1f))
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF111728), RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Column {
                        Text("Max weight over time", fontSize = 11.sp, color = Color(0xFF6b7689), letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (loading) {
                            Box(modifier = Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                            }
                        } else if (sessions.size >= 2) {
                            GymProgressChart(sessions)
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                                Text("Not enough sessions yet", fontSize = 12.sp, color = Color(0xFF6b7689))
                            }
                        }
                    }
                }
            }

            if (exercise.strengthLevel != null && exercise.strengthPercentile != null) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF111728), RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Column {
                            Text(
                                "Vs other lifters at 191 lbs (age 35)", fontSize = 11.sp, color = Color(0xFF6b7689),
                                letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 10.dp)
                            )
                            GymStrengthBar(level = exercise.strengthLevel, percentile = exercise.strengthPercentile)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "You are ${exercise.strengthLevel} — stronger than ~${exercise.strengthPercentile}% of lifters at your bodyweight.",
                                fontSize = 12.sp, color = Color(0xFFcdd6e2), lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF111728), RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Column {
                        Text("Recent sessions", fontSize = 11.sp, color = Color(0xFF6b7689), letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
                        if (!loading && sessions.isEmpty()) {
                            Text("No session data", fontSize = 12.sp, color = Color(0xFF6b7689))
                        }
                        sessions.forEachIndexed { i, session ->
                            val isPr = i < sessions.size - 1 && session.weightLbs > sessions[i + 1].weightLbs
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(session.date, fontSize = 12.sp, color = Color(0xFF6b7689))
                                Text("${session.weightLbs} × ${session.reps}", fontSize = 12.sp, color = Color.White)
                                Text(
                                    if (isPr) "PR" else "—", fontSize = 10.sp,
                                    color = if (isPr) Color(0xFFFFD700) else Color(0xFF6b7689)
                                )
                            }
                            if (i < sessions.size - 1) {
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1f2937)))
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun GymStatBox(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.White) {
    Box(
        modifier = modifier.background(Color(0xFF1a1a1a), RoundedCornerShape(8.dp)).padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = Color(0xFF6b7689))
            Spacer(modifier = Modifier.height(3.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
fun GymProgressChart(sessions: List<ExerciseSession>) {
    val ordered = sessions.reversed()
    Canvas(modifier = Modifier.fillMaxWidth().height(70.dp)) {
        if (ordered.size < 2) return@Canvas
        val maxW = ordered.maxOf { it.weightLbs }.toFloat()
        val minW = ordered.minOf { it.weightLbs }.toFloat()
        val range = if (maxW == minW) 1f else maxW - minW
        val padY = size.height * 0.12f
        val chartH = size.height - padY * 2
        val points = ordered.mapIndexed { i, s ->
            val x = (i.toFloat() / (ordered.size - 1)) * size.width
            val y = padY + chartH - ((s.weightLbs - minW) / range) * chartH
            Offset(x, y)
        }
        for (i in 0 until points.size - 1) {
            drawLine(Color(0xFF4FB3D9), points[i], points[i + 1], strokeWidth = 3f, cap = StrokeCap.Round)
        }
        points.forEachIndexed { i, p ->
            drawCircle(if (i == points.size - 1) Color(0xFFFFD700) else Color(0xFF4FB3D9), 5f, p)
        }
    }
}

@Composable
fun GymStrengthBar(level: String, percentile: Int) {
    val tiers = listOf("Beginner", "Novice", "Intermediate", "Advanced", "Elite")
    val tierColors = listOf(
        Color(0xFF4B5563), Color(0xFFca8a04), Color(0xFF4FB3D9), Color(0xFFa78bfa), Color(0xFFFB923C)
    )
    val activeIdx = tiers.indexOf(level).coerceAtLeast(0)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tiers.forEachIndexed { i, _ ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .background(if (i == activeIdx) tierColors[i] else tierColors[i].copy(alpha = 0.2f))
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            tiers.forEachIndexed { i, tier ->
                Text(
                    text = tier, modifier = Modifier.weight(1f),
                    fontSize = 8.sp,
                    color = if (i == activeIdx) tierColors[i] else Color(0xFF6b7689),
                    textAlign = TextAlign.Center,
                    fontWeight = if (i == activeIdx) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

private fun gymLevelColor(level: String?) = when (level) {
    "Beginner"     -> Color(0xFF4B5563)
    "Novice"       -> Color(0xFFca8a04)
    "Intermediate" -> Color(0xFF4FB3D9)
    "Advanced"     -> Color(0xFFa78bfa)
    "Elite"        -> Color(0xFFFB923C)
    else           -> Color(0xFF6b7689)
}

data class SupplementGroup(
    val category: String,
    val supplements: List<Pair<String, String>>
)