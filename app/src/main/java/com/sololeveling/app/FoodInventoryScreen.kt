package com.sololeveling.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ─── Data ─────────────────────────────────────────────────────────────────────

data class FoodItem(
    val id: Int,
    val name: String,
    val source: String,
    val level: Int  // 0=Out, 1=Low, 2=Medium, 3=Full
)

// 0=Out, 1=Low, 2=Medium, 3=Full
val LEVEL_LABELS = listOf("Out", "Low", "Medium", "Full")
val LEVEL_COLORS = listOf(
    Color(0xFFCF6679),  // Out — red
    Color(0xFFE8944A),  // Low — orange
    Color(0xFFD4B84A),  // Medium — yellow
    Color(0xFF4CAF50),  // Full — green
)

// ─── Network ──────────────────────────────────────────────────────────────────

fun fetchFoodInventory(baseUrl: String): List<FoodItem> {
    val url = URL("$baseUrl/api/food-inventory")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    return try {
        val text = conn.inputStream.bufferedReader().readText()
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            FoodItem(
                id = obj.getInt("id"),
                name = obj.getString("name"),
                source = obj.getString("source"),
                level = obj.getInt("level")
            )
        }
    } finally {
        conn.disconnect()
    }
}

fun patchFoodLevel(baseUrl: String, id: Int, level: Int): FoodItem? {
    val url = URL("$baseUrl/api/food-inventory/$id")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "PATCH"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    return try {
        val body = JSONObject().apply { put("level", level) }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val text = conn.inputStream.bufferedReader().readText()
        val obj = JSONObject(text)
        FoodItem(
            id = obj.getInt("id"),
            name = obj.getString("name"),
            source = obj.getString("source"),
            level = obj.getInt("level")
        )
    } finally {
        conn.disconnect()
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun FoodInventoryScreen(baseUrl: String) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Group order: Out first, then Low, Medium, Full
    val grouped = remember(items) {
        listOf(0, 1, 2, 3).mapNotNull { lvl ->
            val group = items.filter { it.level == lvl }
            if (group.isNotEmpty()) lvl to group else null
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchFoodInventory(baseUrl)
                }
                isLoading = false
            } catch (e: Exception) {
                errorMsg = e.message
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A))
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Text(
            text = "FOOD SUPPLY",
            color = Color(0xFF7B8CDE),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
        Text(
            text = "Tap an item to cycle its level",
            color = Color(0xFF555577),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF7B8CDE))
                }
            }
            errorMsg != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: $errorMsg", color = Color(0xFFCF6679))
                }
            }
            else -> {
                // Summary bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF12122A))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(0, 1, 2, 3).forEach { lvl ->
                        val count = items.count { it.level == lvl }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = count.toString(),
                                color = LEVEL_COLORS[lvl],
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = LEVEL_LABELS[lvl],
                                color = Color(0xFF888899),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grouped.forEach { (lvl, group) ->
                        item(key = "header_$lvl") {
                            Text(
                                text = LEVEL_LABELS[lvl].uppercase(),
                                color = LEVEL_COLORS[lvl].copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(group, key = { it.id }) { item ->
                            FoodItemRow(
                                item = item,
                                onCycleLevel = { newLevel ->
                                    // Optimistic update
                                    items = items.map {
                                        if (it.id == item.id) it.copy(level = newLevel) else it
                                    }
                                    scope.launch {
                                        try {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                patchFoodLevel(baseUrl, item.id, newLevel)
                                            }
                                        } catch (e: Exception) {
                                            // Revert on failure
                                            items = items.map {
                                                if (it.id == item.id) it.copy(level = item.level) else it
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun FoodItemRow(item: FoodItem, onCycleLevel: (Int) -> Unit) {
    val nextLevel = (item.level + 3) % 4

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF12122A))
            .clickable { onCycleLevel(nextLevel) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Level indicator bar on left
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LEVEL_COLORS[item.level])
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = if (item.level == 0) Color(0xFF777788) else Color(0xFFDDDDEE),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.source,
                color = Color(0xFF444466),
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.width(10.dp))

        // Level chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(LEVEL_COLORS[item.level].copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = LEVEL_LABELS[item.level],
                color = LEVEL_COLORS[item.level],
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}