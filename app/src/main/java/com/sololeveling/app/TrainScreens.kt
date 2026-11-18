package com.sololeveling.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Train: the in-app Hevy replacement (routines, live session, history, exercise detail) ───

private const val TR_BASE = "https://mysololeveling.us"
private val TR_GOLD = Color(0xFFFFD700)
private val TR_CARD = Color(0xFF111728)
private val TR_MUTED = Color(0xFF6b7689)
private val TR_GREEN = Color(0xFF10b981)

fun trainCall(method: String, path: String, body: Map<String, Any?>? = null): String {
    val conn = URL(TR_BASE + path).openConnection() as HttpURLConnection
    conn.requestMethod = method
    conn.connectTimeout = 8000
    conn.readTimeout = 15000
    conn.setRequestProperty("Content-Type", "application/json")
    try {
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(com.google.gson.GsonBuilder().serializeNulls().create().toJson(body).toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw Exception("HTTP " + code + " " + text)
        return text
    } finally {
        conn.disconnect()
    }
}

private fun trJson(text: String): Map<*, *> = (Gson().fromJson(text, Map::class.java) as? Map<*, *>) ?: emptyMap<Any, Any>()

private fun trParseIso(s: String?): Long {
    if (s == null || s.length < 19) return System.currentTimeMillis()
    return try {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        f.parse(s.substring(0, 19))?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}

private fun trFmtDate(iso: String?): String {
    val ms = trParseIso(iso)
    return SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(Date(ms))
}

private fun trFmtW(w: Double?): String {
    if (w == null) return ""
    val r = Math.round(w * 10.0) / 10.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}

private fun trFmtClock(totalSeconds: Long): String {
    val s = if (totalSeconds < 0) 0 else totalSeconds
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

private fun trFmtDuration(seconds: Long?): String {
    if (seconds == null) return ""
    val m = seconds / 60
    return if (m >= 60) (m / 60).toString() + "h " + (m % 60).toString() + "m" else m.toString() + "m"
}

private fun trTitleCase(s: String?): String {
    if (s == null) return ""
    return s.replace('_', ' ').split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
}

private fun trSetText(w: Double?, reps: Int?, seconds: Int?): String {
    if (w != null && w > 0.0 && reps != null) return trFmtW(w) + " × " + reps
    if (reps != null) return reps.toString() + " reps"
    if (seconds != null) return trFmtClock(seconds.toLong())
    return "—"
}

private fun trTypeLetter(type: String): String = when (type) {
    "warmup" -> "W"
    "failure" -> "F"
    "dropset" -> "D"
    else -> ""
}

private fun trTypeColor(type: String): Color = when (type) {
    "warmup" -> Color(0xFFfbbf24)
    "failure" -> Color(0xFFf87171)
    "dropset" -> Color(0xFFa78bfa)
    else -> Color(0xFF9ca3af)
}

// ── Models ──

class TrSetUi(val id: Long, type0: String, weight0: String, reps0: String, done0: Boolean) {
    var type by mutableStateOf(type0)
    var weight by mutableStateOf(weight0)
    var reps by mutableStateOf(reps0)
    var done by mutableStateOf(done0)
}

class TrExUi(
    val id: Long,
    val exerciseId: String?,
    val title: String,
    val notes: String,
    val rest: Int,
    val primary: String?,
    val secondary: List<String>,
    val previous: List<String>,
    val sets: SnapshotStateList<TrSetUi>
)

class TrSessionUi(val id: String, val routineTitle: String, val startMs: Long, val exercises: List<TrExUi>)

private fun trParseSession(m: Map<*, *>): TrSessionUi {
    val exs = ArrayList<TrExUi>()
    (m["exercises"] as? List<*>)?.forEach { e ->
        val em = e as? Map<*, *>
        if (em != null) {
            val sets = mutableStateListOf<TrSetUi>()
            (em["sets"] as? List<*>)?.forEach { s ->
                val sm = s as? Map<*, *>
                if (sm != null) {
                    sets.add(
                        TrSetUi(
                            (sm["id"] as? Number)?.toLong() ?: 0L,
                            (sm["type"] as? String) ?: "normal",
                            trFmtW((sm["weight_lbs"] as? Number)?.toDouble()),
                            (sm["reps"] as? Number)?.toInt()?.toString() ?: "",
                            (sm["done"] as? Boolean) ?: false
                        )
                    )
                }
            }
            val prev = ArrayList<String>()
            (em["previous"] as? List<*>)?.forEach { p ->
                val pm = p as? Map<*, *>
                if (pm != null) {
                    prev.add(trSetText((pm["weight_lbs"] as? Number)?.toDouble(), (pm["reps"] as? Number)?.toInt(), (pm["duration_seconds"] as? Number)?.toInt()))
                }
            }
            val sec = ArrayList<String>()
            (em["secondary_muscles"] as? List<*>)?.forEach { x -> if (x is String) sec.add(x) }
            exs.add(
                TrExUi(
                    (em["id"] as? Number)?.toLong() ?: 0L,
                    em["exercise_id"] as? String,
                    (em["title"] as? String) ?: "Exercise",
                    (em["notes"] as? String) ?: "",
                    (em["rest_seconds"] as? Number)?.toInt() ?: 0,
                    em["primary_muscle"] as? String,
                    sec,
                    prev,
                    sets
                )
            )
        }
    }
    return TrSessionUi(
        (m["id"] as? String) ?: "",
        (m["routine_title"] as? String) ?: "Workout",
        trParseIso(m["start_time"] as? String),
        exs
    )
}

// ── Top level: this IS the Gym tab now ──

@Composable
fun TrainScreen() {
    var tab by remember { mutableStateOf(0) }
    var session by remember { mutableStateOf<TrSessionUi?>(null) }
    var minimized by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<Map<*, *>?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var editId by remember { mutableStateOf<String?>(null) }
    var loadingActive by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var rotation by remember { mutableStateOf<BlockRotation?>(null) }

    LaunchedEffect(Unit) {
        try {
            val text = withContext(Dispatchers.IO) { trainCall("GET", "/api/train/session/active") }
            val s = trJson(text)["session"] as? Map<*, *>
            if (s != null) session = trParseSession(s)
        } catch (e: Exception) {
            android.util.Log.e("TRAIN", "active: " + e.message)
        } finally {
            loadingActive = false
        }
    }
    LaunchedEffect(Unit) {
        rotation = try { fetchBlockRotation() } catch (e: Exception) { null }
    }

    BackHandler(enabled = detailId != null || summary != null) {
        if (detailId != null) detailId = null else summary = null
    }

    val did = detailId
    val eid = editId
    val sum = summary
    val sess = session
    when {
        did != null -> TrExerciseDetail(did, onBack = { detailId = null })
        sum != null -> TrSummaryScreen(sum, onDone = { summary = null; reloadKey++ })
        eid != null -> TrRoutineEditor(eid, onBack = { editId = null; reloadKey++ })
        sess != null && !minimized -> TrSessionScreen(
            session = sess,
            onOpenDetail = { detailId = it },
            onFinished = { s -> session = null; minimized = false; summary = s },
            onDiscarded = { session = null; minimized = false; reloadKey++ },
            onLeave = { minimized = true }
        )
        else -> Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).systemBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                Text("Gym", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a))) {
                listOf("Workout", "History").forEachIndexed { i, name ->
                    Box(
                        modifier = Modifier.weight(1f).clickable { tab = i }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, color = if (tab == i) TR_GOLD else TR_MUTED, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Box(modifier = Modifier.width(60.dp).height(2.dp).background(if (tab == i) TR_GOLD else Color.Transparent))
                        }
                    }
                }
            }
            if (loadingActive) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = TR_GOLD) }
            } else if (tab == 0) {
                TrRoutinesTab(
                    reloadKey = reloadKey,
                    rotation = rotation,
                    active = sess,
                    onResume = { minimized = false },
                    onStarted = { session = it; minimized = false },
                    onEdit = { editId = it }
                )
            } else {
                TrHistoryTab(reloadKey, onOpenDetail = { detailId = it })
            }
        }
    }
}

@Composable
private fun TrStatusCard(rot: BlockRotation?, thisWeek: Int, goal: Int, streak: Int, best: Int) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF141414), RoundedCornerShape(10.dp)).padding(14.dp)) {
        if (rot != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("BLOCK " + rot.block + " · WEEK " + rot.weekInBlock + " OF 4", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TR_GOLD, letterSpacing = 1.sp)
                Text(if (rot.daysLeftInBlock > 0) rot.daysLeftInBlock.toString() + " days left" else "last day", fontSize = 11.sp, color = TR_MUTED)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (1..4).forEach { w ->
                    Box(modifier = Modifier.weight(1f).height(5.dp).background(if (w <= rot.weekInBlock) TR_GOLD else Color(0xFF2a2a3a), RoundedCornerShape(3.dp)))
                }
            }
            Spacer(Modifier.height(5.dp))
            Text("Day " + rot.dayInBlock + " of 28 · block ends " + rot.blockEnd, fontSize = 10.sp, color = TR_MUTED)
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2a2a3a)))
            Spacer(Modifier.height(12.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("THIS WEEK", fontSize = 10.sp, color = TR_MUTED, letterSpacing = 1.sp)
                Text(thisWeek.toString() + " / " + goal, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (thisWeek >= goal) TR_GREEN else Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("STREAK", fontSize = 10.sp, color = TR_MUTED, letterSpacing = 1.sp)
                Text(streak.toString() + " wk", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TR_GOLD)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("BEST", fontSize = 10.sp, color = TR_MUTED, letterSpacing = 1.sp)
                Text(best.toString() + " wk", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ── Workout tab: streak + this block's routines ──

@Composable
fun TrRoutinesTab(reloadKey: Int, rotation: BlockRotation?, active: TrSessionUi?, onResume: () -> Unit, onStarted: (TrSessionUi) -> Unit, onEdit: (String) -> Unit) {
    var blockTitle by remember { mutableStateOf("") }
    var routines by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    var stThis by remember { mutableStateOf(0) }
    var stGoal by remember { mutableStateOf(0) }
    var stCur by remember { mutableStateOf(0) }
    var stBest by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var folders by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    val expandedIds = remember { mutableStateListOf<String>() }
    var starting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey, showAll) {
        loading = true
        try {
            val t = withContext(Dispatchers.IO) { trainCall("GET", "/api/train/routines" + (if (showAll) "?all=1" else "")) }
            val m = trJson(t)
            blockTitle = ((m["block"] as? Map<*, *>)?.get("title") as? String) ?: ""
            routines = (m["routines"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
            folders = (m["folders"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
            val st = trJson(withContext(Dispatchers.IO) { trainCall("GET", "/api/train/streak") })
            stThis = (st["this_week"] as? Number)?.toInt() ?: 0
            stGoal = (st["goal"] as? Number)?.toInt() ?: 0
            stCur = (st["current_streak"] as? Number)?.toInt() ?: 0
            stBest = (st["best_streak"] as? Number)?.toInt() ?: 0
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = TR_GOLD) }
        return
    }
    val startRoutine: (String) -> Unit = { rid ->
        if (!starting) {
            starting = true
            scope.launch {
                try {
                    val t = withContext(Dispatchers.IO) { trainCall("POST", "/api/train/session/start", mapOf("routine_id" to rid)) }
                    val sess = trJson(t)["session"] as? Map<*, *>
                    if (sess != null) onStarted(trParseSession(sess))
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    starting = false
                }
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                if (active != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF14532d), RoundedCornerShape(8.dp)).clickable { onResume() }.padding(14.dp)
                    ) { Text("▶ Workout in progress: " + active.routineTitle + " — tap to resume", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(10.dp))
                }
                TrStatusCard(rotation, stThis, stGoal, stCur, stBest)
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (showAll) "ALL ROUTINES" else blockTitle.uppercase(), fontSize = 11.sp, color = TR_MUTED, letterSpacing = 1.5.sp)
                    Text(
                        if (showAll) "Current block" else "All blocks", fontSize = 12.sp, color = Color(0xFF7B8CDE),
                        modifier = Modifier.clickable { showAll = !showAll }
                    )
                }
                if (error != null) Text("Could not load: " + error, color = Color(0xFFf87171), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (!showAll || folders.isEmpty()) {
            items(routines) { r -> TrRoutineCard(r, startRoutine, onEdit) }
        } else {
            folders.forEachIndexed { fi, f ->
                val fid = (f["folder_id"] as? String) ?: ""
                val isCollapsed = !expandedIds.contains(fid)
                val group = routines.filter { (it["folder_id"] as? String) == fid }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a2e), RoundedCornerShape(10.dp))
                            .clickable { if (isCollapsed) expandedIds.add(fid) else expandedIds.remove(fid) }.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("BLOCK " + (fi + 1), color = TR_GOLD, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text((f["title"] as? String ?: "") + " · " + group.size + " routines", color = TR_MUTED, fontSize = 11.sp)
                        }
                        Text(if (isCollapsed) "▸" else "▾", color = TR_GOLD, fontSize = 18.sp)
                    }
                }
                if (!isCollapsed) items(group) { r -> TrRoutineCard(r, startRoutine, onEdit) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TrRoutineCard(r: Map<*, *>, onStart: (String) -> Unit, onEdit: (String) -> Unit) {
    val title = (r["title"] as? String) ?: ""
    val rid = (r["routine_id"] as? String) ?: ""
    val exs = (r["exercises"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF141414), RoundedCornerShape(10.dp)).padding(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Edit", color = Color(0xFF7B8CDE), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onEdit(rid) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        exs.forEach { e ->
            val n = (e["sets"] as? List<*>)?.size ?: 0
            Text(n.toString() + " × " + ((e["title"] as? String) ?: ""), color = Color(0xFFb8c0cc), fontSize = 13.sp, modifier = Modifier.padding(vertical = 1.dp))
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onStart(rid) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1e1e3a)),
            border = BorderStroke(1.dp, Color(0xFF3a3a5a)),
            modifier = Modifier.fillMaxWidth()
        ) { Text("▶ Start Workout", color = Color(0xFFa78bfa), fontWeight = FontWeight.Bold) }
    }
}

// ── Active session ──

@Composable
fun TrSessionScreen(
    session: TrSessionUi,
    onOpenDetail: (String) -> Unit,
    onFinished: (Map<*, *>) -> Unit,
    onDiscarded: () -> Unit,
    onLeave: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var restEnd by remember { mutableStateOf(0L) }
    var restLabel by remember { mutableStateOf("") }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val unfinished = session.exercises.sumOf { ex -> ex.sets.count { !it.done } }
    val doneCount = session.exercises.sumOf { ex -> ex.sets.count { it.done } }
    val restLeft = (restEnd - now) / 1000

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.routineTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        trFmtClock((now - session.startMs) / 1000) + "  ·  " + doneCount + " sets done",
                        color = TR_GOLD, fontSize = 14.sp, fontFamily = FontFamily.Monospace
                    )
                }
                Text("Leave", color = TR_MUTED, fontSize = 13.sp, modifier = Modifier.clickable { onLeave() }.padding(8.dp))
                Button(
                    onClick = { confirmFinish = true },
                    colors = ButtonDefaults.buttonColors(containerColor = TR_GREEN),
                    enabled = !busy
                ) { Text("Finish", color = Color.White, fontWeight = FontWeight.Bold) }
            }
            if (restLeft > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1e1e3a), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rest  " + trFmtClock(restLeft) + "  " + restLabel, color = Color(0xFFa78bfa), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Skip", color = Color(0xFFf87171), fontSize = 13.sp, modifier = Modifier.clickable {
                        TimerService.stop(context)
                        restEnd = 0L
                    }.padding(4.dp))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(2.dp)) }
            items(session.exercises) { ex ->
                TrExerciseCard(
                    ex = ex,
                    sessionId = session.id,
                    onOpenDetail = onOpenDetail,
                    onRestStart = { secs ->
                        TimerService.start(context, "Rest · " + ex.title, secs, "Rest over — next set", true)
                        restEnd = System.currentTimeMillis() + secs * 1000L
                        restLabel = ex.title
                    }
                )
            }
            item {
                if (error != null) Text(error ?: "", color = Color(0xFFf87171), fontSize = 13.sp)
                Text(
                    "Discard workout", color = Color(0xFFf87171), fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clickable { confirmDiscard = true }.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            containerColor = Color(0xFF1a1a1a),
            title = { Text("Finish workout?", color = Color.White) },
            text = {
                Text(
                    if (unfinished > 0) unfinished.toString() + " sets aren't checked off and will be dropped." else "All sets are done.",
                    color = Color(0xFFcdd6e2)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    busy = true
                    scope.launch {
                        try {
                            val t = withContext(Dispatchers.IO) { trainCall("POST", "/api/train/session/" + session.id + "/finish", emptyMap()) }
                            TimerService.stop(context)
                            val m = trJson(t)
                            if (m["discarded"] == true) onDiscarded() else onFinished(m)
                        } catch (e: Exception) {
                            error = "Could not finish: " + e.message
                            busy = false
                        }
                    }
                }) { Text("Finish", color = TR_GREEN, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going", color = TR_MUTED) } }
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Color(0xFF1a1a1a),
            title = { Text("Discard workout?", color = Color.White) },
            text = { Text("Everything logged in this session will be deleted.", color = Color(0xFFcdd6e2)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { trainCall("DELETE", "/api/train/session/" + session.id) }
                            TimerService.stop(context)
                            onDiscarded()
                        } catch (e: Exception) {
                            error = "Could not discard: " + e.message
                        }
                    }
                }) { Text("Discard", color = Color(0xFFf87171), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel", color = TR_MUTED) } }
        )
    }
}

@Composable
fun TrExerciseCard(ex: TrExUi, sessionId: String, onOpenDetail: (String) -> Unit, onRestStart: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    // Normal sets are numbered; W / F / D are lettered (like Hevy).
    val labels = ArrayList<String>()
    var n = 0
    ex.sets.forEach { s ->
        val letter = trTypeLetter(s.type)
        if (letter.isEmpty()) { n++; labels.add(n.toString()) } else labels.add(letter)
    }
    Column(modifier = Modifier.fillMaxWidth().background(TR_CARD, RoundedCornerShape(10.dp)).padding(12.dp)) {
        Text(
            ex.title, color = TR_GOLD, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { val id = ex.exerciseId; if (id != null) onOpenDetail(id) }
        )
        val muscles = ArrayList<String>()
        if (ex.primary != null) muscles.add(trTitleCase(ex.primary))
        ex.secondary.forEach { muscles.add(trTitleCase(it)) }
        if (muscles.isNotEmpty()) Text(muscles.joinToString(" · "), color = TR_MUTED, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        if (ex.notes.isNotBlank()) {
            Text(ex.notes, color = Color(0xFFfbbf24), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (ex.rest > 0) Text("Rest " + trFmtClock(ex.rest.toLong()), color = Color(0xFFa78bfa), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SET", color = TR_MUTED, fontSize = 10.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            Text("PREVIOUS", color = TR_MUTED, fontSize = 10.sp, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center)
            Text("LBS", color = TR_MUTED, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("REPS", color = TR_MUTED, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.width(44.dp))
        }
        ex.sets.forEachIndexed { i, s ->
            TrSetRow(
                set = s,
                label = if (i < labels.size) labels[i] else "",
                prev = if (i < ex.previous.size) ex.previous[i] else "—",
                onToggle = { newDone ->
                    s.done = newDone
                    scope.launch {
                        try {
                            val body = HashMap<String, Any?>()
                            body["weight_lbs"] = s.weight.toDoubleOrNull()
                            body["reps"] = s.reps.toIntOrNull()
                            body["type"] = s.type
                            body["done"] = newDone
                            val t = withContext(Dispatchers.IO) { trainCall("PATCH", "/api/train/set/" + s.id, body) }
                            val rest = (trJson(t)["rest_seconds"] as? Number)?.toInt() ?: 0
                            if (newDone && rest > 0) onRestStart(rest)
                        } catch (e: Exception) {
                            s.done = !newDone
                        }
                    }
                },
                onDelete = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { trainCall("DELETE", "/api/train/set/" + s.id) }
                            ex.sets.remove(s)
                        } catch (e: Exception) {
                        }
                    }
                }
            )
        }
        Text(
            "+ Add Set", color = Color(0xFF7B8CDE), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable {
                scope.launch {
                    try {
                        val t = withContext(Dispatchers.IO) { trainCall("POST", "/api/train/session-exercise/" + ex.id + "/set", emptyMap()) }
                        val newId = (trJson(t)["set_id"] as? Number)?.toLong() ?: 0L
                        val last = if (ex.sets.isNotEmpty()) ex.sets[ex.sets.size - 1] else null
                        ex.sets.add(TrSetUi(newId, "normal", last?.weight ?: "", last?.reps ?: "", false))
                    } catch (e: Exception) {
                    }
                }
            }.padding(vertical = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TrSetRow(set: TrSetUi, label: String, prev: String, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Edits are saved ~1s after you stop typing (so an app kill never loses a half-filled set).
    LaunchedEffect(set.weight, set.reps, set.type) {
        if (first) { first = false; return@LaunchedEffect }
        delay(900)
        try {
            val body = HashMap<String, Any?>()
            body["weight_lbs"] = set.weight.toDoubleOrNull()
            body["reps"] = set.reps.toIntOrNull()
            body["type"] = set.type
            withContext(Dispatchers.IO) { trainCall("PATCH", "/api/train/set/" + set.id, body) }
        } catch (e: Exception) {
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(if (set.done) Color(0xFF0f2a22) else Color.Transparent, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Text(
                label, color = trTypeColor(set.type), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { menu = true }.padding(horizontal = 10.dp, vertical = 8.dp)
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Normal Set") }, onClick = { set.type = "normal"; menu = false })
                DropdownMenuItem(text = { Text("Warm Up (W)") }, onClick = { set.type = "warmup"; menu = false })
                DropdownMenuItem(text = { Text("To Failure (F)") }, onClick = { set.type = "failure"; menu = false })
                DropdownMenuItem(text = { Text("Drop Set (D)") }, onClick = { set.type = "dropset"; menu = false })
                DropdownMenuItem(text = { Text("Delete Set") }, onClick = { menu = false; onDelete() })
            }
        }
        Text(prev, color = TR_MUTED, fontSize = 12.sp, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center)
        TrNumField(set.weight, { set.weight = it }, true, set.done, Modifier.weight(1f).padding(horizontal = 4.dp))
        TrNumField(set.reps, { set.reps = it }, false, set.done, Modifier.weight(1f).padding(horizontal = 4.dp))
        Box(
            modifier = Modifier.width(44.dp).height(36.dp).padding(horizontal = 6.dp)
                .background(if (set.done) TR_GREEN else Color(0xFF2a2a3a), RoundedCornerShape(6.dp))
                .clickable { onToggle(!set.done) },
            contentAlignment = Alignment.Center
        ) { Text("✓", color = if (set.done) Color.White else Color(0xFF555566), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun TrNumField(value: String, onChange: (String) -> Unit, decimal: Boolean, done: Boolean, modifier: Modifier, allowDash: Boolean = false) {
    BasicTextField(
        value = value,
        onValueChange = { v ->
            if (v.length <= 7 && v.all { it.isDigit() || (decimal && it == '.') || (allowDash && it == '-') }) onChange(v)
        },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        cursorBrush = SolidColor(TR_GOLD),
        modifier = modifier.background(if (done) Color(0xFF14532d) else Color(0xFF1f2937), RoundedCornerShape(6.dp)).padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

// ── Finish summary ──

@Composable
fun TrSummaryScreen(data: Map<*, *>, onDone: () -> Unit) {
    val sum = data["summary"] as? Map<*, *>
    val prs = (data["prs"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
    val st = data["streak"] as? Map<*, *>
    val dur = (sum?.get("duration_seconds") as? Number)?.toLong()
    val vol = (sum?.get("volume_lbs") as? Number)?.toInt() ?: 0
    val sets = (sum?.get("working_sets") as? Number)?.toInt() ?: 0
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a)).systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Workout Complete", color = TR_GOLD, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrStat("Duration", trFmtDuration(dur), Modifier.weight(1f))
            TrStat("Volume", vol.toString() + " lbs", Modifier.weight(1f))
            TrStat("Work sets", sets.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text("WEEK STREAK", fontSize = 11.sp, color = TR_MUTED, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        if (st != null) {
            val cur = (st["current_streak"] as? Number)?.toInt() ?: 0
            val best = (st["best_streak"] as? Number)?.toInt() ?: 0
            val tw = (st["this_week"] as? Number)?.toInt() ?: 0
            val goal = (st["goal"] as? Number)?.toInt() ?: 0
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrStat("Current", cur.toString() + " wk", Modifier.weight(1f), TR_GOLD)
                TrStat("Best", best.toString() + " wk", Modifier.weight(1f))
                TrStat("This week", tw.toString() + "/" + goal, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("PERSONAL RECORDS", fontSize = 11.sp, color = TR_MUTED, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        if (prs.isEmpty()) {
            Text("No new PRs this time.", color = TR_MUTED, fontSize = 13.sp)
        } else {
            prs.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).background(TR_CARD, RoundedCornerShape(8.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text((p["exercise"] as? String) ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text((p["kind"] as? String) ?: "", color = TR_MUTED, fontSize = 11.sp)
                    }
                    val unit = (p["unit"] as? String) ?: ""
                    Text(
                        "🏆 " + (p["value"] as? Number)?.toInt() + " " + unit + "  (was " + (p["previous"] as? Number)?.toInt() + ")",
                        color = TR_GOLD, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = TR_GREEN), modifier = Modifier.fillMaxWidth()) {
            Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TrStat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.White) {
    Box(modifier = modifier.background(Color(0xFF1a1a1a), RoundedCornerShape(8.dp)).padding(10.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = TR_MUTED)
            Spacer(Modifier.height(3.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

// ── History tab ──

@Composable
fun TrHistoryTab(reloadKey: Int, onOpenDetail: (String) -> Unit) {
    var sessions by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(offset: Int) {
        loading = true
        try {
            val t = withContext(Dispatchers.IO) { trainCall("GET", "/api/train/history?limit=20&offset=" + offset) }
            val m = trJson(t)
            total = (m["total"] as? Number)?.toInt() ?: 0
            val more = (m["sessions"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
            sessions = if (offset == 0) more else sessions + more
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(reloadKey) { load(0) }

    if (loading && sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = TR_GOLD) }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                total.toString() + " WORKOUTS", fontSize = 11.sp, color = TR_MUTED, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (error != null) Text("Could not load: " + error, color = Color(0xFFf87171), fontSize = 13.sp)
        }
        items(sessions) { s ->
            val exs = (s["exercises"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF141414), RoundedCornerShape(10.dp)).padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text((s["routine_title"] as? String) ?: "Workout", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(trFmtDuration((s["duration_seconds"] as? Number)?.toLong()), color = TR_MUTED, fontSize = 12.sp)
                }
                Text(trFmtDate(s["start_time"] as? String), color = TR_GOLD, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                exs.forEach { e ->
                    val eid = e["exercise_id"] as? String
                    Text(
                        (e["title"] as? String) ?: "", color = Color(0xFFb8c0cc), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp).clickable { if (eid != null) onOpenDetail(eid) }
                    )
                    var n = 0
                    (e["sets"] as? List<*>)?.forEach { x ->
                        val sm = x as? Map<*, *>
                        if (sm != null) {
                            val type = (sm["type"] as? String) ?: "normal"
                            val letter = trTypeLetter(type)
                            val lab = if (letter.isEmpty()) { n++; n.toString() } else letter
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                Text(lab, color = trTypeColor(type), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                Text(
                                    trSetText((sm["weight_lbs"] as? Number)?.toDouble(), (sm["reps"] as? Number)?.toInt(), (sm["duration_seconds"] as? Number)?.toInt()),
                                    color = Color.White, fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        if (sessions.size < total) {
            item {
                Text(
                    if (loading) "Loading..." else "Load more", color = Color(0xFF7B8CDE), fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { if (!loading) scope.launch { load(sessions.size) } }.padding(16.dp)
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Exercise detail: muscles, notes, PRs, progress chart ──

@Composable
fun TrExerciseDetail(exerciseId: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<Map<*, *>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sessions by remember { mutableStateOf<List<ExerciseSession>>(emptyList()) }

    LaunchedEffect(exerciseId) {
        try {
            val t = withContext(Dispatchers.IO) { trainCall("GET", "/api/train/exercise/" + exerciseId) }
            val m = trJson(t)
            data = m
            sessions = (m["sessions"] as? List<*>)?.mapNotNull { x ->
                val sm = x as? Map<*, *>
                if (sm == null) null else ExerciseSession(
                    (sm["date"] as? String) ?: "",
                    (sm["weight_lbs"] as? Number)?.toInt() ?: 0,
                    (sm["reps"] as? Number)?.toInt() ?: 0,
                    (sm["estimated_1rm_lbs"] as? Number)?.toInt() ?: 0
                )
            } ?: emptyList()
        } catch (e: Exception) {
            error = e.message
        }
    }

    val d = data
    val bodyweight = d != null && (d["type"] as? String) == "reps_only"
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).systemBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text((d?.get("title") as? String) ?: "Exercise", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Text("Back", color = TR_GOLD, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp).clickable { onBack() })
        }
        if (d == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error != null) Text("Could not load: " + error, color = Color(0xFFf87171)) else CircularProgressIndicator(color = TR_GOLD)
            }
            return@Column
        }
        val pr = d["prs_computed"] as? Map<*, *>
        val secondary = (d["secondary_muscles"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).background(TR_CARD, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Text("MUSCLES", fontSize = 10.sp, color = TR_MUTED, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val p = d["primary_muscle"] as? String
                        if (p != null) TrChip(trTitleCase(p), true)
                        secondary.forEach { TrChip(trTitleCase(it), false) }
                    }
                    val notes = d["notes"] as? String
                    if (!notes.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("NOTES", fontSize = 10.sp, color = TR_MUTED, letterSpacing = 1.5.sp)
                        Text(notes, color = Color(0xFFfbbf24), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    val rest = (d["rest_seconds"] as? Number)?.toInt()
                    if (rest != null && rest > 0) Text("Rest " + trFmtClock(rest.toLong()), color = Color(0xFFa78bfa), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("PERSONAL RECORDS", fontSize = 10.sp, color = TR_MUTED, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrStat("Heaviest", if (bodyweight) "—" else ((pr?.get("heaviest_lbs") as? Number)?.toInt()?.toString() ?: "0") + " lbs", Modifier.weight(1f))
                        TrStat(
                            if (bodyweight) "Best reps" else "Best 1RM",
                            if (bodyweight) ((pr?.get("best_reps") as? Number)?.toInt()?.toString() ?: "0") else ((pr?.get("best_1rm_lbs") as? Number)?.toInt()?.toString() ?: "0") + " lbs",
                            Modifier.weight(1f), TR_GOLD
                        )
                        TrStat(
                            "Best set vol",
                            if (bodyweight) "—" else ((pr?.get("best_set_weight_lbs") as? Number)?.toInt()?.toString() ?: "0") + " × " + ((pr?.get("best_set_reps") as? Number)?.toInt()?.toString() ?: "0"),
                            Modifier.weight(1f)
                        )
                    }
                }
            }
            item {
                Box(modifier = Modifier.fillMaxWidth().background(TR_CARD, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Column {
                        Text(if (bodyweight) "Reps over time" else "Max weight over time", fontSize = 11.sp, color = TR_MUTED, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        if (sessions.size >= 2) GymProgressChart(sessions, byReps = bodyweight)
                        else Box(modifier = Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                            Text("Not enough sessions yet", fontSize = 12.sp, color = TR_MUTED)
                        }
                    }
                }
            }
            item {
                Box(modifier = Modifier.fillMaxWidth().background(TR_CARD, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Column {
                        Text("Sessions · best set each day", fontSize = 11.sp, color = TR_MUTED, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
                        if (sessions.isEmpty()) Text("No session data", fontSize = 12.sp, color = TR_MUTED)
                        sessions.forEachIndexed { i, s ->
                            val isPr = i < sessions.size - 1 && s.weightLbs > sessions[i + 1].weightLbs
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.date, fontSize = 12.sp, color = TR_MUTED)
                                Text(if (bodyweight) s.reps.toString() + " reps" else s.weightLbs.toString() + " × " + s.reps, fontSize = 12.sp, color = Color.White)
                                Text(if (isPr) "PR" else "—", fontSize = 10.sp, color = if (isPr) TR_GOLD else TR_MUTED)
                            }
                            if (i < sessions.size - 1) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1f2937)))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TrChip(text: String, primary: Boolean) {
    Box(
        modifier = Modifier
            .background(if (primary) Color(0xFF3b2f00) else Color(0xFF1f2937), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, color = if (primary) TR_GOLD else Color(0xFFcdd6e2), fontSize = 12.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal) }
}

// ── Routine editor ──

class TrRSet(val id: Long, type0: String, weight0: String, reps0: String) {
    var type by mutableStateOf(type0)
    var weight by mutableStateOf(weight0)
    var reps by mutableStateOf(reps0)
}

class TrREx(val id: Long, val exerciseId: String?, val title: String, rest0: String, notes0: String, val sets: SnapshotStateList<TrRSet>) {
    var rest by mutableStateOf(rest0)
    var notes by mutableStateOf(notes0)
}

private fun trRepsText(m: Map<*, *>): String {
    val mn = (m["rep_min"] as? Number)?.toInt()
    val mx = (m["rep_max"] as? Number)?.toInt()
    val r = (m["reps"] as? Number)?.toInt()
    if (mn != null && mx != null && mn != mx) return mn.toString() + "-" + mx.toString()
    if (r != null) return r.toString()
    if (mx != null) return mx.toString()
    if (mn != null) return mn.toString()
    return ""
}

// Reps box accepts "8" or a range like "8-10".
private fun trSaveRSet(s: TrRSet) {
    val body = HashMap<String, Any?>()
    body["type"] = s.type
    body["weight_lbs"] = s.weight.toDoubleOrNull()
    val t = s.reps.trim()
    if (t.contains("-")) {
        val parts = t.split("-")
        val a = parts[0].toIntOrNull()
        val b = if (parts.size > 1) parts[1].toIntOrNull() else null
        if (a == null || b == null) return
        body["reps"] = null
        body["rep_min"] = Math.min(a, b)
        body["rep_max"] = Math.max(a, b)
    } else {
        body["reps"] = t.toIntOrNull()
        body["rep_min"] = null
        body["rep_max"] = null
    }
    trainCall("PATCH", "/api/train/routine-set/" + s.id, body)
}

@Composable
fun TrRoutineEditor(routineId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var savedTitle by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    val exs = remember { mutableStateListOf<TrREx>() }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<TrREx?>(null) }

    BackHandler { if (showPicker) showPicker = false else onBack() }

    LaunchedEffect(reload) {
        try {
            val t = withContext(Dispatchers.IO) { trainCall("GET", "/api/train/routine/" + routineId) }
            val m = trJson(t)
            val tt = (m["title"] as? String) ?: ""
            title = tt
            savedTitle = tt
            exs.clear()
            (m["exercises"] as? List<*>)?.forEach { e ->
                val em = e as? Map<*, *>
                if (em != null) {
                    val sets = mutableStateListOf<TrRSet>()
                    (em["sets"] as? List<*>)?.forEach { x ->
                        val sm = x as? Map<*, *>
                        if (sm != null) {
                            sets.add(
                                TrRSet(
                                    (sm["id"] as? Number)?.toLong() ?: 0L,
                                    (sm["type"] as? String) ?: "normal",
                                    trFmtW((sm["weight_lbs"] as? Number)?.toDouble()),
                                    trRepsText(sm)
                                )
                            )
                        }
                    }
                    exs.add(
                        TrREx(
                            (em["id"] as? Number)?.toLong() ?: 0L,
                            em["exercise_id"] as? String,
                            (em["title"] as? String) ?: "Exercise",
                            ((em["rest_seconds"] as? Number)?.toInt() ?: 0).toString(),
                            (em["notes"] as? String) ?: "",
                            sets
                        )
                    )
                }
            }
            loaded = true
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }

    LaunchedEffect(title) {
        if (!loaded || title == savedTitle || title.isBlank()) return@LaunchedEffect
        delay(900)
        try {
            withContext(Dispatchers.IO) { trainCall("PATCH", "/api/train/routine/" + routineId, mapOf("title" to title.trim())) }
            savedTitle = title
        } catch (e: Exception) {
            error = e.message
        }
    }

    if (showPicker) {
        TrExercisePicker(
            onPick = { id ->
                showPicker = false
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { trainCall("POST", "/api/train/routine/" + routineId + "/exercise", mapOf("exercise_id" to id)) }
                        reload++
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onClose = { showPicker = false }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).systemBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Edit Routine", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Done", color = TR_GOLD, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBack() }.padding(8.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("ROUTINE NAME", fontSize = 10.sp, color = TR_MUTED, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = title,
                        onValueChange = { if (it.length <= 60) title = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(TR_GOLD),
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF1f2937), RoundedCornerShape(8.dp)).padding(12.dp)
                    )
                    Text("Changes save automatically.", fontSize = 11.sp, color = TR_MUTED, modifier = Modifier.padding(top = 6.dp))
                    if (error != null) Text("Error: " + error, fontSize = 12.sp, color = Color(0xFFf87171), modifier = Modifier.padding(top = 4.dp))
                }
            }
            items(exs, key = { it.id }) { ex ->
                TrRExCard(
                    ex = ex,
                    index = exs.indexOf(ex),
                    count = exs.size,
                    onMove = { d ->
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { trainCall("POST", "/api/train/routine-exercise/" + ex.id + "/move", mapOf("dir" to d)) }
                                reload++
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    },
                    onRemove = { removeTarget = ex }
                )
            }
            item {
                Button(
                    onClick = { showPicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1e1e3a)),
                    border = BorderStroke(1.dp, Color(0xFF3a3a5a)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Add Exercise", color = Color(0xFFa78bfa), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val rt = removeTarget
    if (rt != null) {
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            containerColor = Color(0xFF1a1a1a),
            title = { Text("Remove exercise?", color = Color.White) },
            text = { Text(rt.title + " will be removed from this routine. Past workouts are not affected.", color = Color(0xFFcdd6e2)) },
            confirmButton = {
                TextButton(onClick = {
                    removeTarget = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { trainCall("DELETE", "/api/train/routine-exercise/" + rt.id) }
                            reload++
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                }) { Text("Remove", color = Color(0xFFf87171), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel", color = TR_MUTED) } }
        )
    }
}

@Composable
private fun TrRExCard(ex: TrREx, index: Int, count: Int, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf(true) }

    LaunchedEffect(ex.rest, ex.notes) {
        if (first) { first = false; return@LaunchedEffect }
        delay(900)
        try {
            val body = HashMap<String, Any?>()
            val r = ex.rest.toIntOrNull()
            if (r != null) body["rest_seconds"] = r
            body["notes"] = ex.notes
            withContext(Dispatchers.IO) { trainCall("PATCH", "/api/train/routine-exercise/" + ex.id, body) }
        } catch (e: Exception) {
        }
    }

    val labels = ArrayList<String>()
    var n = 0
    ex.sets.forEach { s ->
        val letter = trTypeLetter(s.type)
        if (letter.isEmpty()) { n++; labels.add(n.toString()) } else labels.add(letter)
    }

    Column(modifier = Modifier.fillMaxWidth().background(TR_CARD, RoundedCornerShape(10.dp)).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ex.title, color = TR_GOLD, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("▲", color = if (index > 0) TR_GOLD else Color(0xFF3a3a4a), fontSize = 16.sp, modifier = Modifier.clickable { if (index > 0) onMove(-1) }.padding(8.dp))
            Text("▼", color = if (index < count - 1) TR_GOLD else Color(0xFF3a3a4a), fontSize = 16.sp, modifier = Modifier.clickable { if (index < count - 1) onMove(1) }.padding(8.dp))
            Text("✕", color = Color(0xFFf87171), fontSize = 16.sp, modifier = Modifier.clickable { onRemove() }.padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rest (sec)", color = TR_MUTED, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            TrNumField(ex.rest, { ex.rest = it }, false, false, Modifier.width(80.dp))
            Spacer(Modifier.width(10.dp))
            Text("= " + trFmtClock((ex.rest.toLongOrNull() ?: 0L)), color = Color(0xFFa78bfa), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = ex.notes,
            onValueChange = { if (it.length <= 2000) ex.notes = it },
            textStyle = TextStyle(color = Color(0xFFfbbf24), fontSize = 13.sp),
            cursorBrush = SolidColor(TR_GOLD),
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1f2937), RoundedCornerShape(6.dp)).padding(10.dp),
            decorationBox = { inner ->
                Box {
                    if (ex.notes.isEmpty()) Text("Notes (shown during the workout)", color = TR_MUTED, fontSize = 13.sp)
                    inner()
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SET", color = TR_MUTED, fontSize = 10.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            Text("LBS", color = TR_MUTED, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("REPS (8 or 8-10)", color = TR_MUTED, fontSize = 10.sp, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center)
        }
        ex.sets.forEachIndexed { i, s ->
            TrRSetRow(
                set = s,
                label = if (i < labels.size) labels[i] else "",
                onDelete = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { trainCall("DELETE", "/api/train/routine-set/" + s.id) }
                            ex.sets.remove(s)
                        } catch (e: Exception) {
                        }
                    }
                }
            )
        }
        Text(
            "+ Add Set", color = Color(0xFF7B8CDE), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable {
                scope.launch {
                    try {
                        val t = withContext(Dispatchers.IO) { trainCall("POST", "/api/train/routine-exercise/" + ex.id + "/set", emptyMap()) }
                        val newId = (trJson(t)["set_id"] as? Number)?.toLong() ?: 0L
                        val last = if (ex.sets.isNotEmpty()) ex.sets[ex.sets.size - 1] else null
                        ex.sets.add(TrRSet(newId, "normal", last?.weight ?: "", last?.reps ?: ""))
                    } catch (e: Exception) {
                    }
                }
            }.padding(vertical = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TrRSetRow(set: TrRSet, label: String, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }

    LaunchedEffect(set.weight, set.reps, set.type) {
        if (first) { first = false; return@LaunchedEffect }
        delay(900)
        try {
            withContext(Dispatchers.IO) { trSaveRSet(set) }
        } catch (e: Exception) {
        }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Text(
                label, color = trTypeColor(set.type), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { menu = true }.padding(horizontal = 10.dp, vertical = 8.dp)
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Normal Set") }, onClick = { set.type = "normal"; menu = false })
                DropdownMenuItem(text = { Text("Warm Up (W)") }, onClick = { set.type = "warmup"; menu = false })
                DropdownMenuItem(text = { Text("To Failure (F)") }, onClick = { set.type = "failure"; menu = false })
                DropdownMenuItem(text = { Text("Drop Set (D)") }, onClick = { set.type = "dropset"; menu = false })
                DropdownMenuItem(text = { Text("Delete Set") }, onClick = { menu = false; onDelete() })
            }
        }
        TrNumField(set.weight, { set.weight = it }, true, false, Modifier.weight(1f).padding(horizontal = 4.dp))
        TrNumField(set.reps, { set.reps = it }, false, false, Modifier.weight(1.3f).padding(horizontal = 4.dp), true)
    }
}

@Composable
private fun TrExercisePicker(onPick: (String) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }

    LaunchedEffect(q) {
        delay(300)
        try {
            val t = withContext(Dispatchers.IO) { trainCall("GET", "/api/train/exercises?q=" + java.net.URLEncoder.encode(q, "UTF-8")) }
            results = (trJson(t)["exercises"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        } catch (e: Exception) {
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0a0a))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).systemBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Add Exercise", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Cancel", color = TR_GOLD, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onClose() }.padding(8.dp))
        }
        BasicTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(TR_GOLD),
            modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color(0xFF1f2937), RoundedCornerShape(8.dp)).padding(12.dp),
            decorationBox = { inner ->
                Box {
                    if (q.isEmpty()) Text("Search exercises...", color = TR_MUTED, fontSize = 16.sp)
                    inner()
                }
            }
        )
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results) { r ->
                val id = (r["id"] as? String) ?: ""
                Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF141414), RoundedCornerShape(8.dp)).clickable { onPick(id) }.padding(12.dp)) {
                    Text((r["title"] as? String) ?: "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    val mu = r["primary_muscle"] as? String
                    if (mu != null) Text(trTitleCase(mu), color = TR_MUTED, fontSize = 11.sp)
                }
            }
        }
    }
}
