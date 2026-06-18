package com.sololeveling.app

import androidx.activity.compose.BackHandler
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

// The YYYY-MM that today's delivery week pays out in.
// Today's week_start (Sunday) -> Wednesday (+3) -> pay date (+16) -> that month.
fun currentDeliveryPayMonth(): String {
    val cal = Calendar.getInstance()
    // Back up to this week's Sunday
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        cal.add(Calendar.DAY_OF_MONTH, -1)
    }
    // Sunday + 3 = Wednesday, + 16 = pay date
    cal.add(Calendar.DAY_OF_MONTH, 19)
    return SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
}

data class DeliveryWeek(
    val id: Int,
    val weekStart: String,
    val checkNumber: Int,
    val tueDelivered: Int,
    val tueDuplicates: Int,
    val tueUndeliverable: Int,
    val wedDelivered: Int,
    val wedDuplicates: Int,
    val wedUndeliverable: Int,
    val tueRoute324: Boolean = false,
    val wedRoute324: Boolean = false
) {
    val tueBillable: Int get() = maxOf(0, tueDelivered - tueDuplicates - tueUndeliverable)
    val wedBillable: Int get() = maxOf(0, wedDelivered - wedDuplicates - wedUndeliverable)
    val tueRate: Double get() = if (tueRoute324) 1.90 else 1.60
    val wedRate: Double get() = if (wedRoute324) 1.90 else 1.60
    val totalBillable: Int get() = tueBillable + wedBillable
    val tuePay: Double get() = tueBillable * tueRate
    val wedPay: Double get() = wedBillable * wedRate
    val totalPay: Double get() = tuePay + wedPay

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

fun fetchDeliveryWeeks(baseUrl: String, monthId: Int? = null): List<DeliveryWeek> {
    val url = URL("$baseUrl/api/delivery-weeks" + (if (monthId != null) "?month_id=$monthId" else ""))
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
                checkNumber = obj.optInt("check_number", 0),
                tueDelivered = obj.getInt("tue_delivered"),
                tueDuplicates = obj.getInt("tue_duplicates"),
                tueUndeliverable = obj.getInt("tue_undeliverable"),
                wedDelivered = obj.getInt("wed_delivered"),
                wedDuplicates = obj.getInt("wed_duplicates"),
                wedUndeliverable = obj.getInt("wed_undeliverable"),
                tueRoute324 = obj.optInt("tue_route324", 0) == 1,
                wedRoute324 = obj.optInt("wed_route324", 0) == 1
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
            put("tue_route324", if (week.tueRoute324) 1 else 0)
            put("wed_route324", if (week.wedRoute324) 1 else 0)
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val text = conn.inputStream.bufferedReader().readText()
        val obj = JSONObject(text)
        DeliveryWeek(
            id = obj.getInt("id"),
            weekStart = obj.getString("week_start"),
            checkNumber = obj.optInt("check_number", 0),
            tueDelivered = obj.getInt("tue_delivered"),
            tueDuplicates = obj.getInt("tue_duplicates"),
            tueUndeliverable = obj.getInt("tue_undeliverable"),
            wedDelivered = obj.getInt("wed_delivered"),
            wedDuplicates = obj.getInt("wed_duplicates"),
            wedUndeliverable = obj.getInt("wed_undeliverable"),
            tueRoute324 = obj.optInt("tue_route324", 0) == 1,
            wedRoute324 = obj.optInt("wed_route324", 0) == 1
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
fun DeliveryWeekCard(week: DeliveryWeek, onSave: (DeliveryWeek) -> Unit) {
    var expandedDay by remember { mutableStateOf<String?>(null) }

    var tueDelivered by remember(week) { mutableStateOf(week.tueDelivered.toString()) }
    var tueDuplicates by remember(week) { mutableStateOf(week.tueDuplicates.toString()) }
    var tueUndeliverable by remember(week) { mutableStateOf(week.tueUndeliverable.toString()) }
    var wedDelivered by remember(week) { mutableStateOf(week.wedDelivered.toString()) }
    var wedDuplicates by remember(week) { mutableStateOf(week.wedDuplicates.toString()) }
    var wedUndeliverable by remember(week) { mutableStateOf(week.wedUndeliverable.toString()) }
    var tueRoute324 by remember(week) { mutableStateOf(week.tueRoute324) }
    var wedRoute324 by remember(week) { mutableStateOf(week.wedRoute324) }

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF12122A))) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Week of ${week.weekLabel()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B8CDE))
                if (week.checkNumber > 0) {
                    Text(text = "SpeedX Check ${week.checkNumber}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4CAF50))
                }
            }
            Spacer(Modifier.height(12.dp))

            DayRow(label = "Tuesday", billable = week.tueBillable, pay = week.tuePay,
                isExpanded = expandedDay == "tue", onClick = { expandedDay = if (expandedDay == "tue") null else "tue" })

            if (expandedDay == "tue") {
                Spacer(Modifier.height(10.dp))
                Route324Toggle(checked = tueRoute324, onCheckedChange = { tueRoute324 = it })
                Spacer(Modifier.height(10.dp))
                DeliveryInputRow(delivered = tueDelivered, duplicates = tueDuplicates, undeliverable = tueUndeliverable,
                    onDeliveredChange = { tueDelivered = it }, onDuplicatesChange = { tueDuplicates = it },
                    onUndeliverableChange = { tueUndeliverable = it },
                    onSave = {
                        expandedDay = null
                        onSave(week.copy(tueDelivered = tueDelivered.toIntOrNull() ?: 0,
                            tueDuplicates = tueDuplicates.toIntOrNull() ?: 0,
                            tueUndeliverable = tueUndeliverable.toIntOrNull() ?: 0,
                            tueRoute324 = tueRoute324))
                    })
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))

            DayRow(label = "Wednesday", billable = week.wedBillable, pay = week.wedPay,
                isExpanded = expandedDay == "wed", onClick = { expandedDay = if (expandedDay == "wed") null else "wed" })

            if (expandedDay == "wed") {
                Spacer(Modifier.height(10.dp))
                Route324Toggle(checked = wedRoute324, onCheckedChange = { wedRoute324 = it })
                Spacer(Modifier.height(10.dp))
                DeliveryInputRow(delivered = wedDelivered, duplicates = wedDuplicates, undeliverable = wedUndeliverable,
                    onDeliveredChange = { wedDelivered = it }, onDuplicatesChange = { wedDuplicates = it },
                    onUndeliverableChange = { wedUndeliverable = it },
                    onSave = {
                        expandedDay = null
                        onSave(week.copy(wedDelivered = wedDelivered.toIntOrNull() ?: 0,
                            wedDuplicates = wedDuplicates.toIntOrNull() ?: 0,
                            wedUndeliverable = wedUndeliverable.toIntOrNull() ?: 0,
                            wedRoute324 = wedRoute324))
                    })
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2a2a3a)))
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Weekly Total", fontSize = 12.sp, color = Color(0xFF888899))
                    Text("${week.totalBillable} packages", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$${String.format("%.2f", week.totalPay)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Text("💰 Pay Date: ${week.payDate()}", fontSize = 11.sp, color = Color(0xFF888899))
                }
            }
        }
    }
}

@Composable
fun Route324Toggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) Color(0x1A4CAF50) else Color(0xFF0e0e1e))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (checked) "$1.90 · Route 324" else "$1.60 · Standard",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (checked) Color(0xFF4CAF50) else Color(0xFFB0B0B0)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF4CAF50),
                uncheckedThumbColor = Color(0xFFB0B0B0),
                uncheckedTrackColor = Color(0xFF2a2a3a)
            )
        )
    }
}

@Composable
fun DayRow(label: String, billable: Int, pay: Double, isExpanded: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1a1a2e))
        .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Text(text = "$billable billable packages", fontSize = 11.sp, color = Color(0xFF888899))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "$${String.format("%.2f", pay)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
            Text(text = if (isExpanded) "▲" else "▼", fontSize = 11.sp, color = Color(0xFF7B8CDE))
        }
    }
}

@Composable
fun DeliveryInputRow(delivered: String, duplicates: String, undeliverable: String,
                     onDeliveredChange: (String) -> Unit, onDuplicatesChange: (String) -> Unit,
                     onUndeliverableChange: (String) -> Unit, onSave: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0e0e1e)).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeliveryField("Delivered", delivered, onDeliveredChange, Modifier.weight(1f))
            DeliveryField("Duplicates", duplicates, onDuplicatesChange, Modifier.weight(1f))
            DeliveryField("Undel.", undeliverable, onUndeliverableChange, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        val del = delivered.toIntOrNull() ?: 0
        val dup = duplicates.toIntOrNull() ?: 0
        val und = undeliverable.toIntOrNull() ?: 0
        val billable = maxOf(0, del - dup - und)
        val pay = billable * 1.60
        Text(text = "$del - $dup - $und = $billable packages → $${String.format("%.2f", pay)}",
            fontSize = 12.sp, color = Color(0xFF7B8CDE), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B8CDE)), shape = RoundedCornerShape(8.dp)) {
            Text("Save", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DeliveryField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(value = value,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) onChange(it) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = Color.White, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF7B8CDE),
                unfocusedBorderColor = Color(0xFF2a2a3a), cursorColor = Color(0xFF7B8CDE)))
        Text(text = label, fontSize = 10.sp, color = Color(0xFF888899), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun DeliveryTrackerScreen(baseUrl: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var weeks by remember { mutableStateOf<List<DeliveryWeek>>(emptyList()) }
    var months by remember { mutableStateOf<List<BookkeepingMonth>>(emptyList()) }
    var selectedMonth by remember { mutableStateOf<BookkeepingMonth?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    fun loadWeeks(month: BookkeepingMonth) {
        selectedMonth = month
        scope.launch {
            try {
                weeks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchDeliveryWeeks(baseUrl, month.id)
                }
            } catch (e: Exception) { errorMsg = e.message }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val m = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                fetchBookkeepingMonths(baseUrl)
            }
            months = m
            val sel = m.find { it.month == currentDeliveryPayMonth() } ?: m.firstOrNull()
            selectedMonth = sel
            weeks = if (sel != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchDeliveryWeeks(baseUrl, sel.id)
                }
            } else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchDeliveryWeeks(baseUrl)
                }
            }
            isLoading = false
        } catch (e: Exception) {
            errorMsg = e.message
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Delivery Tracker", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Package tracking", fontSize = 13.sp, color = Color(0xFF888899))
            }
            Text("Back", color = Color(0xFFFFD700), fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onBack() })
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD700))
            }
            errorMsg != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $errorMsg", color = Color(0xFFCF6679))
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (months.isNotEmpty()) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                months.sortedBy { it.month }.forEach { m ->
                                    val isSelected = m.id == selectedMonth?.id
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF7B8CDE) else Color(0xFF12122A))
                                            .clickable { loadWeeks(m) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(formatMonth(m.month), fontSize = 12.sp,
                                            color = if (isSelected) Color.White else Color(0xFF888899))
                                    }
                                }
                            }
                        }
                    }
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
fun FinanceScreen(baseUrl: String) {
    var currentView by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = currentView != null) { currentView = null }

    when (currentView) {
        "delivery" -> DeliveryTrackerScreen(baseUrl = baseUrl, onBack = { currentView = null })
        "bookkeeping" -> BookkeepingScreen(baseUrl = baseUrl, onBack = { currentView = null })
        else -> {
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A)).padding(16.dp)) {
                Text("FINANCE", color = Color(0xFF7B8CDE), fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 24.dp))

                Button(
                    onClick = { currentView = "delivery" },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12122A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📦  Delivery Tracker", color = Color.White, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { currentView = "bookkeeping" },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12122A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("💰  Bookkeeping", color = Color.White, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}