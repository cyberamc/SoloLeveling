package com.sololeveling.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class UserReminderItem(
    val id: Int,
    val title: String,
    val remindAt: String,   // "YYYY-MM-DD HH:MM:SS"
    val atMillis: Long
)

private const val REMINDERS_BASE = "https://mysololeveling.us"

/** GET /api/reminders */
fun fetchReminders(): List<UserReminderItem> {
    val url = URL("$REMINDERS_BASE/api/reminders")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    val text = conn.inputStream.bufferedReader().readText()
    conn.disconnect()
    val obj = JSONObject(text)
    val arr = obj.optJSONArray("reminders") ?: return emptyList()
    val out = ArrayList<UserReminderItem>()
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val id = o.optInt("id", -1)
        val title = o.optString("title")
        val at = o.optString("remind_at")
        val millis = try { fmt.parse(at)?.time ?: 0L } catch (e: Exception) { 0L }
        if (id >= 0) out.add(UserReminderItem(id, title, at, millis))
    }
    return out
}

/** POST /api/reminders */
fun createReminder(title: String, date: String, time: String) {
    val url = URL("$REMINDERS_BASE/api/reminders")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    val body = JSONObject()
        .put("title", title)
        .put("date", date)
        .put("time", time)
        .toString()
    conn.outputStream.use { it.write(body.toByteArray()) }
    val code = conn.responseCode
    if (code !in 200..299) {
        val err = try {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        } catch (e: Exception) { "" }
        conn.disconnect()
        throw Exception(if (err.isNotBlank()) err else "Failed to save reminder")
    }
    conn.inputStream.bufferedReader().readText()
    conn.disconnect()
}

/** DELETE /api/reminders/{id} */
fun deleteReminder(id: Int) {
    val url = URL("$REMINDERS_BASE/api/reminders/$id")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "DELETE"
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    conn.inputStream.bufferedReader().readText()
    conn.disconnect()
}

/** "2026-07-21 14:30:00" -> "Tomorrow 2:30 PM" */
fun formatReminderWhen(millis: Long): String {
    if (millis <= 0L) return ""
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    var h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    val ap = if (h >= 12) "PM" else "AM"
    h %= 12
    if (h == 0) h = 12
    val timeStr = "$h:${m.toString().padStart(2, '0')} $ap"

    return when {
        sameDay(cal, now) -> "Today $timeStr"
        sameDay(cal, tomorrow) -> "Tomorrow $timeStr"
        else -> {
            val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            "${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}, $timeStr"
        }
    }
}

@Composable
fun RemindersScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reminders by remember { mutableStateOf<List<UserReminderItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }

    // Default to one hour from now.
    val initial = remember { Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) } }
    var year by remember { mutableStateOf(initial.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(initial.get(Calendar.MONTH)) }
    var day by remember { mutableStateOf(initial.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableStateOf(initial.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(initial.get(Calendar.MINUTE)) }

    fun chosenMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val leadMillis = UserReminder.POLL_MINUTES * 60_000L
    val underLead = (chosenMillis() - System.currentTimeMillis()) < leadMillis

    fun reload() {
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) { fetchReminders() }
                reminders = list
                error = null
                loading = false
            } catch (e: Exception) {
                error = e.message ?: "Could not load reminders"
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    val dateLabel = "${month + 1}/$day/$year"
    val timeLabel = run {
        var h = hour
        val ap = if (h >= 12) "PM" else "AM"
        h %= 12
        if (h == 0) h = 12
        "$h:${minute.toString().padStart(2, '0')} $ap"
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1a1a1a))
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "←",
                    fontSize = 24.sp,
                    color = Color(0xFFFFD700),
                    modifier = Modifier.clickable { onBack() }.padding(end = 12.dp)
                )
                Text(text = "Reminders", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "One-time notifications",
                fontSize = 13.sp,
                color = Color(0xFF888888)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Add form ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("What", color = Color(0xFF888888)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color(0xFF3a3a3a),
                            cursorColor = Color(0xFFFFD700)
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = dateLabel,
                            fontSize = 15.sp,
                            color = Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF2a2a2a), shape = RoundedCornerShape(8.dp))
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, y, mo, d -> year = y; month = mo; day = d },
                                        year, month, day
                                    ).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        )
                        Text(
                            text = timeLabel,
                            fontSize = 15.sp,
                            color = Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF2a2a2a), shape = RoundedCornerShape(8.dp))
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m -> hour = h; minute = m },
                                        hour, minute, false
                                    ).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        )
                    }

                    if (underLead) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Under ${UserReminder.POLL_MINUTES} minutes away — it will be armed as soon as you save from here.",
                            fontSize = 12.sp,
                            color = Color(0xFFE5B567),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x332A2010), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (saving) "Saving…" else "Add reminder",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0a0a0a),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (saving) Color(0xFF7a6a1a) else Color(0xFFFFD700),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = !saving) {
                                val t = title.trim()
                                if (t.isEmpty()) {
                                    error = "Enter what the reminder is for"
                                    return@clickable
                                }
                                if (chosenMillis() <= System.currentTimeMillis()) {
                                    error = "That time has already passed"
                                    return@clickable
                                }
                                saving = true
                                error = null
                                scope.launch {
                                    try {
                                        val d = "%04d-%02d-%02d".format(year, month + 1, day)
                                        val tm = "%02d:%02d".format(hour, minute)
                                        withContext(Dispatchers.IO) { createReminder(t, d, tm) }
                                        title = ""
                                        // Arm right away so short-notice reminders still fire.
                                        withContext(Dispatchers.IO) { UserReminder.armAllNow(context) }
                                        saving = false
                                        reload()
                                    } catch (e: Exception) {
                                        error = e.message ?: "Could not save"
                                        saving = false
                                    }
                                }
                            }
                            .padding(vertical = 13.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            error?.let { msg ->
                item {
                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        color = Color(0xFFE77777),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                }
            }

            // ── Pending list ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pending",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (reminders.isNotEmpty()) {
                        Text(
                            text = "  · ${reminders.size}",
                            fontSize = 13.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }

            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFFD700))
                    }
                }
            } else if (reminders.isEmpty()) {
                item {
                    Text(
                        text = "No pending reminders.",
                        fontSize = 13.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                items(reminders, key = { it.id }) { r ->
                    val soon = (r.atMillis - System.currentTimeMillis()) < leadMillis
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1a1a1a), shape = RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = r.title,
                                fontSize = 15.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = formatReminderWhen(r.atMillis),
                                fontSize = 13.sp,
                                color = if (soon) Color(0xFFE5B567) else Color(0xFF9AABBB)
                            )
                        }
                        Text(
                            text = "×",
                            fontSize = 22.sp,
                            color = Color(0xFFAA5555),
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { deleteReminder(r.id) }
                                            reload()
                                        } catch (e: Exception) {
                                            error = e.message ?: "Could not delete"
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
