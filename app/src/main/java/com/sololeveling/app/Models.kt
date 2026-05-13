package com.sololeveling.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class Player(val name: String, val level: Int, val xp: Int, val xpNeeded: Int, val rank: String, val statPoints: Int)

data class Quest(
    val id: Int,
    val title: String,
    val type: String,
    val category: String,
    val xpReward: Int,
    val goldReward: Int,
    val completed: Boolean,
    val streak: Int
)

data class QuestResponse(
    val quests: List<Quest>,
    val perfectClearBonus: Int,
    val dailiesCompleted: Int,
    val totalDailies: Int
)

suspend fun fetchFromApi(endpoint: String): String = withContext(Dispatchers.IO) {
    val url = URL("http://mysololeveling.ddns.net:3742$endpoint")
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    return@withContext try {
        if (connection.responseCode == 200) connection.inputStream.bufferedReader().readText()
        else throw Exception("HTTP ${connection.responseCode}")
    } finally { connection.disconnect() }
}