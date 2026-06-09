package com.sololeveling.app

import androidx.activity.compose.BackHandler
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
    val source: String?,
    val level: Int,
    val room: String? = null
)

val LEVEL_LABELS = listOf("Out", "Low", "Medium", "Full")
val LEVEL_COLORS = listOf(
    Color(0xFFCF6679),
    Color(0xFFE8944A),
    Color(0xFFD4B84A),
    Color(0xFF4CAF50),
)

val ROOM_ORDER = listOf("Kitchen", "Garage", "Bedroom", "Bathroom")

// ─── Network ──────────────────────────────────────────────────────────────────

fun fetchInventory(baseUrl: String, endpoint: String): List<FoodItem> {
    val url = URL("$baseUrl$endpoint")
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
                source = if (obj.has("source") && !obj.isNull("source")) obj.getString("source") else null,
                level = obj.getInt("level"),
                room = if (obj.has("room") && !obj.isNull("room")) obj.getString("room") else null
            )
        }
    } finally {
        conn.disconnect()
    }
}

fun patchInventoryLevel(baseUrl: String, endpoint: String, id: Int, level: Int) {
    val url = URL("$baseUrl$endpoint/$id")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "PATCH"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    try {
        val body = JSONObject().apply { put("level", level) }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.inputStream.bufferedReader().readText()
    } finally {
        conn.disconnect()
    }
}

// ─── Shared Inventory Screen ──────────────────────────────────────────────────

@Composable
fun InventoryScreen(
    baseUrl: String,
    endpoint: String,
    title: String,
    groupByRoom: Boolean = false,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchInventory(baseUrl, endpoint)
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
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1a1a1a))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Back",
                color = Color(0xFFFFD700),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onBack() }
            )
        }

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
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(12.dp))

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

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Tap to cycle level",
                        color = Color(0xFF555577),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (groupByRoom) {
                    // Group by room in defined order
                    val roomGroups = ROOM_ORDER.mapNotNull { room ->
                        val group = items.filter { it.room == room }
                        if (group.isNotEmpty()) room to group else null
                    }
                    // Any items without a room
                    val noRoom = items.filter { it.room == null }

                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        roomGroups.forEach { (room, group) ->
                            item(key = "room_$room") {
                                Text(
                                    text = room.uppercase(),
                                    color = Color(0xFF7B8CDE),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            val sorted = group.sortedWith(compareBy({ it.level }, { it.name }))
                            items(sorted, key = { "${endpoint}_${it.id}" }) { item ->
                                InventoryItemRow(
                                    item = item,
                                    onCycleLevel = { newLevel ->
                                        items = items.map { if (it.id == item.id) it.copy(level = newLevel) else it }
                                        scope.launch {
                                            try {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    patchInventoryLevel(baseUrl, endpoint, item.id, newLevel)
                                                }
                                            } catch (e: Exception) {
                                                items = items.map { if (it.id == item.id) it.copy(level = item.level) else it }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        if (noRoom.isNotEmpty()) {
                            item(key = "room_other") {
                                Text(
                                    text = "OTHER",
                                    color = Color(0xFF7B8CDE),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(noRoom, key = { "${endpoint}_${it.id}" }) { item ->
                                InventoryItemRow(
                                    item = item,
                                    onCycleLevel = { newLevel ->
                                        items = items.map { if (it.id == item.id) it.copy(level = newLevel) else it }
                                        scope.launch {
                                            try {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    patchInventoryLevel(baseUrl, endpoint, item.id, newLevel)
                                                }
                                            } catch (e: Exception) {
                                                items = items.map { if (it.id == item.id) it.copy(level = item.level) else it }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                } else {
                    // Group by level (food inventory)
                    val grouped = listOf(0, 1, 2, 3).mapNotNull { lvl ->
                        val group = items.filter { it.level == lvl }
                        if (group.isNotEmpty()) lvl to group else null
                    }
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            items(group, key = { "${endpoint}_${it.id}" }) { item ->
                                InventoryItemRow(
                                    item = item,
                                    onCycleLevel = { newLevel ->
                                        items = items.map { if (it.id == item.id) it.copy(level = newLevel) else it }
                                        scope.launch {
                                            try {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    patchInventoryLevel(baseUrl, endpoint, item.id, newLevel)
                                                }
                                            } catch (e: Exception) {
                                                items = items.map { if (it.id == item.id) it.copy(level = item.level) else it }
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
}

@Composable
fun InventoryItemRow(item: FoodItem, onCycleLevel: (Int) -> Unit) {
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
            if (!item.source.isNullOrEmpty()) {
                Text(
                    text = item.source,
                    color = Color(0xFF444466),
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.width(10.dp))
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

// ─── Supplies Hub Screen ──────────────────────────────────────────────────────

@Composable
fun SuppliesScreen(baseUrl: String) {
    var currentView by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = currentView != null) {
        currentView = null
    }

    when (currentView) {
        "food" -> InventoryScreen(
            baseUrl = baseUrl,
            endpoint = "/api/food-inventory",
            title = "Food Supply",
            groupByRoom = false,
            onBack = { currentView = null }
        )
        "household" -> InventoryScreen(
            baseUrl = baseUrl,
            endpoint = "/api/household-inventory",
            title = "Household Supply",
            groupByRoom = true,
            onBack = { currentView = null }
        )
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A1A))
                    .padding(16.dp)
            ) {
                Text(
                    text = "SUPPLIES",
                    color = Color(0xFF7B8CDE),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 24.dp)
                )

                Button(
                    onClick = { currentView = "food" },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12122A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🥩  Food Supply", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { currentView = "household" },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12122A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🧴  Household Supply", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}