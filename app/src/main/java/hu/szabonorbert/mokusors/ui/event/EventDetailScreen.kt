package hu.szabonorbert.mokusors.ui.event

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.szabonorbert.mokusors.model.CalendarEvent
import hu.szabonorbert.mokusors.model.EventType
import hu.szabonorbert.mokusors.ui.calendar.AppCard
import hu.szabonorbert.mokusors.ui.calendar.statusColor
import hu.szabonorbert.mokusors.ui.theme.LocalAppColors
import hu.szabonorbert.mokusors.viewmodel.EventViewModel
import java.text.SimpleDateFormat
import java.util.*

private val fullDateFmt = SimpleDateFormat("yyyy. MMMM d., EEEE", Locale("hu"))
private val timeFmt = SimpleDateFormat("HH:mm", Locale("hu"))
private val googleDateFmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("Europe/Budapest")
}
private val googleAllDayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    event: CalendarEvent,
    eventViewModel: EventViewModel,
    onBack: () -> Unit,
    onEditEvent: (CalendarEvent) -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    val isAdmin by eventViewModel.isAdmin.collectAsState()

    // Keep event live from the stream
    val events by eventViewModel.events.collectAsState()
    val liveEvent = events.firstOrNull { it.firestoreID == event.firestoreID } ?: event

    val color = statusColor(liveEvent, appColors)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Esemény törlése") },
            text = { Text("Biztosan törölni szeretnéd ezt az eseményt? Ez a művelet nem visszavonható.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        eventViewModel.deleteEvent(liveEvent.firestoreID, onSuccess = { onBack() })
                    }
                ) {
                    Text("Törlés", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Mégse") }
            }
        )
    }

    // Duplicate dialog
    if (showDuplicateDialog) {
        DuplicateDialog(
            event = liveEvent,
            eventViewModel = eventViewModel,
            onDismiss = { showDuplicateDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        appColors.statusBlue.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Esemény", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Vissza")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(color))
                    Text(liveEvent.title, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                }

                Text(formatDate(liveEvent), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                val typeLabel = when (liveEvent.eventType) {
                    EventType.GUEST -> "Vendég esemény"
                    EventType.SPEECH -> "Köszöntőbeszéd"
                    EventType.VACATION -> "Szabadság"
                    EventType.NONE -> null
                }
                if (typeLabel != null || (isAdmin && liveEvent.visibleToUsers)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (typeLabel != null) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(color.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(typeLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
                            }
                        }
                        if (isAdmin && liveEvent.visibleToUsers) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF30B0C7).copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("Intézmény", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF30B0C7))
                            }
                        }
                    }
                }

                if (isAdmin && liveEvent.hasTodoList && !liveEvent.isVacation) {
                    ChecklistCard(liveEvent, eventViewModel)
                }

                if (liveEvent.location.isNotBlank()) {
                    MenuRow(
                        icon = Icons.Default.Map,
                        title = "Útvonaltervezés",
                        subtitle = liveEvent.location,
                        trailingIcon = Icons.Default.OpenInNew
                    ) {
                        val uri = Uri.parse("geo:0,0?q=${Uri.encode(liveEvent.location)}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                }

                MenuRow(
                    icon = Icons.Default.CalendarMonth,
                    title = "Exportálás Google Calendarba",
                    trailingIcon = Icons.Default.OpenInNew
                ) {
                    val url = buildGoogleCalUrl(liveEvent)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }

                if (liveEvent.pdfUrl.isNotBlank()) {
                    MenuRow(
                        icon = Icons.Default.Description,
                        title = "PDF meghívó",
                        trailingIcon = Icons.Default.OpenInNew
                    ) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(liveEvent.pdfUrl)))
                    }
                }

                if (liveEvent.note.isNotBlank()) {
                    AppCard {
                        Text(liveEvent.note, fontSize = 15.sp)
                    }
                }

                // Admin-only actions
                if (isAdmin) {
                    MenuRow(
                        icon = Icons.Default.Edit,
                        title = "Esemény módosítása",
                        trailingIcon = Icons.Default.ChevronRight
                    ) {
                        onEditEvent(liveEvent)
                    }

                    MenuRow(
                        icon = Icons.Default.ContentCopy,
                        title = "Duplikálás",
                        trailingIcon = Icons.Default.ChevronRight
                    ) {
                        showDuplicateDialog = true
                    }

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Esemény törlése")
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateDialog(
    event: CalendarEvent,
    eventViewModel: EventViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance().apply { time = event.date } }
    var selectedDate by remember { mutableStateOf(event.date) }
    val dateFmt = remember { SimpleDateFormat("yyyy. MM. dd.", Locale("hu")) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplikálás") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Válassz dátumot az új eseményhez:")
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { time = selectedDate }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                selectedDate = Calendar.getInstance().apply {
                                    set(y, m, d,
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE), 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.time
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(dateFmt.format(selectedDate))
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                if (isSaving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    isSaving = true
                    eventViewModel.duplicateEvent(
                        event = event,
                        newDate = selectedDate,
                        onSuccess = { onDismiss() },
                        onError = { msg -> isSaving = false; errorMsg = msg }
                    )
                }
            ) {
                Text("Duplikálás")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Mégse") }
        }
    )
}

@Composable
private fun ChecklistCard(event: CalendarEvent, eventViewModel: EventViewModel) {
    val appColors = LocalAppColors.current
    val allItems = listOf(
        Triple("dtk", "DTK részvétel", event.dtkParticipationDone),
        Triple("kk", "KK engedély", event.kkPermissionDone),
        Triple("press", "Sajtómeghívó", event.pressInviteDone),
        Triple("ph", "PH háttéranyag", event.phBackgroundDone),
        Triple("catering", "Catering", event.cateringDone),
        Triple("gifts", "Ajándékok (virág, könyv)", event.giftsDone),
        Triple("certificate", "Oklevél / emléklap", event.certificateDone)
    ).filter { event.activeActivities.contains(it.first) }

    val doneCount = allItems.count { it.third }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Teendők", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(appColors.statusBlue.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("$doneCount/${allItems.size} kész", fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, color = appColors.statusBlue)
                }
            }
            allItems.forEach { (key, title, isDone) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            // Toggle this item
                            val newDtk = if (key == "dtk") !isDone else event.dtkParticipationDone
                            val newKk = if (key == "kk") !isDone else event.kkPermissionDone
                            val newPress = if (key == "press") !isDone else event.pressInviteDone
                            val newPh = if (key == "ph") !isDone else event.phBackgroundDone
                            val newCatering = if (key == "catering") !isDone else event.cateringDone
                            val newGifts = if (key == "gifts") !isDone else event.giftsDone
                            val newCertificate = if (key == "certificate") !isDone else event.certificateDone
                            eventViewModel.updateChecklist(
                                firestoreID = event.firestoreID,
                                dtkParticipationDone = newDtk,
                                kkPermissionDone = newKk,
                                pressInviteDone = newPress,
                                phBackgroundDone = newPh,
                                cateringDone = newCatering,
                                giftsDone = newGifts,
                                certificateDone = newCertificate
                            )
                        }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Icon(
                        if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isDone) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(title, fontSize = 15.sp,
                        color = if (isDone) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingIcon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LocalAppColors.current.statusBlue.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
                tint = LocalAppColors.current.statusBlue)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDate(event: CalendarEvent): String {
    val start = fullDateFmt.format(event.date)
    return if (event.allDay) "$start • egész nap"
    else "$start ${timeFmt.format(event.date)}"
}

private fun buildGoogleCalUrl(event: CalendarEvent): String {
    val title = Uri.encode(event.title)
    val details = Uri.encode(event.note)
    val location = Uri.encode(event.location)
    val start = if (event.allDay) googleAllDayFmt.format(event.date) else googleDateFmt.format(event.date)
    val endCal = Calendar.getInstance().apply {
        time = event.endDate
        if (event.allDay) add(Calendar.DAY_OF_YEAR, 1)
    }
    val end = if (event.allDay) googleAllDayFmt.format(endCal.time) else googleDateFmt.format(endCal.time)
    return "https://calendar.google.com/calendar/render?action=TEMPLATE&text=$title&details=$details&location=$location&dates=$start/$end"
}

// CalendarEvent.isVacation is already a member property of CalendarEvent
