package hu.szabonorbert.mokusors.ui.event

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.storage.FirebaseStorage
import hu.szabonorbert.mokusors.model.CalendarEvent
import hu.szabonorbert.mokusors.model.EventType
import hu.szabonorbert.mokusors.viewmodel.EventViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

private val editActivityKeys = listOf("dtk", "kk", "press", "ph", "catering", "gifts", "certificate")
private val editActivityLabels = mapOf(
    "dtk" to "DTK részvétel",
    "kk" to "KK engedély",
    "press" to "Sajtómeghívó",
    "ph" to "PH háttéranyag",
    "catering" to "Catering",
    "gifts" to "Ajándékok (virág, könyv)",
    "certificate" to "Oklevél / emléklap"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventScreen(
    event: CalendarEvent,
    eventViewModel: EventViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val eventTypeOptions = listOf(
        EventType.NONE to "Nincs",
        EventType.GUEST to "Vendég",
        EventType.SPEECH to "Köszöntőbeszéd",
        EventType.VACATION to "Szabadság"
    )

    var eventType by remember { mutableStateOf(event.eventType) }
    var title by remember { mutableStateOf(event.title) }
    var organizer by remember { mutableStateOf(event.organizer.ifBlank { "Debreceni Tankerületi Központ" }) }
    var location by remember { mutableStateOf(event.location) }
    var allDay by remember { mutableStateOf(event.allDay) }
    var startDate by remember { mutableStateOf(event.date) }
    var endDate by remember { mutableStateOf(event.endDate) }
    var note by remember { mutableStateOf(event.note) }
    var hasTodoList by remember { mutableStateOf(event.hasTodoList) }
    var selectedActivities by remember {
        mutableStateOf(
            if (event.activities.isNotEmpty()) event.activities.toSet()
            else editActivityKeys.toSet()
        )
    }
    var visibleToUsers by remember { mutableStateOf(event.visibleToUsers) }

    // PDF state: keep existing URL unless user changes it
    var existingPdfUrl by remember { mutableStateOf(event.pdfUrl) }
    var pickedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pickedPdfName by remember { mutableStateOf("") }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        pickedPdfUri = uri
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (it.moveToFirst() && nameIndex >= 0) pickedPdfName = it.getString(nameIndex)
            }
        }
    }

    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val canSave = when (eventType) {
        EventType.VACATION -> true
        else -> title.isNotBlank()
    }

    val activePdfUrl = if (pickedPdfUri != null) null else existingPdfUrl

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Módosítás", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Mégse") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (!canSave || isSaving) return@TextButton
                            isSaving = true
                            errorMsg = null
                            val activities = if (hasTodoList) selectedActivities.toList() else emptyList()
                            scope.launch {
                                val resolvedPdfUrl = when {
                                    pickedPdfUri != null -> try {
                                        val storageRef = FirebaseStorage.getInstance().reference
                                            .child("events/${System.currentTimeMillis()}-${UUID.randomUUID()}.pdf")
                                        val stream = context.contentResolver.openInputStream(pickedPdfUri!!)!!
                                        withContext(Dispatchers.IO) { storageRef.putStream(stream).await(); stream.close() }
                                        storageRef.downloadUrl.await().toString()
                                    } catch (_: Exception) { existingPdfUrl }
                                    else -> existingPdfUrl
                                }
                                eventViewModel.updateEvent(
                                    firestoreID = event.firestoreID,
                                    title = title,
                                    date = startDate,
                                    note = note,
                                    location = location,
                                    organizer = organizer,
                                    hasTodoList = hasTodoList,
                                    activities = activities,
                                    eventType = eventType,
                                    visibleToUsers = visibleToUsers,
                                    allDay = allDay,
                                    pdfUrl = resolvedPdfUrl,
                                    onSuccess = { onBack() },
                                    onError = { msg -> isSaving = false; errorMsg = msg }
                                )
                            }
                        },
                        enabled = canSave && !isSaving
                    ) {
                        Text("Mentés", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (errorMsg != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    elevation = CardDefaults.cardElevation(0.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        errorMsg!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp
                    )
                }
            }

            Text("Esemény típusa", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                eventTypeOptions.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = eventType == type,
                        onClick = { eventType = type },
                        shape = SegmentedButtonDefaults.itemShape(index, eventTypeOptions.size)
                    ) {
                        Text(label, fontSize = 11.sp)
                    }
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Esemény neve") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = title.isBlank()
            )

            OutlinedTextField(
                value = organizer,
                onValueChange = { organizer = it },
                label = { Text("Szervező") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Helyszín") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Egész nap", fontSize = 16.sp)
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }

            DatePickerField(
                label = "Mettől",
                date = startDate,
                onDateSelected = { startDate = it }
            )

            if (!allDay) {
                TimePickerFieldEdit(
                    label = "Mettől (időpont)",
                    date = startDate,
                    onTimeSelected = { h, m ->
                        startDate = Calendar.getInstance().apply {
                            time = startDate
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, m)
                            set(Calendar.SECOND, 0)
                        }.time
                    }
                )

                TimePickerFieldEdit(
                    label = "Meddig (időpont)",
                    date = endDate,
                    onTimeSelected = { h, m ->
                        endDate = Calendar.getInstance().apply {
                            time = endDate
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, m)
                            set(Calendar.SECOND, 0)
                        }.time
                    }
                )
            }

            HorizontalDivider()

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Megjegyzés") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Teendőlista hozzáadása", fontSize = 16.sp)
                Switch(
                    checked = hasTodoList,
                    onCheckedChange = {
                        hasTodoList = it
                        if (it && selectedActivities.isEmpty()) {
                            selectedActivities = editActivityKeys.toSet()
                        }
                    }
                )
            }

            if (hasTodoList) {
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    editActivityKeys.forEach { key ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = selectedActivities.contains(key),
                                onCheckedChange = { checked ->
                                    selectedActivities = if (checked) {
                                        selectedActivities + key
                                    } else {
                                        selectedActivities - key
                                    }
                                }
                            )
                            Text(editActivityLabels[key] ?: key, fontSize = 15.sp)
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Intézményi esemény", fontSize = 16.sp)
                    Text("Látható a felhasználóknak", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = visibleToUsers, onCheckedChange = { visibleToUsers = it })
            }

            HorizontalDivider()

            // PDF invite section
            Text("PDF meghívó", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                pickedPdfUri != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null,
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text(pickedPdfName.ifBlank { "PDF csatolva" },
                                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { pickedPdfUri = null; pickedPdfName = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
                existingPdfUrl.isNotBlank() -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null,
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text("PDF meghívó csatolva", fontSize = 14.sp)
                        }
                        IconButton(onClick = { existingPdfUrl = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                    OutlinedButton(
                        onClick = { pdfPicker.launch("application/pdf") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("PDF cseréje")
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = { pdfPicker.launch("application/pdf") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("PDF meghívó csatolása")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TimePickerFieldEdit(
    label: String,
    date: java.util.Date,
    onTimeSelected: (hour: Int, minute: Int) -> Unit
) {
    val context = LocalContext.current
    val timeDisplayFmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale("hu")) }
    OutlinedButton(
        onClick = {
            val cal = Calendar.getInstance().apply { time = date }
            android.app.TimePickerDialog(
                context,
                { _, h, m -> onTimeSelected(h, m) },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Schedule, contentDescription = null,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label: ${timeDisplayFmt.format(date)}")
    }
}
