package com.sololeveling.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
// Today's week_start (Sunday) -> Wednesday (+3) -> pay date (+17) -> that month.
fun currentDeliveryPayMonth(): String {
    val cal = Calendar.getInstance()
    // Back up to this week's Sunday
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        cal.add(Calendar.DAY_OF_MONTH, -1)
    }
    // Sunday + 3 = Wednesday, + 17 = pay date
    cal.add(Calendar.DAY_OF_MONTH, 20)
    return SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
}

// The YYYY-MM this calendar month — the tracker groups weeks by the month they're
// WORKED in (Wednesday's month), so the current worked week always shows immediately.
fun currentWorkedMonth(): String {
    return SimpleDateFormat("yyyy-MM", Locale.US).format(Calendar.getInstance().time)
}

// True if the given week_start (a Sunday, "yyyy-MM-dd") is the week containing today.
fun isCurrentDeliveryWeek(weekStart: String): Boolean {
    return try {
        val cal = Calendar.getInstance()
        // Back up to this week's Sunday
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        val todaySunday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        todaySunday == weekStart
    } catch (e: Exception) { false }
}

data class DeliveryWeek(
    val id: Int,
    val weekStart: String,
    val checkNumber: Int,
    val satDelivered: Int,
    val satDuplicates: Int,
    val satUndeliverable: Int,
    val friDelivered: Int,
    val friDuplicates: Int,
    val friUndeliverable: Int,
    val satRoute324: Boolean = false,
    val friRoute324: Boolean = false,
    val satRoute121: Boolean = false,
    val friRoute121: Boolean = false
) {
    val satBillable: Int get() = maxOf(0, satDelivered - satDuplicates - satUndeliverable)
    val friBillable: Int get() = maxOf(0, friDelivered - friDuplicates - friUndeliverable)
    val satRate: Double get() = if (satRoute324) 1.90 else if (satRoute121) 1.80 else 1.60
    val friRate: Double get() = if (friRoute324) 1.90 else if (friRoute121) 1.80 else 1.60
    val totalBillable: Int get() = satBillable + friBillable
    val satPay: Double get() = satBillable * satRate
    val friPay: Double get() = friBillable * friRate
    val totalPay: Double get() = satPay + friPay

    fun payDate(): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val start = sdf.parse(weekStart) ?: return ""
            val cal = Calendar.getInstance()
            cal.time = start
            cal.add(Calendar.DAY_OF_MONTH, 3) // Sunday + 3 = Wednesday
            cal.add(Calendar.DAY_OF_MONTH, 17) // Wednesday + 17
            SimpleDateFormat("MMM d", Locale.US).format(cal.time)
        } catch (e: Exception) { "" }
    }

    fun weekLabel(): String {
        // Pay period: Sat (week_start - 7) through Fri (week_start - 1)
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val start = sdf.parse(weekStart) ?: return weekStart
            val fmt = SimpleDateFormat("MMM d", Locale.US)
            val periodStart = Calendar.getInstance().apply { time = start; add(Calendar.DAY_OF_MONTH, -7) }.time
            val periodEnd = Calendar.getInstance().apply { time = start; add(Calendar.DAY_OF_MONTH, -1) }.time
            "${fmt.format(periodStart)} – ${fmt.format(periodEnd)}"
        } catch (e: Exception) { weekStart }
    }

    // Dated labels for the two worked days, measured from week_start (a Sunday).
    // Delivery moved from Tue/Wed to Fri/Sat beginning the week of 2026-08-16, so weeks
    // before that keep their real Tue/Wed labels rather than being rewritten. The
    // underlying tue_*/wed_* columns are just "day 1 / day 2" either way.
    private fun dayLabel(offsetDays: Int, dayName: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val start = sdf.parse(weekStart) ?: return dayName
            val cal = Calendar.getInstance().apply {
                time = start
                add(Calendar.DAY_OF_MONTH, offsetDays)
            }
            "$dayName ${SimpleDateFormat("MMM d", Locale.US).format(cal.time)}"
        } catch (e: Exception) { dayName }
    }

    private fun isFriSatWeek(): Boolean = weekStart >= DELIVERY_FRISAT_FROM

    fun satDate(): String =
        if (isFriSatWeek()) dayLabel(-1, "Sat") else dayLabel(2, "Sat")
    fun friDate(): String =
        if (isFriSatWeek()) dayLabel(5, "Fri") else dayLabel(3, "Fri")
}

// First week_start (Sunday) on which delivery days are Friday/Saturday instead of
// Tuesday/Wednesday. Weeks before this keep Tue/Wed labels.
const val DELIVERY_FRISAT_FROM = "2026-08-16"

// ─── Network ──────────────────────────────────────────────────────────────────

fun fetchDeliveryWeeks(baseUrl: String, workedMonth: String? = null): List<DeliveryWeek> {
    val url = URL("$baseUrl/api/delivery-weeks" + (if (workedMonth != null) "?worked_month=$workedMonth" else ""))
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
                satDelivered = obj.getInt("sat_delivered"),
                satDuplicates = obj.getInt("sat_duplicates"),
                satUndeliverable = obj.getInt("sat_undeliverable"),
                friDelivered = obj.getInt("fri_delivered"),
                friDuplicates = obj.getInt("fri_duplicates"),
                friUndeliverable = obj.getInt("fri_undeliverable"),
                satRoute324 = obj.optInt("sat_route324", 0) == 1,
                friRoute324 = obj.optInt("fri_route324", 0) == 1,
                satRoute121 = obj.optInt("sat_route121", 0) == 1,
                friRoute121 = obj.optInt("fri_route121", 0) == 1
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
            put("sat_delivered", week.satDelivered)
            put("sat_duplicates", week.satDuplicates)
            put("sat_undeliverable", week.satUndeliverable)
            put("fri_delivered", week.friDelivered)
            put("fri_duplicates", week.friDuplicates)
            put("fri_undeliverable", week.friUndeliverable)
            put("sat_route324", if (week.satRoute324) 1 else 0)
            put("fri_route324", if (week.friRoute324) 1 else 0)
            put("sat_route121", if (week.satRoute121) 1 else 0)
            put("fri_route121", if (week.friRoute121) 1 else 0)
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val text = conn.inputStream.bufferedReader().readText()
        val obj = JSONObject(text)
        DeliveryWeek(
            id = obj.getInt("id"),
            weekStart = obj.getString("week_start"),
            checkNumber = obj.optInt("check_number", 0),
            satDelivered = obj.getInt("sat_delivered"),
            satDuplicates = obj.getInt("sat_duplicates"),
            satUndeliverable = obj.getInt("sat_undeliverable"),
            friDelivered = obj.getInt("fri_delivered"),
            friDuplicates = obj.getInt("fri_duplicates"),
            friUndeliverable = obj.getInt("fri_undeliverable"),
            satRoute324 = obj.optInt("sat_route324", 0) == 1,
            friRoute324 = obj.optInt("fri_route324", 0) == 1,
            satRoute121 = obj.optInt("sat_route121", 0) == 1,
            friRoute121 = obj.optInt("fri_route121", 0) == 1
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

// ─── SpeedX paychecks ─────────────────────────────────────────────────────────
data class SpeedxCheck(
    val id: Int,
    val checkNumber: Int,
    val payDate: String,
    val amount: Double,
    val paid: Boolean
)

fun fetchSpeedxChecks(baseUrl: String): Pair<String?, List<SpeedxCheck>> {
    val url = URL("$baseUrl/api/speedx-checks")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    try {
        if (conn.responseCode != 200) return Pair(null, emptyList())
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(body)
        val month = obj.optString("month", null)
        val arr = obj.optJSONArray("checks") ?: JSONArray()
        val list = mutableListOf<SpeedxCheck>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            list.add(
                SpeedxCheck(
                    id = c.getInt("id"),
                    checkNumber = c.getInt("check_number"),
                    payDate = c.getString("pay_date"),
                    amount = c.getDouble("amount"),
                    paid = c.optInt("paid", 0) == 1
                )
            )
        }
        return Pair(month, list)
    } finally {
        conn.disconnect()
    }
}

fun patchSpeedxCheckPaid(baseUrl: String, id: Int, paid: Boolean) {
    val url = URL("$baseUrl/api/speedx-checks/$id")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "PATCH"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    try {
        conn.outputStream.use { it.write(JSONObject().put("paid", if (paid) 1 else 0).toString().toByteArray()) }
        conn.responseCode
    } finally {
        conn.disconnect()
    }
}

// Format "2026-08-14" -> "Aug 14"
fun formatPayDate(iso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val d = parser.parse(iso)
        SimpleDateFormat("MMM d", Locale.US).format(d!!)
    } catch (e: Exception) { iso }
}

data class DeliveryDay(
    val label: String, val delivered: Int, val duplicates: Int,
    val undeliverable: Int, val billable: Int, val route324: Boolean, val route121: Boolean
)
data class DeliveryWeekHist(
    val weekStart: String, val checkNumber: Int?, val payDate: String,
    val paid: Boolean, val billableAmount: Double, val day1: DeliveryDay, val day2: DeliveryDay
)
data class DeliveryMonthHist(
    val month: String, val totalDelivered: Int, val totalBillable: Double, val weeks: List<DeliveryWeekHist>
)

fun fetchDeliveryHistory(baseUrl: String): List<DeliveryMonthHist> {
    val url = URL("$baseUrl/api/delivery-history")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    try {
        if (conn.responseCode != 200) return emptyList()
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(body).optJSONArray("months") ?: JSONArray()
        fun day(o: JSONObject) = DeliveryDay(
            label = o.getString("label"), delivered = o.getInt("delivered"),
            duplicates = o.getInt("duplicates"), undeliverable = o.getInt("undeliverable"),
            billable = o.getInt("billable"), route324 = o.optInt("route324", 0) == 1,
            route121 = o.optInt("route121", 0) == 1
        )
        val months = mutableListOf<DeliveryMonthHist>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val wArr = m.optJSONArray("weeks") ?: JSONArray()
            val weeks = mutableListOf<DeliveryWeekHist>()
            for (j in 0 until wArr.length()) {
                val w = wArr.getJSONObject(j)
                weeks.add(DeliveryWeekHist(
                    weekStart = w.getString("week_start"),
                    checkNumber = if (w.isNull("check_number")) null else w.getInt("check_number"),
                    payDate = w.getString("pay_date"),
                    paid = w.optInt("paid", 0) == 1,
                    billableAmount = w.getDouble("billable_amount"),
                    day1 = day(w.getJSONObject("day1")),
                    day2 = day(w.getJSONObject("day2"))
                ))
            }
            months.add(DeliveryMonthHist(
                month = m.getString("month"),
                totalDelivered = m.getInt("totalDelivered"),
                totalBillable = m.getDouble("totalBillable"),
                weeks = weeks
            ))
        }
        return months
    } finally {
        conn.disconnect()
    }
}

@Composable
fun DeliveryHistoryScreen(baseUrl: String, onBack: () -> Unit) {
    var months by remember { mutableStateOf<List<DeliveryMonthHist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try { fetchDeliveryHistory(baseUrl) } catch (e: Exception) { emptyList<DeliveryMonthHist>() }
        }
        months = result
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)) {
            Text("‹ Back", color = Color(0xFF7B8CDE), fontSize = 16.sp,
                modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(16.dp))
            Text("Delivery History", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        if (loading) {
            Text("Loading…", color = Color(0xFF888899), fontSize = 14.sp)
        } else if (months.isEmpty()) {
            Text("No delivery weeks recorded yet.", color = Color(0xFF888899), fontSize = 14.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                months.sortedWith(compareByDescending<DeliveryMonthHist> { it.totalDelivered }.thenByDescending { it.month }).forEach { m ->
                    val sortedWeeks = m.weeks.sortedBy { it.checkNumber ?: 0 }
                    val monthLabel = runCatching {
                        val parts = m.month.split("-")
                        val mon = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")[parts[1].toInt() - 1]
                        "$mon ${parts[0]}"
                    }.getOrDefault(m.month)
                    item(key = "hdr_${m.month}") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(monthLabel, color = Color(0xFF7B9CD8), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${m.totalDelivered} delivered · $${"%.2f".format(m.totalBillable)}",
                                color = Color(0xFF8FD6A8), fontSize = 12.sp)
                        }
                    }
                    items(sortedWeeks, key = { it.weekStart }) { w ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF12122A))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Check ${w.checkNumber ?: "—"}",
                                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (w.paid) Color(0x2645D19A) else Color(0x26F87171))
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(if (w.paid) "Paid" else "Unpaid",
                                        color = if (w.paid) Color(0xFF8FD6A8) else Color(0xFFF87171),
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("Pay ${w.payDate} · $${"%.2f".format(w.billableAmount)} billable",
                                color = Color(0xFF888899), fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(w.day1, w.day2).forEach { d ->
                                    Column(modifier = Modifier.weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0E0E20))
                                        .padding(8.dp)) {
                                        val route = if (d.route324) " · 324" else if (d.route121) " · 121" else ""
                                        Text(d.label + route, color = Color(0xFFB9C4E0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${d.delivered} delivered", color = Color(0xFF9AA4B8), fontSize = 11.sp)
                                        Text("${d.duplicates} dup · ${d.undeliverable} undeliv", color = Color(0xFFC98A8A), fontSize = 11.sp)
                                        Text("${d.billable} billable", color = Color(0xFF8FD6A8), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun SpeedxChecksScreen(baseUrl: String, onBack: () -> Unit) {
    var month by remember { mutableStateOf<String?>(null) }
    var checks by remember { mutableStateOf<List<SpeedxCheck>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { fetchSpeedxChecks(baseUrl) } catch (e: Exception) { Pair(null, emptyList<SpeedxCheck>()) }
            }
            month = result.first
            checks = result.second
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)) {
            Text("‹ Back", color = Color(0xFF7B8CDE), fontSize = 16.sp,
                modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(16.dp))
            Text("SpeedX Paychecks", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        month?.let {
            Text(it, color = Color(0xFF888899), fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
        }

        if (loading) {
            Text("Loading…", color = Color(0xFF888899), fontSize = 14.sp)
        } else if (checks.isEmpty()) {
            Text("No checks for this month.", color = Color(0xFF888899), fontSize = 14.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(checks, key = { it.id }) { check ->
                    var isPaid by remember(check.paid) { mutableStateOf(check.paid) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF12122A))
                            .clickable {
                                isPaid = !isPaid
                                scope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        try { patchSpeedxCheckPaid(baseUrl, check.id, isPaid) } catch (e: Exception) {}
                                    }
                                }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Check ${check.checkNumber}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("$${String.format("%.2f", check.amount)} · pays ${formatPayDate(check.payDate)}",
                                color = Color(0xFF888899), fontSize = 12.sp)
                        }
                        Checkbox(
                            checked = isPaid,
                            onCheckedChange = { checked ->
                                isPaid = checked
                                scope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        try { patchSpeedxCheckPaid(baseUrl, check.id, checked) } catch (e: Exception) {}
                                    }
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF7B8CDE))
                        )
                        Text(if (isPaid) "Paid" else "Unpaid",
                            color = if (isPaid) Color(0xFF6ac46a) else Color(0xFF888899),
                            fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DeliveryWeekCard(week: DeliveryWeek, isActiveWeek: Boolean, onSave: (DeliveryWeek) -> Unit) {
    var expandedDay by remember { mutableStateOf<String?>(null) }
    // Active week is always editable. Other weeks start locked; unlocking is a
    // deliberate per-card action that re-locks immediately after a save.
    var unlocked by remember(week) { mutableStateOf(false) }
    val editable = isActiveWeek || unlocked

    var satDelivered by remember(week) { mutableStateOf(week.satDelivered.toString()) }
    var satDuplicates by remember(week) { mutableStateOf(week.satDuplicates.toString()) }
    var satUndeliverable by remember(week) { mutableStateOf(week.satUndeliverable.toString()) }
    var friDelivered by remember(week) { mutableStateOf(week.friDelivered.toString()) }
    var friDuplicates by remember(week) { mutableStateOf(week.friDuplicates.toString()) }
    var friUndeliverable by remember(week) { mutableStateOf(week.friUndeliverable.toString()) }
    // Rate state per day: 0 = Standard ($1.60), 1 = Route 324 ($1.90), 2 = Route 121 ($1.80)
    var satRateMode by remember(week) { mutableStateOf(if (week.satRoute324) 1 else if (week.satRoute121) 2 else 0) }
    var friRateMode by remember(week) { mutableStateOf(if (week.friRoute324) 1 else if (week.friRoute121) 2 else 0) }

    val cardModifier = if (isActiveWeek) {
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .border(2.dp, Color(0xFF3B6D11), RoundedCornerShape(12.dp))
            .background(Color(0xFF12122A))
    } else {
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF12122A))
    }
    Column(modifier = cardModifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (week.checkNumber > 0) {
                        Text(text = "SpeedX Check ${week.checkNumber}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4CAF50))
                    }
                    Text(text = week.weekLabel(), fontSize = 11.sp, color = Color(0xFF888899))
                    when {
                        isActiveWeek -> Text(text = "● ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF97C459))
                        unlocked -> Text(text = "🔓 Unlocked", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD4B84A))
                        else -> Text(text = "🔒 Locked", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF888899))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            DayRow(label = week.satDate(), billable = week.satBillable, pay = week.satPay,
                isExpanded = expandedDay == "sat", onClick = { expandedDay = if (expandedDay == "sat") null else "sat" })
            DayQuotaLine(delivered = week.satDelivered, billable = week.satBillable, pay = week.satPay, rate = week.satRate)

            if (expandedDay == "sat") {
                Spacer(Modifier.height(10.dp))
                if (editable) {
                    RouteRateSelector(mode = satRateMode, onCycle = { satRateMode = (satRateMode + 1) % 3 })
                    Spacer(Modifier.height(10.dp))
                    DeliveryInputRow(delivered = satDelivered, duplicates = satDuplicates, undeliverable = satUndeliverable,
                        rate = rateForMode(satRateMode),
                        onDeliveredChange = { satDelivered = it }, onDuplicatesChange = { satDuplicates = it },
                        onUndeliverableChange = { satUndeliverable = it },
                        onSave = {
                            expandedDay = null
                            if (!isActiveWeek) unlocked = false
                            onSave(week.copy(satDelivered = satDelivered.toIntOrNull() ?: 0,
                                satDuplicates = satDuplicates.toIntOrNull() ?: 0,
                                satUndeliverable = satUndeliverable.toIntOrNull() ?: 0,
                                satRoute324 = satRateMode == 1,
                                satRoute121 = satRateMode == 2))
                        })
                } else {
                    LockedDaySummary(delivered = week.satDelivered, duplicates = week.satDuplicates,
                        undeliverable = week.satUndeliverable, rateMode = satRateMode)
                    Spacer(Modifier.height(10.dp))
                    UnlockButton(onUnlock = { unlocked = true })
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))

            DayRow(label = week.friDate(), billable = week.friBillable, pay = week.friPay,
                isExpanded = expandedDay == "fri", onClick = { expandedDay = if (expandedDay == "fri") null else "fri" })
            DayQuotaLine(delivered = week.friDelivered, billable = week.friBillable, pay = week.friPay, rate = week.friRate,
                target = WEEKLY_QUOTA - week.satPay)

            if (expandedDay == "fri") {
                Spacer(Modifier.height(10.dp))
                if (editable) {
                    RouteRateSelector(mode = friRateMode, onCycle = { friRateMode = (friRateMode + 1) % 3 })
                    Spacer(Modifier.height(10.dp))
                    DeliveryInputRow(delivered = friDelivered, duplicates = friDuplicates, undeliverable = friUndeliverable,
                        rate = rateForMode(friRateMode),
                        onDeliveredChange = { friDelivered = it }, onDuplicatesChange = { friDuplicates = it },
                        onUndeliverableChange = { friUndeliverable = it },
                        onSave = {
                            expandedDay = null
                            if (!isActiveWeek) unlocked = false
                            onSave(week.copy(friDelivered = friDelivered.toIntOrNull() ?: 0,
                                friDuplicates = friDuplicates.toIntOrNull() ?: 0,
                                friUndeliverable = friUndeliverable.toIntOrNull() ?: 0,
                                friRoute324 = friRateMode == 1,
                                friRoute121 = friRateMode == 2))
                        })
                } else {
                    LockedDaySummary(delivered = week.friDelivered, duplicates = week.friDuplicates,
                        undeliverable = week.friUndeliverable, rateMode = friRateMode)
                    Spacer(Modifier.height(10.dp))
                    UnlockButton(onUnlock = { unlocked = true })
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2a2a3a)))
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                // Pay period total: Sat (day 1) + Fri (day 2) of this pay period
                val periodPay = week.satPay + week.friPay
                val periodBillable = week.satBillable + week.friBillable
                Column {
                    Text("Pay Period Total", fontSize = 12.sp, color = Color(0xFF888899))
                    Text("$periodBillable packages", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$${String.format("%.2f", periodPay)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Text("💰 Pay Date: ${week.payDate()}", fontSize = 11.sp, color = Color(0xFF888899))
                }
            }
        }
    }
}

// Quota targets: $150 per delivery day; $300 for the week. Day 1 is measured
// against the daily $150. Day 2 is measured against whatever remains of the
// $300 weekly goal after day 1's pay, so a strong day 1 shrinks day 2's
// target. The weekly line reports the week's total against $300.
const val DAILY_QUOTA = 150.0
const val WEEKLY_QUOTA = 300.0

@Composable
fun DayQuotaLine(delivered: Int, billable: Int, pay: Double, rate: Double, target: Double = DAILY_QUOTA) {
    // Hidden until the day has activity, so an un-worked day doesn't read as "short".
    if (delivered <= 0) return
    // If the remaining target is already covered (e.g. a strong day 1 cleared the
    // weekly goal), treat the day as met with no shortfall.
    val effectiveTarget = maxOf(0.0, target)
    val met = pay >= effectiveTarget
    Spacer(Modifier.height(4.dp))
    if (met) {
        val over = pay - effectiveTarget
        Text(
            text = "✓ Quota met · $${String.format("%.2f", over)} over",
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF97C459),
            modifier = Modifier.padding(start = 12.dp)
        )
    } else {
        val short = effectiveTarget - pay
        // Additional billable packages needed at this day's current rate to close the gap.
        val morePackages = maxOf(0, Math.ceil(short / rate).toInt())
        Text(
            text = "⚠ $${String.format("%.2f", short)} short · $morePackages more packages",
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE0A030),
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
fun WeeklyQuotaLine(week: DeliveryWeek) {
    // Report the week's total pay against the $250 weekly goal. Hidden until at
    // least one day has been worked.
    if (week.satDelivered <= 0 && week.friDelivered <= 0) return
    val met = week.totalPay >= WEEKLY_QUOTA
    Spacer(Modifier.height(3.dp))
    if (met) {
        val over = week.totalPay - WEEKLY_QUOTA
        Text(
            text = "✓ Weekly quota met · $${String.format("%.2f", over)} over",
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF97C459)
        )
    } else {
        val short = WEEKLY_QUOTA - week.totalPay
        // Packages needed to close the gap, at day 2's rate (the remaining workday);
        // fall back to the standard rate if day 2 isn't set yet.
        val closingRate = if (week.friRate > 0) week.friRate else 1.60
        val morePackages = maxOf(0, Math.ceil(short / closingRate).toInt())
        Text(
            text = "⚠ $${String.format("%.2f", short)} short · $morePackages more packages",
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE0A030)
        )
    }
}

@Composable
fun LockedDaySummary(delivered: Int, duplicates: Int, undeliverable: Int, rateMode: Int) {
    val rate = when (rateMode) { 1 -> 1.90; 2 -> 1.80; else -> 1.60 }
    val rateLabel = when (rateMode) { 1 -> "$1.90 · Route 324"; 2 -> "$1.80 · Route 121"; else -> "$1.60 · Standard" }
    val billable = maxOf(0, delivered - duplicates - undeliverable)
    val pay = billable * rate
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0e0e1e)).padding(12.dp)) {
        Text(text = rateLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF888899),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text = "$delivered - $duplicates - $undeliverable = $billable packages → $${String.format("%.2f", pay)}",
            fontSize = 12.sp, color = Color(0xFF7B8CDE), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
fun UnlockButton(onUnlock: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1a1a2e))
            .clickable { onUnlock() }
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🔓 Unlock to edit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7B8CDE))
    }
}

@Composable
fun rateForMode(mode: Int): Double = when (mode) { 1 -> 1.90; 2 -> 1.80; else -> 1.60 }

@Composable
fun RouteRateSelector(mode: Int, onCycle: () -> Unit) {
    val label = when (mode) {
        1 -> "$1.90 · Route 324"
        2 -> "$1.80 · Route 121"
        else -> "$1.60 · Standard"
    }
    val accent = when (mode) {
        1 -> Color(0xFF4CAF50)
        2 -> Color(0xFFD4B84A)
        else -> Color(0xFFB0B0B0)
    }
    val bg = when (mode) {
        1 -> Color(0x1A4CAF50)
        2 -> Color(0x1AD4B84A)
        else -> Color(0xFF0e0e1e)
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onCycle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)
        Text(text = "Tap to change ▸", fontSize = 11.sp, color = Color(0xFF7B8CDE))
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
                     rate: Double,
                     onDeliveredChange: (String) -> Unit, onDuplicatesChange: (String) -> Unit,
                     onUndeliverableChange: (String) -> Unit, onSave: () -> Unit) {
    val del = delivered.toIntOrNull() ?: 0
    val dup = duplicates.toIntOrNull() ?: 0
    val und = undeliverable.toIntOrNull() ?: 0
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0e0e1e)).padding(12.dp)) {
        // Delivered stays a typed field (large count); duplicates/undeliverable use +/- steppers.
        DeliveryField("Delivered", delivered, onDeliveredChange, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        StepperRow("Duplicates", dup, { onDuplicatesChange(maxOf(0, dup - 1).toString()) }, { onDuplicatesChange((dup + 1).toString()) })
        Spacer(Modifier.height(8.dp))
        StepperRow("Undeliverable", und, { onUndeliverableChange(maxOf(0, und - 1).toString()) }, { onUndeliverableChange((und + 1).toString()) })
        Spacer(Modifier.height(10.dp))
        val billable = maxOf(0, del - dup - und)
        val pay = billable * rate
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
                    fetchDeliveryWeeks(baseUrl, month.month)
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
            // Default to the current worked (calendar) month so this week always shows.
            val sel = m.find { it.month == currentWorkedMonth() } ?: m.maxByOrNull { it.month }
            selectedMonth = sel
            weeks = if (sel != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchDeliveryWeeks(baseUrl, sel.month)
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
                            isActiveWeek = isCurrentDeliveryWeek(week.weekStart),
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

// Inline delivery tracker for TODAY only, shown on the Finance home on delivery days.
// Uses +/- steppers for duplicates and undeliverable; delivered stays a typed field
// (it's a large count you enter, not increment one at a time). Reuses the same
// fetch/patch endpoints as the full tracker.
@Composable
fun TodayDeliveryTracker(baseUrl: String) {
    var week by remember { mutableStateOf<DeliveryWeek?>(null) }
    var isDay2 by remember { mutableStateOf(false) }  // Sat = day 2, else day 1
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Friday is day 2 (fri_* columns); Saturday is day 1 (sat_* columns).
        isDay2 = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        val wks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try { fetchDeliveryWeeks(baseUrl, null) } catch (e: Exception) { emptyList() }
        }
        // Pick the week whose week_start <= today and is closest (current week).
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        week = wks.filter { it.weekStart <= todayStr }.maxByOrNull { it.weekStart }
        loading = false
    }

    val w = week ?: return
    fun persist(updated: DeliveryWeek) {
        week = updated
        scope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { patchDeliveryWeek(baseUrl, updated) } catch (e: Exception) {}
            }
        }
    }

    val delivered = if (isDay2) w.friDelivered else w.satDelivered
    // Local text buffer so backspacing to empty doesn't snap to 0 mid-edit; persists
    // only when it parses to a number.
    var deliveredText by remember(delivered) { mutableStateOf(delivered.toString()) }
    val duplicates = if (isDay2) w.friDuplicates else w.satDuplicates
    val undeliverable = if (isDay2) w.friUndeliverable else w.satUndeliverable
    val billable = maxOf(0, delivered - duplicates - undeliverable)
    val dayLabel = if (isDay2) w.friDate() else w.satDate()

    fun setDelivered(v: Int) = persist(if (isDay2) w.copy(friDelivered = maxOf(0, v)) else w.copy(satDelivered = maxOf(0, v)))
    fun setDup(v: Int) = persist(if (isDay2) w.copy(friDuplicates = maxOf(0, v)) else w.copy(satDuplicates = maxOf(0, v)))
    fun setUnd(v: Int) = persist(if (isDay2) w.copy(friUndeliverable = maxOf(0, v)) else w.copy(satUndeliverable = maxOf(0, v)))

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF12122A), RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Today · $dayLabel", color = Color(0xFF7B9CD8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("$billable billable", color = Color(0xFF8FD6A8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        // Delivered is a typed field (large count); dup/undel use steppers.
        DeliveryField("Delivered", deliveredText, { s ->
            deliveredText = s
            s.toIntOrNull()?.let { setDelivered(it) }
        }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        StepperRow("Duplicates", duplicates, { setDup(duplicates - 1) }, { setDup(duplicates + 1) })
        Spacer(Modifier.height(8.dp))
        StepperRow("Undeliverable", undeliverable, { setUnd(undeliverable - 1) }, { setUnd(undeliverable + 1) })
    }
}

@Composable
fun StepperRow(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFCFCFE0), fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepBtn("\u2212", onMinus)
            Text("$value", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            StepBtn("+", onPlus)
        }
    }
}

@Composable
fun StepBtn(sym: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2a3a5c))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(sym, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FinanceScreen(baseUrl: String) {
    var currentView by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = currentView != null) { currentView = null }

    when (currentView) {
        "delivery" -> DeliveryTrackerScreen(baseUrl = baseUrl, onBack = { currentView = null })
        "bookkeeping" -> BookkeepingScreen(baseUrl = baseUrl, onBack = { currentView = null })
        "speedxchecks" -> SpeedxChecksScreen(baseUrl = baseUrl, onBack = { currentView = null })
        "deliveryhistory" -> DeliveryHistoryScreen(baseUrl = baseUrl, onBack = { currentView = null })
        else -> {
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A)).padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("FINANCE", color = Color(0xFF7B8CDE), fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 24.dp))

                // Delivery-day reminder. Fri (6) / Sat (7) in Calendar's 1-based days.
                val todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                if (todayDow == Calendar.FRIDAY || todayDow == Calendar.SATURDAY) {
Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x26F59E0B), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("\uD83D\uDCE6", fontSize = 16.sp, modifier = Modifier.padding(end = 10.dp))
                        Text(
                            "Never do more than 130 packages",
                            color = Color(0xFFF59E0B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x26F87171), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠", fontSize = 16.sp, modifier = Modifier.padding(end = 10.dp))
                        Text(
                            "Route 122 — avoid 8210 Hwy 6",
                            color = Color(0xFFF87171),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x26F87171), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠", fontSize = 16.sp, modifier = Modifier.padding(end = 10.dp))
                        Text(
                            "Route 117 — avoid 415 Grand Ave & C section / the end",
                            color = Color(0xFFF87171),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x26F87171), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("\u26A0", fontSize = 16.sp, modifier = Modifier.padding(end = 10.dp))
                        Text(
                            "Never do Galveston — too many apartments",
                            color = Color(0xFFF87171),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    TodayDeliveryTracker(baseUrl = baseUrl)
                    Spacer(Modifier.height(12.dp))
                }

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

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { currentView = "speedxchecks" },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12122A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🪙  SpeedX Paychecks", color = Color.White, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { currentView = "deliveryhistory" },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12122A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📊  Delivery History", color = Color.White, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}