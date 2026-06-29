package hu.szabonorbert.mokusors.ui.event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import hu.szabonorbert.mokusors.model.EventType
import hu.szabonorbert.mokusors.viewmodel.EventViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private val dateDisplayFmt = SimpleDateFormat("yyyy. MM. dd.", Locale("hu"))
private val timeDisplayFmt = SimpleDateFormat("HH:mm", Locale("hu"))

private val vacationPeople = listOf("Laci", "Ivett", "Tündi", "Balázs")

private val activityKeys = listOf("dtk", "kk", "press", "ph", "catering", "gifts", "certificate")
private val activityLabels = mapOf(
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
fun AddEventScreen(
    eventViewModel: EventViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var eventType by remember { mutableStateOf(EventType.NONE) }

    // Vacation fields
    var vacationStart by remember { mutableStateOf(Date()) }
    var vacationEnd by remember { mutableStateOf(Date()) }
    var selectedPeople by remember { mutableStateOf(setOf<String>()) }

    // Event fields
    var title by remember { mutableStateOf("") }
    var organizer by remember { mutableStateOf("Debreceni Tankerületi Központ") }
    var location by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(Date()) }
    var endDate by remember { mutableStateOf(Date()) }
    var note by remember { mutableStateOf("") }
    var hasTodoList by remember { mutableStateOf(false) }
    var selectedActivities by remember { mutableStateOf(activityKeys.toSet()) }
    var visibleToUsers by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
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

    val scope = rememberCoroutineScope()

    val eventTypeOptions = listOf(
        EventType.NONE to "Nincs",
        EventType.GUEST to "Vendég",
        EventType.SPEECH to "Köszöntőbeszéd",
        EventType.VACATION to "Szabadság"
    )

    val canSave = when (eventType) {
        EventType.VACATION -> selectedPeople.isNotEmpty()
        else -> title.isNotBlank()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Új esemény", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Mégse") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (!canSave || isSaving) return@TextButton
                            isSaving = true
                            errorMsg = null
                            if (eventType == EventType.VACATION) {
                                eventViewModel.addVacation(
                                    names = selectedPeople.toList(),
                                    startDate = vacationStart,
                                    endDate = vacationEnd,
                                    onSuccess = { onBack() },
                                    onError = { msg -> isSaving = false; errorMsg = msg }
                                )
                            } else {
                                val activities = if (hasTodoList) selectedActivities.toList() else emptyList()
                                scope.launch {
                                    val resolvedPdfUrl = pickedPdfUri?.let { uri ->
                                        try {
                                            val storageRef = FirebaseStorage.getInstance().reference
                                                .child("events/${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.pdf")
                                            val stream = context.contentResolver.openInputStream(uri)!!
                                            withContext(Dispatchers.IO) { storageRef.putStream(stream).await(); stream.close() }
                                            storageRef.downloadUrl.await().toString()
                                        } catch (e: Exception) { "" }
                                    } ?: ""
                                    eventViewModel.addEvent(
                                        title = title,
                                        date = startDate,
                                        endDate = if (allDay) null else endDate,
                                        note = note,
                                        location = location,
                                        organizer = organizer,
                                        hasTodoList = hasTodoList,
                                        activities = activities,
                                        eventType = eventType,
                                        visibleToUsers = visibleToUsers,
                                        allDay = allDay,
                                        pdfUrl = resolvedPdfUrl,
                                        onSuccess = {
                                            sendAdminEventNotification(title, startDate)
                                            onBack()
                                        },
                                        onError = { msg -> isSaving = false; errorMsg = msg }
                                    )
                                }
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

            // Event type picker
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

            if (eventType == EventType.VACATION) {
                // Vacation form
                Text("Időszak", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                DatePickerField(
                    label = "Mettől",
                    date = vacationStart,
                    onDateSelected = { vacationStart = it }
                )

                DatePickerField(
                    label = "Meddig",
                    date = vacationEnd,
                    onDateSelected = { vacationEnd = it }
                )

                HorizontalDivider()

                Text("Kinek?", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                vacationPeople.forEach { person ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = selectedPeople.contains(person),
                            onCheckedChange = { checked ->
                                selectedPeople = if (checked) {
                                    selectedPeople + person
                                } else {
                                    selectedPeople - person
                                }
                            }
                        )
                        Text(person, fontSize = 16.sp)
                    }
                }

                if (selectedPeople.isEmpty()) {
                    Text("Válassz legalább egy személyt.",
                        color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

            } else {
                // Regular event form
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
                    TimePickerField(
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

                    TimePickerField(
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
                            if (it) selectedActivities = activityKeys.toSet()
                        }
                    )
                }

                if (hasTodoList) {
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        activityKeys.forEach { key ->
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
                                Text(activityLabels[key] ?: key, fontSize = 15.sp)
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

                // PDF invite attachment
                Text("PDF meghívó", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pickedPdfUri != null) {
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
                                fontSize = 14.sp, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { pickedPdfUri = null; pickedPdfName = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                } else {
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
internal fun DatePickerField(
    label: String,
    date: Date,
    onDateSelected: (Date) -> Unit
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val cal = Calendar.getInstance().apply { time = date }
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    val newDate = Calendar.getInstance().apply {
                        time = date
                        set(y, m, d)
                    }.time
                    onDateSelected(newDate)
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
        Text("$label: ${dateDisplayFmt.format(date)}")
    }
}

@Composable
private fun TimePickerField(
    label: String,
    date: Date,
    onTimeSelected: (hour: Int, minute: Int) -> Unit
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val cal = Calendar.getInstance().apply { time = date }
            TimePickerDialog(
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

private fun sendAdminEventNotification(title: String, date: Date) {
    val eventDateStr = SimpleDateFormat("yyyy. MM. dd.", Locale("hu")).format(date)
    FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.addOnSuccessListener { result ->
        val token = result.token ?: return@addOnSuccessListener
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = URL("https://mokusors-admin.vercel.app/api/admin/notifications/admin-event")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.doOutput = true
                val body = """{"eventTitle":${org.json.JSONObject.quote(title)},"eventDate":${org.json.JSONObject.quote(eventDateStr)}}"""
                conn.outputStream.write(body.toByteArray())
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }
}
