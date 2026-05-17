package com.sololeveling.app

import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
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

    // Handle back button - navigate to player screen if on quests, otherwise do nothing (let system handle exit)
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

            // Load weekly quests
            val weeklyQuestsList = (questData["weeklyQuests"] as? List<*>)?.mapNotNull {
                if (it is Map<*, *>) {
                    Quest(
                        id = (it["id"] as? Number)?.toInt() ?: 0,
                        title = (it["title"] as? String) ?: "",
                        type = (it["type"] as? String) ?: "weekly",
                        category = (it["category"] as? String) ?: "",
                        xpReward = (it["xpReward"] as? Number)?.toInt() ?: 0,
                        goldReward = (it["goldReward"] as? Number)?.toInt() ?: 0,
                        completed = (it["completed"] as? Boolean) ?: false,
                        streak = (it["streak"] as? Number)?.toInt() ?: 0
                    )
                } else null
            } ?: emptyList()
            weeklyQuests = weeklyQuestsList
            weekliesCompleted = (questData["weekliesCompleted"] as? Number)?.toInt() ?: 0
            hasWeeklyQuests = weeklyQuestsList.isNotEmpty()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    val isAllCompleted = questsCompleted > 0 && questsCompleted == totalQuests

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
                    modifier = Modifier.fillMaxWidth().height(16.dp),
                    color = Color(0xFFFFD700),
                    trackColor = Color(0xFF2a2a2a)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${player!!.xpInCurrentLevel} / ${player!!.xpNeededForLevel} XP", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Total: ${player!!.totalXp} / ${player!!.totalXpNeeded} XP", fontSize = 10.sp, color = Color(0xFF999999))
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (totalQuests > 0) {
                if (isAllCompleted) {
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a472a)).padding(12.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🎉 PERFECT CLEAR! 🎉", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "All daily quests completed", fontSize = 12.sp, color = Color(0xFF86EFAC))
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF472a1a)).padding(12.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "⚠️ Daily Quests Remaining", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9F43))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "$questsCompleted / $totalQuests completed", fontSize = 12.sp, color = Color(0xFFFFB566))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(onClick = onViewDailyQuests, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1a1a1a))) {
                    Text("View Daily Quests", color = Color(0xFFFFD700), fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Weekly Quests Banner - Only show if quests are actually due TODAY
            if (hasWeeklyQuests && weeklyQuests.isNotEmpty()) {
                val isWeeklyAllCompleted = weekliesCompleted > 0 && weekliesCompleted == weeklyQuests.size

                if (isWeeklyAllCompleted) {
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a472a)).padding(12.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "✅ WEEKLY CLEAR! ✅", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "All weekly quests completed", fontSize = 12.sp, color = Color(0xFF86EFAC))
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF472a1a)).padding(12.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "⚠️ Weekly Quests Due Today", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9F43))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "$weekliesCompleted / ${weeklyQuests.size} completed", fontSize = 12.sp, color = Color(0xFFFFB566))
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF2a2a2a)).padding(12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "📅 No Weekly Quests Due Today", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB566))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Weekly Quests Button (always visible)
            Button(onClick = onViewWeeklyQuests, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1a1a1a))) {
                Text("View Weekly Quests", color = Color(0xFFFFD700), fontSize = 16.sp)
            }
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

    val displayQuests = if (questType == "weekly") emptyList() else dailyQuests
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
                Text(text = "$completedCount / ${if (questType == "weekly") weeklyQuests.size else displayQuests.size} Completed", fontSize = 13.sp, color = Color(0xFFFFD700))
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
                    items(displayQuests) { quest ->
                        QuestItem(quest = quest, onCompleteToggle = { onQuestUpdated() }, questId = quest.id, isCompleted = quest.completed, isWeekly = false)
                    }
                } else {
                    if (weeklyQuests.isNotEmpty()) {
                        val calendar = java.util.Calendar.getInstance()
                        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1

                        val todaysQuests = weeklyQuests.filter { it.weekday == dayOfWeek }.sortedBy { it.completed }
                        val otherDaysQuests = weeklyQuests.filter { it.weekday != dayOfWeek }.sortedBy { it.weekday }
                        
                        items(todaysQuests) { quest ->
                            QuestItem(quest = quest, onCompleteToggle = { onQuestUpdated() }, questId = quest.id, isCompleted = quest.completed, isWeekly = true)
                        }

                        if (otherDaysQuests.isNotEmpty()) {
                            item {
                                TextButton(
                                    onClick = { showHiddenWeeklies = !showHiddenWeeklies },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (showHiddenWeeklies) "▲ Hide All Weekly Quests" else "▼ Show All Weekly Quests",
                                        color = Color(0xFFFFD700),
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            if (showHiddenWeeklies) {
                                items(otherDaysQuests) { quest ->
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
    var checked by remember { mutableStateOf(isCompleted) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(
            checked = checked,
            enabled = !isLoading,
            onCheckedChange = { newValue ->
                if (!isLoading) {
                    checked = newValue
                    isLoading = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val endpoint = if (newValue) "/complete" else "/uncomplete"
                            val apiPath = if (isWeekly) "/api/weekly-quests/$questId$endpoint" else "/api/quests/$questId$endpoint"
                            val fullUrl = "https://mysololeveling.us$apiPath"

                            val url = URL(fullUrl)
                            val connection = url.openConnection() as HttpURLConnection
                            connection.requestMethod = "POST"
                            connection.connectTimeout = 10000
                            connection.readTimeout = 10000
                            val responseCode = connection.responseCode
                            connection.disconnect()

                            if (responseCode in 200..299) {
                                // Don't call onCompleteToggle() - just keep the local state
                                // Call it after a small delay so the coroutine finishes
                            } else {
                                checked = !checked
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("QUEST_API", "Error: ${e.message}")
                            checked = !checked
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = quest.title.replaceFirst(Regex(" - (\\d+:\\d+|\\d{1,2}:\\d{2} [AP]M)"), " @ $1").replace("daily ", ""), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (checked) Color.Gray else Color.White, textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None)
            Text(text = "${quest.xpReward} XP", fontSize = 12.sp, color = Color(0xFFFFD700))
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
                    xpReward = (it["xpReward"] as? Number)?.toInt() ?: 0,
                    goldReward = (it["goldReward"] as? Number)?.toInt() ?: 0,
                    completed = (it["completed"] as? Boolean) ?: false,
                    streak = (it["streak"] as? Number)?.toInt() ?: 0
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
                    xpReward = (it["xpReward"] as? Number)?.toInt() ?: 0,
                    goldReward = (it["goldReward"] as? Number)?.toInt() ?: 0,
                    completed = (it["completed"] as? Boolean) ?: false,
                    streak = (it["streak"] as? Number)?.toInt() ?: 0
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
                        xpReward = (it["xpReward"] as? Number)?.toInt() ?: 0,
                        goldReward = 0,
                        completed = (it["completed"] as? Boolean) ?: false,
                        streak = 0,
                        weekday = (it["weekday"] as? Number)?.toInt() ?: -1
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

        onQuestsLoaded(dailyQuestsList, allWeeklyQuestsList, dailiesCompleted, weekliesCompleted, hasWeekly)
    } catch (e: Exception) {
        android.util.Log.e("API_ERROR", "Error loading quests: ${e.message}", e)
        onError(e.message ?: "Unknown error")
    }
}