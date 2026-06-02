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
    var dailyQuestsList by remember { mutableStateOf<List<Quest>>(emptyList()) }
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

            val dailyQuestsData = try {
                val dailyQuestsRaw = (questData["dailyQuests"] as? List<*>) ?: emptyList<Any>()
                dailyQuestsRaw.mapNotNull {
                    if (it is Map<*, *>) {
                        Quest(
                            id = (it["id"] as? Number)?.toInt() ?: 0,
                            title = (it["title"] as? String) ?: "",
                            type = "daily",
                            category = (it["category"] as? String) ?: "",
                            xpReward = (it["xp_reward"] as? Number)?.toInt() ?: 0,
                            goldReward = (it["gold_reward"] as? Number)?.toInt() ?: 0,
                            completed = ((it["completed"] as? Number)?.toInt() ?: 0) == 1,
                            streak = 0
                        )
                    } else null
                }
            } catch (e: Exception) {
                emptyList()
            }
            dailyQuestsList = dailyQuestsData

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

            val hydrationQuests = dailyQuestsList.filter { it.title.contains("Hydrate", ignoreCase = true) }
            val hydrationCompleted = hydrationQuests.count { it.completed }
            if (hydrationCompleted < hydrationQuests.size) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a3a4a), shape = RoundedCornerShape(8.dp)).padding(10.dp), contentAlignment = Alignment.Center) {
                    Text(text = "💧 Complete all daily hydration quests", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4BA3FF))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

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

    val sortedDailies = sortQuestsByTime(dailyQuestsState)
    val todaysWeekly = allWeeklyQuestsState
        .filter { it.weekday == todayWeekday }
        .sortedWith(compareBy({ it.optional }, { it.completed }))
    val overdueQuests = allWeeklyQuestsState
        .filter { it.weekday != todayWeekday && it.isOverdue && !it.completed && !it.optional }
    val thisWeekQuests = allWeeklyQuestsState
        .filter { it.weekday != todayWeekday }
        .filter { !(it.isOverdue && !it.completed && !it.optional) }

    val thisWeekOrder = (1..6).map { (todayWeekday + it) % 7 }
    val overdueOrder = (1..6).map { ((todayWeekday - it) + 7) % 7 }

    val activeThisWeekDays = thisWeekOrder.filter { wd -> thisWeekQuests.any { it.weekday == wd } }
    val activeOverdueDays = overdueOrder.filter { wd -> overdueQuests.any { it.weekday == wd } }

    val requiredDailies = sortedDailies.filter { !it.optional }
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
                items(sortedDailies, key = { "d${it.id}" }) { quest ->
                    QuestItem(
                        quest = quest,
                        onCompleteToggle = { onQuestUpdated() },
                        questId = quest.id,
                        isCompleted = quest.completed,
                        isWeekly = false,
                        showDaySuffix = false
                    )
                }
                if (todaysWeekly.isNotEmpty()) {
                    item(key = "today-weekly-divider") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 4.dp)
                                .height(1.dp)
                                .background(Color(0xFF2a2a2a))
                        )
                    }
                    items(todaysWeekly, key = { "w${it.id}" }) { quest ->
                        QuestItem(
                            quest = quest,
                            onCompleteToggle = { onQuestUpdated() },
                            questId = quest.id,
                            isCompleted = quest.completed,
                            isWeekly = true,
                            showDaySuffix = false
                        )
                    }
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
                                showDaySuffix = false,
                                isOverdue = true
                            )
                        }
                    }
                }

                if (thisWeekQuests.isNotEmpty()) {
                    item(key = "thisweek-header") {
                        ThisWeekHeader(
                            expanded = thisWeekExpanded,
                            onToggle = { thisWeekExpanded = !thisWeekExpanded },
                            days = activeThisWeekDays.map { shortDayNames[it] },
                            count = thisWeekQuests.size
                        )
                    }
                    if (thisWeekExpanded) {
                        activeThisWeekDays.forEach { weekday ->
                            val dayQuests = thisWeekQuests
                                .filter { it.weekday == weekday }
                                .sortedWith(compareBy({ it.optional }, { it.completed }))
                            item(key = "thisweek-sub-$weekday") {
                                DaySubheader(label = shortDayNames[weekday], color = Color(0xFFB0B0B0))
                            }
                            items(dayQuests, key = { "w${it.id}" }) { quest ->
                                QuestItem(
                                    quest = quest,
                                    onCompleteToggle = { onQuestUpdated() },
                                    questId = quest.id,
                                    isCompleted = quest.completed,
                                    isWeekly = true,
                                    showDaySuffix = false
                                )
                            }
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
fun ThisWeekHeader(expanded: Boolean, onToggle: () -> Unit, days: List<String>, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "THIS WEEK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (days.isEmpty()) "No upcoming quests" else "${days.joinToString(", ")} · $count quest${if (count == 1) "" else "s"}",
                fontSize = 11.sp,
                color = Color(0xFFB0B0B0)
            )
        }
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
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1a1a1a))
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabButton(
                label = "Quests",
                isSelected = selectedTab == TabType.QUESTS,
                onClick = { selectedTab = TabType.QUESTS },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                label = "Supplements",
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
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White
        )
    }
}

enum class TabType {
    QUESTS, SUPPLEMENTS, DIET
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
    val netCarbs: Int
)

data class MealStep(
    val title: String,
    val detail: String
)

val BREAKFAST_MEALS = listOf(
    Meal(
        name = "Beef & Eggs",
        mealType = "Breakfast",
        macros = Macros(calories = 510, protein = 39, fat = 36, netCarbs = 3),
        ingredients = listOf(
            "5 oz 80/20 ground beef",
            "40g diced white onion",
            "1 tsp minced garlic",
            "3 large eggs",
            "Pinch of salt",
            "Black pepper",
            "Splash of water or heavy cream",
            "Small sliver of butter (if needed)"
        ),
        steps = listOf(
            MealStep(
                "1. Crisp the Beef & Aromatics First",
                "Heat your skillet over medium-high heat. Drop in your 5 oz of 80/20 ground beef. Let it sit untouched for 2–3 minutes before breaking it up — this lets it develop a deep, brown, flavorful crust. Once you break it apart, toss in your 40g of diced white onion and 1 tsp of minced garlic. Sauté until the onions are translucent and the beef is fully cooked and crispy."
            ),
            MealStep(
                "2. The Prep",
                "Whisk your 3 large eggs in a bowl with a pinch of salt, black pepper, and a tiny splash of water or heavy cream. Whisk vigorously until the eggs are completely uniform and bubbly — this incorporates air for maximum fluffiness."
            ),
            MealStep(
                "3. The Switch",
                "Turn the burner heat down to medium-low. Push the crispy beef and onions to one side of the pan. If the pan looks dry, drop a tiny sliver of butter onto the empty side."
            ),
            MealStep(
                "4. The Pour",
                "Pour the whisked eggs into the empty side of the pan. Let them sit for about 30 seconds until the edges just start to set."
            ),
            MealStep(
                "5. The Fold",
                "Using a spatula, gently push the eggs from the outside edge toward the center, creating long, folding sheets of egg. As they become semi-solid, gently fold the crispy beef and onions back into the eggs."
            ),
            MealStep(
                "6. Pull the Heat",
                "Turn off the burner while the eggs still look a tiny bit glossy and wet. The residual heat of the pan will finish cooking them in the 10 seconds it takes to plate them."
            )
        )
    )
)

val LUNCH_MEALS = listOf(
    Meal(
        name = "Chicken & Pepper",
        mealType = "Lunch",
        macros = Macros(calories = 680, protein = 48, fat = 48, netCarbs = 7),
        ingredients = listOf(
            "8 oz chicken breast",
            "1.5 cups bell peppers",
            "1 tsp minced garlic",
            "1 tbsp olive oil",
            "Dried thyme",
            "Salt",
            "Black pepper",
            "1 oz shredded cheddar cheese"
        ),
        steps = listOf(
            MealStep(
                "1. The Raw Prep & Quick Marinade",
                "Dice your 8 oz chicken breast into uniform 1/2-inch cubes. Slice your 1.5 cups of bell peppers into bite-sized strips. In a bowl, toss the raw chicken cubes with 1 tsp minced garlic, 1 tbsp olive oil, dried thyme, salt, and pepper. Let it sit 5 minutes on the counter while the pan heats. Tip: salting the raw chicken early helps it retain its internal juices when it hits the hot pan."
            ),
            MealStep(
                "2. The High-Heat Pan Sear",
                "Place a large skillet over medium-high heat — hot enough that the chicken sizzles immediately on contact. Drop the cubes in a single layer and sear untouched for 2 minutes to build a golden-brown crust that locks in the juices. Toss and cook another 2–3 minutes until fully cooked through. The small cubes cook fast, so pull them onto a plate immediately so they don't overcook."
            ),
            MealStep(
                "3. Sauté the Peppers",
                "Leave the burner on medium-high and drop the bell peppers into the same pan so they pick up the leftover garlic, thyme, and chicken juices. Sauté for just 3 minutes, tossing frequently, until vibrant and slightly blistered on the edges but still crisp."
            ),
            MealStep(
                "4. The Fresh Cheddar Melt",
                "Turn off the heat. Return the cooked chicken to the skillet with the peppers to combine. Sprinkle 1 oz shredded cheddar evenly over the top, cover with a lid or foil for 60 seconds with the heat off — the trapped steam melts the cheese into a gooey blanket without overcooking the meat. Slide onto a plate and eat hot."
            )
        )
    )
)

val DINNER_MEALS = listOf(
    Meal(
        name = "Steak & Mushrooms",
        mealType = "Dinner",
        macros = Macros(calories = 640, protein = 45, fat = 46, netCarbs = 8),
        ingredients = listOf(
            "Steak — preferred cut (Top Sirloin or Ribeye), room temperature",
            "8–12 oz white or baby bella mushrooms, sliced thick",
            "1 tbsp high-smoke-point oil (avocado or light olive oil)",
            "1–2 tbsp butter (for the finish)",
            "Coarse salt",
            "Coarse black pepper",
            "Optional: minced garlic or fresh thyme"
        ),
        steps = listOf(
            MealStep(
                "1. Prep and Dry the Steak (~30 min before)",
                "Take the steak out of the fridge early to drop the chill. Pat it completely dry with paper towels — heavy moisture prevents a good sear. Season generously on all sides with coarse salt and black pepper."
            ),
            MealStep(
                "2. Sear the Steak (4–6 min)",
                "Heat a heavy skillet (cast iron is perfect) over high heat until it's smoking hot. Add 1 tbsp high-smoke-point oil and drop the steak in. Sear 2–3 minutes per side without moving it, creating a deep, brown crust."
            ),
            MealStep(
                "3. Rest the Meat (5–8 min)",
                "Pull the steak out and set it on a cutting board or warm plate to rest. Don't skip this — resting lets the muscle fibers relax so the juices stay inside the steak when you cut it."
            ),
            MealStep(
                "4. Sauté the Mushrooms (5–6 min)",
                "Turn the burner down to medium-high. Drop the sliced mushrooms straight into the same hot pan with the leftover steak fat. Let them sit flat for 2 minutes to get color, then stir. They'll absorb the fat, release water, then start turning golden brown."
            ),
            MealStep(
                "5. The Garlic Butter Finish (1–2 min)",
                "Once the mushrooms are browned, drop 1–2 tbsp butter into the pan with some minced garlic (and fresh thyme if you have it). Stir vigorously for a minute until the butter foams, turns slightly nutty, and coats the mushrooms."
            ),
            MealStep(
                "Chef's Tip",
                "Slice the rested steak against the grain and pile the hot, buttery garlic mushrooms right over the top. The warm mushrooms will reheat the surface of the steak perfectly."
            )
        )
    )
)

val SNACK_MEALS = listOf(
    Meal(
        name = "Pecan Halves",
        mealType = "Snack",
        macros = Macros(calories = 215, protein = 3, fat = 22, netCarbs = 1),
        ingredients = listOf("Member's Mark Natural Pecan Halves — 30g"),
        steps = emptyList()
    )
)

val DESSERT_MEALS = listOf(
    Meal(
        name = "Pecan Mousse",
        mealType = "Dessert",
        macros = Macros(calories = 150, protein = 4, fat = 19, netCarbs = 3),
        ingredients = listOf(
            "2 oz heavy whipping cream",
            "1 tbsp cocoa powder",
            "1 tbsp sweetener",
            "7g pecan halves"
        ),
        steps = listOf(
            MealStep(
                "1. Chill and Prep Your Tools (1 min)",
                "For the fastest results, use a small metal or glass bowl. If you have an extra 60 seconds, drop the bowl and your whisk (or hand mixer beaters) into the freezer. Cold tools make heavy cream whip up twice as fast."
            ),
            MealStep(
                "2. Combine the Base (2 min)",
                "Pour the 2 oz heavy whipping cream straight into the chilled bowl. Sift or dump in the 1 tbsp cocoa powder and 1 tbsp sweetener."
            ),
            MealStep(
                "3. Whip to Thick Peaks (2 min)",
                "Beat the mixture vigorously. Hand mixer: start on low so the cocoa doesn't fly out, then turn to high. By hand: move your wrist in a rapid, circular motion to incorporate air. After about 90–120 seconds the cream suddenly transforms into a thick, velvety, scoopable mousse that holds its shape. Stop immediately once it reaches this point so it doesn't turn into chocolate butter."
            ),
            MealStep(
                "4. The Pecan Crunch Finish (1 min)",
                "Spoon the mousse into a small bowl or glass. Give your 7g of pecan halves a quick chop and scatter them generously over the top."
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
            modifier = Modifier.padding(bottom = 16.dp)
        )
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

data class SupplementGroup(
    val category: String,
    val supplements: List<Pair<String, String>>
)