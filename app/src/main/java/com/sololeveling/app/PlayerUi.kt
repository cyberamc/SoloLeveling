package com.sololeveling.app

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

@Composable
fun PlayerScreen() {
    var currentScreen by remember { mutableStateOf("player") }
    var questType by remember { mutableStateOf("daily") }  // ADD THIS LINE
    var refreshTrigger by remember { mutableIntStateOf(0) }

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
            questType = questType  // ADD THIS
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
            hasWeeklyQuests = (questData["hasWeeklyQuests"] as? Boolean) ?: false
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
            Text(text = "Level ${player!!.level} • Rank ${player!!.rank}", fontSize = 20.sp, color = Color(0xFFFFD700))
            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Experience", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (player!!.xp.toFloat() / player!!.xpNeeded.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(16.dp),
                    color = Color(0xFFFFD700),
                    trackColor = Color(0xFF2a2a2a)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${player!!.xp} / ${player!!.xpNeeded} XP", fontSize = 12.sp, color = Color.Gray)
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
                            Text(text = "⚠️ Quests Remaining", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9F43))
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

            Spacer(modifier = Modifier.height(12.dp))

            // Weekly Quests Banner
            if (hasWeeklyQuests) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF2a1a1a)).padding(12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "⚠️ Weekly Quests Due Today", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9F43))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "$weekliesCompleted / ${weeklyQuests.size} completed", fontSize = 12.sp, color = Color(0xFFFFB566))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF2a2a2a)).padding(12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "📅 No Weekly Quests Due Today", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB566))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
    var showHiddenWeeklies by remember { mutableStateOf(false) }  // Start collapsed
    
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

    val displayQuests = if (questType == "weekly") emptyList() else dailyQuests  // Don't show initial list for weekly
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
                // For daily quests, show the list normally
                if (questType == "daily") {
                    items(displayQuests) { quest ->
                        QuestItem(quest = quest, onCompleteToggle = { onQuestUpdated() }, questId = quest.id, isCompleted = quest.completed, isWeekly = false)
                    }
                } else {
                    // For weekly quests, show collapsible section
                    if (weeklyQuests.isNotEmpty()) {
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
                            items(weeklyQuests) { quest ->
                                QuestItem(quest = quest, onCompleteToggle = { onQuestUpdated() }, questId = quest.id, isCompleted = quest.completed, isWeekly = true)
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
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(
            checked = isCompleted,
            onCheckedChange = {
                Thread {
                    try {
                        val endpoint = if (!isCompleted) "/complete" else "/uncomplete"
                        val apiPath = if (isWeekly) "/api/weekly-quests/$questId$endpoint" else "/api/quests/$questId$endpoint"
                        val url = URL("http://192.168.1.230:3742")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        connection.responseCode
                        connection.disconnect()
                        onCompleteToggle()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            },
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = quest.title.replace(" - ", " @ ").replace("daily ", ""), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (isCompleted) Color.Gray else Color.White, textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None)
            Text(text = "${quest.xpReward} XP", fontSize = 12.sp, color = Color(0xFFFFD700))
        }
    }
}

suspend fun loadAllQuests(onQuestsLoaded: (List<Quest>, List<Quest>, Int, Int, Boolean) -> Unit, onError: (String) -> Unit) {
    try {
        val response = fetchFromApi("/api/quests")
        val questData = Gson().fromJson(response, Map::class.java)

        // Parse daily quests
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

        // Parse weekly quests (today's due quests)
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

        // Fetch ALL weekly quests (for collapsible section)
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
                        completed = false,
                        streak = 0
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