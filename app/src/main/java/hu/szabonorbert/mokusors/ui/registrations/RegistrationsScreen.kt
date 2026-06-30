package hu.szabonorbert.mokusors.ui.registrations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

data class RegSlot(val id: String, val startsAt: Date, val capacity: Int)
data class RegEntry(
    val id: String, val slotId: String, val userId: String,
    val userName: String, val contactName: String, val contactInfo: String, val reservedSeats: Int
)
data class RegEvent(
    val id: String, val title: String, val location: String, val note: String,
    val createdAt: String, val slots: List<RegSlot>, val registrations: List<RegEntry>
) {
    val nextSlotDate: Date get() = slots.minByOrNull { it.startsAt }?.startsAt ?: Date(Long.MAX_VALUE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationsScreen(isAdmin: Boolean = false, onBack: () -> Unit) {
    val green = Color(0xFF34C759)
    val blue = Color(0xFF007AFF)
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val userEmail = auth.currentUser?.email ?: ""
    val userName = auth.currentUser?.displayName ?: userEmail

    var events by remember { mutableStateOf<List<RegEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var registrationsByEventId by remember { mutableStateOf<Map<String, List<RegEntry>>>(emptyMap()) }
    var showFormFor by remember { mutableStateOf<RegEvent?>(null) }

    // Merge base events with subcollection registrations
    val mergedEvents = remember(events, registrationsByEventId) {
        events.map { event ->
            val subRegs = registrationsByEventId[event.id] ?: emptyList()
            val mergedMap = (event.registrations + subRegs).associateBy { it.id }
            event.copy(registrations = mergedMap.values.toList())
        }.sortedBy { it.nextSlotDate }
    }

    // Load registration events + subcollection listeners
    DisposableEffect(Unit) {
        val regListeners = mutableListOf<ListenerRegistration>()

        val mainListener = db.collection("registrationEvents")
            .addSnapshotListener { snap, _ ->
                val loaded = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    if (d["deleted"] as? Boolean == true) return@mapNotNull null

                    val slots = (d["slots"] as? List<*> ?: emptyList<Any>())
                        .filterIsInstance<Map<String, Any>>()
                        .mapNotNull { s ->
                            val slotId = s["id"] as? String ?: return@mapNotNull null
                            val date = when (val v = s["startsAt"]) {
                                is Timestamp -> v.toDate()
                                else -> return@mapNotNull null
                            }
                            RegSlot(slotId, date, (s["capacity"] as? Long)?.toInt() ?: 1)
                        }.sortedBy { it.startsAt }

                    val embeddedRegs = (d["registrations"] as? List<*> ?: emptyList<Any>())
                        .filterIsInstance<Map<String, Any>>()
                        .mapNotNull { r -> parseEntry(r, null) }

                    RegEvent(
                        id = doc.id, title = d["title"] as? String ?: "",
                        location = d["location"] as? String ?: "",
                        note = d["note"] as? String ?: "",
                        createdAt = d["createdAt"] as? String ?: "",
                        slots = slots, registrations = embeddedRegs
                    )
                }
                events = loaded
                isLoading = false

                // Attach subcollection listeners for each event
                regListeners.forEach { it.remove() }
                regListeners.clear()
                loaded.forEach { event ->
                    val l = db.collection("registrationEvents").document(event.id)
                        .collection("registrations")
                        .addSnapshotListener { regSnap, _ ->
                            val regs = (regSnap?.documents ?: emptyList()).mapNotNull { doc ->
                                parseEntry(doc.data ?: return@mapNotNull null, doc.id)
                            }
                            registrationsByEventId = registrationsByEventId.toMutableMap().also { it[event.id] = regs }
                        }
                    regListeners.add(l)
                }
            }

        onDispose {
            mainListener.remove()
            regListeners.forEach { it.remove() }
        }
    }

    fun registerMultiple(
        event: RegEvent, slotIds: List<String>, seats: Int,
        contactName: String, contactInfo: String
    ) {
        if (slotIds.isEmpty()) return
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val eventRef = db.collection("registrationEvents").document(event.id)
        val existingSlotIds = event.registrations.filter { it.userId == uid }.map { it.slotId }.toSet()
        val toRemove = existingSlotIds - slotIds.toSet()
        val toAdd = slotIds.toSet() - existingSlotIds

        db.runTransaction { tx ->
            val snap = tx.get(eventRef)
            val slotUsage = (snap.data?.get("slotUsage") as? Map<*, *>)
                ?.mapKeys { it.key.toString() }
                ?.mapValues { (it.value as? Long)?.toInt() ?: 0 }
                ?.toMutableMap() ?: mutableMapOf()

            // Release slots being removed
            toRemove.forEach { slotId ->
                val oldReg = event.registrations.firstOrNull { it.userId == uid && it.slotId == slotId }
                val used = slotUsage[slotId] ?: 0
                slotUsage[slotId] = maxOf(0, used - (oldReg?.reservedSeats ?: 1))
                tx.delete(eventRef.collection("registrations").document("${uid}_$slotId"))
            }

            // Add new slots
            toAdd.forEach { slotId ->
                val slot = event.slots.firstOrNull { it.id == slotId } ?: return@forEach
                val used = slotUsage[slotId] ?: 0
                if (used + seats > slot.capacity) throw Exception("Nincs elég szabad férőhely.")
                slotUsage[slotId] = used + seats
                val docId = "${uid}_$slotId"
                tx.set(eventRef.collection("registrations").document(docId), mapOf(
                    "id" to docId, "eventId" to event.id, "slotId" to slotId,
                    "userId" to uid, "userEmail" to userEmail, "userName" to userName,
                    "contactName" to contactName, "contactInfo" to contactInfo,
                    "reservedSeats" to seats, "createdAt" to now
                ))
            }

            tx.update(eventRef, mapOf("slotUsage" to slotUsage,
                "updatedAt" to now, "updatedAtServer" to FieldValue.serverTimestamp()))
        }
    }

    fun cancelRegistration(event: RegEvent) {
        val ownRegs = event.registrations.filter { it.userId == uid }
        if (ownRegs.isEmpty()) return
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val eventRef = db.collection("registrationEvents").document(event.id)

        db.runTransaction { tx ->
            val snap = tx.get(eventRef)
            val slotUsage = (snap.data?.get("slotUsage") as? Map<*, *>)
                ?.mapKeys { it.key.toString() }
                ?.mapValues { (it.value as? Long)?.toInt() ?: 0 }
                ?.toMutableMap() ?: mutableMapOf()
            ownRegs.forEach { reg ->
                val used = slotUsage[reg.slotId] ?: 0
                slotUsage[reg.slotId] = maxOf(0, used - reg.reservedSeats)
                tx.delete(eventRef.collection("registrations").document(reg.id))
            }
            tx.update(eventRef, mapOf("slotUsage" to slotUsage,
                "updatedAt" to now, "updatedAtServer" to FieldValue.serverTimestamp()))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Programok", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (mergedEvents.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nincs elérhető program.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(mergedEvents) { event ->
                val ownRegs = event.registrations.filter { it.userId == uid }
                val isRegistered = ownRegs.isNotEmpty()
                val totalReg = event.registrations.sumOf { it.reservedSeats }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isRegistered) green.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface)
                        .border(
                            width = if (isRegistered) 1.5.dp else 0.dp,
                            color = if (isRegistered) green.copy(alpha = 0.4f) else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Title + badge
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top) {
                            Text(event.title, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                                modifier = Modifier.weight(1f))
                            if (isRegistered) {
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(50))
                                        .background(green.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Jelentkezett", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = green)
                                }
                            }
                        }

                        // Location
                        if (event.location.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(event.location, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Note
                        if (event.note.isNotBlank()) {
                            Text(event.note, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        // Slots
                        if (event.slots.isNotEmpty()) {
                            SlotsList(event, uid, green, blue, isAdmin)
                        }

                        // Stats
                        Text("$totalReg fő jelentkezett összesen", fontSize = 12.sp,
                            color = blue, fontWeight = FontWeight.Medium)

                        // Action buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isRegistered) {
                                Button(
                                    onClick = { showFormFor = event },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = blue),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Jelentkezés", fontWeight = FontWeight.Bold) }
                            } else {
                                OutlinedButton(
                                    onClick = { showFormFor = event },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Módosítás") }
                                OutlinedButton(
                                    onClick = { cancelRegistration(event) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3B30))
                                ) { Text("Lemondás") }
                            }
                        }
                    }
                }
            }
        }
    }

    // Registration form dialog
    showFormFor?.let { event ->
        RegistrationFormDialog(
            event = event,
            existingRegs = event.registrations.filter { it.userId == uid },
            onDismiss = { showFormFor = null },
            onSubmit = { slotIds, seats, contactName, contactInfo ->
                registerMultiple(event, slotIds, seats, contactName, contactInfo)
                showFormFor = null
            }
        )
    }
}

@Composable
private fun SlotsList(event: RegEvent, uid: String, green: Color, blue: Color, isAdmin: Boolean = false) {
    val slotFmt = remember { SimpleDateFormat("MMM d., HH:mm", Locale("hu")) }
    val ownSlotIds = event.registrations.filter { it.userId == uid }.map { it.slotId }.toSet()
    val slotUsage = event.registrations.groupBy { it.slotId }.mapValues { e -> e.value.sumOf { it.reservedSeats } }
    val regsBySlot = if (isAdmin) event.registrations.groupBy { it.slotId } else emptyMap()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Időpontok:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        event.slots.forEach { slot ->
            val used = slotUsage[slot.id] ?: 0
            val free = slot.capacity - used
            val isOwn = slot.id in ownSlotIds
            val slotRegs = regsBySlot[slot.id] ?: emptyList()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isOwn) green.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isOwn) Icon(Icons.Default.CheckCircle, null, tint = green, modifier = Modifier.size(14.dp))
                        Text(slotFmt.format(slot.startsAt), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = if (free <= 0) "Telt" else "$free szabad / ${slot.capacity}",
                        fontSize = 12.sp,
                        color = if (free <= 0) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isAdmin && slotRegs.isNotEmpty()) {
                    slotRegs.forEach { reg ->
                        Text(
                            "· ${reg.userName.ifBlank { reg.contactName }} (${reg.reservedSeats} fő)",
                            fontSize = 11.sp,
                            color = blue
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegistrationFormDialog(
    event: RegEvent,
    existingRegs: List<RegEntry>,
    onDismiss: () -> Unit,
    onSubmit: (slotIds: List<String>, seats: Int, contactName: String, contactInfo: String) -> Unit
) {
    val slotFmt = remember { SimpleDateFormat("MMM d., EEEE HH:mm", Locale("hu")) }
    var selectedSlotIds by remember {
        mutableStateOf(
            existingRegs.map { it.slotId }.toSet().ifEmpty {
                setOfNotNull(event.slots.firstOrNull()?.id)
            }
        )
    }
    var seats by remember { mutableStateOf(existingRegs.firstOrNull()?.reservedSeats?.toString() ?: "1") }
    var contactName by remember { mutableStateOf(existingRegs.firstOrNull()?.contactName ?: "") }
    var contactInfo by remember { mutableStateOf(existingRegs.firstOrNull()?.contactInfo ?: "") }
    var errorMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                // Multi-slot selection
                Text(
                    if (event.slots.size > 1) "Időpont(ok) kiválasztása:" else "Időpont:",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                event.slots.forEach { slot ->
                    val isSelected = slot.id in selectedSlotIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) Color(0xFF007AFF).copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable {
                                selectedSlotIds = if (isSelected) selectedSlotIds - slot.id
                                else selectedSlotIds + slot.id
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedSlotIds = if (checked) selectedSlotIds + slot.id
                                    else selectedSlotIds - slot.id
                                }
                            )
                            Text(slotFmt.format(slot.startsAt), fontSize = 14.sp)
                        }
                        Text("${slot.capacity} fő", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Seats
                OutlinedTextField(
                    value = seats,
                    onValueChange = { seats = it.filter { c -> c.isDigit() } },
                    label = { Text("Férőhelyek száma") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Contact info
                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("Kapcsolattartó neve *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = contactInfo,
                    onValueChange = { contactInfo = it },
                    label = { Text("Elérhetőség (e-mail / telefon) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = Color(0xFFFF3B30), fontSize = 13.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)) { Text("Mégse") }
                    Button(
                        onClick = {
                            when {
                                selectedSlotIds.isEmpty() -> errorMsg = "Válasszon legalább egy időpontot."
                                contactName.isBlank() -> errorMsg = "Adja meg a kapcsolattartó nevét."
                                contactInfo.isBlank() -> errorMsg = "Adja meg az elérhetőséget."
                                else -> onSubmit(selectedSlotIds.toList(), seats.toIntOrNull() ?: 1, contactName, contactInfo)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                    ) { Text("Jelentkezés", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun parseEntry(data: Map<String, Any>, fallbackId: String?): RegEntry? {
    val slotId = data["slotId"] as? String ?: return null
    val userId = data["userId"] as? String ?: return null
    val id = data["id"] as? String ?: fallbackId ?: "${userId}_${slotId}"
    return RegEntry(
        id = id, slotId = slotId, userId = userId,
        userName = data["userName"] as? String ?: "",
        contactName = data["contactName"] as? String ?: data["userName"] as? String ?: "",
        contactInfo = data["contactInfo"] as? String ?: "",
        reservedSeats = (data["reservedSeats"] as? Long)?.toInt() ?: 1
    )
}
