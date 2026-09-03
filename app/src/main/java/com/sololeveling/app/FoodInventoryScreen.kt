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

// Three statuses: 0 = Out, 1 = Low, 2 = Good. Legacy levels 2/3 both read as Good.
val INV_STATUS_LABELS = listOf("Out", "Low", "Good")
val INV_STATUS_COLORS = listOf(
    Color(0xFFCF6679),  // Out  - red
    Color(0xFFE8944A),  // Low  - amber
    Color(0xFF4CAF50),  // Good - green
)
fun invStatus(level: Int): Int = if (level >= 2) 2 else level

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
                        listOf(2, 1, 0).forEach { st ->
                            val count = items.count { invStatus(it.level) == st }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = count.toString(),
                                    color = INV_STATUS_COLORS[st],
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = INV_STATUS_LABELS[st],
                                    color = Color(0xFF888899),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Tap a status to set it",
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
                            val sorted = group.sortedBy { it.name }
                            items(sorted, key = { "${endpoint}_${it.id}" }) { item ->
                                InventoryItemRow(
                                    item = item,
                                    onSetStatus = { newStatus ->
                                        items = items.map { if (it.id == item.id) it.copy(level = newStatus) else it }
                                        scope.launch {
                                            try {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    patchInventoryLevel(baseUrl, endpoint, item.id, newStatus)
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
                            items(noRoom.sortedBy { it.name }, key = { "${endpoint}_${it.id}" }) { item ->
                                InventoryItemRow(
                                    item = item,
                                    onSetStatus = { newStatus ->
                                        items = items.map { if (it.id == item.id) it.copy(level = newStatus) else it }
                                        scope.launch {
                                            try {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    patchInventoryLevel(baseUrl, endpoint, item.id, newStatus)
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
                    // Flat list, sorted by name only. Status is set inline and the item
                    // stays put — no grouping by status, so nothing jumps when you change it.
                    val sorted = items.sortedBy { it.name }
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sorted, key = { "${endpoint}_${it.id}" }) { item ->
                            InventoryItemRow(
                                item = item,
                                onSetStatus = { newStatus ->
                                    items = items.map { if (it.id == item.id) it.copy(level = newStatus) else it }
                                    scope.launch {
                                        try {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                patchInventoryLevel(baseUrl, endpoint, item.id, newStatus)
                                            }
                                        } catch (e: Exception) {
                                            items = items.map { if (it.id == item.id) it.copy(level = item.level) else it }
                                        }
                                    }
                                }
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryItemRow(item: FoodItem, onSetStatus: (Int) -> Unit) {
    val status = invStatus(item.level)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF12122A))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(INV_STATUS_COLORS[status])
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = if (status == 0) Color(0xFF777788) else Color(0xFFDDDDEE),
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
        Spacer(Modifier.width(8.dp))
        // Good / Low / Out — the active one is filled, the rest outlined.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(2, 1, 0).forEach { st ->
                val active = status == st
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) INV_STATUS_COLORS[st] else Color(0xFF1E1E36))
                        .clickable { if (!active) onSetStatus(st) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = INV_STATUS_LABELS[st],
                        color = if (active) Color(0xFF0A0A1A) else INV_STATUS_COLORS[st].copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
        "supplement" -> InventoryScreen(
            baseUrl = baseUrl,
            endpoint = "/api/supplement-inventory",
            title = "Supplement Supply",
            groupByRoom = false,
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

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { currentView = "supplement" },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12122A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("💊  Supplement Supply", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}