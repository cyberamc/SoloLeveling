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
import androidx.compose.foundation.lazy.itemsIndexed
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

// Application-level scope for fire-and-refresh network calls (quest toggles,
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
    var showSteps by remember { mutableStateOf(false) }
    var showPreSleep by remember { mutableStateOf(false) }
    var showLuna by remember { mutableStateOf(false) }
    var showHydration by remember { mutableStateOf(false) }
    var showUrgeCard by remember { mutableStateOf(false) }
    var showConfidenceDialog by remember { mutableStateOf(false) }
    var confidenceRefresh by remember { mutableIntStateOf(0) }
    var showTimerDialog by remember { mutableStateOf(false) }

    if (showRoutine) {
        RoutineScreen(onBack = { showRoutine = false })
        return
    }
    if (showNotepad) {
        NotepadScreen(onBack = { showNotepad = false })
        return
    }
    if (showSteps) {
        StepsScreen(onBack = { showSteps = false })
        return
    }
    if (showPreSleep) {
        PreSleepScreen(onBack = { showPreSleep = false })
        return
    }
    if (showHydration) {
        HydrationScreen(onBack = { showHydration = false })
        return
    }
    if (showLuna) {
        LunaScreen(onBack = { showLuna = false })
        return
    }
    if (showUrgeCard) {
        UrgeCardScreen(onBack = { showUrgeCard = false })
        return
    }

    if (showConfidenceDialog) {
        ConfidenceDialog(
            onDismiss = { showConfidenceDialog = false },
            onLogged = { confidenceRefresh++ }
        )
    }

    if (showTimerDialog) {
        TimerDialog(onDismiss = { showTimerDialog = false })
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
                onQuestsLoaded = { daily, weekly, _, _, _ ->
                    dailyQuestsState = daily
                    allWeeklyQuestsState = weekly
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
                    text = "Routine",
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
                    text = "Luna",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier
                        .background(Color(0xFF2a2a2a), shape = RoundedCornerShape(8.dp))
                        .clickable { showLuna = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                WaterJugIconCanvas(
                    modifier = Modifier
                        .clickable { showHydration = true }
                        .padding(horizontal = 4.dp)
                )
                StepsIconCanvas(
                    modifier = Modifier
                        .clickable { showSteps = true }
                        .padding(horizontal = 4.dp)
                )
                MoonIconCanvas(
                    modifier = Modifier
                        .clickable { showPreSleep = true }
                        .padding(horizontal = 4.dp)
                )
                TimerIconCanvas(
                    modifier = Modifier
                        .clickable { showTimerDialog = true }
                        .padding(horizontal = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\"The Pain Of Discipline Or The Pain Of Regret.\"",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showUrgeCard = true })
                    }
                ) {
                    Text(text = "🔥", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "$nofapStreak Days", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                }
                Spacer(modifier = Modifier.width(10.dp))
                ShieldIconCanvas(
                    modifier = Modifier.clickable { showConfidenceDialog = true }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ConfidenceMeter(refreshKey = confidenceRefresh)
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
                item(key = "daily-routine-reference") {
                    DailyRoutineReference()
                }
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
                    item(key = "core-reference") {
                        RoutineCoreReference(weekday = selectedDay)
                    }
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

// Core static routine for the selected weekday, shown above that day's quests on the
// View Routine page. Collapsible so it doesn't push the checklist down.
@Composable
fun RoutineCoreReference(weekday: Int) {
    var expanded by remember { mutableStateOf(false) }
    var reference by remember(weekday) { mutableStateOf<RoutineReference?>(null) }

    LaunchedEffect(weekday) {
        reference = kotlinx.coroutines.withContext(Dispatchers.IO) {
            try { fetchRoutineReference(weekday) } catch (e: Exception) { null }
        }
    }

    val ref = reference ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1f1f1f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CORE ROUTINE · " + ref.label.uppercase(),
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF8A8A8A), letterSpacing = 2.sp
            )
            Text(text = if (expanded) "▲" else "▼", fontSize = 13.sp, color = Color(0xFF8A8A8A))
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            ref.items.forEach { task ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "•", fontSize = 13.sp, color = Color(0xFF6A6A6A), modifier = Modifier.padding(end = 8.dp))
                    Text(text = task, fontSize = 13.sp, color = Color(0xFFBFBFBF))
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

// The everyday core routine — the autopilot tasks done every day, shown read-only.
// Single source of truth is the server's routine_reference table, fetched via
// /api/routine-reference. This hardcoded list is only an offline fallback so the
// card still shows something if the network is unavailable.
// Offline fallback for the routine reference. The server returns today's schedule
// (WFM or Delivery) with a label; when unreachable we show this generic WFM list.
val DAILY_ROUTINE_CORE_FALLBACK = listOf(
    "5:45 AM - Wake Up",
    "Bathroom, Teeth, Clothes, Hair, & Supplement Drink",
    "6:00 AM - Walk Toby & Turn Off Lights",
    "6:15 AM - Bed, Trash, Supplement Drink, Ice, & Protein Shake",
    "6:30 AM - Pickup Dad",
    "6:45 AM - Gym (Mon, Tues, & Thurs to Sat)",
    "7:30 AM - Air Humidifier & Protein Shake",
    "7:35 AM - One Hour Of Day Specific Tasks",
    "9:00 AM - Prepare Clothes & Shower",
    "9:20 AM - Clean Hearing Aids & Meditate",
    "9:30 AM - Feed Toby & Luna",
    "9:40 AM - Chill",
    "10:10 AM - Cook Lunch, Eat, Clean, & Prepare Dinner",
    "11:00 AM - Study",
    "11:45 AM - Prepare For Work & Air Humidifier",
    "3:00 PM - Cook Rice",
    "3:30 PM - Bake Pork",
    "4:00 PM - Sear Pork",
    "4:20 PM - Eat Dessert",
    "4:40 PM - Clean & Prepare Soda",
    "7:00 PM - Brush Teeth",
    "8:00 PM - Take & Prepare Evening Supplement",
    "9:00 PM - Walk Toby & Turn On Lights"
)

// Holds the routine reference response: a label (WFM / Delivery Day) plus the items.
data class RoutineReference(val label: String, val items: List<String>)

// Fetches today's routine from the server. Returns null on failure so the caller
// can fall back to the baked-in list.
fun fetchRoutineReference(weekday: Int? = null): RoutineReference? {
    return try {
        val suffix = if (weekday != null) "?weekday=$weekday" else ""
        val url = URL("https://mysololeveling.us/api/routine-reference$suffix")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = Gson().fromJson(body, Map::class.java)
            val label = (obj["label"] as? String) ?: "Daily Routine"
            val rawItems = obj["items"] as? List<*> ?: return null
            val items = rawItems.mapNotNull {
                if (it is Map<*, *>) (it["title"] as? String) else null
            }
            if (items.isEmpty()) null else RoutineReference(label, items)
        } finally { conn.disconnect() }
    } catch (e: Exception) { null }
}

// Hydration schedule reference — static, no per-pull tracking. One jug per day.
data class HydrationBlock(val title: String, val subtitle: String, val pulls: List<String>)

val HYDRATION_SCHEDULE = listOf(
    HydrationBlock(
        "WFH Gym Days (Mon, Tue, Thu–Sat)",
        "",
        listOf(
            "7:35 AM tasks → 8 oz",
            "9 AM shower → 8 oz",
            "10 AM lunch → 8 oz",
            "11 AM Study → 8 oz (halfway)",
            "2 PM pork out → 8 oz",
            "3:30 PM bake → 8 oz",
            "4:20 PM dessert → 8 oz",
            "7 PM teeth/walk → 8 oz (finish)"
        )
    ),
    HydrationBlock(
        "Rest Days (Sun & Wed)",
        "plasma, no gym",
        listOf(
            "6:50 AM Donate Plasma → 8 oz (hydrate before, helps the draw)",
            "8:30 AM chores block → 8 oz",
            "10 AM lunch → 8 oz",
            "11 AM Study → 8 oz (halfway — jug should be at 32 oz)",
            "~2 PM → 8 oz",
            "~4 PM dessert → 8 oz",
            "~7 PM → 8 oz",
            "Evening → 8 oz (finish)"
        )
    ),
    HydrationBlock(
        "Delivery Days",
        "anchor to packages, not clock",
        listOf(
            "Sorting at WH → 8 oz",
            "Every ~25 delivered → 8 oz (package ~60 = halfway)",
            "Route done → 8 oz",
            "Dinner → 8 oz (finish)"
        )
    )
)

@Composable
fun WaterJugIconCanvas(modifier: Modifier = Modifier) {
    val blue = Color(0xFF5AC8FA)
    androidx.compose.foundation.Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f)
        // Jug body
        val left = w * 0.22f
        val right = w * 0.82f
        val top = h * 0.26f
        val bottom = h * 0.94f
        drawRoundRect(
            color = blue,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f, w * 0.12f),
            style = stroke
        )
        // Cap / neck
        drawLine(
            color = blue,
            start = androidx.compose.ui.geometry.Offset(w * 0.40f, h * 0.10f),
            end = androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.10f),
            strokeWidth = w * 0.11f
        )
        drawLine(
            color = blue,
            start = androidx.compose.ui.geometry.Offset(w * 0.52f, h * 0.10f),
            end = androidx.compose.ui.geometry.Offset(w * 0.52f, top),
            strokeWidth = w * 0.09f
        )
        // Handle
        drawLine(
            color = blue,
            start = androidx.compose.ui.geometry.Offset(right, h * 0.40f),
            end = androidx.compose.ui.geometry.Offset(w * 0.95f, h * 0.52f),
            strokeWidth = w * 0.08f
        )
        // Water line
        drawLine(
            color = blue,
            start = androidx.compose.ui.geometry.Offset(left, h * 0.60f),
            end = androidx.compose.ui.geometry.Offset(right, h * 0.60f),
            strokeWidth = w * 0.07f
        )
    }
}

// Static reference shown on long-press of the streak counter.
val URGE_STEPS = listOf(
    "Feet on the pad. 2 mph. Now. Don't negotiate, just start walking.",
    "The urge is a cue, not a command. It's your body flagging idle + alone. Answer it with motion.",
    "10 minutes minimum. The wave passes. It always passes.",
    "Two wins, one move. Steps banked. Loop broken. Same pad, both jobs."
)

// Daily step target reference — static.
data class StepBlock(val title: String, val subtitle: String, val lines: List<String>)

val STEP_SCHEDULE = listOf(
    StepBlock(
        "WFH Days",
        "where the steps come from",
        listOf(
            "9:30 AM Chill → 10 min pad, then PC games",
            "Idle shift windows → pad during low-call stretches",
            "7:00 PM Toby walk → outdoor steps",
            "Evening → pad while watching YouTube/anime",
            "Urge hits? → pad. Steps + loop broken, same move."
        )
    ),
    StepBlock(
        "Delivery Days",
        "",
        listOf(
            "Route handles it — 100–120 packages on foot clears 12k easily. No pad needed."
        )
    )
)

data class SleepStep(val title: String, val detail: String)

val PRE_SLEEP_STEPS = listOf(
    SleepStep(
        "TV off, ~30 min before bed",
        "Hard stop on screens."
    ),
    SleepStep(
        "Legs-up-the-wall (Viparita Karani), 5–10 min",
        "Lie on your back with your butt close to the wall and legs extended straight up against it. Arms relaxed at your sides, breathe slow. It shifts blood flow, calms the nervous system, and eases tension from a day of standing/delivering. Keep the room dim while you do it."
    ),
    SleepStep(
        "Read a book, ~20–30 min",
        "Paper or warm-lit e-reader, low-stakes material, dim warm light."
    ),
    SleepStep(
        "Lights out at a consistent time",
        "Same time every night — that consistency is what turns the sequence into a real sleep trigger."
    )
)

// Crescent moon: a filled disc with an offset disc punched out in the header's
// background colour, which reads as a moon rather than a stroked "C".
@Composable
fun MoonIconCanvas(modifier: Modifier = Modifier) {
    val lilac = Color(0xFFA5B4FC)
    val headerBg = Color(0xFF1a1a1a)
    androidx.compose.foundation.Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = lilac,
            radius = w * 0.44f,
            center = androidx.compose.ui.geometry.Offset(w * 0.46f, h * 0.50f)
        )
        drawCircle(
            color = headerBg,
            radius = w * 0.40f,
            center = androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.36f)
        )
    }
}

@Composable
fun PreSleepScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Text(
                text = "←",
                color = Color(0xFFFFD700),
                fontSize = 24.sp,
                modifier = Modifier.clickable { onBack() }
            )
            Spacer(Modifier.width(16.dp))
            Text("Pre-Sleep", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(
            text = "~30–40 min before lights out",
            fontSize = 13.sp,
            color = Color(0xFF888888),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(PRE_SLEEP_STEPS) { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5B4FC),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = step.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFDDDDDD),
                            lineHeight = 21.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = step.detail,
                            fontSize = 13.sp,
                            color = Color(0xFF999999),
                            lineHeight = 19.sp
                        )
                    }
                }
            }
            item {
                Text(
                    text = "No true days off — WFM shift plus the delivery route — so consistency matters more than any single step. Same sequence every night.",
                    fontSize = 13.sp,
                    color = Color(0xFF888888),
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(Color(0xFF141414), shape = RoundedCornerShape(10.dp))
                        .padding(14.dp)
                )
            }
        }
    }
}

@Composable
fun StepsIconCanvas(modifier: Modifier = Modifier) {
    val green = Color(0xFF6ACB6A)
    androidx.compose.foundation.Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        // Two footprints, offset diagonally
        // Back foot (lower left)
        drawOval(
            color = green,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.44f),
            size = androidx.compose.ui.geometry.Size(w * 0.30f, h * 0.40f)
        )
        drawOval(
            color = green,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.11f, h * 0.30f),
            size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.16f)
        )
        // Front foot (upper right)
        drawOval(
            color = green,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.55f, h * 0.24f),
            size = androidx.compose.ui.geometry.Size(w * 0.30f, h * 0.40f)
        )
        drawOval(
            color = green,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.10f),
            size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.16f)
        )
    }
}

@Composable
fun StepsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                text = "←",
                color = Color(0xFFFFD700),
                fontSize = 24.sp,
                modifier = Modifier.clickable { onBack() }
            )
            Spacer(Modifier.width(16.dp))
            Text("Steps", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // Target header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF14261A), shape = RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "DAILY TARGET: 12,000 STEPS",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6ACB6A),
                letterSpacing = 1.sp
            )
            Text(
                text = "Pad minimum: 30 min @ 2 mph total (~2,000 steps / ~1 mile)",
                fontSize = 13.sp,
                color = Color(0xFF9FBF9F),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(STEP_SCHEDULE, key = { it.title }) { block ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = block.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6ACB6A)
                    )
                    if (block.subtitle.isNotEmpty()) {
                        Text(
                            text = block.subtitle,
                            fontSize = 12.sp,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    block.lines.forEach { line ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                text = "•",
                                fontSize = 13.sp,
                                color = Color(0xFF6A6A6A),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(text = line, fontSize = 13.sp, color = Color(0xFFCFCFCF), lineHeight = 19.sp)
                        }
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414), shape = RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "THE RULE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Pad is the default for any idle-and-alone window. Don't sit idle — walk idle.",
                        fontSize = 14.sp,
                        color = Color(0xFFCFCFCF),
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "PACE CHECK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "~10,500 by end of shift = on track.",
                        fontSize = 14.sp,
                        color = Color(0xFFCFCFCF),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UrgeCardScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Text(
                text = "←",
                color = Color(0xFFFFD700),
                fontSize = 24.sp,
                modifier = Modifier.clickable { onBack() }
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "When The Urge Hits",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text = "STAND UP → PAD",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD700),
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(URGE_STEPS) { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = step,
                        fontSize = 15.sp,
                        color = Color(0xFFDDDDDD),
                        lineHeight = 22.sp
                    )
                }
            }
            item {
                Text(
                    text = "If you're not near the pad: leave the room. Change what you're looking at. Then come back to the pad.",
                    fontSize = 14.sp,
                    color = Color(0xFF999999),
                    lineHeight = 21.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(Color(0xFF141414), shape = RoundedCornerShape(10.dp))
                        .padding(14.dp)
                )
            }
        }
    }
}

@Composable
fun HydrationScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Text(
                text = "←",
                color = Color(0xFFFFD700),
                fontSize = 24.sp,
                modifier = Modifier.clickable { onBack() }
            )
            Spacer(Modifier.width(16.dp))
            Text("Hydration", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(
            text = "One jug a day — finish it. Reference only.",
            fontSize = 13.sp,
            color = Color(0xFF888888),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(HYDRATION_SCHEDULE, key = { it.title }) { block ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = block.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5AC8FA)
                    )
                    if (block.subtitle.isNotEmpty()) {
                        Text(
                            text = block.subtitle,
                            fontSize = 12.sp,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    block.pulls.forEach { pull ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                text = "•",
                                fontSize = 13.sp,
                                color = Color(0xFF6A6A6A),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(text = pull, fontSize = 13.sp, color = Color(0xFFCFCFCF), lineHeight = 19.sp)
                        }
                    }
                }
            }
        }
    }
}

data class ConfidenceState(
    val value: Int,
    val max: Int,
    val hunger: Int,
    val urge: Int,
    val total: Int
)

suspend fun fetchConfidence(): ConfidenceState? {
    return try {
        val body = fetchFromApi("/api/confidence")
        val o = Gson().fromJson(body, Map::class.java)
        ConfidenceState(
            value = (o["value"] as? Number)?.toInt() ?: 0,
            max = (o["max"] as? Number)?.toInt() ?: 100,
            hunger = (o["hunger"] as? Number)?.toInt() ?: 0,
            urge = (o["urge"] as? Number)?.toInt() ?: 0,
            total = (o["total"] as? Number)?.toInt() ?: 0
        )
    } catch (e: Exception) { null }
}

fun logConfidenceWin(type: String) {
    val url = URL("https://mysololeveling.us/api/confidence")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    try {
        conn.outputStream.use { it.write("{\"type\":\"$type\"}".toByteArray()) }
        conn.responseCode
    } finally { conn.disconnect() }
}

// Small shield, tapped to log an overcome urge.
@Composable
fun ShieldIconCanvas(modifier: Modifier = Modifier) {
    val teal = Color(0xFF4FD1C5)
    androidx.compose.foundation.Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.06f)
            lineTo(w * 0.90f, h * 0.24f)
            lineTo(w * 0.90f, h * 0.55f)
            cubicTo(w * 0.90f, h * 0.78f, w * 0.72f, h * 0.90f, w * 0.5f, h * 0.96f)
            cubicTo(w * 0.28f, h * 0.90f, w * 0.10f, h * 0.78f, w * 0.10f, h * 0.55f)
            lineTo(w * 0.10f, h * 0.24f)
            close()
        }
        drawPath(
            path = path,
            color = teal,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.10f)
        )
    }
}

// 0-100 meter of recent wins over urges. Rises when logged, decays slowly.
@Composable
fun ConfidenceMeter(refreshKey: Int) {
    var state by remember { mutableStateOf<ConfidenceState?>(null) }

    LaunchedEffect(refreshKey) {
        state = fetchConfidence()
    }

    val s = state ?: return
    val pct = if (s.max > 0) (s.value.toFloat() / s.max.toFloat()).coerceIn(0f, 1f) else 0f
    val barColor = when {
        pct >= 0.66f -> Color(0xFF4FD1C5)
        pct >= 0.33f -> Color(0xFFFFD700)
        else -> Color(0xFFF59E0B)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CONFIDENCE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8A8A8A),
                letterSpacing = 1.5.sp
            )
            Text(
                text = "${s.value} / ${s.max}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF2a2a2a))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = "Hunger ${s.hunger} · Urges ${s.urge}",
            fontSize = 10.sp,
            color = Color(0xFF6A6A6A)
        )
    }
}

@Composable
fun ConfidenceDialog(onDismiss: () -> Unit, onLogged: () -> Unit) {
    val scope = rememberCoroutineScope()

    fun log(type: String) {
        scope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                try { logConfidenceWin(type) } catch (e: Exception) {}
            }
            onLogged()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a1a),
        title = { Text("Overcame an urge", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Log the win. Which one did you beat?",
                    color = Color(0xFFB0B0B0),
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            Text(
                text = "Hunger",
                color = Color(0xFF4FD1C5),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { log("hunger") }
                    .padding(10.dp)
            )
        },
        dismissButton = {
            Text(
                text = "Urge",
                color = Color(0xFF4FD1C5),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { log("urge") }
                    .padding(10.dp)
            )
        }
    )
}

@Composable
fun TimerIconCanvas(modifier: Modifier = Modifier) {
    val gold = Color(0xFFFFD700)
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(22.dp)
    ) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f)
        // Body circle (leave room at top for the stem/button)
        val cx = w / 2f
        val cy = h * 0.58f
        val r = w * 0.36f
        drawCircle(color = gold, radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy), style = stroke)
        // Top stem (little button on top of the stopwatch)
        drawLine(
            color = gold,
            start = androidx.compose.ui.geometry.Offset(cx, h * 0.06f),
            end = androidx.compose.ui.geometry.Offset(cx, cy - r),
            strokeWidth = w * 0.10f
        )
        // Clock hand (pointing up-right)
        drawLine(
            color = gold,
            start = androidx.compose.ui.geometry.Offset(cx, cy),
            end = androidx.compose.ui.geometry.Offset(cx + r * 0.55f, cy - r * 0.45f),
            strokeWidth = w * 0.08f
        )
    }
}

@Composable
fun TimerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Two presets (editable). label -> default minutes.
    val presets = listOf("Meditation" to 2, "Vaping & Video Games" to 30)
    var selected by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf(presets[0].second.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a1a),
        title = { Text("Timer", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                presets.forEachIndexed { i, (label, def) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    selected = i
                                    minutes = def.toString()
                                })
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selected == i) "●" else "○",
                            fontSize = 16.sp,
                            color = if (selected == i) Color(0xFFFFD700) else Color(0xFF777777),
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Text(text = label, fontSize = 15.sp, color = Color.White)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Minutes", fontSize = 11.sp, color = Color(0xFF777777))
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF7b8cde),
                        unfocusedBorderColor = Color(0xFF2a2a3a)
                    )
                )
            }
        },
        confirmButton = {
            Text(
                text = "Start",
                color = Color(0xFF7b8cde),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        val mins = minutes.toIntOrNull() ?: 0
                        if (mins > 0) {
                            val (label, _) = presets[selected]
                            val doneText = if (label == "Meditation")
                                "Meditation complete" else "Time's up — done vaping & gaming"
                            TimerService.start(context, label, mins * 60, doneText)
                            onDismiss()
                        }
                    })
                }.padding(8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "Stop",
                color = Color(0xFFa55),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        TimerService.stop(context)
                        onDismiss()
                    })
                }.padding(8.dp)
            )
        }
    )
}

@Composable
fun DailyRoutineReference() {
    var expanded by remember { mutableStateOf(false) }
    var tasks by remember { mutableStateOf(DAILY_ROUTINE_CORE_FALLBACK) }
    var headerLabel by remember { mutableStateOf("DAILY ROUTINE") }

    // Load from the server once; keep the fallback if the fetch fails.
    LaunchedEffect(Unit) {
        val fetched = kotlinx.coroutines.withContext(Dispatchers.IO) { fetchRoutineReference() }
        if (fetched != null) {
            tasks = fetched.items
            headerLabel = "DAILY ROUTINE · " + fetched.label.uppercase()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1f1f1f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerLabel,
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF8A8A8A), letterSpacing = 2.sp
            )
            Text(text = if (expanded) "▲" else "▼", fontSize = 13.sp, color = Color(0xFF8A8A8A))
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            tasks.forEach { task ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "•", fontSize = 13.sp, color = Color(0xFF6A6A6A), modifier = Modifier.padding(end = 8.dp))
                    Text(text = task, fontSize = 13.sp, color = Color(0xFFBFBFBF))
                }
            }
        }
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

suspend fun loadAllQuests(onQuestsLoaded: (List<Quest>, List<Quest>, Int, Int, Boolean) -> Unit, onError: (String) -> Unit) {
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

        android.util.Log.d("API_DEBUG", "Daily quests: ${dailyQuestsList.size}, Weekly quests due today: ${weeklyQuestsList.size}, All weekly quests: ${allWeeklyQuestsList.size}")
        android.util.Log.d("QUEST_COMPLETED", "All weekly quests: ${allWeeklyQuestsList.map { "${it.title}: ${it.completed}" }.joinToString(", ")}")

        onQuestsLoaded(dailyQuestsList, allWeeklyQuestsList, dailiesCompleted, weekliesCompleted, hasWeekly)
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

// ─── Delivery-day diet ────────────────────────────────────────────────────────
// Delivery days swap breakfast (double shake) and lunch (Whataburger on the road).
// Dinner, snack, and dessert are the same as WFM days.
val DELIVERY_BREAKFAST_MEALS = listOf(
    Meal(
        name = "Protein Shake (Double)",
        mealType = "Breakfast",
        macros = Macros(calories = 450, protein = 62, fat = 11, netCarbs = "25"),
        ingredients = listOf(
            "4 scoops (78g) Premier Protein Vanilla Milkshake powder — 2 per batch",
            "1/2 banana (~60g), frozen — batch 1 only",
            "1 tbsp cocoa powder — batch 1 only",
            "7g pecans (about 1 tbsp chopped) — batch 1 only",
            "Swerve sweetener, to taste (1–2 tsp) — batch 1 only",
            "16 to 24 oz cold water total"
        ),
        steps = listOf(
            MealStep(
                "Batch 1 — Full Shake",
                "2 scoops (39g) Premier Protein powder, 1/2 frozen banana, 1 tbsp cocoa powder, 7g pecans, Swerve to taste, and 8–12 oz cold water.\n\nBlend 20–30 sec until smooth."
            ),
            MealStep(
                "Batch 2 — Plain Protein",
                "2 scoops (39g) Premier Protein powder and 8–12 oz cold water.\n\nBlend or shake until smooth."
            ),
            MealStep(
                "Space Them Out",
                "Full shake in the morning, plain one mid-route — for steadier protein and to keep the calorie/carb bump down."
            )
        )
    )
)

val DELIVERY_SNACK_MEALS = listOf(
    Meal(
        name = "Beef Stick",
        mealType = "Snack",
        macros = Macros(calories = 90, protein = 9, fat = 6, netCarbs = "1"),
        ingredients = listOf(
            "1 grass-fed beef stick (28g / 1 oz)"
        ),
        steps = emptyList()
    )
)

val DELIVERY_LUNCH_MEALS = listOf(
    Meal(
        name = "Whataburger Breakfast Burger Combo",
        mealType = "Lunch",
        macros = Macros(calories = 955, protein = 31, fat = 55, netCarbs = "83"),
        ingredients = listOf(
            "Breakfast Burger (670 cal · 28g P · 38g F · 51g C)",
            "Large hash brown sticks (~285 cal · 3g P · 17g F · 32g C)",
            "Diet soda (0 cal)"
        ),
        steps = emptyList()
    )
)

val BREAKFAST_MEALS = listOf(
    Meal(
        name = "Protein Shake",
        mealType = "Breakfast",
        macros = Macros(calories = 300, protein = 32, fat = 8, netCarbs = "22"),
        ingredients = listOf(
            "2 scoops (39g) Premier Protein Vanilla Milkshake powder",
            "1/2 banana (~60g), frozen",
            "1 tbsp cocoa powder",
            "7g pecans (about 1 tbsp chopped, ~10 halves)",
            "Swerve sweetener, to taste (start with 1–2 tsp)",
            "8 to 12 oz cold water"
        ),
        steps = listOf(
            MealStep(
                "1. Measure the Base",
                "Pour 8 to 12 oz of cold water into the Ninja blender jar. (Thicker shake: stick to 8 oz. More fluid: go up to 12 oz.)"
            ),
            MealStep(
                "2. Add Everything",
                "Add the frozen banana, 2 scoops of Premier Protein powder, 1 tbsp cocoa powder, 7g pecans, and Swerve to taste."
            ),
            MealStep(
                "3. Blend",
                "Secure the lid and blend 20–30 seconds until smooth and the pecans and banana are broken down."
            ),
            MealStep(
                "4. Finish",
                "Let it sit 30 seconds for foam to settle, then pour and drink."
            )
        )
    )
)

val LUNCH_MEALS = listOf(
    Meal(
        name = "Bacon & Ham Sliders",
        mealType = "Lunch",
        macros = Macros(calories = 710, protein = 63, fat = 33, netCarbs = "36"),
        ingredients = listOf(
            "3 slices Member's Mark Double Smoked Thick Cut Bacon",
            "6 slices Member's Mark Uncured Black Forest Ham",
            "2 Member's Mark brioche slider buns",
            "1 oz (approx. 1/3 cup) Member's Mark Mexican Style Finely Shredded Cheese"
        ),
        steps = listOf(
            MealStep(
                "1. Air Fry the Bacon",
                "Lay the thick-cut bacon in a single layer in the air fryer basket (cut in half if needed to fit, don't overlap). Air fry at 350°F for 14 min, until crispy. No need to flip. Drain on a paper towel. Thick-cut renders a lot of fat, so manage grease partway through to cut smoke."
            ),
            MealStep(
                "2. Warm the Ham, Cheese & Buns (Oven)",
                "Preheat oven to 300°F. Lay the 6 ham slices on a baking sheet (3 per slider), sprinkle the cheese over the ham, and add the split buns cut-side up alongside. Warm everything 5 min — ham heated through, cheese melted, buns lightly toasted."
            ),
            MealStep(
                "3. Build the Sliders",
                "On each bottom bun, stack the cheesy ham (3 slices, folded to fit), then the bacon. With 3 slices, put 1.5 slices per slider (halve as needed to fit). Cap with the top bun. Press gently."
            ),
            MealStep(
                "4. Serve",
                "Eat while the cheese is melty and the bacon is crisp. On delivery days, wrap in foil to eat one-handed in the truck."
            )
        )
    )
)

val DINNER_MEALS = listOf(
    Meal(
        name = "Pork Blade Steak & Rice",
        mealType = "Dinner",
        macros = Macros(calories = 640, protein = 47, fat = 24, netCarbs = "32"),
        ingredients = listOf(
            "1 bone-in pork shoulder blade steak (~10 oz raw)",
            "3/4 cup jasmine rice (uncooked)",
            "1 tsp Knorr Granulated Beef Bouillon (for rice)",
            "Water for rice (per the cooker's fill line for 3/4 cup rice)",
            "Tony Chachere's Original Creole Seasoning",
            "Touch of oil, black pepper"
        ),
        steps = listOf(
            MealStep(
                "Pork 1. Season & Oven",
                "Preheat oven to 300°F. Pat the steak dry, season both sides with Tony Chachere's and black pepper."
            ),
            MealStep(
                "Pork 2. Cook To Temp",
                "Place the steak on a rack over a baking sheet. Cook in the oven until it reaches ~135–140°F internal (about 20–30 min depending on thickness). Use a meat thermometer — this is the key to reverse sear."
            ),
            MealStep(
                "Pork 3. Rest & Heat Pan",
                "Pull it out and let it rest a few minutes while you heat the pan."
            ),
            MealStep(
                "Pork 4. Sear (Cast Iron)",
                "Get a cast iron skillet screaming hot over high heat with a touch of oil. Sear the steak hard, ~1 min per side, just to build a deep crust. Don't overcook — the interior is already done."
            ),
            MealStep(
                "Pork 5. Rest & Serve",
                "Rest a couple minutes. Serve whole, bone-in — cut and eat at the plate."
            ),
            MealStep(
                "Rice 1. Cook (Aroma ARC-5204SB)",
                "Add the 3/4 cup rice and water to the cooker's fill line. Stir in 1 tsp bouillon until dissolved. Close the lid, select White Rice, and start."
            ),
            MealStep(
                "Rice 2. Fluff",
                "When it switches to Keep Warm, let it rest 5 min, then fluff with the spatula (lift and turn, don't stir)."
            ),
            MealStep(
                "Combine",
                "Plate the whole steak alongside the bouillon rice.\n\nNote: Tony Chachere's is salt-based — it seasons and salts in one, so no separate salt needed. Between the Creole seasoning and the bouillon rice, sodium runs high — keep water up on delivery days."
            )
        )
    )
)

val SNACK_MEALS = listOf(
    Meal(
        name = "Protein Snack",
        mealType = "Snack",
        macros = Macros(calories = 300, protein = 12, fat = 27, netCarbs = "2"),
        ingredients = listOf(
            "30g Member's Mark Natural Pecan Halves",
            "1 grass-fed beef stick (28g / 1 oz)",
            "Note: Pecans — 210 cal · 3p · 21f · 1nc\n      Beef stick — 90 cal · 9p · 6f · 1nc"
        ),
        steps = emptyList()
    )
)

val DESSERT_MEALS = listOf(
    Meal(
        name = "Chocolate Pecan Fat Bombs",
        mealType = "Dessert",
        macros = Macros(calories = 145, protein = 1, fat = 15, netCarbs = "1.5"),
        ingredients = listOf(
            "3/4 cup (1.5 sticks) butter, softened — or 3/4 cup coconut oil",
            "6 tbsp cocoa powder",
            "7 tbsp sweetener (to taste)",
            "47g pecans (weighed whole, then chopped)",
            "Pinch of salt (optional, sharpens the chocolate)"
        ),
        steps = listOf(
            MealStep(
                "1. Melt the Base",
                "Gently melt the butter (or coconut oil) — microwave 30–40 sec or a small pan on low. Don't boil it, just liquefy."
            ),
            MealStep(
                "2. Mix",
                "Stir in the cocoa powder, sweetener, and pinch of salt until smooth with no lumps. Taste and adjust sweetener now."
            ),
            MealStep(
                "3. Add Pecans",
                "Chop the 47g pecans and stir them in so they're evenly distributed."
            ),
            MealStep(
                "4. Fill the Mold",
                "Pour into the silicone ice cube tray, filling each cavity to the top. This batch fills about 10 cavities."
            ),
            MealStep(
                "5. Freeze",
                "Freeze at least 1–2 hours until solid."
            ),
            MealStep(
                "6. Store & Serve",
                "Pop the whole batch out of the tray at once (let it sit at room temp ~60 sec first, then flex the silicone), and store the pieces loose in a freezer bag or container. Each day, just grab one. Let it soften ~1–2 min before eating.\n\nMakes ~10 pieces · Per piece: ~145 cal, 1g protein, 15g fat, 1.5g net carb"
            )
        )
    )
)

@Composable
fun DietScreen() {
    var selectedMeal by remember { mutableStateOf<Meal?>(null) }
    // Default to today's diet: Fri/Sat are delivery days, everything else is WFM.
    val todayDow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
    var deliveryDiet by remember {
        mutableStateOf(
            todayDow == java.util.Calendar.FRIDAY || todayDow == java.util.Calendar.SATURDAY
        )
    }

    BackHandler(enabled = selectedMeal != null) {
        selectedMeal = null
    }

    val meal = selectedMeal
    if (meal == null) {
        DietListScreen(
            deliveryDiet = deliveryDiet,
            onToggleDiet = { deliveryDiet = !deliveryDiet },
            onMealSelected = { selectedMeal = it }
        )
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
fun DietListScreen(deliveryDiet: Boolean, onToggleDiet: () -> Unit, onMealSelected: (Meal) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0a0a0a))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Diet",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (deliveryDiet) "Delivery Day" else "WFM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = if (deliveryDiet) "View WFM" else "View Delivery",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFD700),
                modifier = Modifier
                    .background(Color(0xFF2a2a2a), shape = RoundedCornerShape(8.dp))
                    .clickable { onToggleDiet() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

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
                    MacroBox("Calories", if (deliveryDiet) "2,135" else "2,095", Modifier.weight(1f))
                    MacroBox("Protein", if (deliveryDiet) "149g" else "155g", Modifier.weight(1f))
                    MacroBox("Fat", if (deliveryDiet) "96g" else "107g", Modifier.weight(1f))
                    MacroBox("Net Carbs", if (deliveryDiet) "141g" else "93.5g", Modifier.weight(1f))
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
            items(if (deliveryDiet) DELIVERY_BREAKFAST_MEALS else BREAKFAST_MEALS, key = { it.name }) { meal ->
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
            items(if (deliveryDiet) DELIVERY_LUNCH_MEALS else LUNCH_MEALS, key = { it.name }) { meal ->
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
            items(if (deliveryDiet) DELIVERY_SNACK_MEALS else SNACK_MEALS, key = { it.name }) { meal ->
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
            // No dessert on delivery days.
            if (!deliveryDiet) {
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
                                if (ingredient.startsWith("Note:")) {
                                    Text(text = ingredient, fontSize = 13.sp, color = Color(0xFF9A9A9A), lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                                } else {
                                    Text(text = "• $ingredient", fontSize = 14.sp, color = Color.White, modifier = Modifier.padding(vertical = 2.dp))
                                }
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
                                "Caffeine" to "200 mg",
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
                                "L-Theanine" to "400 mg",
                                "L-Tyrosine" to "1000 mg"
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
                item(key = "plateau-alerts") {
                    PlateauAlerts()
                }
                items(routines, key = { it.routineId }) { routine ->
                    GymRoutineSection(routine = routine, onExerciseSelected = onExerciseSelected)
                }
            }
        }
    }
}

data class PlateauSuggestion(
    val id: Int,
    val exercise: String?,
    val muscleGroup: String?,
    val signal: String,
    val severity: String,
    val detail: String,
    val fix: String
)

suspend fun fetchPlateauSuggestions(): List<PlateauSuggestion> {
    val response = fetchFromApi("/api/gym/suggestions")
    val obj = Gson().fromJson(response, Map::class.java)
    val list = obj["suggestions"] as? List<*> ?: return emptyList()
    return list.mapNotNull {
        if (it is Map<*, *>) PlateauSuggestion(
            id = (it["id"] as? Number)?.toInt() ?: return@mapNotNull null,
            exercise = it["exercise"] as? String,
            muscleGroup = it["muscle_group"] as? String,
            signal = (it["signal"] as? String) ?: "",
            severity = (it["severity"] as? String) ?: "medium",
            detail = (it["detail"] as? String) ?: "",
            fix = (it["fix"] as? String) ?: ""
        ) else null
    }
}

fun dismissPlateauSuggestion(id: Int) {
    val url = URL("https://mysololeveling.us/api/gym/suggestions/$id/dismiss")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    try { conn.responseCode } finally { conn.disconnect() }
}

// Collapsed badge at the top of the Gym tab; tap to expand the full list.
@Composable
fun PlateauAlerts() {
    var expanded by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<PlateauSuggestion>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        suggestions = try { fetchPlateauSuggestions() } catch (e: Exception) { emptyList() }
    }

    if (suggestions.isEmpty()) return

    val highCount = suggestions.count { it.severity == "high" }
    val accent = if (highCount > 0) Color(0xFFf87171) else Color(0xFFFFD700)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PLATEAU ALERTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${suggestions.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0a0a0a),
                    modifier = Modifier
                        .background(accent, shape = RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                )
            }
            Text(text = if (expanded) "▲" else "▼", fontSize = 13.sp, color = accent)
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            suggestions.forEach { s ->
                val sevColor = if (s.severity == "high") Color(0xFFf87171) else Color(0xFFFFD700)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color(0xFF141414), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = s.exercise ?: (s.muscleGroup ?: "").replaceFirstChar { c -> c.uppercase() },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = sevColor,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "×",
                            fontSize = 18.sp,
                            color = Color(0xFF777777),
                            modifier = Modifier
                                .clickable {
                                    val gone = s.id
                                    suggestions = suggestions.filter { it.id != gone }
                                    scope.launch {
                                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                                            try { dismissPlateauSuggestion(gone) } catch (e: Exception) {}
                                        }
                                    }
                                }
                                .padding(start = 10.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(text = s.detail, fontSize = 13.sp, color = Color(0xFFCFCFCF), lineHeight = 19.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = s.fix,
                        fontSize = 13.sp,
                        color = Color(0xFF9FBF9F),
                        lineHeight = 19.sp
                    )
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