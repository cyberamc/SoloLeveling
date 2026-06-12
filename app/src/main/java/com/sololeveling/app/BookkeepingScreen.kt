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

// ─── Data ─────────────────────────────────────────────────────────────────────

data class BookkeepingBill(
    val id: Int,
    val groupName: String,
    val name: String,
    val amount: Double,
    val status: String,
    val sortOrder: Int
)

data class BookkeepingMonth(
    val id: Int,
    val month: String,
    val speedxAmount: Double
)

val BILL_STATUSES = listOf("NOT PAID", "PAID", "AUTOPAY", "ON HOLD")
val STATUS_COLORS = mapOf(
    "PAID" to Color(0xFF4CAF50),
    "NOT PAID" to Color(0xFF888899),
    "AUTOPAY" to Color(0xFFD4B84A),
    "ON HOLD" to Color(0xFF8B6914)
)

val FIXED_INCOME = mapOf(
    "Check 1" to 590.0,
    "Check 2" to 581.0,
    "Check 3" to 587.0,
    "Check 4" to 578.0,
    "Plasma" to 520.0
)

val GROUP_ORDER = listOf(
    "Check 1", "Check 2", "Check 3", "Check 4", "Plasma",
    "SpeedX Check 1", "SpeedX Check 2", "SpeedX Check 3", "SpeedX Check 4",
    "People", "Subscriptions"
)

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
                speedxAmount = obj.getDouble("speedx_amount")
            )
        }
    } finally { conn.disconnect() }
}

fun fetchBookkeepingDetail(baseUrl: String, monthId: Int): Pair<List<BookkeepingBill>, Double> {
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
                sortOrder = b.getInt("sort_order")
            )
        }
        Pair(bills, speedxTotal)
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
            sortOrder = b.getInt("sort_order")
        )
    } finally { conn.disconnect() }
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
                selectedMonth = m.first()
                val (b, s) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchBookkeepingDetail(baseUrl, m.first().id)
                }
                bills = b
                speedxTotal = s
            }
            isLoading = false
        } catch (e: Exception) {
            errorMsg = e.message
            isLoading = false
        }
    }

    fun loadMonth(month: BookkeepingMonth) {
        selectedMonth = month
        scope.launch {
            try {
                val (b, s) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchBookkeepingDetail(baseUrl, month.id)
                }
                bills = b
                speedxTotal = s
            } catch (e: Exception) { errorMsg = e.message }
        }
    }

    val totalIncome = FIXED_INCOME.values.sum() + speedxTotal
    val totalExpenses = bills.sumOf { it.amount }
    val paidExpenses = bills.filter { it.status == "PAID" || it.status == "AUTOPAY" }.sumOf { it.amount }

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
                    if (months.size > 1) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                months.forEach { m ->
                                    val isSelected = m.id == selectedMonth?.id
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF7B8CDE) else Color(0xFF12122A))
                                            .clickable { loadMonth(m) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(formatMonth(m.month), fontSize = 12.sp,
                                            color = if (isSelected) Color.White else Color(0xFF888899))
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
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SummaryBox("Paid", "$${String.format("%.0f", paidExpenses)}", Color(0xFFD4B84A), Modifier.weight(1f))
                                SummaryBox("Remaining", "$${String.format("%.0f", totalExpenses - paidExpenses)}", Color(0xFF7B8CDE), Modifier.weight(1f))
                            }
                            SummaryBox("SpeedX (Delivery)", "$${String.format("%.2f", speedxTotal)}", Color(0xFF4CAF50), Modifier.fillMaxWidth())
                        }
                    }

                    // Bill groups
                    val groupedBills = bills.groupBy { it.groupName }
                    val orderedGroups = GROUP_ORDER.filter { groupedBills.containsKey(it) } +
                            groupedBills.keys.filter { !GROUP_ORDER.contains(it) }

                    items(orderedGroups) { groupName ->
                        val groupBills = groupedBills[groupName] ?: return@items
                        val income = FIXED_INCOME[groupName]
                        val groupTotal = groupBills.sumOf { it.amount }

                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF12122A)).padding(12.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(groupName, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7B8CDE))
                                if (income != null) {
                                    Text("$${income.toInt()} income · $${groupTotal.toInt()} bills",
                                        fontSize = 11.sp, color = Color(0xFF888899))
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
        Text(bill.name, fontSize = 13.sp, color = Color(0xFFCCCCDD),
            modifier = Modifier.weight(1f).padding(end = 8.dp))
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