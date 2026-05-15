package com.sololeveling.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

data class Stats(
    val STR: Int,
    val INT: Int,
    val VIT: Int,
    val AGI: Int,
    val END: Int
)

data class Player(
    val id: Int,
    val name: String,
    val level: Int,
    val xp: Int,
    val xpInCurrentLevel: Int,
    val xpNeededForLevel: Int,
    val totalXp: Int,
    val totalXpNeeded: Int,
    val xpNeeded: Int = xpNeededForLevel,
    val rank: String,
    val gold: Int,
    val statPoints: Int,
    val stats: Stats
)
data class Quest(
    val id: Int,
    val title: String,
    val type: String,
    val category: String,
    val xpReward: Int,
    val goldReward: Int,
    val completed: Boolean,
    val streak: Int,
    val weekday: Int = -1  // -1 means not a weekly quest or unknown day
)

data class QuestResponse(
    val quests: List<Quest>,
    val perfectClearBonus: Int,
    val dailiesCompleted: Int,
    val totalDailies: Int
)

private fun disableSSLVerification() {
    val trustAllCerts = arrayOf<X509TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
    HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
    HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
}

suspend fun fetchFromApi(endpoint: String): String {
    return withContext(Dispatchers.IO) {
        try {
            disableSSLVerification()
            val url = URL("https://mysololeveling.us$endpoint")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            return@withContext try {
                if (connection.responseCode == 200) connection.inputStream.bufferedReader().readText()
                else throw Exception("HTTP ${connection.responseCode}")
            } finally { connection.disconnect() }
        } catch (e: Exception) {
            throw Exception("Failed to fetch from API: ${e.message}")
        }
    }
}