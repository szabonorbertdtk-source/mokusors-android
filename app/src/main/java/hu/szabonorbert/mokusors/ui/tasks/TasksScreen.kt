package hu.szabonorbert.mokusors.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

data class TaskItem(
    val id: String,
    val title: String,
    val deadlineDate: Date,
    val owner: String,
    val reminderTargetEmail: String,
    val status: String,
    val note: String,
    val completedBy: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(isAdmin: Boolean = false, onBack: () -> Unit) {
    val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
    var tasks by remember { mutableStateOf<List<TaskItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val dateFmt = remember { SimpleDateFormat("yyyy. MM. dd. HH:mm", Locale("hu")) }

    DisposableEffect(Unit) {
        val reg: ListenerRegistration = FirebaseFirestore.getInstance()
            .collection("deadlineTasks")
            .addSnapshotListener { snap, _ ->
                val list = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val status = d["status"] as? String ?: "progress"
                    if (status == "irrelevant") return@mapNotNull null

                    // Resolve deadline date: prefer deadlineAt Timestamp, then year/month/day parts
                    val deadlineDate: Date = when (val da = d["deadlineAt"]) {
                        is Timestamp -> da.toDate()
                        else -> {
                            val year = (d["year"] as? Long)?.toInt() ?: 2026
                            val month = (d["month"] as? Long)?.toInt() ?: 1
                            val day = (d["day"] as? Long)?.toInt() ?: 1
                            val time = d["time"] as? String ?: "09:00"
                            val parts = time.split(":")
                            Calendar.getInstance().apply {
                                set(year, month - 1, day,
                                    parts.getOrNull(0)?.toIntOrNull() ?: 9,
                                    parts.getOrNull(1)?.toIntOrNull() ?: 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.time
                        }
                    }

                    TaskItem(
                        id = doc.id,
                        title = d["title"] as? String ?: "",
                        deadlineDate = deadlineDate,
                        owner = d["owner"] as? String ?: "",
                        reminderTargetEmail = d["reminderTargetEmail"] as? String ?: "",
                        status = status,
                        note = d["note"] as? String ?: "",
                        completedBy = d["completedBy"] as? String ?: ""
                    )
                }.sortedWith(compareBy({ it.status == "done" }, { it.deadlineDate }))
                tasks = list
                isLoading = false
            }
        onDispose { reg.remove() }
    }

    fun markDone(task: TaskItem) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val displayName = FirebaseAuth.getInstance().currentUser?.displayName
            ?.takeIf { it.isNotBlank() } ?: userEmail
        FirebaseFirestore.getInstance().collection("deadlineTasks").document(task.id)
            .update(mapOf("status" to "done", "completedAt" to now, "completedBy" to displayName))
    }

    fun markIrrelevant(task: TaskItem) {
        FirebaseFirestore.getInstance().collection("deadlineTasks").document(task.id)
            .update("status", "irrelevant")
    }

    // Split by status
    val pending = tasks.filter { it.status != "done" }
    val done = tasks.filter { it.status == "done" }
    val now = Date()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Határidős feladatok", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nincs határidős feladat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pending.isNotEmpty()) {
                item {
                    Text("Folyamatban", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(pending) { task ->
                    val isOverdue = task.deadlineDate.before(now)
                    val color = if (isOverdue) Color(0xFFFF3B30) else Color(0xFFFF9500)
                    TaskCard(
                        task = task, color = color, dateFmt = dateFmt,
                        isAdmin = isAdmin, userEmail = userEmail,
                        onMarkDone = { markDone(task) },
                        onMarkIrrelevant = { markIrrelevant(task) }
                    )
                }
            }
            if (done.isNotEmpty()) {
                item {
                    Text("Kész", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(done) { task ->
                    TaskCard(
                        task = task, color = Color(0xFF34C759), dateFmt = dateFmt,
                        isAdmin = isAdmin, userEmail = userEmail,
                        onMarkDone = {}, onMarkIrrelevant = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskItem,
    color: Color,
    dateFmt: SimpleDateFormat,
    isAdmin: Boolean,
    userEmail: String,
    onMarkDone: () -> Unit,
    onMarkIrrelevant: () -> Unit
) {
    val isDone = task.status == "done"
    val isSuperAdmin = userEmail.contains("laszlo.turk", ignoreCase = true) ||
        userEmail.contains("tunde.ilona.makkai", ignoreCase = true)
    val isOwner = isSuperAdmin ||
        task.owner.equals(userEmail, ignoreCase = true) ||
        task.reminderTargetEmail.equals(userEmail, ignoreCase = true)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    null, tint = color, modifier = Modifier.size(22.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title.ifEmpty { "Névtelen feladat" },
                        fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(dateFmt.format(task.deadlineDate), fontSize = 13.sp, color = color)
                        if (task.owner.isNotBlank()) {
                            Text("• ${task.owner}", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (task.note.isNotBlank()) {
                Text(task.note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp))
            }

            if (isDone && task.completedBy.isNotBlank()) {
                Text("Elvégezte: ${task.completedBy}", fontSize = 12.sp,
                    color = Color(0xFF34C759), modifier = Modifier.padding(start = 32.dp))
            }

            if (!isDone && (isOwner || isAdmin)) {
                Row(
                    modifier = Modifier.padding(start = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isOwner) {
                        OutlinedButton(
                            onClick = onMarkDone,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34C759)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("Kész", fontSize = 13.sp) }
                    }
                    if (isAdmin) {
                        OutlinedButton(
                            onClick = onMarkIrrelevant,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8E8E93)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("Nem releváns", fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}
