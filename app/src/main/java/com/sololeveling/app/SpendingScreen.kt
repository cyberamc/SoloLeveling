package com.sololeveling.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

// ─── Spending tracker (Finance) ───────────────────────────────────────────────

private val SpBg = Color(0xFF0A0A1A)
private val SpCard = Color(0xFF12122A)
private val SpInner = Color(0xFF0E0E1E)
private val SpAccent = Color(0xFF7B8CDE)
private val SpGreen = Color(0xFF4CAF50)
private val SpRed = Color(0xFFCF6679)
private val SpGold = Color(0xFFFFD700)
private val SpOrange = Color(0xFFE8944A)
private val SpMuted = Color(0xFF888899)

data class SpPending(val kind: String, val id: Int, val name: String, val sub: String, val amount: Double)
data class SpEntry(val id: Int, val amount: Double, val category: String, val note: String, val spentOn: String, val cleared: Boolean)
data class SpBillDue(val name: String, val amount: Double, val dueDay: Int?)
data class SpSummary(
    val today: String,
    val categories: List<String>,
    val balance: Double?,
    val balanceAt: String?,
    val pending: List<SpPending>,
    val pendingTotal: Double,
    val available: Double?,
    val paydayDate: String?,
    val paydayLabel: String?,
    val paydayDays: Int,
    val billsDue: List<SpBillDue>,
    val billsDueTotal: Double,
    val safe: Double?,
    val perDay: Double?,
    val perWeek: Double?,
    val spentToday: Double,
    val spentWeek: Double,
    val spentMonth: Double,
    val byCategory: List<Pair<String, Double>>
)

fun spMoney(v: Double): String =
    (if (v < 0) "-" else "") + "\$" + String.format(Locale.US, "%,.2f", Math.abs(v))

private fun spCall(baseUrl: String, method: String, path: String, body: JSONObject? = null): String {
    val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
    conn.requestMethod = method
    conn.connectTimeout = 5000
    conn.readTimeout = 8000
    if (body != null) {
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
    }
    return try {
        if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        stream?.bufferedReader()?.readText() ?: ""
    } finally {
        conn.disconnect()
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else getDouble(key)

fun fetchSpendingSummary(baseUrl: String): SpSummary {
    val o = JSONObject(spCall(baseUrl, "GET", "/api/spending/summary"))
    val cats = o.getJSONArray("categories").let { a -> (0 until a.length()).map { a.getString(it) } }
    val pend = o.getJSONArray("pending").let { a ->
        (0 until a.length()).map {
            val p = a.getJSONObject(it)
            SpPending(p.getString("kind"), p.getInt("id"), p.getString("name"), p.optString("sub", ""), p.getDouble("amount"))
        }
    }
    val due = o.getJSONArray("bills_due").let { a ->
        (0 until a.length()).map {
            val b = a.getJSONObject(it)
            SpBillDue(b.getString("name"), b.getDouble("amount"), if (b.isNull("due_day")) null else b.getInt("due_day"))
        }
    }
    val by = o.getJSONArray("by_category").let { a ->
        (0 until a.length()).map { a.getJSONObject(it).let { c -> c.getString("category") to c.getDouble("total") } }
    }
    val pd = if (o.isNull("next_payday")) null else o.getJSONObject("next_payday")
    return SpSummary(
        today = o.getString("today"),
        categories = cats,
        balance = o.optDoubleOrNull("balance"),
        balanceAt = if (o.isNull("balance_at")) null else o.getString("balance_at"),
        pending = pend,
        pendingTotal = o.getDouble("pending_total"),
        available = o.optDoubleOrNull("available"),
        paydayDate = pd?.getString("date"),
        paydayLabel = pd?.getString("label"),
        paydayDays = pd?.getInt("days") ?: 1,
        billsDue = due,
        billsDueTotal = o.getDouble("bills_due_total"),
        safe = o.optDoubleOrNull("safe_to_spend"),
        perDay = o.optDoubleOrNull("per_day"),
        perWeek = o.optDoubleOrNull("per_week"),
        spentToday = o.getDouble("spent_today"),
        spentWeek = o.getDouble("spent_week"),
        spentMonth = o.getDouble("spent_month"),
        byCategory = by
    )
}

fun fetchSpendingEntries(baseUrl: String, query: String, category: String, allTime: Boolean): List<SpEntry> {
    val month = spCall(baseUrl, "GET", "/api/spending/summary").let { JSONObject(it).getString("month") }
    val path = "/api/spending?month=$month&scope=${if (allTime) "all" else "month"}" +
        "&q=${URLEncoder.encode(query, "UTF-8")}&category=${URLEncoder.encode(category, "UTF-8")}"
    val arr = JSONObject(spCall(baseUrl, "GET", path)).getJSONArray("entries")
    return (0 until arr.length()).map {
        val e = arr.getJSONObject(it)
        SpEntry(e.getInt("id"), e.getDouble("amount"), e.getString("category"), e.optString("note", ""),
            e.getString("spent_on"), e.optInt("cleared", 0) == 1)
    }
}

fun postSpending(baseUrl: String, amount: Double, category: String, note: String, spentOn: String) {
    spCall(baseUrl, "POST", "/api/spending", JSONObject().apply {
        put("amount", amount); put("category", category); put("note", note); put("spent_on", spentOn)
    })
}

fun setSpendingBalance(baseUrl: String, balance: Double, clearedEntries: List<Int>, clearedBills: List<Int>) {
    spCall(baseUrl, "POST", "/api/spending/balance", JSONObject().apply {
        put("balance", balance)
        put("cleared_entries", JSONArray(clearedEntries))
        put("cleared_bills", JSONArray(clearedBills))
    })
}

fun clearSpendingItem(baseUrl: String, kind: String, id: Int) {
    spCall(baseUrl, "POST", "/api/spending/clear", JSONObject().apply {
        put("entries", JSONArray(if (kind == "entry") listOf(id) else emptyList<Int>()))
        put("bills", JSONArray(if (kind == "bill") listOf(id) else emptyList<Int>()))
    })
}

fun setSpendingEntryCleared(baseUrl: String, id: Int, cleared: Boolean) {
    spCall(baseUrl, "PATCH", "/api/spending/$id", JSONObject().apply { put("cleared", if (cleared) 1 else 0) })
}

fun deleteSpendingEntry(baseUrl: String, id: Int) {
    spCall(baseUrl, "DELETE", "/api/spending/$id")
}

// Compact card for the Finance hub: tap to open the full Spending screen.
@Composable
fun SpendingCard(baseUrl: String, onOpen: () -> Unit) {
    var s by remember { mutableStateOf<SpSummary?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { try { fetchSpendingSummary(baseUrl) } catch (e: Exception) { null } }
        s = r
        failed = r == null
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SpCard)
            .clickable { onOpen() }
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SPENDING", color = SpAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Open ›", color = SpMuted, fontSize = 12.sp)
        }
        val cur = s
        if (cur == null) {
            Text(if (failed) "Can't reach the server." else "Loading…", color = SpMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp))
        } else if (cur.safe == null) {
            Text("Tap to set your bank balance.", color = SpMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        } else {
            val bad = cur.safe <= 0
            Text(if (bad) "OVER BY" else "SAFE TO SPEND UNTIL PAYDAY", color = SpMuted, fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp))
            Text(spMoney(if (bad) -cur.safe else cur.safe), color = if (bad) SpRed else SpGreen,
                fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("${spMoney(cur.perDay ?: 0.0)} per day · ${spMoney(cur.perWeek ?: 0.0)} per week · spent today ${spMoney(cur.spentToday)}",
                color = SpGold, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun SpField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
                    number: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier, singleLine = true,
        placeholder = { Text(hint, color = SpMuted, fontSize = 13.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpAccent,
            unfocusedBorderColor = Color(0xFF2a2a3a), cursorColor = SpAccent)
    )
}

@Composable
private fun SpChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (on) SpAccent else Color(0xFF1A1A30))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(label, color = if (on) Color.White else Color(0xFFAAAAAA), fontSize = 12.sp) }
}

@Composable
private fun SpRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFFCCCCCC), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SpendingScreen(baseUrl: String, onBack: () -> Unit) {
    BackHandler { onBack() }
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var s by remember { mutableStateOf<SpSummary?>(null) }
    var entries by remember { mutableStateOf<List<SpEntry>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }

    var query by remember { mutableStateOf("") }
    var filterCat by remember { mutableStateOf("") }
    var allTime by remember { mutableStateOf(false) }

    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var addCat by remember { mutableStateOf("Food") }
    var showBalance by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<Int?>(null) }
    var showDue by remember { mutableStateOf(false) }

    LaunchedEffect(tick) {
        val r = withContext(Dispatchers.IO) { try { fetchSpendingSummary(baseUrl) } catch (e: Exception) { null } }
        s = r
        failed = r == null
        if (r != null && date.isEmpty()) date = r.today
    }
    LaunchedEffect(tick, query, filterCat, allTime) {
        val r = withContext(Dispatchers.IO) {
            try { fetchSpendingEntries(baseUrl, query, filterCat, allTime) } catch (e: Exception) { null }
        }
        if (r != null) entries = r
    }

    fun doIo(block: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { try { block() } catch (e: Exception) { } }
            tick++
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(SpBg).padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)) {
            Text("‹ Back", color = SpAccent, fontSize = 16.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(16.dp))
            Text("Spending", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        val cur = s
        if (cur == null) {
            Text(if (failed) "Can't reach the server." else "Loading…", color = SpMuted, fontSize = 14.sp)
            return@Column
        }

        // ── Summary ──
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SpCard).padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("SPENDING", color = SpAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A2A1A))
                    .clickable { showBalance = true }.padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text(if (cur.balance == null) "Set balance" else "Update balance", color = SpGreen, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (cur.balance == null || cur.available == null || cur.safe == null) {
                Text("Enter your bank balance to see how much you can safely spend. Purchases you add count against it right away.",
                    color = SpMuted, fontSize = 13.sp)
            } else {
                SpRow("Bank balance" + (cur.balanceAt?.let { "  (set ${it.take(16)})" } ?: ""), spMoney(cur.balance))
                SpRow("Pending, not charged yet", "-" + spMoney(cur.pendingTotal).replace("-", ""), SpRed)
                SpRow("Available now", spMoney(cur.available))
                SpRow("Bills due before payday" + (cur.paydayDate?.let { "  (${it.substring(5)}, ${cur.paydayLabel})" } ?: ""),
                    "-" + spMoney(cur.billsDueTotal).replace("-", ""), SpRed)
                val bad = cur.safe <= 0
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (bad) Color(0xFF1A0E10) else Color(0xFF0E1A12)).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (bad) "OVER BY" else "SAFE TO SPEND UNTIL PAYDAY", color = SpMuted, fontSize = 11.sp)
                    Text(spMoney(if (bad) -cur.safe else cur.safe), color = if (bad) SpRed else SpGreen,
                        fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        spMoney(cur.perDay ?: 0.0) to "PER DAY · ${cur.paydayDays} ${if (cur.paydayDays == 1) "day" else "days"}",
                        spMoney(cur.perWeek ?: 0.0) to "PER WEEK"
                    ).forEach { (v, l) ->
                        Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(SpInner).padding(9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, color = SpGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(l, color = SpMuted, fontSize = 10.sp)
                        }
                    }
                }
                Text("Spent today ${spMoney(cur.spentToday)} · This week ${spMoney(cur.spentWeek)}",
                    color = SpMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                if (cur.billsDue.isNotEmpty()) {
                    Text((if (showDue) "▾ " else "▸ ") + "Bills counted (${cur.billsDue.size})", color = SpMuted, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp).clickable { showDue = !showDue })
                    if (showDue) cur.billsDue.forEach { b ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(b.name + if (b.dueDay == null) "  (no due day)" else "", color = Color(0xFFAAAAAA), fontSize = 12.sp,
                                modifier = Modifier.weight(1f))
                            Text(spMoney(b.amount), color = Color(0xFFAAAAAA), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ── Pending ──
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SpCard).padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PENDING", color = SpAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("until they clear", color = SpMuted, fontSize = 11.sp)
            }
            if (cur.pending.isEmpty()) {
                Text("Nothing pending.", color = SpMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            cur.pending.forEach { p ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name, color = Color.White, fontSize = 13.sp)
                        Text(p.sub, color = SpMuted, fontSize = 11.sp)
                    }
                    Text("-" + spMoney(p.amount), color = SpRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A1F10))
                        .clickable { doIo { clearSpendingItem(baseUrl, p.kind, p.id) } }
                        .padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text("Cleared", color = SpOrange, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── Quick add ──
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SpCard).padding(14.dp)) {
            Text("QUICK ADD", color = SpAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "\$ amount", Modifier.weight(1f), number = true)
                SpField(date, { date = it.take(10) }, "YYYY-MM-DD", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            SpField(note, { note = it.take(200) }, "Note (optional)", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                cur.categories.forEach { c -> SpChip(c, c == addCat) { addCat = c } }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val a = amount.toDoubleOrNull()
                    if (a != null && a > 0) {
                        val n = note
                        val d = if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) date else cur.today
                        val c = addCat
                        amount = ""; note = ""; date = cur.today
                        doIo { postSpending(baseUrl, a, c, n, d) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A1A)),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Add", color = SpGreen, fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("THIS MONTH BY CATEGORY", color = SpAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(spMoney(cur.spentMonth), color = SpMuted, fontSize = 12.sp)
            }
            val total = if (cur.spentMonth > 0) cur.spentMonth else 1.0
            cur.byCategory.forEach { (c, t) ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c, color = Color(0xFFCCCCCC), fontSize = 12.sp)
                    Text(spMoney(t), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1A1A30))) {
                    Box(modifier = Modifier.fillMaxWidth(fraction = (t / total).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(SpAccent))
                }
            }
        }

        // ── History ──
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SpCard).padding(14.dp)) {
            Text("SPENDING HISTORY", color = SpAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SpField(query, { query = it }, "Search…", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SpChip("All", filterCat == "") { filterCat = "" }
                cur.categories.forEach { c -> SpChip(c, filterCat == c) { filterCat = if (filterCat == c) "" else c } }
                SpChip(if (allTime) "All time" else "This month", allTime) { allTime = !allTime }
            }
            if (entries.isEmpty()) {
                Text("No spending found.", color = SpMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            entries.forEach { e ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (e.note.isNotEmpty()) e.note else e.category, color = Color.White, fontSize = 13.sp)
                        Text("${e.spentOn.substring(5)} · ${e.category}", color = SpMuted, fontSize = 11.sp)
                    }
                    Text("-" + spMoney(e.amount), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(if (e.cleared) "Cleared" else "Pending", color = if (e.cleared) SpGreen else SpOrange, fontSize = 12.sp,
                        modifier = Modifier.clickable { doIo { setSpendingEntryCleared(baseUrl, e.id, !e.cleared) } })
                    Spacer(Modifier.width(10.dp))
                    Text("✕", color = SpMuted, fontSize = 14.sp, modifier = Modifier.clickable { deleteId = e.id })
                }
            }
        }
        Spacer(Modifier.height(32.dp))

        if (deleteId != null) {
            AlertDialog(
                onDismissRequest = { deleteId = null },
                title = { Text("Delete this entry?") },
                confirmButton = {
                    TextButton(onClick = { val id = deleteId!!; deleteId = null; doIo { deleteSpendingEntry(baseUrl, id) } }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
            )
        }

        if (showBalance) {
            var balText by remember { mutableStateOf(cur.balance?.let { String.format(Locale.US, "%.2f", it) } ?: "") }
            val posted = remember { mutableStateListOf<String>() }
            AlertDialog(
                onDismissRequest = { showBalance = false },
                title = { Text("Bank balance") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        SpField(balText, { balText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } }, "Current balance", Modifier.fillMaxWidth(), number = true)
                        if (cur.pending.isNotEmpty()) {
                            Text("Which of these have posted at your bank?", fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                            cur.pending.forEach { p ->
                                val key = p.kind + ":" + p.id
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = posted.contains(key),
                                        onCheckedChange = { if (it) posted.add(key) else posted.remove(key) })
                                    Text(p.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text(spMoney(p.amount), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val b = balText.toDoubleOrNull()
                        if (b != null) {
                            val ce = posted.filter { it.startsWith("entry:") }.map { it.substringAfter(":").toInt() }
                            val cb = posted.filter { it.startsWith("bill:") }.map { it.substringAfter(":").toInt() }
                            showBalance = false
                            doIo { setSpendingBalance(baseUrl, b, ce, cb) }
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showBalance = false }) { Text("Cancel") } }
            )
        }
    }
}
