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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

// ─── Data ─────────────────────────────────────────────────────────────────────

data class BookkeepingBill(
    val id: Int,
    val groupName: String,
    val name: String,
    val amount: Double,
    val status: String,
    val sortOrder: Int,
    val autopay: Boolean = false
)

data class BookkeepingMonth(
    val id: Int,
    val month: String,
    val speedxAmount: Double,
    val notes: String = ""
)

val BILL_STATUSES = listOf("NOT PAID", "PAID", "ON HOLD")
val STATUS_COLORS = mapOf(
    "PAID" to Color(0xFF4CAF50),
    "NOT PAID" to Color(0xFF888899),
    "ON HOLD" to Color(0xFF8B6914)
)
val AUTOPAY_BADGE_COLOR = Color(0xFFD4B84A)

// Counts toward Total Income (+ speedxTotal added separately)
val FIXED_INCOME = mapOf(
    "IT Check 1" to 620.0,
    "IT Check 2" to 620.0,
    "IT Check 3" to 620.0,
    "IT Check 4" to 620.0,
    "Plasma" to 520.0
)

val GROUP_ORDER = listOf(
    "IT Check 1", "IT Check 2", "IT Check 3", "IT Check 4", "People",
    "SpeedX Check 1", "SpeedX Check 2", "SpeedX Check 3", "SpeedX Check 4",
    "Subscriptions"
)

// Groups that show no "income · bills" header line and no "Remaining" line
val NO_REMAINING_GROUPS = setOf("People", "Subscriptions")

// ─── Network ──────────────────────────────────────────────────────────────────

fun fetchBookkeepingMonths(baseUrl: String): List<BookkeepingMonth> {
    val url = URL("$baseUrl/api/bookkeeping")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    return try {
        val text = conn.inputStream.bufferedReader().readText()
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            BookkeepingMonth(
                id = obj.getInt("id"),
                month = obj.getString("month"),
                speedxAmount = obj.getDouble("speedx_amount"),
                notes = obj.optString("notes", "")
            )
        }
    } finally { conn.disconnect() }
}

data class BookkeepingDetail(
    val bills: List<BookkeepingBill>,
    val speedxTotal: Double,
    val speedxByCheck: Map<String, Double>,
    val incomeLabels: Map<String, Double>
)

fun fetchBookkeepingDetail(baseUrl: String, monthId: Int): BookkeepingDetail {
    val url = URL("$baseUrl/api/bookkeeping/$monthId")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    return try {
        val text = conn.inputStream.bufferedReader().readText()
        val obj = JSONObject(text)
        val billsArr = obj.getJSONArray("bills")
        val speedxTotal = obj.getDouble("speedxTotal")
        val bills = (0 until billsArr.length()).map { i ->
            val b = billsArr.getJSONObject(i)
            BookkeepingBill(
                id = b.getInt("id"),
                groupName = b.getString("group_name"),
                name = b.getString("name"),
                amount = b.getDouble("amount"),
                status = b.getString("status"),
                sortOrder = b.getInt("sort_order"),
                autopay = b.optInt("autopay", 0) == 1
            )
        }
        val speedxByCheck = mutableMapOf<String, Double>()
        obj.optJSONObject("speedxByCheck")?.let { sc ->
            sc.keys().forEach { k -> speedxByCheck[k] = sc.getDouble(k) }
        }
        val incomeLabels = mutableMapOf<String, Double>()
        obj.optJSONObject("incomeLabels")?.let { il ->
            il.keys().forEach { k -> incomeLabels[k] = il.getDouble(k) }
        }
        BookkeepingDetail(bills, speedxTotal, speedxByCheck, incomeLabels)
    } finally { conn.disconnect() }
}

fun patchBillStatus(baseUrl: String, id: Int, status: String): BookkeepingBill {
    val url = URL("$baseUrl/api/bookkeeping/bills/$id")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "PATCH"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    return try {
        val body = JSONObject().apply { put("status", status) }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val text = conn.inputStream.bufferedReader().readText()
        val b = JSONObject(text)
        BookkeepingBill(
            id = b.getInt("id"),
            groupName = b.getString("group_name"),
            name = b.getString("name"),
            amount = b.getDouble("amount"),
            status = b.getString("status"),
            sortOrder = b.getInt("sort_order"),
            autopay = b.optInt("autopay", 0) == 1
        )
    } finally { conn.disconnect() }
}

fun saveMonthNotes(baseUrl: String, monthId: Int, notes: String) {
    val url = URL("$baseUrl/api/bookkeeping/$monthId/notes")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "PATCH"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    try {
        val body = JSONObject().apply { put("notes", notes) }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.responseCode
    } finally { conn.disconnect() }
}

fun deleteBookkeepingMonth(baseUrl: String, monthId: Int) {
    val url = URL("$baseUrl/api/bookkeeping/$monthId")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "DELETE"
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    try { conn.responseCode } finally { conn.disconnect() }
}

fun currentYearMonth(): String {
    return SimpleDateFormat("yyyy-MM", Locale.US).format(java.util.Date())
}

fun formatMonth(month: String): String {
    return try {
        val parts = month.split("-")
        val year = parts[0]
        val monthNum = parts[1].toInt()
        val monthNames = listOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")
        "${monthNames[monthNum - 1]} $year"
    } catch (e: Exception) { month }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun BookkeepingScreen(baseUrl: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var months by remember { mutableStateOf<List<BookkeepingMonth>>(emptyList()) }
    var selectedMonth by remember { mutableStateOf<BookkeepingMonth?>(null) }
    var bills by remember { mutableStateOf<List<BookkeepingBill>>(emptyList()) }
    var speedxTotal by remember { mutableStateOf(0.0) }
    var speedxByCheck by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var incomeLabels by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var pendingDeleteMonth by remember { mutableStateOf<BookkeepingMonth?>(null) }
    var notesText by remember { mutableStateOf("") }
    var notesStatus by remember { mutableStateOf("Saved") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        try {
            val m = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                fetchBookkeepingMonths(baseUrl)
            }
            months = m
            if (m.isNotEmpty()) {
                val ym = currentYearMonth()
                val defaultMonth = m.find { it.month == ym } ?: m.first()
                selectedMonth = defaultMonth
                notesText = defaultMonth.notes
                val detail = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchBookkeepingDetail(baseUrl, defaultMonth.id)
                }
                bills = detail.bills
                speedxTotal = detail.speedxTotal
                speedxByCheck = detail.speedxByCheck
                incomeLabels = detail.incomeLabels
            }
            isLoading = false
        } catch (e: Exception) {
            errorMsg = e.message
            isLoading = false
        }
    }

    fun loadMonth(month: BookkeepingMonth) {
        selectedMonth = month
        notesText = month.notes
        scope.launch {
            try {
                val detail = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchBookkeepingDetail(baseUrl, month.id)
                }
                bills = detail.bills
                speedxTotal = detail.speedxTotal
                speedxByCheck = detail.speedxByCheck
                incomeLabels = detail.incomeLabels
            } catch (e: Exception) { errorMsg = e.message }
        }
    }

    fun deleteMonth(month: BookkeepingMonth) {
        scope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    deleteBookkeepingMonth(baseUrl, month.id)
                }
                val m = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchBookkeepingMonths(baseUrl)
                }
                months = m
                val newSel = m.firstOrNull()
                selectedMonth = newSel
                notesText = newSel?.notes ?: ""
                if (newSel != null) {
                    val detail = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        fetchBookkeepingDetail(baseUrl, newSel.id)
                    }
                    bills = detail.bills
                    speedxTotal = detail.speedxTotal
                    speedxByCheck = detail.speedxByCheck
                    incomeLabels = detail.incomeLabels
                } else {
                    bills = emptyList()
                }
            } catch (e: Exception) { errorMsg = e.message }
        }
    }

    val totalIncome = FIXED_INCOME.values.sum() + speedxTotal
    val totalExpenses = bills.filter { it.status != "ON HOLD" && it.groupName != "People" }.sumOf { it.amount }

    // Debounced autosave for month notes (only when text differs from the loaded month's notes)
    LaunchedEffect(notesText, selectedMonth?.id) {
        val m = selectedMonth ?: return@LaunchedEffect
        if (notesText == m.notes) return@LaunchedEffect
        notesStatus = "Editing..."
        kotlinx.coroutines.delay(1500)
        if (notesText == (selectedMonth?.notes ?: "")) return@LaunchedEffect
        notesStatus = "Saving..."
        try {
            val mId = m.id
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                saveMonthNotes(baseUrl, mId, notesText)
            }
            selectedMonth = selectedMonth?.copy(notes = notesText)
            months = months.map { if (it.id == mId) it.copy(notes = notesText) else it }
            notesStatus = "Saved"
        } catch (e: Exception) {
            notesStatus = "Save failed"
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A))) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Bookkeeping", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                selectedMonth?.let { Text(formatMonth(it.month), fontSize = 12.sp, color = Color(0xFF888899)) }
            }
            Text("Back", color = Color(0xFFFFD700), fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onBack() })
        }

        pendingDeleteMonth?.let { m ->
            AlertDialog(
                onDismissRequest = { pendingDeleteMonth = null },
                title = { Text("Delete ${formatMonth(m.month)}?") },
                text = { Text("This removes its bills and delivery weeks. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDeleteMonth = null
                        deleteMonth(m)
                    }) { Text("Delete", color = Color(0xFFCF6679), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteMonth = null }) { Text("Cancel") }
                }
            )
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD700))
            }
            errorMsg != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $errorMsg", color = Color(0xFFCF6679))
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    // Month selector
                    if (months.isNotEmpty()) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                months.sortedBy { it.month }.forEach { m ->
                                    val isSelected = m.id == selectedMonth?.id
                                    Row(
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF7B8CDE) else Color(0xFF12122A)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(formatMonth(m.month), fontSize = 12.sp,
                                            color = if (isSelected) Color.White else Color(0xFF888899),
                                            modifier = Modifier.clickable { loadMonth(m) }
                                                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp))
                                        Text("×", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color(0xFF888899),
                                            modifier = Modifier.clickable { pendingDeleteMonth = m }
                                                .padding(end = 10.dp, top = 4.dp, bottom = 4.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Summary
                    item {
                        Column(modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SummaryBox("Income", "$${String.format("%.0f", totalIncome)}", Color(0xFF4CAF50), Modifier.weight(1f))
                                SummaryBox("Expenses", "$${String.format("%.0f", totalExpenses)}", Color(0xFFCF6679), Modifier.weight(1f))
                            }
                            SummaryBox("Plasma", "$520", Color(0xFF4CAF50), Modifier.fillMaxWidth())

                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF12122A)).padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Notes", fontSize = 11.sp, color = Color(0xFF888899))
                                    Text(notesStatus, fontSize = 11.sp, color = Color(0xFF4CAF50))
                                }
                                OutlinedTextField(
                                    value = notesText,
                                    onValueChange = { notesText = it },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    placeholder = { Text("Add a note...", color = Color(0xFF555577)) },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF7B8CDE),
                                        unfocusedBorderColor = Color(0xFF2a2a3a),
                                        cursorColor = Color(0xFF7B8CDE)
                                    ),
                                    minLines = 2
                                )
                            }
                        }
                    }

                    // Bill groups
                    val groupedBills = bills.groupBy { it.groupName }
                    val orderedGroups = (GROUP_ORDER.filter { groupedBills.containsKey(it) } +
                            groupedBills.keys.filter { !GROUP_ORDER.contains(it) }).filter { it != "Plasma" }

                    // People reimbursements fund the Subscriptions bucket
                    val peopleFunding = (groupedBills["People"] ?: emptyList())
                        .filter { it.status != "ON HOLD" }.sumOf { it.amount }

                    items(orderedGroups) { groupName ->
                        val groupBills = groupedBills[groupName] ?: return@items
                        val income = speedxByCheck[groupName] ?: incomeLabels[groupName]
                        val groupTotal = groupBills.filter { it.status != "ON HOLD" }.sumOf { it.amount }
                        val showIncomeLine = income != null && groupName !in NO_REMAINING_GROUPS
                        // Remaining after bills, mirroring web: income-bearing cards use their
                        // income; Subscriptions uses People funding; People (and others) show none.
                        val remaining: Double? = when {
                            income != null && groupName != "People" && groupName != "Subscriptions" -> income - groupTotal
                            groupName == "Subscriptions" -> peopleFunding - groupTotal
                            else -> null
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF12122A)).padding(12.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(groupName, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7B8CDE))
                                if (showIncomeLine) {
                                    Text("$${income!!.toInt()} income · $${groupTotal.toInt()} bills",
                                        fontSize = 11.sp, color = Color(0xFF888899))
                                } else if (groupName == "People") {
                                    Text("Owed: $${groupTotal.toInt()}",
                                        fontSize = 11.sp, color = Color(0xFF4CAF50))
                                } else if (groupName == "Subscriptions") {
                                    Text("$${peopleFunding.toInt()} from People · $${groupTotal.toInt()} bills",
                                        fontSize = 11.sp, color = Color(0xFF4CAF50))
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            groupBills.forEach { bill ->
                                BillRow(bill = bill, onStatusCycle = { newStatus ->
                                    bills = bills.map { if (it.id == bill.id) it.copy(status = newStatus) else it }
                                    scope.launch {
                                        try {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                patchBillStatus(baseUrl, bill.id, newStatus)
                                            }
                                        } catch (e: Exception) {
                                            bills = bills.map { if (it.id == bill.id) it.copy(status = bill.status) else it }
                                        }
                                    }
                                })
                            }
                            if (remaining != null) {
                                Spacer(Modifier.height(6.dp))
                                Text("Remaining after bills: $${"%.2f".format(remaining)}",
                                    fontSize = 11.sp, color = Color(0xFF888899))
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun SummaryBox(label: String, value: String, color: Color, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF12122A)).padding(12.dp)) {
        Column {
            Text(label, fontSize = 11.sp, color = Color(0xFF888899))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun BillRow(bill: BookkeepingBill, onStatusCycle: (String) -> Unit) {
    val statusColor = STATUS_COLORS[bill.status] ?: Color(0xFF888899)
    val nextStatus = BILL_STATUSES[(BILL_STATUSES.indexOf(bill.status) + 1) % BILL_STATUSES.size]

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(bill.name, fontSize = 13.sp, color = Color(0xFFCCCCDD))
            if (bill.autopay) {
                Box(
                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(4.dp))
                        .background(AUTOPAY_BADGE_COLOR.copy(alpha = 0.13f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text("AUTOPAY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AUTOPAY_BADGE_COLOR)
                }
            }
        }
        Text("$${bill.amount.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = Color.White, modifier = Modifier.padding(end = 8.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .clickable { onStatusCycle(nextStatus) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(bill.status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
        }
    }
}