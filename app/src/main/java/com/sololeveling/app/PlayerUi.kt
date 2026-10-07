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
    var showWeightScreen by remember { mutableStateOf(false) }
    var showStepScreen by remember { mutableStateOf(false) }
    var showMobilityScreen by remember { mutableStateOf(false) }
    var showBirthdayScreen by remember { mutableStateOf(false) }
    var showTravelScreen by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }

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

    if (showWeightScreen) {
        WeightScreen(onBack = { showWeightScreen = false })
        return
    }
    if (showStepScreen) {
        StepScreen(onBack = { showStepScreen = false })
        return
    }
    if (showMobilityScreen) {
        MobilityScreen(onBack = { showMobilityScreen = false })
        return
    }
    if (showBirthdayScreen) {
        BirthdayScreen(onBack = { showBirthdayScreen = false })
        return
    }
    if (showTravelScreen) {
        TravelScreen(onBack = { showTravelScreen = false })
        return
    }

    if (showTimerDialog) {
        TimerDialog(onDismiss = { showTimerDialog = false })
    }

    if (showAlarmDialog) {
        AlarmTimeDialog(onDismiss = { showAlarmDialog = false })
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
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = "📅",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showRoutine = true }
                        .padding(horizontal = 2.dp)
                )
                Text(
                    text = "📝",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showNotepad = true }
                        .padding(horizontal = 2.dp)
                )
                Text(
                    text = "🐶",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showLuna = true }
                        .padding(horizontal = 2.dp)
                )
                Text(
                    text = "📊",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showWeightScreen = true }
                        .padding(horizontal = 2.dp)
                )
                Text(
                    text = "🌙",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showPreSleep = true }
                        .padding(horizontal = 2.dp)
                )
                Text(
                    text = "⏱️",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showTimerDialog = true }
                        .padding(horizontal = 2.dp)
                )
                Text(
                    text = "🤸",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showMobilityScreen = true }
                        .padding(horizontal = 2.dp)
                )
                Text(
                    text = "🧳",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showTravelScreen = true }
                        .padding(horizontal = 2.dp)
                )
                Text(
                    text = "🎂",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showBirthdayScreen = true }
                        .padding(horizontal = 2.dp)
                )
                Text(
                    text = "🔔",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showAlarmDialog = true }
                        .padding(horizontal = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // weight(1f) so the quote takes the leftover space; the streak keeps its
                // own width and never wraps.
                Text(
                    text = "\"Stop Letting Comfort And Emotions Ruin Your Potential.\"",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
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
                    Text(
                        text = "$nofapStreak Days",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ConfidenceMeter(refreshKey = 0)
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
                    Spacer(modifier = Modifier.height(8.dp))
                    CcnaScheduleReference()
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF0d1f0d), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(14.dp)
                    ) {
                        Column {
                            Text("I am getting my CCNA so I can:", color = Color(0xFF4ade80), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                            Text("\u2022 Stop working two jobs", color = Color(0xFF86efac), fontSize = 13.sp)
                            Text("\u2022 Get my own place", color = Color(0xFF86efac), fontSize = 13.sp)
                            Text("\u2022 Leave Level 1 support behind", color = Color(0xFF86efac), fontSize = 13.sp)
                        }
                    }
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
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
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
// Reasons to pull up when the urge fires — reframes, not scolding.
data class UrgeReason(val headline: String, val body: String)

val URGE_REASONS = listOf(
    UrgeReason(
        "The urge is temporary — you're not.",
        "It peaks and passes within minutes if you don't feed it. Riding it out proves you're in control, not the impulse."
    ),
    UrgeReason(
        "It drains the drive you're building things with.",
        "You're stacking real projects — the homelab, the SYSTEM app, CCNA. That focus and momentum is the same energy. Redirecting it is fuel, not deprivation."
    ),
    UrgeReason(
        "Nothing changes for the better afterward.",
        "The problem you were avoiding is still there, plus the low, foggy, slightly-ashamed feeling that follows. You never once finish and think \"glad I did that.\""
    ),
    UrgeReason(
        "It reinforces the exact loop you're trying to break.",
        "Every time you give in, you teach your brain that discomfort = escape. Every time you don't, you weaken that wiring and get stronger."
    ),
    UrgeReason(
        "It never stops at one.",
        "Inviting it in once always turns into inviting it again — and it ends with $60 gone and nothing actually changed. You already know it's wrong, and you already know it costs you every time."
    ),
    UrgeReason(
        "You're training discipline, and it transfers.",
        "The person who can say no here is the same person who shows up for workouts, studies when tired, and follows through on the route. This is rep one."
    ),
    UrgeReason(
        "Future-you is watching.",
        "The version of you a month clean doesn't want you to reset the counter over five minutes of impulse. Don't rob him of the streak he earned."
    ),
    UrgeReason(
        "Act, don't wait.",
        "You already know the moves — get up, cold water, walk, push-ups, leave the room. The urge can't survive you standing up and changing your state."
    )
)

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
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
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
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
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
            item {
                Text(
                    text = "REASONS TO PULL UP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp)
                )
            }
            items(URGE_REASONS, key = { it.headline }) { reason ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = reason.headline,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDDDDDD),
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = reason.body,
                        fontSize = 13.sp,
                        color = Color(0xFF999999),
                        lineHeight = 19.sp
                    )
                }
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
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
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
        modifier = modifier.size(18.dp)
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

fun secsToMmss(s: Int): String { val m = s / 60; val sec = s % 60; return "%02d:%02d".format(m, sec) }
fun mmssToSecs(t: String): Int { val p = t.split(":"); return if (p.size == 2) (p[0].toIntOrNull() ?: 0) * 60 + (p[1].toIntOrNull() ?: 0) else t.toIntOrNull() ?: 0 }

@Composable
fun TimerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("timer_presets", android.content.Context.MODE_PRIVATE)
    // Two presets (editable). label -> default minutes.
    // label, default minutes, notification text when it finishes
    val presets = listOf(
        Triple("Vaping & Video Games", 30 * 60, "Time's up — done vaping & gaming"),
        Triple("Meditation", 2 * 60, "Meditation complete"),
        Triple("Legs Up The Wall", 5 * 60, "Legs up the wall done — next: read"),
        Triple("Read A Book", 20 * 60, "Reading done — lights out soon"),
        Triple("Wall Sit", 30, "Wall sit complete"),
        Triple("Foot Soak", 15 * 60, "Foot soak complete")
    )
    var selected by remember { mutableStateOf(0) }
    var seconds by remember { mutableStateOf(secsToMmss(presets[0].second)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a1a),
        title = { Text("Timer", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                presets.forEachIndexed { i, (label, def, _) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    selected = i
                                    seconds = secsToMmss(prefs.getInt("preset_$i", def))
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
                Text("MM:SS", fontSize = 11.sp, color = Color(0xFF777777))
                OutlinedTextField(
                    value = seconds,
                    onValueChange = { v -> seconds = v.filter { it.isDigit() || it == ':' }.take(5) },
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
                        val totalSecs = mmssToSecs(seconds)
                        if (totalSecs > 0) {
                            prefs.edit().putInt("preset_$selected", totalSecs).apply()
                            val preset = presets[selected]
                            TimerService.start(context, preset.first, totalSecs, preset.third)
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

data class CcnaSchedule(val items: List<String>)

suspend fun fetchCcnaSchedule(): CcnaSchedule? {
    return try {
        val body = fetchFromApi("/api/ccna-schedule")
        val o = Gson().fromJson(body, Map::class.java)
        val raw = o["items"] as? List<*> ?: emptyList<Any>()
        CcnaSchedule(items = raw.mapNotNull { it as? String })
    } catch (e: Exception) { null }
}

// Collapsible per-day CCNA study timeline. Hidden on days with no schedule (Fri/Sat).
@Composable
fun CcnaScheduleReference() {
    var expanded by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val fetched = kotlinx.coroutines.withContext(Dispatchers.IO) { fetchCcnaSchedule() }
        if (fetched != null) items = fetched.items
    }

    if (items.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF15151f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CCNA SCHEDULE",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF7B9CD8), letterSpacing = 2.sp
            )
            Text(text = if (expanded) "▲" else "▼", fontSize = 13.sp, color = Color(0xFF7B9CD8))
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            items.forEach { step ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "•", fontSize = 13.sp, color = Color(0xFF6A6A6A), modifier = Modifier.padding(end = 8.dp))
                    Text(text = step, fontSize = 13.sp, color = Color(0xFFBFCADF))
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
fun AlarmIconCanvas(modifier: Modifier = Modifier) {
    val gold = Color(0xFFFFD700)
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val cy = h * 0.52f
        val r = w * 0.34f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f)
        // Bell body arc
        drawArc(color = gold, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = stroke)
        // Bell sides
        drawLine(color = gold, start = androidx.compose.ui.geometry.Offset(cx - r, cy),
            end = androidx.compose.ui.geometry.Offset(cx - r, cy + r * 0.5f), strokeWidth = w * 0.09f)
        drawLine(color = gold, start = androidx.compose.ui.geometry.Offset(cx + r, cy),
            end = androidx.compose.ui.geometry.Offset(cx + r, cy + r * 0.5f), strokeWidth = w * 0.09f)
        // Bell base
        drawLine(color = gold, start = androidx.compose.ui.geometry.Offset(cx - r * 1.2f, cy + r * 0.5f),
            end = androidx.compose.ui.geometry.Offset(cx + r * 1.2f, cy + r * 0.5f), strokeWidth = w * 0.09f)
        // Clapper
        drawCircle(color = gold, radius = w * 0.07f,
            center = androidx.compose.ui.geometry.Offset(cx, cy + r * 0.75f))
        // Top stem
        drawLine(color = gold, start = androidx.compose.ui.geometry.Offset(cx, cy - r),
            end = androidx.compose.ui.geometry.Offset(cx, h * 0.06f), strokeWidth = w * 0.09f)
    }
}

@Composable
fun AlarmTimeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val morningPrefs = context.getSharedPreferences("morning_brief", android.content.Context.MODE_PRIVATE)
    val bedtimePrefs = context.getSharedPreferences("bedtime_alarm", android.content.Context.MODE_PRIVATE)
    var morningEnabled by remember { mutableStateOf(morningPrefs.getBoolean("alarm_enabled", true)) }
    var morningHour by remember { mutableIntStateOf(morningPrefs.getInt("alarm_hour", 5)) }
    var morningMinute by remember { mutableIntStateOf(morningPrefs.getInt("alarm_minute", 0)) }
    var bedtimeEnabled by remember { mutableStateOf(bedtimePrefs.getBoolean("alarm_enabled", true)) }
    var bedtimeHour by remember { mutableIntStateOf(bedtimePrefs.getInt("alarm_hour", 21)) }
    var bedtimeMinute by remember { mutableIntStateOf(bedtimePrefs.getInt("alarm_minute", 30)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        title = { Text("Alarms", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Morning Brief", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    androidx.compose.material3.Switch(checked = morningEnabled, onCheckedChange = { morningEnabled = it },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3b82f6), uncheckedTrackColor = Color(0xFF2a2a3a)))
                }
                if (morningEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = { morningHour = (morningHour + 1) % 24 }) { Text("\u25b2", color = Color(0xFF3b82f6), fontSize = 18.sp) }
                            Text("%02d".format(morningHour), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { morningHour = (morningHour + 23) % 24 }) { Text("\u25bc", color = Color(0xFF3b82f6), fontSize = 18.sp) }
                        }
                        Text(":", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = { morningMinute = (morningMinute + 1) % 60 }) { Text("\u25b2", color = Color(0xFF3b82f6), fontSize = 18.sp) }
                            Text("%02d".format(morningMinute), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { morningMinute = (morningMinute + 59) % 60 }) { Text("\u25bc", color = Color(0xFF3b82f6), fontSize = 18.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.HorizontalDivider(color = Color(0xFF2a2a3a))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Bedtime", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    androidx.compose.material3.Switch(checked = bedtimeEnabled, onCheckedChange = { bedtimeEnabled = it },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3b82f6), uncheckedTrackColor = Color(0xFF2a2a3a)))
                }
                if (bedtimeEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = { bedtimeHour = (bedtimeHour + 1) % 24 }) { Text("\u25b2", color = Color(0xFF3b82f6), fontSize = 18.sp) }
                            Text("%02d".format(bedtimeHour), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { bedtimeHour = (bedtimeHour + 23) % 24 }) { Text("\u25bc", color = Color(0xFF3b82f6), fontSize = 18.sp) }
                        }
                        Text(":", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = { bedtimeMinute = (bedtimeMinute + 1) % 60 }) { Text("\u25b2", color = Color(0xFF3b82f6), fontSize = 18.sp) }
                            Text("%02d".format(bedtimeMinute), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { bedtimeMinute = (bedtimeMinute + 59) % 60 }) { Text("\u25bc", color = Color(0xFF3b82f6), fontSize = 18.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    morningPrefs.edit().putInt("alarm_hour", morningHour).putInt("alarm_minute", morningMinute).putBoolean("alarm_enabled", morningEnabled).apply()
                    if (morningEnabled) MorningBrief.schedule(context, morningHour, morningMinute) else MorningBrief.cancel(context)
                    bedtimePrefs.edit().putInt("alarm_hour", bedtimeHour).putInt("alarm_minute", bedtimeMinute).putBoolean("alarm_enabled", bedtimeEnabled).apply()
                    if (bedtimeEnabled) BedtimeAlarm.schedule(context, bedtimeHour, bedtimeMinute) else BedtimeAlarm.cancel(context)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3b82f6))
            ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94a3b8)) }
        }
    )
}

@Composable
fun MorningBriefOverlay(onDismiss: () -> Unit) {
    var tasks by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val playerResponse = fetchFromApi("/api/player")
                val playerData = com.google.gson.Gson().fromJson(playerResponse, Map::class.java)
                streak = (playerData["nofapStreak"] as? Number)?.toInt() ?: 0
            } catch (e: Exception) {}
            try {
                loadAllQuests(
                    onQuestsLoaded = { daily, _, _, _, _ -> tasks = daily },
                    onError = {}
                )
            } catch (e: Exception) {}
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Text("🔥 $streak day streak", fontSize = 15.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            val streakMsg = when {
                streak == 0 -> "Today is day one. You don\u2019t need a perfect streak \u2014 you just need today. One day without. That\u2019s it."
                streak == 1 -> "One day in. The hardest part is behind you. Keep it moving."
                streak < 7 -> "Day $streak. You\u2019re building something real. Don\u2019t give it up for five minutes of nothing."
                streak < 14 -> "$streak days. A week-plus of discipline. You\u2019re rewiring your brain \u2014 that\u2019s not small."
                streak < 30 -> "$streak days strong. The urges are quieter now. That\u2019s you taking your power back."
                streak < 60 -> "$streak days. A month of clarity. The version of you from day 0 would be proud."
                streak < 90 -> "$streak days. Two months in. You\u2019re not the same person who started this."
                else -> "$streak days. This is who you are now. Protect it."
            }
            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a0a), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(14.dp)) {
                Text(streakMsg, color = Color(0xFFF59E0B), fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Box(modifier = Modifier.width(40.dp).height(3.dp).background(Color(0xFF3b82f6),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Break the pattern today\nor the loop will repeat tomorrow.",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
                textAlign = TextAlign.Center, lineHeight = 32.sp
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF0d1f0d), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(14.dp)
            ) {
                Column {
                    Text("I am getting my CCNA so I can:", color = Color(0xFF4ade80), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    Text("\u2022 Stop working two jobs", color = Color(0xFF86efac), fontSize = 13.sp)
                    Text("\u2022 Get my own place", color = Color(0xFF86efac), fontSize = 13.sp)
                    Text("\u2022 Leave Level 1 support behind", color = Color(0xFF86efac), fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(44.dp))
            if (tasks.isNotEmpty()) {
                Text("TODAY'S TASKS", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF94a3b8), letterSpacing = 2.sp)
                Spacer(Modifier.height(14.dp))
                tasks.filter { !it.optional }.forEach { task ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF3b82f6),
                            shape = androidx.compose.foundation.shape.CircleShape))
                        Text(task.title, fontSize = 15.sp, color = Color(0xFFe2e8f0))
                    }
                }
            }
            Spacer(Modifier.height(52.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3b82f6)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                Text("Let's go →", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}


@Composable
fun EveningBriefOverlay(onDismiss: () -> Unit) {
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a1a)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Good Night", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Wind down. Same sequence every night.", fontSize = 14.sp, color = Color(0xFF94a3b8), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            PRE_SLEEP_STEPS.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Color(0xFF111827), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(14.dp)
                ) {
                    Text("${index + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC), modifier = Modifier.padding(end = 12.dp))
                    Column {
                        Text(step.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDDDDDD), lineHeight = 21.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(step.detail, fontSize = 13.sp, color = Color(0xFF999999), lineHeight = 19.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA5B4FC)),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Dismiss", color = Color(0xFF0a0a1a), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
fun getMorningBriefPrefs(context: android.content.Context) =
    context.getSharedPreferences("morning_brief", android.content.Context.MODE_PRIVATE)

fun hasDismissedToday(context: android.content.Context): Boolean {
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    return getMorningBriefPrefs(context).getString("dismissed_date", "") == today
}

fun markDismissedToday(context: android.content.Context) {
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    getMorningBriefPrefs(context).edit().putString("dismissed_date", today).apply()
}

@Composable
fun TravelIconCanvas(modifier: Modifier = Modifier) {
    val color = Color(0xFF60a5fa)
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(w*0.1f, h*0.3f), size = androidx.compose.ui.geometry.Size(w*0.8f, h*0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(w*0.32f, h*0.12f), size = androidx.compose.ui.geometry.Size(w*0.36f, h*0.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w*0.1f, h*0.6f), end = androidx.compose.ui.geometry.Offset(w*0.9f, h*0.6f), strokeWidth = 1.5.dp.toPx())
    }
}

data class TravelItem(val id: Int, val category: String, val name: String, val checked: Boolean, val sortOrder: Int)
data class TravelTrip(val id: Int, val travelDate: String)

fun fetchTravelTrip(): TravelTrip? {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/travel/trip")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        if (text == "null") return null
        val o = org.json.JSONObject(text)
        TravelTrip(o.getInt("id"), o.getString("travel_date"))
    } catch (e: Exception) { null }
}

fun fetchTravelItems(): List<TravelItem> {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/travel/items")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = org.json.JSONArray(text)
        (0 until arr.length()).map { val o = arr.getJSONObject(it); TravelItem(o.getInt("id"), o.getString("category"), o.getString("name"), o.getInt("checked") == 1, o.getInt("sort_order")) }
    } catch (e: Exception) { emptyList() }
}

fun patchTravelItem(id: Int, checked: Boolean): Boolean {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/travel/items/$id")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "PATCH"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write("{\"checked\":${if (checked) 1 else 0}}".toByteArray()) }
        conn.inputStream.bufferedReader().readText(); conn.disconnect(); true
    } catch (e: Exception) { false }
}

fun postTravelTrip(date: String): Boolean {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/travel/trip")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write("{\"travel_date\":\"$date\"}".toByteArray()) }
        conn.inputStream.bufferedReader().readText(); conn.disconnect(); true
    } catch (e: Exception) { false }
}

fun postTravelItem(category: String, name: String): Boolean {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/travel/items")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
        val body = org.json.JSONObject().apply { put("category", category); put("name", name) }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.inputStream.bufferedReader().readText(); conn.disconnect(); true
    } catch (e: Exception) { false }
}

fun deleteTravelItem(id: Int): Boolean {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/travel/items/$id")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "DELETE"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.inputStream.bufferedReader().readText(); conn.disconnect(); true
    } catch (e: Exception) { false }
}

fun checkTravelReset(): Boolean {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/travel/reset-if-due")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write("{}".toByteArray()) }
        val text = conn.inputStream.bufferedReader().readText(); conn.disconnect()
        org.json.JSONObject(text).optBoolean("reset", false)
    } catch (e: Exception) { false }
}

val TRAVEL_CATEGORY_ORDER = listOf("Toiletries", "Electronics", "Supplements", "Clothing", "Drugs")

@Composable
fun TravelScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var items by remember { mutableStateOf<List<TravelItem>>(emptyList()) }
    var trip by remember { mutableStateOf<TravelTrip?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showDateDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var dateInput by remember { mutableStateOf("") }
    var addCategory by remember { mutableStateOf("Toiletries") }
    var addName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch(Dispatchers.IO) {
            checkTravelReset()
            val t = fetchTravelTrip()
            val i = fetchTravelItems()
            trip = t; items = i; loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    val grouped = TRAVEL_CATEGORY_ORDER.mapNotNull { cat ->
        val catItems = items.filter { it.category == cat }
        if (catItems.isNotEmpty()) cat to catItems else null
    }
    val totalItems = items.size
    val checkedItems = items.count { it.checked }

    if (showDateDialog) {
        AlertDialog(
            onDismissRequest = { showDateDialog = false },
            containerColor = Color(0xFF1a1a2e),
            title = { Text("Set Travel Date", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your travel date (YYYY-MM-DD)", color = Color(0xFF94a3b8), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = dateInput, onValueChange = { dateInput = it }, singleLine = true,
                        label = { Text("e.g. 2026-10-15") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF60a5fa), unfocusedBorderColor = Color(0xFF2a2a3a), focusedLabelColor = Color(0xFF60a5fa), unfocusedLabelColor = Color(0xFF94a3b8)),
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (dateInput.isBlank()) return@Button
                    saving = true
                    scope.launch(Dispatchers.IO) {
                        postTravelTrip(dateInput.trim())
                        saving = false; showDateDialog = false; dateInput = ""; reload()
                    }
                }, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))) {
                    Text(if (saving) "Saving..." else "Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDateDialog = false; dateInput = "" }) { Text("Cancel", color = Color(0xFF94a3b8)) } }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Color(0xFF1a1a2e),
            title = { Text("Add Item", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Category", color = Color(0xFF94a3b8), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        TRAVEL_CATEGORY_ORDER.forEach { cat ->
                            val sel = addCategory == cat
                            Box(modifier = Modifier.background(if (sel) Color(0xFF60a5fa) else Color(0xFF1e1e3a), shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable { addCategory = cat }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(cat, color = if (sel) Color.White else Color(0xFF94a3b8), fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = addName, onValueChange = { addName = it }, singleLine = true,
                        label = { Text("Item name") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF60a5fa), unfocusedBorderColor = Color(0xFF2a2a3a), focusedLabelColor = Color(0xFF60a5fa), unfocusedLabelColor = Color(0xFF94a3b8)),
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (addName.isBlank()) return@Button
                    saving = true
                    scope.launch(Dispatchers.IO) {
                        postTravelItem(addCategory, addName.trim())
                        saving = false; showAddDialog = false; addName = ""; reload()
                    }
                }, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))) {
                    Text(if (saving) "Saving..." else "Add", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false; addName = "" }) { Text("Cancel", color = Color(0xFF94a3b8)) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a1a))) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190 Back", color = Color(0xFF60a5fa), fontSize = 14.sp) }
            Spacer(Modifier.weight(1f))
            Text("Travel Checklist", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showAddDialog = true }) { Text("+ Item", color = Color(0xFF60a5fa), fontSize = 13.sp) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(Color(0xFF111827), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (trip != null) "Travel Date: ${trip!!.travelDate}" else "No travel date set", color = if (trip != null) Color(0xFF60a5fa) else Color(0xFF94a3b8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (totalItems > 0) Text("$checkedItems / $totalItems packed", color = Color(0xFF94a3b8), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            TextButton(onClick = { showDateDialog = true }) { Text(if (trip != null) "Change" else "Set Date", color = Color(0xFF60a5fa), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        if (totalItems > 0) {
            LinearProgressIndicator(progress = { if (totalItems > 0) checkedItems.toFloat() / totalItems else 0f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(4.dp), color = Color(0xFF60a5fa), trackColor = Color(0xFF1a1a2e))
        }
        Spacer(Modifier.height(8.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF60a5fa)) }
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                grouped.forEach { (cat, catItems) ->
                    Text(cat.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), letterSpacing = 2.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    catItems.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).background(Color(0xFF111827), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).clickable {
                            val newChecked = !item.checked
                            items = items.map { if (it.id == item.id) it.copy(checked = newChecked) else it }
                            scope.launch(Dispatchers.IO) { patchTravelItem(item.id, newChecked) }
                        }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(20.dp).background(if (item.checked) Color(0xFF60a5fa) else Color(0xFF3a3a5a), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(2.dp).background(if (item.checked) Color(0xFF60a5fa) else Color(0xFF0a0a1a), androidx.compose.foundation.shape.RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                                if (item.checked) Text("\u2713", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(item.name, color = if (item.checked) Color(0xFF555577) else Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f),
                                style = if (item.checked) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun BirthdayIconCanvas(modifier: Modifier = Modifier) {
    val pink = Color(0xFFf472b6)
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        // Cake base
        drawRect(color = pink, topLeft = androidx.compose.ui.geometry.Offset(w*0.1f, h*0.55f), size = androidx.compose.ui.geometry.Size(w*0.8f, h*0.38f))
        // Cake middle layer
        drawRect(color = pink.copy(alpha = 0.7f), topLeft = androidx.compose.ui.geometry.Offset(w*0.18f, h*0.38f), size = androidx.compose.ui.geometry.Size(w*0.64f, h*0.2f))
        // Candle
        drawRect(color = Color(0xFFfde68a), topLeft = androidx.compose.ui.geometry.Offset(w*0.45f, h*0.18f), size = androidx.compose.ui.geometry.Size(w*0.1f, h*0.22f))
        // Flame
        drawOval(color = Color(0xFFfb923c), topLeft = androidx.compose.ui.geometry.Offset(w*0.43f, h*0.08f), size = androidx.compose.ui.geometry.Size(w*0.14f, h*0.14f))
    }
}

data class Birthday(val id: Int, val name: String, val birthday: String, val birthYear: Int? = null)

fun fetchBirthdays(): List<Birthday> {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/birthdays")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = org.json.JSONArray(text)
        (0 until arr.length()).map { val o = arr.getJSONObject(it); Birthday(o.getInt("id"), o.getString("name"), o.getString("birthday")) }
    } catch (e: Exception) { emptyList() }
}

fun postBirthday(name: String, birthday: String): Boolean {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/birthdays")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write("{\"name\":\"$name\",\"birthday\":\"$birthday\"}".toByteArray()) }
        conn.inputStream.bufferedReader().readText(); conn.disconnect(); true
    } catch (e: Exception) { false }
}

fun deleteBirthday(id: Int): Boolean {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/birthdays/$id")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "DELETE"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.inputStream.bufferedReader().readText(); conn.disconnect(); true
    } catch (e: Exception) { false }
}

fun daysUntilBirthday(birthday: String): Int {
    return try {
        val parts = birthday.split("-")
        val bMonth = parts[0].toInt(); val bDay = parts[1].toInt()
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        val todayInt = sdf.format(java.util.Date()).toInt()
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val thisYear = "%04d%02d%02d".format(year, bMonth, bDay).toInt()
        val nextYear = "%04d%02d%02d".format(year + 1, bMonth, bDay).toInt()
        val targetInt = if (thisYear > todayInt) thisYear else nextYear
        val t = sdf.parse(todayInt.toString())!!
        val n = sdf.parse(targetInt.toString())!!
        ((n.time - t.time) / (1000 * 60 * 60 * 24)).toInt()
    } catch (e: Exception) { 999 }
}

fun birthdayMonthDay(birthday: String): String {
    return try {
        val parts = birthday.split("-")
        val month = parts[0].toInt(); val day = parts[1].toInt()
        val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        "${months[month-1]} $day"
    } catch (e: Exception) { birthday }
}

@Composable
fun BirthdayScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var birthdays by remember { mutableStateOf<List<Birthday>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var monthInput by remember { mutableStateOf("") }
    var dayInput by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun reload() { scope.launch(Dispatchers.IO) { val r = fetchBirthdays().sortedBy { daysUntilBirthday(it.birthday) }; birthdays = r; loading = false } }
    LaunchedEffect(Unit) { reload() }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Color(0xFF1a1a2e),
            title = { Text("Add Birthday", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it },
                        label = { Text("Name") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFf472b6), unfocusedBorderColor = Color(0xFF2a2a3a), focusedLabelColor = Color(0xFFf472b6), unfocusedLabelColor = Color(0xFF94a3b8)),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = monthInput, onValueChange = { monthInput = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Month") }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFf472b6), unfocusedBorderColor = Color(0xFF2a2a3a), focusedLabelColor = Color(0xFFf472b6), unfocusedLabelColor = Color(0xFF94a3b8)),
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(value = dayInput, onValueChange = { dayInput = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Day") }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFf472b6), unfocusedBorderColor = Color(0xFF2a2a3a), focusedLabelColor = Color(0xFFf472b6), unfocusedLabelColor = Color(0xFF94a3b8)),
                            modifier = Modifier.weight(1f))
                    }
                    if (errorMsg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(errorMsg, color = Color(0xFFf87171), fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val m = monthInput.toIntOrNull(); val d = dayInput.toIntOrNull()
                    if (nameInput.isBlank()) { errorMsg = "Enter a name"; return@Button }
                    if (m == null || m < 1 || m > 12) { errorMsg = "Enter a valid month (1-12)"; return@Button }
                    if (d == null || d < 1 || d > 31) { errorMsg = "Enter a valid day"; return@Button }
                    saving = true
                    val bday = "%02d-%02d".format(m, d)
                    scope.launch(Dispatchers.IO) {
                        val ok = postBirthday(nameInput.trim(), bday)
                        if (ok) { showAddDialog = false; nameInput = ""; monthInput = ""; dayInput = ""; errorMsg = ""; reload() } else { errorMsg = "Failed to save" }
                        saving = false
                    }
                }, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf472b6))) {
                    Text(if (saving) "Saving..." else "Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false; nameInput = ""; monthInput = ""; dayInput = ""; errorMsg = "" }) { Text("Cancel", color = Color(0xFF94a3b8)) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a1a))) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190 Back", color = Color(0xFFf472b6), fontSize = 14.sp) }
            Spacer(Modifier.weight(1f))
            Text("Birthdays", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(onClick = { showAddDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf472b6)), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Text("+ Add", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = Color(0xFFf472b6)) }
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("UPCOMING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94a3b8), letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                if (birthdays.isEmpty()) {
                    Text("No birthdays yet. Tap + Add to get started.", color = Color(0xFF94a3b8), fontSize = 14.sp)
                } else {
                    val monthNames = listOf("January","February","March","April","May","June","July","August","September","October","November","December")
                    val grouped = birthdays.groupBy { it.birthday.split("-")[0].toIntOrNull() ?: 0 }
                    val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
                    val sortedMonths = ((currentMonth..12) + (1 until currentMonth)).filter { grouped.containsKey(it) }
                    sortedMonths.forEach { monthNum ->
                        val monthBirthdays = grouped[monthNum]?.sortedBy { it.birthday.split("-")[1].toIntOrNull() ?: 0 } ?: emptyList()
                        Text(monthNames.getOrElse(monthNum - 1) { "Unknown" }.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFf472b6), letterSpacing = 2.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                        monthBirthdays.forEach { b ->
                            val days = daysUntilBirthday(b.birthday)
                            val isClose = days <= 7
                            val isSoon = days <= 30
                            val accentColor = if (isClose) Color(0xFFf87171) else if (isSoon) Color(0xFFfbbf24) else Color(0xFFf472b6)
                            val daysLabel = when (days) { 0 -> "Today!"; 1 -> "Tomorrow!"; else -> "In $days days" }
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Color(0xFF111827), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(b.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(birthdayMonthDay(b.birthday), color = Color(0xFF94a3b8), fontSize = 13.sp)
                                }
                                Text(daysLabel, color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}


@Composable
fun MobilityIconCanvas(modifier: Modifier = Modifier) {
    val color = Color(0xFFa78bfa)
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        drawCircle(color = color, radius = w * 0.12f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.1f))
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.22f), end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.55f), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.32f), end = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.48f), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.32f), end = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.48f), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.55f), end = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.85f), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.55f), end = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.85f), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.85f), end = androidx.compose.ui.geometry.Offset(w * 0.05f, h * 0.75f), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.85f), end = androidx.compose.ui.geometry.Offset(w * 0.95f, h * 0.75f), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

data class MobilityExercise(
    val title: String,
    val meta: String,
    val description: String,
    val timerSecs: Int?,
    val reps: Int?
)

val MOBILITY_EXERCISES = listOf(
    MobilityExercise(
        title = "Assisted Deep Squat Hold",
        meta = "2 sets \u00b7 30 sec each",
        description = "Hold a door frame. Lower into deepest squat with heels flat. Let hips sink, chest up. Breathe and relax into it.",
        timerSecs = 30,
        reps = null
    ),
    MobilityExercise(
        title = "Ankle Dorsiflexion Stretch",
        meta = "60 sec \u00b7 each side",
        description = "Kneel facing a wall, foot ~4 inches away. Drive knee forward without lifting heel. Feel stretch in calf and Achilles.",
        timerSecs = 60,
        reps = null
    ),
    MobilityExercise(
        title = "Hip Flexor Stretch",
        meta = "30 sec \u00b7 each side",
        description = "One knee on floor, other foot forward. Shift hips forward until stretch at front of back hip. Keep torso upright.",
        timerSecs = 30,
        reps = null
    ),
    MobilityExercise(
        title = "Deep Squat Ankle Rocks",
        meta = "10 reps",
        description = "From assisted squat position, slowly rock forward and back. Let ankles flex as much as possible on forward rock.",
        timerSecs = null,
        reps = 10
    )
)

@Composable
fun MobilityScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val completed = remember { mutableStateListOf(false, false, false, false) }
    val repCount = remember { mutableStateOf(10) }
    val doneCount = completed.count { it }
    val progress = doneCount.toFloat() / MOBILITY_EXERCISES.size

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a1a))) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190 Back", color = Color(0xFFa78bfa), fontSize = 14.sp) }
            Spacer(Modifier.weight(1f))
            Text("Daily Mobility", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        Text("Every night before bed \u00b7 ~5 min \u00b7 Deep squat mobility", color = Color(0xFF7a8090), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp), color = Color(0xFFa78bfa), trackColor = Color(0xFF1a1a2e))
        Spacer(Modifier.height(14.dp))
        Text("EXERCISES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7a8090), letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            MOBILITY_EXERCISES.forEachIndexed { i, ex ->
                val isDone = completed[i]
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).background(Color(0xFF111827), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(ex.meta, color = Color(0xFF7a8090), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            Box(
                                modifier = Modifier.size(28.dp).background(if (isDone) Color(0xFF10b981) else Color(0xFF2a2a3a), androidx.compose.foundation.shape.CircleShape).padding(2.dp).background(if (isDone) Color(0xFF10b981) else Color(0xFF0a0a1a), androidx.compose.foundation.shape.CircleShape).clickable { completed[i] = !isDone },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDone) Text("\u2713", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                            Text(ex.description, color = Color(0xFF8ca3c8), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
                            if (ex.timerSecs != null) {
                                val def = ex.timerSecs
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { TimerService.start(context, ex.title, def, "${ex.title} complete") },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1e1e3a)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3a3a5a)),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("\u25b6 Start ${secsToMmss(def)}", color = Color(0xFFa78bfa), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                    Box(modifier = Modifier.background(Color(0xFF0d1120), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                                        Text(secsToMmss(def), color = Color(0xFFa78bfa), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                }
                            }
                            if (ex.reps != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.material3.IconButton(onClick = { if (repCount.value > 0) repCount.value-- }, modifier = Modifier.size(36.dp).background(Color(0xFF1e1e3a), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))) {
                                        Text("\u2212", color = Color(0xFFa78bfa), fontSize = 20.sp)
                                    }
                                    Text("${repCount.value}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Text("/ ${ex.reps} reps", color = Color(0xFF7a8090), fontSize = 13.sp)
                                    Spacer(Modifier.weight(1f))
                                    androidx.compose.material3.IconButton(onClick = { repCount.value++ }, modifier = Modifier.size(36.dp).background(Color(0xFF1e1e3a), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))) {
                                        Text("+", color = Color(0xFFa78bfa), fontSize = 20.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (doneCount == MOBILITY_EXERCISES.size) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF14261a), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("Mobility routine complete! Great work.", color = Color(0xFF8fd6a8), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}


@Composable
fun StepIconCanvas(modifier: Modifier = Modifier) {
    val color = Color(0xFF10b981)
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        drawOval(color = color, topLeft = androidx.compose.ui.geometry.Offset(w*0.08f, h*0.45f), size = androidx.compose.ui.geometry.Size(w*0.32f, h*0.42f))
        drawOval(color = color, topLeft = androidx.compose.ui.geometry.Offset(w*0.08f, h*0.28f), size = androidx.compose.ui.geometry.Size(w*0.13f, h*0.2f))
        drawOval(color = color, topLeft = androidx.compose.ui.geometry.Offset(w*0.22f, h*0.22f), size = androidx.compose.ui.geometry.Size(w*0.11f, h*0.18f))
        drawOval(color = color, topLeft = androidx.compose.ui.geometry.Offset(w*0.33f, h*0.25f), size = androidx.compose.ui.geometry.Size(w*0.10f, h*0.16f))
        drawOval(color = color, topLeft = androidx.compose.ui.geometry.Offset(w*0.58f, h*0.13f), size = androidx.compose.ui.geometry.Size(w*0.32f, h*0.42f))
        drawOval(color = color, topLeft = androidx.compose.ui.geometry.Offset(w*0.58f, h*0.0f), size = androidx.compose.ui.geometry.Size(w*0.13f, h*0.2f))
        drawOval(color = color, topLeft = androidx.compose.ui.geometry.Offset(w*0.71f, h*0.0f), size = androidx.compose.ui.geometry.Size(w*0.11f, h*0.18f))
        drawOval(color = color, topLeft = androidx.compose.ui.geometry.Offset(w*0.82f, h*0.03f), size = androidx.compose.ui.geometry.Size(w*0.10f, h*0.16f))
    }
}

data class StepEntry(val id: Int, val date: String, val stepCount: Int)

fun fetchStepLog(): List<StepEntry> {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/step-log")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = org.json.JSONObject(text).optJSONArray("entries") ?: return emptyList()
        (0 until arr.length()).map { val o = arr.getJSONObject(it); StepEntry(o.getInt("id"), o.getString("logged_date"), o.getInt("step_count")) }
    } catch (e: Exception) { emptyList() }
}

fun postStepEntry(date: String, stepCount: Int): Boolean {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/step-log")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write("{\"logged_date\":\"$date\",\"step_count\":$stepCount}".toByteArray()) }
        conn.inputStream.bufferedReader().readText(); conn.disconnect(); true
    } catch (e: Exception) { false }
}

fun deleteStepEntry(id: Int): Boolean {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/step-log/$id")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "DELETE"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.inputStream.bufferedReader().readText(); conn.disconnect(); true
    } catch (e: Exception) { false }
}

@Composable
fun StepScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val goalSteps = 10000
    var entries by remember { mutableStateOf<List<StepEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showLogDialog by remember { mutableStateOf(false) }
    var stepInput by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun reload() { scope.launch(Dispatchers.IO) { val r = fetchStepLog(); entries = r; loading = false } }
    LaunchedEffect(Unit) { reload() }

    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            containerColor = Color(0xFF1a1a2e),
            title = { Text("Log Steps", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter today's step count", color = Color(0xFF94a3b8), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = stepInput, onValueChange = { stepInput = it },
                        label = { Text("Steps") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF10b981), unfocusedBorderColor = Color(0xFF2a2a3a), focusedLabelColor = Color(0xFF10b981), unfocusedLabelColor = Color(0xFF94a3b8)))
                    if (errorMsg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(errorMsg, color = Color(0xFFf87171), fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val s = stepInput.trim().toIntOrNull()
                    if (s == null || s < 0) { errorMsg = "Enter a valid step count"; return@Button }
                    saving = true
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    scope.launch(Dispatchers.IO) {
                        val ok = postStepEntry(today, s)
                        if (ok) { showLogDialog = false; stepInput = ""; errorMsg = ""; reload() } else { errorMsg = "Failed to save" }
                        saving = false
                    }
                }, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))) {
                    Text(if (saving) "Saving..." else "Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showLogDialog = false; stepInput = ""; errorMsg = "" }) { Text("Cancel", color = Color(0xFF94a3b8)) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a1a))) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190 Back", color = Color(0xFF10b981), fontSize = 14.sp) }
            Spacer(Modifier.weight(1f))
            Text("Step Log", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(onClick = { showLogDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981)), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Text("+ Log", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF10b981)) }
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                val todaySteps = entries.lastOrNull { it.date == today }?.stepCount ?: 0
                val progress = (todaySteps.toFloat() / goalSteps).coerceIn(0f, 1f)
                Text("TODAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94a3b8), letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF111827), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(16.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${"%,d".format(todaySteps)} steps", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("/ ${"%,d".format(goalSteps)}", color = Color(0xFF94a3b8), fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = if (progress >= 1f) Color(0xFF10b981) else Color(0xFF3b82f6), trackColor = Color(0xFF1e293b))
                        Spacer(Modifier.height(4.dp))
                        Text(if (todaySteps >= goalSteps) "Goal reached!" else "${"%,d".format(goalSteps - todaySteps)} steps to go", color = if (todaySteps >= goalSteps) Color(0xFF10b981) else Color(0xFF94a3b8), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (entries.size >= 2) {
                    Text("HISTORY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94a3b8), letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))
                    val maxS = entries.maxOf { it.stepCount }.coerceAtLeast(goalSteps)
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF111827), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(16.dp)) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val goalY = size.height - (goalSteps.toFloat() / maxS * size.height)
                            drawLine(color = Color(0xFF10b981).copy(alpha = 0.3f), start = androidx.compose.ui.geometry.Offset(0f, goalY), end = androidx.compose.ui.geometry.Offset(size.width, goalY), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                            val pts = entries.mapIndexed { i, e ->
                                val x = if (entries.size == 1) size.width / 2f else i.toFloat() / (entries.size - 1) * size.width
                                val y = size.height - (e.stepCount.toFloat() / maxS * size.height * 0.9f + size.height * 0.05f)
                                androidx.compose.ui.geometry.Offset(x, y)
                            }
                            for (i in 0 until pts.size - 1) { drawLine(color = Color(0xFF3b82f6), start = pts[i], end = pts[i+1], strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round) }
                            pts.forEachIndexed { i, pt -> drawCircle(color = if (entries[i].stepCount >= goalSteps) Color(0xFF10b981) else Color(0xFF60a5fa), radius = 4.dp.toPx(), center = pt) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entries.first().date, color = Color(0xFF94a3b8), fontSize = 11.sp)
                        Text("\u25A0 goal line", color = Color(0xFF10b981).copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(entries.last().date, color = Color(0xFF94a3b8), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Text("LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94a3b8), letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                if (entries.isEmpty()) {
                    Text("No entries yet. Tap + Log to record your steps.", color = Color(0xFF94a3b8), fontSize = 14.sp)
                } else {
                    entries.reversed().forEach { entry ->
                        val met = entry.stepCount >= goalSteps
                        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF111827), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.date, color = Color(0xFF94a3b8), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("%,d".format(entry.stepCount), color = if (met) Color(0xFF10b981) else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (met) { Spacer(Modifier.width(4.dp)); Text("\u2713", color = Color(0xFF10b981), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = { scope.launch(Dispatchers.IO) { deleteStepEntry(entry.id); reload() } }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text("\u2715", color = Color(0xFFa55555), fontSize = 16.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}


@Composable
fun WeightIconCanvas(modifier: Modifier = Modifier) {
    val color = Color(0xFF60a5fa)
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.25f), end = androidx.compose.ui.geometry.Offset(w * 0.9f, h * 0.25f), strokeWidth = w * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.82f), end = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.82f), strokeWidth = w * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.25f), end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.09f)
        drawArc(color = color, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(w * 0.04f, h * 0.08f), size = androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.34f), style = stroke)
        drawArc(color = color, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.08f), size = androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.34f), style = stroke)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.23f, h * 0.25f), end = androidx.compose.ui.geometry.Offset(w * 0.23f, h * 0.08f), strokeWidth = w * 0.06f)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.23f, h * 0.25f), end = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.08f), strokeWidth = w * 0.06f)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.77f, h * 0.25f), end = androidx.compose.ui.geometry.Offset(w * 0.77f, h * 0.08f), strokeWidth = w * 0.06f)
        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(w * 0.77f, h * 0.25f), end = androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.08f), strokeWidth = w * 0.06f)
    }
}

data class WeightEntry(val id: Int, val date: String, val weightLbs: Float)

fun fetchWeightLog(): List<WeightEntry> {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/weight-log")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = org.json.JSONObject(text).optJSONArray("entries") ?: return emptyList()
        (0 until arr.length()).map { val o = arr.getJSONObject(it); WeightEntry(o.getInt("id"), o.getString("logged_date"), o.getDouble("weight_lbs").toFloat()) }
    } catch (e: Exception) { emptyList() }
}

fun postWeightEntry(date: String, weightLbs: Float): Boolean {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/weight-log")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write("""{"logged_date":"$date","weight_lbs":$weightLbs}""".toByteArray()) }
        conn.inputStream.bufferedReader().readText(); conn.disconnect(); true
    } catch (e: Exception) { false }
}

fun deleteWeightEntry(id: Int): Boolean {
    return try {
        val url = java.net.URL("https://mysololeveling.us/api/weight-log/$id")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "DELETE"; conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.inputStream.bufferedReader().readText(); conn.disconnect(); true
    } catch (e: Exception) { false }
}

@Composable
fun WeightScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var entries by remember { mutableStateOf<List<WeightEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showLogDialog by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun reload() { scope.launch(Dispatchers.IO) { val r = fetchWeightLog(); entries = r; loading = false } }
    LaunchedEffect(Unit) { reload() }

    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            containerColor = Color(0xFF1a1a2e),
            title = { Text("Log Weight", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your weight in lbs", color = Color(0xFF94a3b8), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = weightInput, onValueChange = { weightInput = it },
                        label = { Text("Weight (lbs)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF3b82f6), unfocusedBorderColor = Color(0xFF2a2a3a), focusedLabelColor = Color(0xFF3b82f6), unfocusedLabelColor = Color(0xFF94a3b8)))
                    if (errorMsg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(errorMsg, color = Color(0xFFf87171), fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val w = weightInput.trim().toFloatOrNull()
                    if (w == null || w <= 0) { errorMsg = "Enter a valid weight"; return@Button }
                    saving = true
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    scope.launch(Dispatchers.IO) {
                        val prevLast = entries.lastOrNull()?.weightLbs
                        val ok = postWeightEntry(today, w)
                        if (ok) {
                            if (prevLast != null && w < prevLast) {
                                kotlinx.coroutines.withContext(Dispatchers.IO) { logConfidenceWin("hunger") }
                            }
                            showLogDialog = false; weightInput = ""; errorMsg = ""; reload()
                        } else { errorMsg = "Failed to save" }
                        saving = false
                    }
                }, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3b82f6))) {
                    Text(if (saving) "Saving..." else "Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showLogDialog = false; weightInput = ""; errorMsg = "" }) { Text("Cancel", color = Color(0xFF94a3b8)) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a1a))) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF3b82f6), fontSize = 14.sp) }
            Spacer(Modifier.weight(1f))
            Text("Weight Log", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(onClick = { showLogDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3b82f6)), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Text("+ Log", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF3b82f6)) }
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (entries.size >= 2) {
                    Text("PROGRESS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94a3b8), letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))
                    val minW = entries.minOf { it.weightLbs }; val maxW = entries.maxOf { it.weightLbs }
                    val range = (maxW - minW).coerceAtLeast(5f)
                    val first = entries.first().weightLbs; val last = entries.last().weightLbs
                    val diff = kotlin.math.abs(last - first)
                    val trend = if (last < first) "↓ ${String.format("%.1f", diff)} lbs lost" else if (last > first) "↑ ${String.format("%.1f", diff)} lbs gained" else "→ No change"
                    val trendColor = if (last <= first) Color(0xFF10b981) else Color(0xFFf87171)
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF111827), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(16.dp)) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val pts = entries.mapIndexed { i, e ->
                                val x = if (entries.size == 1) size.width / 2f else i.toFloat() / (entries.size - 1) * size.width
                                val y = size.height - ((e.weightLbs - minW) / range * size.height * 0.8f + size.height * 0.1f)
                                androidx.compose.ui.geometry.Offset(x, y)
                            }
                            for (i in 0 until pts.size - 1) { drawLine(color = Color(0xFF3b82f6), start = pts[i], end = pts[i+1], strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round) }
                            pts.forEach { drawCircle(color = Color(0xFF60a5fa), radius = 4.dp.toPx(), center = it) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entries.first().date, color = Color(0xFF94a3b8), fontSize = 11.sp)
                        Text(trend, color = trendColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(entries.last().date, color = Color(0xFF94a3b8), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Text("LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94a3b8), letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                if (entries.isEmpty()) {
                    Text("No entries yet. Tap + Log to record your weight.", color = Color(0xFF94a3b8), fontSize = 14.sp)
                } else {
                    entries.reversed().forEach { entry ->
                        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF111827), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.date, color = Color(0xFF94a3b8), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("${entry.weightLbs} lbs", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = { scope.launch(Dispatchers.IO) { deleteWeightEntry(entry.id); reload() } }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text("✕", color = Color(0xFFa55555), fontSize = 16.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MainTabScreen() {
    var selectedTab by remember { mutableStateOf(TabType.QUESTS) }
    val context = LocalContext.current
    var showMorningBrief by remember { mutableStateOf(
        NavigationState.pendingNavigation.value == "morning_brief"
    ) }

    LaunchedEffect(Unit) {
        if (NavigationState.pendingNavigation.value == "morning_brief") {
            NavigationState.pendingNavigation.value = null
            showMorningBrief = true
        }
    }

    if (showMorningBrief) {
        MorningBriefOverlay(onDismiss = {
            markDismissedToday(context)
            showMorningBrief = false
        })
        return
    }

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
            "30g Member's Mark Natural Pecan Halves, toasted (chocolate) — 210 cal · 3g protein · 21g fat · 1g net carb",
            "1 grass-fed beef stick (28g / 1 oz) — 90 cal · 9g protein · 6g fat · 1g net carb"
        ),
        steps = listOf(
            MealStep("Toast the Pecans", "Air fry the 30g pecans at 300°F for 4–6 min, shaking the basket once, until fragrant. Tip them out onto a plate right away and, while still warm, toss with 1/2 tsp cocoa powder, 1/2–1 tsp Swerve, and a pinch of salt until coated."),
            MealStep("Serve", "Pair the chocolate pecans with the beef stick.")
        )
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

data class BlockRotation(
    val block: Int,
    val weekInBlock: Int,
    val dayInBlock: Int,
    val daysLeftInBlock: Int,
    val blockEnd: String
)

suspend fun fetchBlockRotation(): BlockRotation? {
    return try {
        val body = fetchFromApi("/api/gym/rotation")
        val o = Gson().fromJson(body, Map::class.java)
        val block = (o["block"] as? Number)?.toInt() ?: return null
        BlockRotation(
            block = block,
            weekInBlock = (o["weekInBlock"] as? Number)?.toInt() ?: 1,
            dayInBlock = (o["dayInBlock"] as? Number)?.toInt() ?: 1,
            daysLeftInBlock = (o["daysLeftInBlock"] as? Number)?.toInt() ?: 0,
            blockEnd = (o["blockEnd"] as? String) ?: ""
        )
    } catch (e: Exception) { null }
}

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
    val strengthPercentile: Int?,
    val suggestions: List<PlateauSuggestion> = emptyList(),
    val isBodyweight: Boolean = false,
    val stuckAtWeightLbs: Int = 0,
    val stuckAtReps: Int = 0,
    val prHeaviestLbs: Int = 0,
    val prBest1RMLbs: Int = 0,
    val prBestSetWeightLbs: Int = 0,
    val prBestSetReps: Int = 0,
    val prBestReps: Int = 0
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
    var showGlossary by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedExercise != null || showStandards || showGlossary || showPlan) {
        if (selectedExercise != null) selectedExercise = null
        else if (showGlossary) showGlossary = false
        else if (showPlan) showPlan = false
        else showStandards = false
    }

    when {
        selectedExercise != null -> GymDetailScreen(exercise = selectedExercise!!, onBack = { selectedExercise = null })
        showStandards -> GymStandardsScreen(onBack = { showStandards = false })
        showGlossary -> GymGlossaryScreen(onBack = { showGlossary = false })
        showPlan -> DeloadPlanScreen(onBack = { showPlan = false })
        else -> GymListScreen(
            onExerciseSelected = { selectedExercise = it },
            onViewStandards = { showStandards = true },
            onViewGlossary = { showGlossary = true },
            onViewPlan = { showPlan = true }
        )
    }
}

data class GlossaryEntry(val term: String, val body: String)

val GYM_GLOSSARY = listOf(
    GlossaryEntry(
        "1RM — one-rep max",
        "The heaviest weight you could lift for a single rep. You don't test it; it's estimated from your working sets with the Epley formula: weight × (1 + reps ÷ 30).\n\nSo 175 lbs × 6 reps ≈ 210 lbs estimated 1RM."
    ),
    GlossaryEntry(
        "Why estimate it at all?",
        "Your sets vary week to week — 175×6 one session, 180×5 the next, 170×8 after that. Raw weight alone can't tell you if that's progress. Converting each session to one comparable number lets a trend line show whether you're actually getting stronger.\n\nIt's an estimate, and it gets looser at high reps: a 15-rep set predicts your true max far less precisely than a 5-rep set. That's why isolation work looks noisier than heavy machine presses."
    ),
    GlossaryEntry(
        "Flat vs declining",
        "FLAT means the estimate isn't moving — a true plateau. Usually fixed by chasing one more rep at the same load before adding weight.\n\nDECLINING means it's going backward. That normally isn't lost strength; it's accumulated fatigue masking the strength you have. Grinding harder digs the hole deeper."
    ),
    GlossaryEntry(
        "Deload and rebuild",
        "The standard fix for a declining lift:\n\n1. Drop the working weight about 10%.\n2. Run 2–3 sessions at that lighter load, hitting all your reps cleanly with a rep or two left in the tank. It should feel easy — that's the point, not a failure.\n3. Add weight back, roughly 5 lbs per session, until you're at your old load.\n4. You usually pass the old sticking point instead of stalling at it again.\n\nThe lighter sessions let fatigue dissipate while fitness stays, so you come back able to lift more."
    ),
    GlossaryEntry(
        "Swap a variation instead",
        "The other option the alerts offer. A different movement gives the stalled pattern a break while still training the same muscle — useful when a specific machine or angle is the thing that's stuck."
    ),
    GlossaryEntry(
        "Missing the rep target",
        "Different problem from a plateau. If your top set is a rep or two short, hold the load and chase the extra rep. If it's 3+ reps short, the weight is simply wrong for that block's rep range — drop 20–30% and work back up once you're clearing the target."
    )
)

data class PlanSection(val heading: String, val bullets: List<String>)

val DELOAD_PLAN = listOf(
    PlanSection(
        "When to deload again",
        listOf(
            "Roughly every 6–8 weeks of hard training, or",
            "When several lifts stall at once + a suddenly-weak session shows up. That combo is a recovery signal, not weakness."
        )
    ),
    PlanSection(
        "Improving Push days",
        listOf(
            "Push sits the day after rest, so it's a fresh day — the bounce isn't from prior-session fatigue.",
            "Anchor progression to the first two lifts only: Shoulder Press + Chest Press. Beat the log by one rep each week.",
            "Pressdowns, lateral raises, push-ups are pump/accessory. Don't chase numbers there.",
            "Protect sleep before Push — delts and triceps are small and feel poor sleep first.",
            "The pre-bed routine helps most here: TV off 30 min early, legs up the wall, read."
        )
    )
)

@Composable
fun DeloadPlanScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0a0a0a))
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
            Text("Deload & Push", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(
            text = "Moving forward",
            fontSize = 13.sp,
            color = Color(0xFF888888),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(DELOAD_PLAN, key = { it.heading }) { section ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF12122a), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = section.heading,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                    Spacer(Modifier.height(6.dp))
                    section.bullets.forEach { b ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                text = "•",
                                fontSize = 13.sp,
                                color = Color(0xFF6A6A6A),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(text = b, fontSize = 13.sp, color = Color(0xFFCFCFCF), lineHeight = 19.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GymGlossaryScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0a0a0a))
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
            Text("Gym Terms", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(
            text = "What the plateau alerts mean",
            fontSize = 13.sp,
            color = Color(0xFF888888),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(GYM_GLOSSARY, key = { it.term }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF12122a), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = entry.term,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = entry.body,
                        fontSize = 13.sp,
                        color = Color(0xFFCFCFCF),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GymListScreen(onExerciseSelected: (GymExercise) -> Unit, onViewStandards: () -> Unit, onViewGlossary: () -> Unit, onViewPlan: () -> Unit) {
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
                            strengthPercentile = (ex["strength_percentile"] as? Number)?.toInt(),
                            suggestions = parseSuggestions(ex["suggestions"]),
                            isBodyweight = (ex["is_bodyweight"] as? Boolean) ?: false,
                            stuckAtWeightLbs = (ex["stuck_at_weight_lbs"] as? Number)?.toInt() ?: 0,
                            stuckAtReps = (ex["stuck_at_reps"] as? Number)?.toInt() ?: 0,
                            prHeaviestLbs = prInt(ex["prs"], "heaviest_weight_lbs"),
                            prBest1RMLbs = prInt(ex["prs"], "best_1rm_lbs"),
                            prBestSetWeightLbs = prInt(ex["prs"], "best_set_weight_lbs"),
                            prBestSetReps = prInt(ex["prs"], "best_set_reps"),
                            prBestReps = prInt(ex["prs"], "best_reps")
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

    var rotation by remember { mutableStateOf<BlockRotation?>(null) }
    LaunchedEffect(Unit) {
        rotation = try { fetchBlockRotation() } catch (e: Exception) { null }
    }

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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "❓ What is 1RM? What is a rebuild?",
                fontSize = 12.sp,
                color = Color(0xFF7B8CDE),
                modifier = Modifier.clickable { onViewGlossary() }
            )
            rotation?.let { rot ->
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111728), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "BLOCK ${rot.block} · WEEK ${rot.weekInBlock} OF 4",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700), letterSpacing = 1.sp
                        )
                        Text(
                            if (rot.daysLeftInBlock > 0) "${rot.daysLeftInBlock} days left" else "last day",
                            fontSize = 11.sp, color = Color(0xFF6b7689)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Four week segments; filled up to the current week.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        (1..4).forEach { w ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (w <= rot.weekInBlock) Color(0xFFFFD700)
                                        else Color(0xFF2a2a3a)
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Day ${rot.dayInBlock} of 28 · block ends ${rot.blockEnd}",
                        fontSize = 10.sp, color = Color(0xFF6b7689)
                    )
                }
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                item(key = "deload-plan") {
                    Text(
                        text = "📋  Deload & Push Plan",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF12122A), RoundedCornerShape(8.dp))
                            .clickable { onViewPlan() }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    )
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
                if (exercise.isBodyweight) "${exercise.bestReps} reps · ${exercise.sessionsAtCurrentWeight} since PR"
                else "${exercise.bestWeightLbs} lbs × ${exercise.bestReps} · ${exercise.sessionsAtCurrentWeight} since PR"
            else
                if (exercise.isBodyweight) "${exercise.bestReps} reps · ${exercise.sessionCount} sessions"
                else "${exercise.bestWeightLbs} lbs × ${exercise.bestReps} · ${exercise.sessionCount} sessions"
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

fun prInt(raw: Any?, key: String): Int {
    val m = raw as? Map<*, *> ?: return 0
    return (m[key] as? Number)?.toInt() ?: 0
}

fun parseSuggestions(raw: Any?): List<PlateauSuggestion> {
    val list = raw as? List<*> ?: return emptyList()
    return list.mapNotNull {
        if (it is Map<*, *>) PlateauSuggestion(
            id = (it["id"] as? Number)?.toInt() ?: return@mapNotNull null,
            exercise = null,
            muscleGroup = null,
            signal = (it["signal"] as? String) ?: "",
            severity = (it["severity"] as? String) ?: "medium",
            detail = (it["detail"] as? String) ?: "",
            fix = (it["fix"] as? String) ?: ""
        ) else null
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
                    if (exercise.isBodyweight) "${exercise.bestReps} reps · bodyweight"
                    else "${exercise.bestWeightLbs} lbs × ${exercise.bestReps} · Est. 1RM: ${exercise.estimated1RMLbs} lbs",
                    fontSize = 11.sp, color = Color(0xFF6b7689), modifier = Modifier.padding(top = 3.dp)
                )
            } else {
                Text("No data yet", fontSize = 11.sp, color = Color(0xFF6b7689), modifier = Modifier.padding(top = 3.dp))
            }
            exercise.suggestions.forEach { s ->
                val sev = if (s.severity == "high") Color(0xFFf87171) else Color(0xFFFFD700)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(Color(0x14FFFFFF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Text(s.detail, fontSize = 11.sp, color = sev, lineHeight = 15.sp)
                    Text(s.fix, fontSize = 11.sp, color = Color(0xFF9FBF9F), lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 3.dp))
                }
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
                    strengthPercentile = (it["strength_percentile"] as? Number)?.toInt(),
                    suggestions = parseSuggestions(it["suggestions"]),
                    isBodyweight = (it["is_bodyweight"] as? Boolean) ?: false,
                    stuckAtWeightLbs = (it["stuck_at_weight_lbs"] as? Number)?.toInt() ?: 0,
                    stuckAtReps = (it["stuck_at_reps"] as? Number)?.toInt() ?: 0,
                    prHeaviestLbs = prInt(it["prs"], "heaviest_weight_lbs"),
                    prBest1RMLbs = prInt(it["prs"], "best_1rm_lbs"),
                    prBestSetWeightLbs = prInt(it["prs"], "best_set_weight_lbs"),
                    prBestSetReps = prInt(it["prs"], "best_set_reps"),
                    prBestReps = prInt(it["prs"], "best_reps")
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
            // Pass the title so the backend can feed these sets into the PR tracker.
            val encoded = java.net.URLEncoder.encode(exercise.title, "UTF-8")
            val response = fetchFromApi("/api/gym/history/${exercise.exerciseTemplateId}?title=$encoded")
            val obj = Gson().fromJson(response, Map::class.java)
            val data = (obj["sessions"] as? List<*>) ?: emptyList<Any>()
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
                        if (exercise.isBodyweight)
                            "${exercise.sessionsAtCurrentWeight} sessions since PR (${exercise.stuckAtReps} reps)"
                        else
                            "${exercise.sessionsAtCurrentWeight} sessions since PR (${exercise.stuckAtWeightLbs} × ${exercise.stuckAtReps})",
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
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(
                    "PERSONAL RECORDS",
                    fontSize = 10.sp,
                    color = Color(0xFF6b7689),
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GymStatBox(
                        "Heaviest",
                        if (exercise.isBodyweight) "—" else "${exercise.prHeaviestLbs} lbs",
                        Modifier.weight(1f)
                    )
                    GymStatBox(
                        if (exercise.isBodyweight) "Best reps" else "Best 1RM",
                        if (exercise.isBodyweight) "${exercise.prBestReps}" else "${exercise.prBest1RMLbs} lbs",
                        Modifier.weight(1f), Color(0xFFFFD700)
                    )
                    GymStatBox(
                        "Best set vol",
                        if (exercise.isBodyweight) "—" else "${exercise.prBestSetWeightLbs} × ${exercise.prBestSetReps}",
                        Modifier.weight(1f)
                    )
                }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF111728), RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Column {
                        Text(if (exercise.isBodyweight) "Reps over time" else "Max weight over time", fontSize = 11.sp, color = Color(0xFF6b7689), letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (loading) {
                            Box(modifier = Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                            }
                        } else if (sessions.size >= 2) {
                            GymProgressChart(sessions, byReps = exercise.isBodyweight)
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
                        Text("Recent sessions · full history", fontSize = 11.sp, color = Color(0xFF6b7689), letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
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
fun GymProgressChart(sessions: List<ExerciseSession>, byReps: Boolean = false) {
    val ordered = sessions.reversed()
    // Bodyweight lifts have no load to plot, so their progression line is reps.
    fun value(s: ExerciseSession): Float = if (byReps) s.reps.toFloat() else s.weightLbs.toFloat()
    Canvas(modifier = Modifier.fillMaxWidth().height(70.dp)) {
        if (ordered.size < 2) return@Canvas
        val maxW = ordered.maxOf { value(it) }
        val minW = ordered.minOf { value(it) }
        val range = if (maxW == minW) 1f else maxW - minW
        val padY = size.height * 0.12f
        val chartH = size.height - padY * 2
        val points = ordered.mapIndexed { i, s ->
            val x = (i.toFloat() / (ordered.size - 1)) * size.width
            val y = padY + chartH - ((value(s) - minW) / range) * chartH
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
