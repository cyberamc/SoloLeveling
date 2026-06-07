package com.sololeveling.app

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import okhttp3.RequestBody
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
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

@Composable
fun PlayerScreen() {
    var currentScreen by remember { mutableStateOf("player") }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Check for pending navigation from notifications
    LaunchedEffect(NavigationState.pendingNavigation.value) {
        val pending = NavigationState.pendingNavigation.value
        if (pending == "daily_quests" || pending == "weekly_quests" || pending == "quests") {
            currentScreen = "quests"
            NavigationState.pendingNavigation.value = null
        }
    }

    BackHandler(enabled = currentScreen != "player") {
        currentScreen = "player"
    }

    if (currentScreen == "player") {
        PlayerStatsScreen(
            onViewQuests = { currentScreen = "quests" },
            refreshTrigger = refreshTrigger
        )
    } else {
        QuestsListScreen(
            onBackToPlayer = { currentScreen = "player" },
            onQuestUpdated = { refreshTrigger++ },
            refreshTrigger = refreshTrigger
        )
    }
}

@Composable
fun PlayerStatsScreen(onViewQuests: () -> Unit, refreshTrigger: Int) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<Player?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var questsCompleted by remember { mutableIntStateOf(0) }
    var totalQuests by remember { mutableIntStateOf(0) }
    var weeklyQuests by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var weekliesCompleted by remember { mutableIntStateOf(0) }
    var hasWeeklyQuests by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        loading = true
        try {
            val response = fetchFromApi("/api/player")
            player = Gson().fromJson(response, Player::class.java)
            val questResponse = fetchFromApi("/api/quests")
            val questData = Gson().fromJson(questResponse, Map::class.java)
            questsCompleted = (questData["dailiesCompleted"] as? Number)?.toInt() ?: 0
            totalQuests = (questData["totalDailies"] as? Number)?.toInt() ?: 0

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
                            goldReward = (it["gold_reward"] as? Number)?.toInt() ?: 0,
                            completed = ((it["completed"] as? Number)?.toInt() ?: 0) == 1,
                            streak = 0,
                            weekday = (it["weekday"] as? Number)?.toInt() ?: -1,
                            optional = ((it["optional"] as? Number)?.toInt() ?: 0) == 1,
                            isOverdue = ((it["isOverdue"] as? Number)?.toInt() ?: 0) == 1
                        )
                    } else null
                }
            } catch (e: Exception) {
                emptyList()
            }

            val calendar = java.util.Calendar.getInstance()
            val todayWeekday = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
            val todaysWeeklyQuests = allWeeklyQuestsList.filter { it.weekday == todayWeekday && !it.optional }
            val todaysWeeklyCompleted = todaysWeeklyQuests.count { it.completed }

            weeklyQuests = todaysWeeklyQuests
            weekliesCompleted = todaysWeeklyCompleted
            hasWeeklyQuests = todaysWeeklyQuests.isNotEmpty()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFFD700))
        }
    } else if (error != null) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a)), contentAlignment = Alignment.Center) {
            Text("Error: $error", color = Color.Red)
        }
    } else if (player != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0a0a0a))
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = player!!.name, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            val displayLevel = try { player!!.level.toFloat().toInt() } catch (e: Exception) { 0 }
            Text(text = "Level $displayLevel • Rank ${player!!.rank}", fontSize = 20.sp, color = Color(0xFFFFD700))
            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Experience", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (player!!.xpInCurrentLevel.toFloat() / player!!.xpNeededForLevel.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = Color(0xFFFFD700),
                    trackColor = Color(0xFF2a2a2a)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "${player!!.xpInCurrentLevel} / ${player!!.xpNeededForLevel} XP", fontSize = 11.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "\"The pain of discipline or the pain of regret.\"",
                    fontSize = 13.sp,
                    color = Color(0xFFFFD700),
                    textAlign = TextAlign.Center,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f).background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Daily Quests", fontSize = 11.sp, color = Color(0xFFB0B0B0))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "$questsCompleted / $totalQuests", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                    }
                }
                Box(modifier = Modifier.weight(1f).background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Weekly Quests", fontSize = 11.sp, color = Color(0xFFB0B0B0))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "$weekliesCompleted / ${weeklyQuests.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onViewQuests,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1a1a1a))
            ) {
                Text("View Quests", color = Color(0xFFFFD700), fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            val calendar = java.util.Calendar.getInstance()
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
            if (dayOfWeek == 2 || dayOfWeek == 3) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a3a4a), shape = RoundedCornerShape(8.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "📦 DELIVERY DAY REMINDER", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4FB3D9))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Count all packages before storing in car to prevent loss", fontSize = 12.sp, color = Color(0xFF7DD3FC))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a2a1a), shape = RoundedCornerShape(8.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔥 Consistency", fontSize = 13.sp, color = Color(0xFFB0B0B0))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "${player!!.nofapStreak} Days", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Stay strong", fontSize = 12.sp, color = Color(0xFFB0B0B0))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
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
fun QuestsListScreen(onBackToPlayer: () -> Unit, onQuestUpdated: () -> Unit, refreshTrigger: Int) {
    var dailyQuestsState by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var allWeeklyQuestsState by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var thisWeekExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        loadAllQuests(
            onQuestsLoaded = { daily, weekly, _, _, _ ->
                dailyQuestsState = daily
                allWeeklyQuestsState = weekly
                loading = false
            },
            onError = { err -> error = err; loading = false }
        )
    }

    val calendar = java.util.Calendar.getInstance()
    val todayWeekday = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
    val shortDayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val fullDayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    val todaysWeekly = allWeeklyQuestsState
        .filter { it.weekday == todayWeekday }
    val combinedToday = sortQuestsByTime(dailyQuestsState + todaysWeekly)
    val overdueQuests = allWeeklyQuestsState
        .filter { it.weekday != todayWeekday && it.isOverdue && !it.completed && !it.optional }
    val thisWeekQuests = allWeeklyQuestsState
        .filter { it.weekday != todayWeekday }
        .filter { !(it.isOverdue && !it.completed && !it.optional) }

    val thisWeekOrder = (1..6).map { (todayWeekday + it) % 7 }
    val overdueOrder = (1..6).map { ((todayWeekday - it) + 7) % 7 }

    val activeThisWeekDays = thisWeekOrder.filter { wd -> thisWeekQuests.any { it.weekday == wd } }
    val activeOverdueDays = overdueOrder.filter { wd -> overdueQuests.any { it.weekday == wd } }

    val requiredDailies = dailyQuestsState.filter { !it.optional }
    val requiredDailiesCompleted = requiredDailies.count { it.completed }
    val requiredTodayWeekly = todaysWeekly.filter { !it.optional }
    val requiredTodayWeeklyCompleted = requiredTodayWeekly.count { it.completed }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).systemBarsPadding().padding(top = 16.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Quests", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                val overdueText = if (overdueQuests.isNotEmpty()) " · ${overdueQuests.size} overdue" else ""
                Text(
                    text = "$requiredDailiesCompleted/${requiredDailies.size} daily · $requiredTodayWeeklyCompleted/${requiredTodayWeekly.size} weekly$overdueText",
                    fontSize = 13.sp,
                    color = Color(0xFFFFD700)
                )
            }
            Text(text = "Back", color = Color(0xFFFFD700), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp).clickable { onBackToPlayer() })
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
                        onCompleteToggle = { onQuestUpdated() },
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
                            subtitle = "${overdueQuests.size} quest${if (overdueQuests.size == 1) "" else "s"}",
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
                                onCompleteToggle = { onQuestUpdated() },
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
                                onCompleteToggle = { onQuestUpdated() },
                                questId = quest.id,
                                isCompleted = quest.completed,
                                isWeekly = true
                            )
                        }
                    }
                }
            }
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
    val scope = rememberCoroutineScope()

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
                    scope.launch(Dispatchers.IO) {
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
                Text(text = "${quest.xpReward} XP", fontSize = 12.sp, color = Color(0xFFFFD700))
                if (isWeekly) {
                    Text(
                        text = "Weekly",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFa78bfa),
                        modifier = Modifier
                            .background(Color(0x26a78bfa), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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
                    optional = ((it["optional"] as? Number)?.toInt() ?: 0) == 1
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
                        isOverdue = ((it["isOverdue"] as? Number)?.toInt() ?: 0) == 1
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
                label = "Quests",
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
            fontSize = 12.sp,
            color = Color.White
        )
    }
}

enum class TabType {
    QUESTS, SUPPLEMENTS, DIET, GYM, FOOD
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
        name = "Beef & Eggs",
        mealType = "Breakfast",
        macros = Macros(calories = 620, protein = 45, fat = 45, netCarbs = "4"),
        ingredients = listOf(
            "5 oz 80/20 ground beef",
            "40g diced white onion",
            "1 tsp minced garlic",
            "3 large eggs",
            "1 oz shredded cheese",
            "pinch of salt",
            "black pepper",
            "splash of water or heavy cream",
            "small sliver of butter (if needed)"
        ),
        steps = listOf(
            MealStep(
                "1. Crisp the Beef & Aromatics First",
                "The Prep: Heat your skillet over medium-high heat. Whisk your 3 large eggs in a bowl with a pinch of salt, black pepper, and a tiny splash of water or heavy cream. Whisk vigorously until the eggs are completely uniform and bubbly—this incorporates air for maximum fluffiness.\n\nDrop in your 5 oz of 80/20 ground beef. Let it sit untouched for 2–3 minutes before breaking it up. This lets it develop a deep, brown, flavorful crust.\n\nOnce you break it apart, toss in your 40g of diced white onion and 1 tsp of minced garlic. Sauté everything until the onions are translucent and the beef is fully cooked and crispy."
            ),
            MealStep(
                "2. The Fresh \"Soft-Fold\" Egg Technique",
                "The Switch: Turn the burner heat down to medium-low. Push the crispy beef and onions to one side of the pan. If the pan looks dry, drop a tiny sliver of butter onto the empty side.\n\nThe Pour: Pour the whisked eggs into the empty side of the pan. Let them sit for about 30 seconds until the edges just start to set.\n\nThe Melt & Fold: Sprinkle 1 oz of shredded cheese evenly over the eggs. Using a spatula, gently push the eggs and cheese from the outside edge toward the center, creating long, folding sheets. As they become semi-solid, gently fold the crispy beef and onions back into the cheesy eggs.\n\nPull the Heat: Turn off the burner while the eggs still look a tiny bit glossy and wet. The residual heat of the pan will finish melting the cheese and cooking the eggs to perfection in the 10 seconds it takes to plate them."
            )
        )
    )
)

val LUNCH_MEALS = listOf(
    Meal(
        name = "Protein Shake",
        mealType = "Lunch",
        macros = Macros(calories = 360, protein = 60, fat = 8, netCarbs = "12"),
        ingredients = listOf(
            "2 level scoops body fortress whey protein powder",
            "8 to 12 oz cold water",
            "2 to 3 ice cubes (optional)"
        ),
        steps = listOf(
            MealStep(
                "1. Measure the Base",
                "The Liquid: Pour 8 to 12 oz of cold water into the shaker.\n\n(Optional Tip: If you prefer a thicker shake, stick to 8 oz of water. If you prefer it more fluid, go up to 12 oz. For an ice-cold texture, drop 2 to 3 ice cubes into the liquid.)"
            ),
            MealStep(
                "2. Add the Powder",
                "The Protein: Using the scoop from your tub, add exactly 2 level scoops of Body Fortress Whey Protein powder on top of the water."
            ),
            MealStep(
                "3. Mix and Emulsify",
                "The Mix: Screw the lid on tightly and snap the flip-cap completely shut. Mix vigorously for 20 to 30 seconds until the powder is fully dissolved and the shake is completely smooth, frothy, and emulsified. Let it sit for 30 seconds to allow the foam to settle before drinking."
            )
        )
    )
)

val DINNER_MEALS = listOf(
    Meal(
        name = "Steak & Mushrooms",
        mealType = "Dinner",
        macros = Macros(calories = 515, protein = 55, fat = 29, netCarbs = "5"),
        ingredients = listOf(
            "8 oz sirloin tip steak",
            "100g white mushrooms or baby bella mushrooms, sliced thick",
            "1 cup beef bone broth",
            "1/2 tbsp butter",
            "1 tsp minced garlic",
            "1/4 tsp dried thyme",
            "coarse salt",
            "coarse black pepper"
        ),
        steps = listOf(
            MealStep(
                "1. Prep and Sear the Steak",
                "The Prep: Pat your 8 oz of sirloin tip steak dry with a paper towel and cut into bite-sized cubes. Season generously on all sides with salt and coarse black pepper.\n\nHeat a heavy skillet over medium-high heat. Drop in a tiny dab of your 1/2 tbsp of butter just to coat the bottom of the pan.\n\nOnce the pan is hot, add the steak tips in a single layer. Let them sear undisturbed for 2 minutes to build a deep crust, then flip and cook for another 1 to 2 minutes until medium-rare.\n\nRemove the steak from the pan and set it aside on a plate. Do not clean out the pan."
            ),
            MealStep(
                "2. Sauté the Mushrooms",
                "The Veg: Turn the burner down to medium heat. Toss 100g of sliced white mushrooms and 1/4 tsp of dried thyme leaves directly into the remaining steak fats left in the pan.\n\nLet the mushrooms cook down for 4 to 5 minutes until they turn a rich, golden brown.\n\nStir in 1 tsp of minced garlic during the last 30 seconds of cooking, moving it constantly so it becomes highly fragrant without burning."
            ),
            MealStep(
                "3. Build the Bone Broth Reduction",
                "The Sauce: Pour 1 cup of beef bone broth into the hot skillet. It will bubble rapidly. Use a spatula to scrape up all the dark, savory bits (fond) stuck to the bottom of the pan.\n\nKeep the pan at a steady simmer for about 4 to 5 minutes, allowing the broth to reduce by half."
            ),
            MealStep(
                "4. Gloss the Sauce & Serve",
                "The Finish: Turn off the burner completely and slide the pan off the heat. Drop the remaining cold butter into the reduction and stir it continuously until it emulsifies into a glossy, velvety sauce.\n\nPour any rested juices from the steak plate back into the pan, then toss the sirloin tips back into the garlic-thyme mushroom glaze to coat them completely. Plate immediately."
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
                    MacroBox("Calories", "2,000", Modifier.weight(1f))
                    MacroBox("Protein", "167g", Modifier.weight(1f))
                    MacroBox("Fat", "132g", Modifier.weight(1f))
                    MacroBox("Net Carbs", "25.5g", Modifier.weight(1f))
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
                                "L-Theanine" to "400 mg",
                                "L-Tyrosine" to "1000 mg",
                                "Alpha-GPC" to "600 mg"
                            )
                        ),
                        SupplementGroup(
                            category = "Powder",
                            supplements = listOf(
                                "L-Citrulline" to "6g",
                                "Beta-Alanine" to "3.2g",
                                "Betaine Anhydrous" to "3g",
                                "Creatine" to "5g",
                                "BCAA" to "6g"
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
                            category = "Powder",
                            supplements = listOf(
                                "Creatine" to "5g",
                                "Beta-Alanine" to "3g",
                                "Betaine Anhydrous" to "1.5g",
                                "BCAA" to "6g"
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
                                "L-Theanine" to "200 mg",
                                "Magnesium Glycinate" to "210 mg",
                                "Ashwagandha" to "600 mg",
                                "Chamomile" to "750 mg",
                                "Valerian Root" to "500 mg"
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

    BackHandler(enabled = selectedExercise != null) {
        selectedExercise = null
    }

    if (selectedExercise == null) {
        GymListScreen(onExerciseSelected = { selectedExercise = it })
    } else {
        GymDetailScreen(exercise = selectedExercise!!, onBack = { selectedExercise = null })
    }
}

@Composable
fun GymListScreen(onExerciseSelected: (GymExercise) -> Unit) {
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
                                "Vs other lifters at 191 lbs", fontSize = 11.sp, color = Color(0xFF6b7689),
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