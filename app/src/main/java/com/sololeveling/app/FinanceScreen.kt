package com.sololeveling.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// ─── Data ─────────────────────────────────────────────────────────────────────

data class DeliveryWeek(
    val id: Int,
    val weekStart: String,
    val tueDelivered: Int,
    val tueDuplicates: Int,
    val tueUndeliverable: Int,
    val wedDelivered: Int,
    val wedDuplicates: Int,
    val wedUndeliverable: Int
) {
    val tueBillable: Int get() = maxOf(0, tueDelivered - tueDuplicates - tueUndeliverable)
    val wedBillable: Int get() = maxOf(0, wedDelivered - wedDuplicates - wedUndeliverable)
    val totalBillable: Int get() = tueBillable + wedBillable
    val totalPay: Double get() = totalBillable * 1.60
    val tuePay: Double get() = tueBillable * 1.60
    val wedPay: Double get() = wedBillable * 1.60

    fun payDate(): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val start = sdf.parse(weekStart) ?: return ""
            val cal = Calendar.getInstance()
            cal.time = start
            cal.add(Calendar.DAY_OF_MONTH, 3) // Sunday + 3 = Wednesday
            cal.add(Calendar.DAY_OF_MONTH, 16) // Wednesday + 16
            SimpleDateFormat("MMM d", Locale.US).format(cal.time)
        } catch (e: Exception) { "" }
    }

    fun weekLabel(): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val start = sdf.parse(weekStart) ?: return weekStart
            val end = Calendar.getInstance().apply {
                time = start
                add(Calendar.DAY_OF_MONTH, 6)
            }.time
            val fmt = SimpleDateFormat("MMM d", Locale.US)
            "${fmt.format(start)} - ${fmt.format(end)}"
        } catch (e: Exception) { weekStart }
    }
}

// ─── Network ──────────────────────────────────────────────────────────────────

fun fetchDeliveryWeeks(baseUrl: String): List<DeliveryWeek> {
    val url = URL("$baseUrl/api/delivery-weeks")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    return try {
        val text = conn.inputStream.bufferedReader().readText()
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            DeliveryWeek(
                id = obj.getInt("id"),
                weekStart = obj.getString("week_start"),
                tueDelivered = obj.getInt("tue_delivered"),
                tueDuplicates = obj.getInt("tue_duplicates"),
                tueUndeliverable = obj.getInt("tue_undeliverable"),
                wedDelivered = obj.getInt("wed_delivered"),
                wedDuplicates = obj.getInt("wed_duplicates"),
                wedUndeliverable = obj.getInt("wed_undeliverable")
            )
        }
    } finally {
        conn.disconnect()
    }
}

fun patchDeliveryWeek(baseUrl: String, week: DeliveryWeek): DeliveryWeek {
    val url = URL("$baseUrl/api/delivery-weeks/${week.id}")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "PATCH"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    return try {
        val body = JSONObject().apply {
            put("tue_delivered", week.tueDelivered)
            put("tue_duplicates", week.tueDuplicates)
            put("tue_undeliverable", week.tueUndeliverable)
            put("wed_delivered", week.wedDelivered)
            put("wed_duplicates", week.wedDuplicates)
            put("wed_undeliverable", week.wedUndeliverable)
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val text = conn.inputStream.bufferedReader().readText()
        val obj = JSONObject(text)
        DeliveryWeek(
            id = obj.getInt("id"),
            weekStart = obj.getString("week_start"),
            tueDelivered = obj.getInt("tue_delivered"),
            tueDuplicates = obj.getInt("tue_duplicates"),
            tueUndeliverable = obj.getInt("tue_undeliverable"),
            wedDelivered = obj.getInt("wed_delivered"),
            wedDuplicates = obj.getInt("wed_duplicates"),
            wedUndeliverable = obj.getInt("wed_undeliverable")
        )
    } finally {
        conn.disconnect()
    }
}

fun deleteDeliveryWeek(baseUrl: String, id: Int) {
    val url = URL("$baseUrl/api/delivery-weeks/$id")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "DELETE"
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    try {
        conn.responseCode
    } finally {
        conn.disconnect()
    }
}

@Composable
fun FinanceScreen(baseUrl: String) {
    val scope = rememberCoroutineScope()
    var weeks by remember { mutableStateOf<List<DeliveryWeek>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            weeks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                fetchDeliveryWeeks(baseUrl)
            }
            isLoading = false
        } catch (e: Exception) {
            errorMsg = e.message
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A))
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1a1a1a))
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text("Finance", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Delivery Package Tracker", fontSize = 13.sp, color = Color(0xFF888899))
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD700))
            }
            errorMsg != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $errorMsg", color = Color(0xFFCF6679))
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(weeks, key = { it.id }) { week ->
                        DeliveryWeekCard(
                            week = week,
                            onSave = { updated ->
                                weeks = weeks.map { if (it.id == updated.id) updated else it }
                                scope.launch {
                                    try {
                                        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            patchDeliveryWeek(baseUrl, updated)
                                        }
                                        weeks = weeks.map { if (it.id == result.id) result else it }
                                    } catch (e: Exception) {
                                        weeks = weeks.map { if (it.id == week.id) week else it }
                                    }
                                }
                            },
                            onDelete = { id ->
                                weeks = weeks.filter { it.id != id }
                                scope.launch {
                                    try {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            deleteDeliveryWeek(baseUrl, id)
                                        }
                                    } catch (e: Exception) {
                                        // refetch on failure
                                        try {
                                            weeks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                fetchDeliveryWeeks(baseUrl)
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun DeliveryWeekCard(week: DeliveryWeek, onSave: (DeliveryWeek) -> Unit, onDelete: (Int) -> Unit) {
    var expandedDay by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Tuesday edit state
    var tueDelivered by remember(week) { mutableStateOf(week.tueDelivered.toString()) }
    var tueDuplicates by remember(week) { mutableStateOf(week.tueDuplicates.toString()) }
    var tueUndeliverable by remember(week) { mutableStateOf(week.tueUndeliverable.toString()) }

    // Wednesday edit state
    var wedDelivered by remember(week) { mutableStateOf(week.wedDelivered.toString()) }
    var wedDuplicates by remember(week) { mutableStateOf(week.wedDuplicates.toString()) }
    var wedUndeliverable by remember(week) { mutableStateOf(week.wedUndeliverable.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF12122A))
    ) {
        // Week header
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Week of ${week.weekLabel()}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7B8CDE)
            )
            Spacer(Modifier.height(12.dp))

            // Tuesday row
            DayRow(
                label = "Tuesday",
                billable = week.tueBillable,
                pay = week.tuePay,
                isExpanded = expandedDay == "tue",
                onClick = { expandedDay = if (expandedDay == "tue") null else "tue" }
            )

            if (expandedDay == "tue") {
                Spacer(Modifier.height(10.dp))
                DeliveryInputRow(
                    delivered = tueDelivered,
                    duplicates = tueDuplicates,
                    undeliverable = tueUndeliverable,
                    onDeliveredChange = { tueDelivered = it },
                    onDuplicatesChange = { tueDuplicates = it },
                    onUndeliverableChange = { tueUndeliverable = it },
                    onSave = {
                        expandedDay = null
                        onSave(week.copy(
                            tueDelivered = tueDelivered.toIntOrNull() ?: 0,
                            tueDuplicates = tueDuplicates.toIntOrNull() ?: 0,
                            tueUndeliverable = tueUndeliverable.toIntOrNull() ?: 0
                        ))
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))

            // Wednesday row
            DayRow(
                label = "Wednesday",
                billable = week.wedBillable,
                pay = week.wedPay,
                isExpanded = expandedDay == "wed",
                onClick = { expandedDay = if (expandedDay == "wed") null else "wed" }
            )

            if (expandedDay == "wed") {
                Spacer(Modifier.height(10.dp))
                DeliveryInputRow(
                    delivered = wedDelivered,
                    duplicates = wedDuplicates,
                    undeliverable = wedUndeliverable,
                    onDeliveredChange = { wedDelivered = it },
                    onDuplicatesChange = { wedDuplicates = it },
                    onUndeliverableChange = { wedUndeliverable = it },
                    onSave = {
                        expandedDay = null
                        onSave(week.copy(
                            wedDelivered = wedDelivered.toIntOrNull() ?: 0,
                            wedDuplicates = wedDuplicates.toIntOrNull() ?: 0,
                            wedUndeliverable = wedUndeliverable.toIntOrNull() ?: 0
                        ))
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            // Divider
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2a2a3a)))
            Spacer(Modifier.height(12.dp))

            // Weekly total + pay date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Weekly Total", fontSize = 12.sp, color = Color(0xFF888899))
                    Text(
                        text = "${week.totalBillable} packages",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$${String.format("%.2f", week.totalPay)}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "💰 Pay Date: ${week.payDate()}",
                        fontSize = 11.sp,
                        color = Color(0xFF888899)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (showDeleteConfirm) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showDeleteConfirm = false },
                        modifier = Modifier.weight(1f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2a2a3a)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onDelete(week.id) },
                        modifier = Modifier.weight(1f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Delete", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Got my check — remove week", color = Color(0xFF555577), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun DayRow(label: String, billable: Int, pay: Double, isExpanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1a1a2e))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Text(text = "$billable billable packages", fontSize = 11.sp, color = Color(0xFF888899))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "$${String.format("%.2f", pay)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )
            Text(text = if (isExpanded) "▲" else "▼", fontSize = 11.sp, color = Color(0xFF7B8CDE))
        }
    }
}

@Composable
fun DeliveryInputRow(
    delivered: String,
    duplicates: String,
    undeliverable: String,
    onDeliveredChange: (String) -> Unit,
    onDuplicatesChange: (String) -> Unit,
    onUndeliverableChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0e0e1e))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeliveryField("Delivered", delivered, onDeliveredChange, Modifier.weight(1f))
            DeliveryField("Duplicates", duplicates, onDuplicatesChange, Modifier.weight(1f))
            DeliveryField("Undel.", undeliverable, onUndeliverableChange, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        // Live preview
        val del = delivered.toIntOrNull() ?: 0
        val dup = duplicates.toIntOrNull() ?: 0
        val und = undeliverable.toIntOrNull() ?: 0
        val billable = maxOf(0, del - dup - und)
        val pay = billable * 1.60
        Text(
            text = "$del - $dup - $und = $billable packages → $${String.format("%.2f", pay)}",
            fontSize = 12.sp,
            color = Color(0xFF7B8CDE),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B8CDE)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Save", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DeliveryField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) onChange(it) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF7B8CDE),
                unfocusedBorderColor = Color(0xFF2a2a3a),
                cursorColor = Color(0xFF7B8CDE)
            )
        )
        Text(text = label, fontSize = 10.sp, color = Color(0xFF888899), modifier = Modifier.padding(top = 2.dp))
    }
}