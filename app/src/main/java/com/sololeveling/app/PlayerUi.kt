package com.sololeveling.app

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
    var questType by remember { mutableStateOf("daily") }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Check for pending navigation from notifications
    LaunchedEffect(NavigationState.pendingNavigation.value) {
        val pending = NavigationState.pendingNavigation.value
        if (pending == "daily_quests") {
            currentScreen = "quests"
            questType = "daily"
            NavigationState.pendingNavigation.value = null
        }
    }

    BackHandler(enabled = currentScreen != "player") {
        currentScreen = "player"
    }

    if (currentScreen == "player") {
        PlayerStatsScreen(
            onViewDailyQuests = {
                currentScreen = "quests"
                questType = "daily"
            },
            onViewWeeklyQuests = {
                currentScreen = "quests"
                questType = "weekly"
            },
            refreshTrigger = refreshTrigger
        )
    } else {
        QuestsListScreen(
            onBackToPlayer = { currentScreen = "player" },
            onQuestUpdated = { refreshTrigger++ },
            refreshTrigger = refreshTrigger,
            questType = questType
        )
    }
}

@Composable
fun PlayerStatsScreen(onViewDailyQuests: () -> Unit, onViewWeeklyQuests: () -> Unit, refreshTrigger: Int) {
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
    var overdueQuests by remember { mutableStateOf<List<Quest>>(emptyList()) }

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
            val overdueRequiredQuests = allWeeklyQuestsList.filter { !it.optional && it.isOverdue && !it.completed }

            weeklyQuests = todaysWeeklyQuests
            weekliesCompleted = todaysWeeklyCompleted
            hasWeeklyQuests = todaysWeeklyQuests.isNotEmpty()
            overdueQuests = overdueRequiredQuests
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

            if (overdueQuests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a), shape = RoundedCornerShape(8.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                    Text(text = "⚠️ ${overdueQuests.size} Overdue Weekly Quests", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onViewDailyQuests, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1a1a1a))) {
                    Text("View Daily", color = Color(0xFFFFD700), fontSize = 14.sp)
                }
                Button(onClick = onViewWeeklyQuests, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1a1a1a))) {
                    Text("View Weekly", color = Color(0xFFFFD700), fontSize = 14.sp)
                }
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
fun QuestsListScreen(onBackToPlayer: () -> Unit, onQuestUpdated: () -> Unit, refreshTrigger: Int, questType: String = "daily") {
    var dailyQuests by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var weeklyQuests by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var dailiesCompletedCount by remember { mutableIntStateOf(0) }
    var weekliesCompletedCount by remember { mutableIntStateOf(0) }
    var showHiddenWeeklies by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        loadAllQuests(
            onQuestsLoaded = { daily, weekly, dailiesCompleted, weekliesCompleted, hasWeekly ->
                dailyQuests = daily
                weeklyQuests = weekly
                dailiesCompletedCount = dailiesCompleted
                weekliesCompletedCount = weekliesCompleted
                loading = false
            },
            onError = { err -> error = err; loading = false }
        )
    }

    val displayQuests = if (questType == "weekly") emptyList() else sortQuestsByTime(dailyQuests)
    val completedCount = if (questType == "weekly") weekliesCompletedCount else dailiesCompletedCount
    val title = if (questType == "weekly") "Weekly Quests" else "Daily Quests"

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).systemBarsPadding().padding(top = 16.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                if (questType == "weekly") {
                    val requiredWeeklies = weeklyQuests.filter { !it.optional }
                    val requiredCompleted = requiredWeeklies.count { it.completed }
                    Text(text = "$requiredCompleted / ${requiredWeeklies.size} Completed", fontSize = 13.sp, color = Color(0xFFFFD700))
                } else {
                    val requiredDailies = displayQuests.filter { !it.optional }
                    val requiredDailiesCompleted = requiredDailies.count { it.completed }
                    Text(text = "$requiredDailiesCompleted / ${requiredDailies.size} Completed", fontSize = 13.sp, color = Color(0xFFFFD700))
                }
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
                if (questType == "daily") {
                    items(displayQuests, key = { it.id }) { quest ->
                        QuestItem(quest = quest, onCompleteToggle = { onQuestUpdated() }, questId = quest.id, isCompleted = quest.completed, isWeekly = false)
                    }
                } else {
                    if (weeklyQuests.isNotEmpty()) {
                        val calendar = java.util.Calendar.getInstance()
                        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1

                        val todaysQuests = weeklyQuests.filter { it.weekday == dayOfWeek }.sortedWith(compareBy({ it.optional }, { it.completed }))
                        val overdueQuests = weeklyQuests.filter { it.isOverdue && !it.completed && !it.optional }
                        val otherDaysQuests = weeklyQuests.filter { it.weekday != dayOfWeek && !(it.isOverdue && !it.completed) }.sortedWith(compareBy({ it.completed || (it.optional && it.weekday < dayOfWeek) }, { it.weekday }))

                        items(todaysQuests, key = { it.id }) { quest ->
                            QuestItem(quest = quest, onCompleteToggle = { onQuestUpdated() }, questId = quest.id, isCompleted = quest.completed, isWeekly = true)
                        }

                        if (overdueQuests.isNotEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF7a1a1a)).padding(12.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "⚠️ ${overdueQuests.size} OVERDUE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = "Past due required quests", fontSize = 12.sp, color = Color(0xFFFF9999))
                                    }
                                }
                            }

                            items(overdueQuests, key = { it.id }) { quest ->
                                QuestItem(quest = quest, onCompleteToggle = { onQuestUpdated() }, questId = quest.id, isCompleted = quest.completed, isWeekly = true)
                            }
                        }

                        if (otherDaysQuests.isNotEmpty()) {
                            item {
                                TextButton(
                                    onClick = { showHiddenWeeklies = !showHiddenWeeklies },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (showHiddenWeeklies) "▲ Hide Other Days" else "▼ Show Other Days",
                                        color = Color(0xFFFFD700),
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            if (showHiddenWeeklies) {
                                items(otherDaysQuests, key = { it.id }) { quest ->
                                    QuestItem(quest = quest, onCompleteToggle = { onQuestUpdated() }, questId = quest.id, isCompleted = quest.completed, isWeekly = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestItem(quest: Quest, onCompleteToggle: () -> Unit, questId: Int, isCompleted: Boolean, isWeekly: Boolean = false) {
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
            val displayTitle = if (isWeekly && quest.weekday >= 0) {
                "${quest.title} - ${dayNames[quest.weekday]}"
            } else {
                quest.title.replaceFirst(Regex(" - (\\d+:\\d+|\\d{1,2}:\\d{2} [AP]M)"), " @ $1").replace("daily ", "")
            }
            Text(
                text = displayTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) Color.Gray else Color.White,
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
    QUESTS, SUPPLEMENTS
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