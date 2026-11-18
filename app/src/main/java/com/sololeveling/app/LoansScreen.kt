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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ─── Loans (Cash App / Affirm / Klarna ...) ───────────────────────────────────

data class LoanPayment(val id: Int, val dueDate: String, val amount: Double, val paid: Boolean)
data class LoanItem(val id: Int, val provider: String, val name: String, val amount: Double,
                    val payments: List<LoanPayment>, val left: Int)
data class LoansData(val today: String, val paydayDate: String?, val loans: List<LoanItem>,
                     val owed: Double, val dueTotal: Double, val providers: List<String>)

private fun loCall(baseUrl: String, method: String, path: String, body: JSONObject? = null): String {
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

fun fetchLoans(baseUrl: String): LoansData {
    val o = JSONObject(loCall(baseUrl, "GET", "/api/loans"))
    val arr = o.getJSONArray("loans")
    val loans = (0 until arr.length()).map { i ->
        val l = arr.getJSONObject(i)
        val pa = l.getJSONArray("payments")
        LoanItem(
            id = l.getInt("id"), provider = l.getString("provider"), name = l.getString("name"),
            amount = l.getDouble("amount"),
            payments = (0 until pa.length()).map { j ->
                val p = pa.getJSONObject(j)
                LoanPayment(p.getInt("id"), p.getString("due_date"), p.getDouble("amount"), p.getInt("paid") == 1)
            },
            left = l.getInt("left")
        )
    }
    val provs = o.getJSONArray("providers").let { a -> (0 until a.length()).map { a.getString(it) } }
    val pd = if (o.isNull("payday")) null else o.getJSONObject("payday").getString("date")
    return LoansData(o.getString("today"), pd, loans, o.getDouble("owed"), o.getDouble("due_total"), provs)
}

private fun loMoney(v: Double): String = "\$" + String.format(java.util.Locale.US, "%,.2f", v)

private fun loShort(d: String): String {
    val mon = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return runCatching { mon[d.substring(5, 7).toInt() - 1] + " " + d.substring(8, 10).toInt() }.getOrDefault(d)
}

private fun loProviderColor(p: String): Color = when (p) {
    "Cash App" -> Color(0xFF00D632)
    "Affirm" -> Color(0xFF6AA0FF)
    "Klarna" -> Color(0xFFFFB3C7)
    else -> Color(0xFFBBBBBB)
}

@Composable
fun LoansScreen(baseUrl: String, onBack: () -> Unit) {
    BackHandler { onBack() }
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf<LoansData?>(null) }
    var failed by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(tick) {
        val r = withContext(Dispatchers.IO) { try { fetchLoans(baseUrl) } catch (e: Exception) { null } }
        data = r
        failed = r == null
    }

    fun doIo(block: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { try { block() } catch (e: Exception) { } }
            tick++
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A)).padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)) {
            Text("‹ Back", color = Color(0xFF7B8CDE), fontSize = 16.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(16.dp))
            Text("Loans", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        val d = data
        if (d == null) {
            Text(if (failed) "Can't reach the server." else "Loading…", color = Color(0xFF888899), fontSize = 14.sp)
            return@Column
        }

        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF12122A)).padding(14.dp)) {
            Text("DUE BEFORE PAYDAY", color = Color(0xFFE8944A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(loMoney(d.dueTotal), color = if (d.dueTotal > 0) Color(0xFFCF6679) else Color(0xFF4CAF50),
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Owed in total ${loMoney(d.owed)} · ${d.loans.count { it.left > 0 }} active loans",
                color = Color(0xFF888899), fontSize = 12.sp)
        }

        d.loans.forEach { l ->
            val next = l.payments.firstOrNull { !it.paid }
            Spacer(Modifier.height(10.dp))
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF12122A)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.provider.uppercase(), color = loProviderColor(l.provider), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(l.name, color = if (l.left > 0) Color.White else Color(0xFF777788), fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (next != null) Text(loMoney(next.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (d.paydayDate != null && next.dueDate <= d.paydayDate) Color(0xFFCF6679) else Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("✕", color = Color(0xFF777788), fontSize = 14.sp, modifier = Modifier.clickable { deleteId = l.id })
                }
                Text(if (next != null) "Next: ${loShort(next.dueDate)} · ${l.left} left" else "Paid off",
                    color = Color(0xFF888899), fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        l.payments.forEach { p ->
                            val due = !p.paid && d.paydayDate != null && p.dueDate <= d.paydayDate
                            Box(modifier = Modifier.clip(RoundedCornerShape(5.dp))
                                .background(if (p.paid) Color(0xFF1A2A1A) else if (due) Color(0xFF3A1A1A) else Color(0xFF1A1A30))
                                .clickable { doIo { setLoanPaid(baseUrl, p.id, !p.paid) } }
                                .padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(loShort(p.dueDate), fontSize = 11.sp,
                                    color = if (p.paid) Color(0xFF4CAF50) else if (due) Color(0xFFCF6679) else Color(0xFFAAAAAA),
                                    textDecoration = if (p.paid) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                            }
                        }
                    }
                    if (next != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A1F10))
                            .clickable { doIo { setLoanPaid(baseUrl, next.id, true) } }
                            .padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text("Mark paid", color = Color(0xFFE8944A), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (d.loans.isEmpty()) {
            Text("No loans yet.", color = Color(0xFF888899), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Text("Red dates are due before your next payday and are subtracted from Safe to Spend. Tap a date to mark it paid or unpaid. Paid payments wait in Pending until you clear them.",
            color = Color(0xFF666677), fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A1A)),
            shape = RoundedCornerShape(8.dp)
        ) { Text("+ Add loan", color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(32.dp))

        if (deleteId != null) {
            AlertDialog(
                onDismissRequest = { deleteId = null },
                title = { Text("Delete this loan and all its payments?") },
                confirmButton = { TextButton(onClick = { val id = deleteId!!; deleteId = null; doIo { deleteLoan(baseUrl, id) } }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
            )
        }

        if (showAdd) {
            var provider by remember { mutableStateOf("Affirm") }
            var name by remember { mutableStateOf("") }
            var amount by remember { mutableStateOf("") }
            var first by remember { mutableStateOf(d.today) }
            var count by remember { mutableStateOf("1") }
            var repeat by remember { mutableStateOf("biweekly") }
            var custom by remember { mutableStateOf("") }
            val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF7B8CDE),
                unfocusedBorderColor = Color(0xFF2a2a3a), cursorColor = Color(0xFF7B8CDE))
            AlertDialog(
                onDismissRequest = { showAdd = false },
                title = { Text("Add loan") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            d.providers.forEach { p ->
                                Box(modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(if (p == provider) Color(0xFF7B8CDE) else Color(0xFF1A1A30))
                                    .clickable { provider = p }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(p, color = if (p == provider) Color.White else Color(0xFFAAAAAA), fontSize = 12.sp)
                                }
                            }
                        }
                        OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true,
                            label = { Text("Name (e.g. SuperX)") }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true,
                            label = { Text("\$ per payment") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = first, onValueChange = { first = it.take(10) }, singleLine = true,
                            label = { Text("First due (YYYY-MM-DD)") }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf("once" to "One time", "weekly" to "Weekly", "biweekly" to "Every 2 weeks", "monthly" to "Monthly").forEach { (k, label) ->
                                Box(modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(if (k == repeat) Color(0xFF7B8CDE) else Color(0xFF1A1A30))
                                    .clickable { repeat = k }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(label, color = if (k == repeat) Color.White else Color(0xFFAAAAAA), fontSize = 12.sp)
                                }
                            }
                        }
                        if (repeat != "once") {
                            OutlinedTextField(value = count, onValueChange = { count = it.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                                label = { Text("Number of payments") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        OutlinedTextField(value = custom, onValueChange = { custom = it }, singleLine = true,
                            label = { Text("Or custom dates, comma separated") }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                        Text("Payments dated before today are saved as already paid.", fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val a = amount.toDoubleOrNull()
                        if (a != null && a > 0) {
                            val dates = custom.split(",", " ").map { it.trim() }.filter { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }
                            val body = JSONObject().apply {
                                put("provider", provider); put("name", name); put("amount", a)
                                put("first_due", first); put("count", count.toIntOrNull() ?: 1); put("repeat", repeat)
                                if (dates.isNotEmpty()) put("dates", JSONArray(dates))
                            }
                            showAdd = false
                            doIo { loCall(baseUrl, "POST", "/api/loans", body) }
                        }
                    }) { Text("Add") }
                },
                dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
            )
        }
    }
}

fun setLoanPaid(baseUrl: String, paymentId: Int, paid: Boolean) {
    loCall(baseUrl, "PATCH", "/api/loans/payments/$paymentId", JSONObject().apply { put("paid", if (paid) 1 else 0) })
}

fun deleteLoan(baseUrl: String, id: Int) {
    loCall(baseUrl, "DELETE", "/api/loans/$id")
}
