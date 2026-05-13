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
    var refreshTrigger by remember { mutableIntStateOf(0) }

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
    var player by remember { mutableStateOf<Player?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var questsCompleted by remember { mutableIntStateOf(0) }
    var totalQuests by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        loading = true
        try {
            val response = fetchFromApi("/api/player")
            player = Gson().fromJson(response, Player::class.java)
            val questResponse = fetchFromApi("/api/quests")
            val questData = Gson().fromJson(questResponse, QuestResponse::class.java)
            questsCompleted = questData.dailiesCompleted
            totalQuests = questData.totalDailies
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

                Button(onClick = onViewQuests, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1a1a1a))) {
                    Text("View Daily Quests", color = Color(0xFFFFD700), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun QuestsListScreen(onBackToPlayer: () -> Unit, onQuestUpdated: () -> Unit, refreshTrigger: Int) {
    var quests by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var completedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        loadQuests(
            onQuestsLoaded = { q, cc -> quests = q; completedCount = cc; loading = false },
            onError = { err -> error = err; loading = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).systemBarsPadding().padding(top = 16.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Daily Quests", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "$completedCount / ${if (quests.isEmpty()) 0 else quests.size} Completed", fontSize = 13.sp, color = Color(0xFFFFD700))
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
                items(quests) { quest ->
                    QuestItem(quest = quest, onCompleteToggle = { onQuestUpdated() }, questId = quest.id, isCompleted = quest.completed)
                }
            }
        }
    }
}

@Composable
fun QuestItem(quest: Quest, onCompleteToggle: () -> Unit, questId: Int, isCompleted: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(
            checked = isCompleted,
            onCheckedChange = {
                Thread {
                    try {
                        val endpoint = if (!isCompleted) "/complete" else "/uncomplete"
                        val url = URL("http://mysololeveling.ddns.net:3742/api/quests/$questId$endpoint")
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

suspend fun loadQuests(onQuestsLoaded: (List<Quest>, Int) -> Unit, onError: (String) -> Unit) {
    try {
        val response = fetchFromApi("/api/quests")
        val questData = Gson().fromJson(response, Map::class.java)
        val questsList = (questData["dailyQuests"] as? List<*>)?.mapNotNull {
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
        val dailiesCompleted = (questData["dailiesCompleted"] as? Number)?.toInt() ?: 0
        onQuestsLoaded(questsList, dailiesCompleted)
    } catch (e: Exception) {
        onError(e.message ?: "Unknown error")
    }
}

fun extractTimeFromTitle(title: String): Int {
    val timePattern = """(\d{1,2}):?(\d{2})?\s*(AM|PM)""".toRegex()
    val match = timePattern.find(title)
    if (match != null) {
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val ampm = match.groupValues[3]
        var totalMinutes = hour * 60 + minute
        if (ampm == "PM" && hour != 12) totalMinutes += 12 * 60
        if (ampm == "AM" && hour == 12) totalMinutes = minute
        return totalMinutes
    }
    return Int.MAX_VALUE
}