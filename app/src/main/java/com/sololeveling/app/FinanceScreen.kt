package com.sololeveling.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val tueDelivered: Int,
    val tueDuplicates: Int,
    val tueUndeliverable: Int,
    val wedDelivered: Int,
    val wedDuplicates: Int,
    val wedUndeliverable: Int,
    val tueRoute324: Boolean = false,
    val wedRoute324: Boolean = false,
    val tueRoute121: Boolean = false,
    val wedRoute121: Boolean = false
) {
    val tueBillable: Int get() = maxOf(0, tueDelivered - tueDuplicates - tueUndeliverable)
    val wedBillable: Int get() = maxOf(0, wedDelivered - wedDuplicates - wedUndeliverable)
    val tueRate: Double get() = if (tueRoute324) 1.90 else if (tueRoute121) 1.80 else 1.60
    val wedRate: Double get() = if (wedRoute324) 1.90 else if (wedRoute121) 1.80 else 1.60
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
            cal.add(Calendar.DAY_OF_MONTH, 17) // Wednesday + 17
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

    fun tueDate(): String =
        if (isFriSatWeek()) dayLabel(5, "Fri") else dayLabel(2, "Tue")
    fun wedDate(): String =
        if (isFriSatWeek()) dayLabel(6, "Sat") else dayLabel(3, "Wed")
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
                tueDelivered = obj.getInt("tue_delivered"),
                tueDuplicates = obj.getInt("tue_duplicates"),
                tueUndeliverable = obj.getInt("tue_undeliverable"),
                wedDelivered = obj.getInt("wed_delivered"),
                wedDuplicates = obj.getInt("wed_duplicates"),
                wedUndeliverable = obj.getInt("wed_undeliverable"),
                tueRoute324 = obj.optInt("tue_route324", 0) == 1,
                wedRoute324 = obj.optInt("wed_route324", 0) == 1,
                tueRoute121 = obj.optInt("tue_route121", 0) == 1,
                wedRoute121 = obj.optInt("wed_route121", 0) == 1
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
            put("tue_route121", if (week.tueRoute121) 1 else 0)
            put("wed_route121", if (week.wedRoute121) 1 else 0)
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
            wedRoute324 = obj.optInt("wed_route324", 0) == 1,
            tueRoute121 = obj.optInt("tue_route121", 0) == 1,
            wedRoute121 = obj.optInt("wed_route121", 0) == 1
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

    var tueDelivered by remember(week) { mutableStateOf(week.tueDelivered.toString()) }
    var tueDuplicates by remember(week) { mutableStateOf(week.tueDuplicates.toString()) }
    var tueUndeliverable by remember(week) { mutableStateOf(week.tueUndeliverable.toString()) }
    var wedDelivered by remember(week) { mutableStateOf(week.wedDelivered.toString()) }
    var wedDuplicates by remember(week) { mutableStateOf(week.wedDuplicates.toString()) }
    var wedUndeliverable by remember(week) { mutableStateOf(week.wedUndeliverable.toString()) }
    // Rate state per day: 0 = Standard ($1.60), 1 = Route 324 ($1.90), 2 = Route 121 ($1.80)
    var tueRateMode by remember(week) { mutableStateOf(if (week.tueRoute324) 1 else if (week.tueRoute121) 2 else 0) }
    var wedRateMode by remember(week) { mutableStateOf(if (week.wedRoute324) 1 else if (week.wedRoute121) 2 else 0) }

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
                    when {
                        isActiveWeek -> Text(text = "● ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF97C459))
                        unlocked -> Text(text = "🔓 Unlocked", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD4B84A))
                        else -> Text(text = "🔒 Locked", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF888899))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            DayRow(label = week.tueDate(), billable = week.tueBillable, pay = week.tuePay,
                isExpanded = expandedDay == "tue", onClick = { expandedDay = if (expandedDay == "tue") null else "tue" })
            DayQuotaLine(delivered = week.tueDelivered, billable = week.tueBillable, pay = week.tuePay, rate = week.tueRate)

            if (expandedDay == "tue") {
                Spacer(Modifier.height(10.dp))
                if (editable) {
                    RouteRateSelector(mode = tueRateMode, onCycle = { tueRateMode = (tueRateMode + 1) % 3 })
                    Spacer(Modifier.height(10.dp))
                    DeliveryInputRow(delivered = tueDelivered, duplicates = tueDuplicates, undeliverable = tueUndeliverable,
                        rate = rateForMode(tueRateMode),
                        onDeliveredChange = { tueDelivered = it }, onDuplicatesChange = { tueDuplicates = it },
                        onUndeliverableChange = { tueUndeliverable = it },
                        onSave = {
                            expandedDay = null
                            if (!isActiveWeek) unlocked = false
                            onSave(week.copy(tueDelivered = tueDelivered.toIntOrNull() ?: 0,
                                tueDuplicates = tueDuplicates.toIntOrNull() ?: 0,
                                tueUndeliverable = tueUndeliverable.toIntOrNull() ?: 0,
                                tueRoute324 = tueRateMode == 1,
                                tueRoute121 = tueRateMode == 2))
                        })
                } else {
                    LockedDaySummary(delivered = week.tueDelivered, duplicates = week.tueDuplicates,
                        undeliverable = week.tueUndeliverable, rateMode = tueRateMode)
                    Spacer(Modifier.height(10.dp))
                    UnlockButton(onUnlock = { unlocked = true })
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))

            DayRow(label = week.wedDate(), billable = week.wedBillable, pay = week.wedPay,
                isExpanded = expandedDay == "wed", onClick = { expandedDay = if (expandedDay == "wed") null else "wed" })
            DayQuotaLine(delivered = week.wedDelivered, billable = week.wedBillable, pay = week.wedPay, rate = week.wedRate,
                target = WEEKLY_QUOTA - week.tuePay)

            if (expandedDay == "wed") {
                Spacer(Modifier.height(10.dp))
                if (editable) {
                    RouteRateSelector(mode = wedRateMode, onCycle = { wedRateMode = (wedRateMode + 1) % 3 })
                    Spacer(Modifier.height(10.dp))
                    DeliveryInputRow(delivered = wedDelivered, duplicates = wedDuplicates, undeliverable = wedUndeliverable,
                        rate = rateForMode(wedRateMode),
                        onDeliveredChange = { wedDelivered = it }, onDuplicatesChange = { wedDuplicates = it },
                        onUndeliverableChange = { wedUndeliverable = it },
                        onSave = {
                            expandedDay = null
                            if (!isActiveWeek) unlocked = false
                            onSave(week.copy(wedDelivered = wedDelivered.toIntOrNull() ?: 0,
                                wedDuplicates = wedDuplicates.toIntOrNull() ?: 0,
                                wedUndeliverable = wedUndeliverable.toIntOrNull() ?: 0,
                                wedRoute324 = wedRateMode == 1,
                                wedRoute121 = wedRateMode == 2))
                        })
                } else {
                    LockedDaySummary(delivered = week.wedDelivered, duplicates = week.wedDuplicates,
                        undeliverable = week.wedUndeliverable, rateMode = wedRateMode)
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

// Quota targets: $125 per delivery day; $250 for the week. Day 1 is measured
// against the daily $125. Day 2 is measured against whatever remains of the
// $250 weekly goal after day 1's pay, so a strong day 1 shrinks day 2's
// target. The weekly line reports the week's total against $250.
const val DAILY_QUOTA = 125.0
const val WEEKLY_QUOTA = 250.0

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
    if (week.tueDelivered <= 0 && week.wedDelivered <= 0) return
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
        val closingRate = if (week.wedRate > 0) week.wedRate else 1.60
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

@Composable
fun FinanceScreen(baseUrl: String) {
    var currentView by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = currentView != null) { currentView = null }

    when (currentView) {
        "delivery" -> DeliveryTrackerScreen(baseUrl = baseUrl, onBack = { currentView = null })
        "bookkeeping" -> BookkeepingScreen(baseUrl = baseUrl, onBack = { currentView = null })
        "speedxchecks" -> SpeedxChecksScreen(baseUrl = baseUrl, onBack = { currentView = null })
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
            }
        }
    }
}