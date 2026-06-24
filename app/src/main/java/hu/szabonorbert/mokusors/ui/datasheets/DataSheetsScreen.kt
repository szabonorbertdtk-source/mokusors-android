package hu.szabonorbert.mokusors.ui.datasheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private val deadlineDisplayFmt = SimpleDateFormat("yyyy. MMM. d.", Locale("hu"))
private val deadlineParseFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

data class DataSheetField(
    val id: String,
    val label: String,
    val type: String,
    val required: Boolean,
    val order: Int
)

data class DataSheet(
    val id: String,
    val title: String,
    val description: String,
    val deadline: String,
    val status: String,
    val fields: List<DataSheetField>,
    val multiRow: Boolean,
    val createdBy: String = ""
) {
    val deadlineFormatted: String get() {
        if (deadline.isBlank()) return ""
        return try {
            val d = deadlineParseFmt.parse(deadline) ?: return deadline
            deadlineDisplayFmt.format(d)
        } catch (_: Exception) { deadline }
    }
}

data class DataSheetRow(
    val id: String,
    val userId: String,
    val userName: String,
    val values: Map<String, String>,
    val updatedAt: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSheetsScreen(onBack: () -> Unit) {
    val blue = Color(0xFF007AFF)
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val userEmail = auth.currentUser?.email ?: ""
    val userName = auth.currentUser?.displayName ?: userEmail

    var sheets by remember { mutableStateOf<List<DataSheet>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSheetId by remember { mutableStateOf<String?>(null) }
    var ownValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var ownRowLoaded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    // multiRow state
    var multiRowRows by remember { mutableStateOf<List<DataSheetRow>>(emptyList()) }
    var editingMultiRowId by remember { mutableStateOf<String?>(null) } // null=list, ""=new, docId=edit
    var editingValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val selectedSheet = sheets.firstOrNull { it.id == selectedSheetId } ?: sheets.firstOrNull()

    // Load sheets
    DisposableEffect(Unit) {
        val reg: ListenerRegistration = db.collection("dataSheets")
            .addSnapshotListener { snap, _ ->
                sheets = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    if (d["deleted"] as? Boolean == true) return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val rawFields = d["fields"] as? List<Map<String, Any>> ?: emptyList()
                    val fields = rawFields.mapIndexed { i, f ->
                        DataSheetField(
                            id = f["id"] as? String ?: UUID.randomUUID().toString(),
                            label = f["label"] as? String ?: "",
                            type = f["type"] as? String ?: "text",
                            required = f["required"] as? Boolean ?: false,
                            order = (f["order"] as? Long)?.toInt() ?: i
                        )
                    }.sortedBy { it.order }
                    DataSheet(
                        id = doc.id,
                        title = d["title"] as? String ?: "",
                        description = d["description"] as? String ?: "",
                        deadline = d["deadline"] as? String ?: "",
                        status = (d["status"] as? String)?.lowercase() ?: "open",
                        fields = fields,
                        multiRow = d["multiRow"] as? Boolean ?: false,
                        createdBy = d["createdBy"] as? String ?: d["creatorName"] as? String ?: ""
                    )
                }.sortedWith(compareBy({ it.status != "open" }, { it.deadline.ifEmpty { "9999" } }))
                if (selectedSheetId == null && sheets.isNotEmpty()) {
                    selectedSheetId = sheets.first().id
                }
                isLoading = false
            }
        onDispose { reg.remove() }
    }

    // Load rows for selected sheet
    val isMultiRow = selectedSheet?.multiRow == true
    var rowListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    LaunchedEffect(selectedSheetId, uid, isMultiRow) {
        rowListener?.remove()
        rowListener = null
        ownValues = emptyMap()
        ownRowLoaded = false
        multiRowRows = emptyList()
        editingMultiRowId = null
        editingValues = emptyMap()
        savedMessage = ""
        errorMsg = ""
        val sheetId = selectedSheetId ?: return@LaunchedEffect
        if (uid.isBlank()) return@LaunchedEffect
        if (isMultiRow) {
            rowListener = db.collection("dataSheets").document(sheetId)
                .collection("rows")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snap, _ ->
                    @Suppress("UNCHECKED_CAST")
                    multiRowRows = snap?.documents?.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        DataSheetRow(
                            id = doc.id,
                            userId = d["userId"] as? String ?: uid,
                            userName = d["userName"] as? String ?: userName,
                            values = (d["values"] as? Map<String, String>) ?: emptyMap(),
                            updatedAt = d["updatedAt"] as? String ?: ""
                        )
                    } ?: emptyList()
                    ownRowLoaded = true
                }
        } else {
            rowListener = db.collection("dataSheets").document(sheetId)
                .collection("rows").document(uid)
                .addSnapshotListener { snap, _ ->
                    @Suppress("UNCHECKED_CAST")
                    val vals = snap?.data?.get("values") as? Map<String, String> ?: emptyMap()
                    ownValues = vals
                    ownRowLoaded = true
                }
        }
    }
    DisposableEffect(Unit) { onDispose { rowListener?.remove() } }

    fun saveRow() {
        val sheetId = selectedSheetId ?: return
        isSaving = true
        savedMessage = ""
        errorMsg = ""
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val data = mapOf(
            "userId" to uid,
            "userEmail" to userEmail,
            "userName" to userName,
            "institutionName" to userName,
            "values" to ownValues,
            "updatedAt" to now
        )
        db.collection("dataSheets").document(sheetId)
            .collection("rows").document(uid)
            .set(data)
            .addOnSuccessListener { isSaving = false; savedMessage = "Sikeresen mentve." }
            .addOnFailureListener { e -> isSaving = false; errorMsg = e.message ?: "Mentési hiba." }
    }

    fun saveMultiRow() {
        val sheetId = selectedSheetId ?: return
        val editId = editingMultiRowId ?: return
        isSaving = true
        savedMessage = ""
        errorMsg = ""
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val data = mapOf(
            "userId" to uid,
            "userEmail" to userEmail,
            "userName" to userName,
            "institutionName" to userName,
            "values" to editingValues,
            "updatedAt" to now
        )
        val rowsRef = db.collection("dataSheets").document(sheetId).collection("rows")
        val task = if (editId.isEmpty()) rowsRef.add(data) else rowsRef.document(editId).set(data)
        task
            .addOnSuccessListener {
                isSaving = false
                savedMessage = "Sikeresen mentve."
                editingMultiRowId = null
                editingValues = emptyMap()
            }
            .addOnFailureListener { e -> isSaving = false; errorMsg = e.message ?: "Mentési hiba." }
    }

    fun deleteMultiRow(rowId: String) {
        val sheetId = selectedSheetId ?: return
        db.collection("dataSheets").document(sheetId).collection("rows").document(rowId).delete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adatszolgáltatás", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (sheets.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nincs aktív adatszolgáltatás.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Sheet picker — vertical cards (iOS style)
            items(sheets) { sheet ->
                val isSelected = sheet.id == (selectedSheet?.id)
                val isOpen = sheet.status == "open"
                val accentColor = if (isOpen) blue else Color(0xFF8E8E93)
                val statusLabel = if (isOpen) "Nyitott" else "Lezárt"
                val statusColor = if (isOpen) Color(0xFF34C759) else Color(0xFF8E8E93)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) blue else Color.Transparent,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            selectedSheetId = sheet.id
                            ownValues = emptyMap()
                            savedMessage = ""
                            errorMsg = ""
                        }
                        .padding(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    sheet.title,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (sheet.multiRow) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Color(0xFF5856D6))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text("Több sor", fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                if (!isOpen) {
                                    Icon(Icons.Default.Lock, null,
                                        tint = Color(0xFF8E8E93),
                                        modifier = Modifier.size(14.dp))
                                }
                            }
                            if (sheet.deadlineFormatted.isNotBlank()) {
                                Text("Határidő: ${sheet.deadlineFormatted}", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (sheet.createdBy.isNotBlank()) {
                                Text(sheet.createdBy, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            statusLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Divider between picker and form
            if (selectedSheet != null) {
                item { Spacer(Modifier.height(4.dp)) }
            }

            // Sheet form for selected sheet
            selectedSheet?.let { sheet ->
                if (sheet.multiRow) {
                    item {
                        MultiRowSheetCard(
                            sheet = sheet,
                            rows = multiRowRows,
                            editingId = editingMultiRowId,
                            editingValues = editingValues,
                            ownRowLoaded = ownRowLoaded,
                            isSaving = isSaving,
                            savedMessage = savedMessage,
                            errorMsg = errorMsg,
                            blue = blue,
                            onStartNew = {
                                editingMultiRowId = ""
                                editingValues = emptyMap()
                                savedMessage = ""
                                errorMsg = ""
                            },
                            onStartEdit = { row ->
                                editingMultiRowId = row.id
                                editingValues = row.values
                                savedMessage = ""
                                errorMsg = ""
                            },
                            onDelete = { row -> deleteMultiRow(row.id) },
                            onCancelEdit = {
                                editingMultiRowId = null
                                editingValues = emptyMap()
                                savedMessage = ""
                                errorMsg = ""
                            },
                            onValueChange = { id, v ->
                                editingValues = editingValues.toMutableMap().also { it[id] = v }
                                savedMessage = ""
                            },
                            onSave = { saveMultiRow() }
                        )
                    }
                } else {
                    item {
                        SheetFormCard(
                            sheet = sheet,
                            ownValues = ownValues,
                            ownRowLoaded = ownRowLoaded,
                            isSaving = isSaving,
                            savedMessage = savedMessage,
                            errorMsg = errorMsg,
                            blue = blue,
                            onValueChange = { id, v ->
                                ownValues = ownValues.toMutableMap().also { it[id] = v }
                                savedMessage = ""
                            },
                            onSave = { saveRow() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetFormCard(
    sheet: DataSheet,
    ownValues: Map<String, String>,
    ownRowLoaded: Boolean,
    isSaving: Boolean,
    savedMessage: String,
    errorMsg: String,
    blue: Color,
    onValueChange: (String, String) -> Unit,
    onSave: () -> Unit
) {
    val isOpen = sheet.status == "open"
    val accentColor = if (isOpen) blue else Color(0xFF8E8E93)
    val hasSavedData = ownValues.values.any { it.isNotBlank() }

    val deadlineDaysLeft = remember(sheet.deadline) {
        if (sheet.deadline.isBlank()) null
        else try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val deadline = fmt.parse(sheet.deadline) ?: return@remember null
            val diff = deadline.time - System.currentTimeMillis()
            TimeUnit.MILLISECONDS.toDays(diff)
        } catch (_: Exception) { null }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(sheet.title, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            modifier = Modifier.weight(1f, fill = false))
                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                if (isOpen) "Nyitott" else "Lezárt",
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accentColor
                            )
                        }
                    }
                    if (sheet.description.isNotBlank()) {
                        Text(sheet.description, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (sheet.deadline.isNotBlank()) {
                        val formatted = sheet.deadlineFormatted
                        val deadlineText = when {
                            deadlineDaysLeft == null -> "Határidő: $formatted"
                            deadlineDaysLeft < 0 -> "Határidő lejárt ($formatted)"
                            deadlineDaysLeft == 0L -> "Ma a határidő ($formatted)"
                            deadlineDaysLeft == 1L -> "Holnap a határidő ($formatted)"
                            else -> "Határidő: $formatted (${deadlineDaysLeft} nap)"
                        }
                        val deadlineColor = when {
                            deadlineDaysLeft == null || !isOpen -> accentColor
                            deadlineDaysLeft <= 3 -> Color(0xFFFF3B30)
                            deadlineDaysLeft <= 7 -> Color(0xFFFF9500)
                            else -> accentColor
                        }
                        Text(deadlineText, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium, color = deadlineColor)
                    }
                }
                if (hasSavedData) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34C759),
                        modifier = Modifier.size(22.dp).padding(top = 2.dp))
                }
            }

            if (!isOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF8E8E93).copy(alpha = 0.10f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(15.dp))
                    Text("Ez az adatszolgáltatás lezárva.", fontSize = 13.sp,
                        color = Color(0xFF8E8E93), fontWeight = FontWeight.Medium)
                }
            }

            if (!ownRowLoaded) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // Fields
            if (sheet.fields.isEmpty()) {
                Text("Ennek az adatszolgáltatásnak nincsenek mezői.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                sheet.fields.forEach { field ->
                    FieldInput(
                        field = field,
                        value = ownValues[field.id] ?: "",
                        enabled = isOpen,
                        accentColor = accentColor,
                        onValueChange = { onValueChange(field.id, it) }
                    )
                }
            }

            // Save button
            if (isOpen && sheet.fields.isNotEmpty()) {
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text(if (isSaving) "Mentés..." else "Mentés", fontWeight = FontWeight.Bold)
                }
            }

            if (savedMessage.isNotBlank()) {
                Text(savedMessage, fontSize = 14.sp, color = Color(0xFF34C759),
                    fontWeight = FontWeight.SemiBold)
            }
            if (errorMsg.isNotBlank()) {
                Text(errorMsg, fontSize = 13.sp, color = Color(0xFFFF3B30))
            }
        }
    }
}

@Composable
private fun FieldInput(
    field: DataSheetField,
    value: String,
    enabled: Boolean,
    accentColor: Color,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (field.required) "${field.label} *" else field.label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        when (field.type) {
            "boolean" -> {
                val checked = value == "true" || value == "1"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable(enabled = enabled) { onValueChange(if (checked) "false" else "true") }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Igen", fontSize = 15.sp)
                    Switch(
                        checked = checked,
                        onCheckedChange = if (enabled) { { onValueChange(if (it) "true" else "false") } } else null,
                        enabled = enabled
                    )
                }
            }
            "longText" -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    enabled = enabled,
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    colors = outlinedFieldColors(accentColor)
                )
            }
            "number" -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    colors = outlinedFieldColors(accentColor)
                )
            }
            "date" -> {
                val context = LocalContext.current
                val displayText = if (value.isBlank()) "Válassz dátumot…" else {
                    try {
                        val d = deadlineParseFmt.parse(value)
                        if (d != null) deadlineDisplayFmt.format(d) else value
                    } catch (_: Exception) { value }
                }
                OutlinedTextField(
                    value = displayText,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) {
                            val cal = java.util.Calendar.getInstance()
                            if (value.isNotBlank()) {
                                try {
                                    cal.time = deadlineParseFmt.parse(value) ?: cal.time
                                } catch (_: Exception) {}
                            }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                    val picked = java.util.Calendar.getInstance()
                                        .apply { set(y, m, d) }.time
                                    onValueChange(fmt.format(picked))
                                },
                                cal.get(java.util.Calendar.YEAR),
                                cal.get(java.util.Calendar.MONTH),
                                cal.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    enabled = false,
                    readOnly = true,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = outlinedFieldColors(accentColor),
                    trailingIcon = {
                        Icon(Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = if (enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                )
            }
            "time" -> {
                val context = LocalContext.current
                val displayText = if (value.isBlank()) "Válassz időpontot…" else value
                OutlinedTextField(
                    value = displayText,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) {
                            val parts = value.split(":")
                            val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
                            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    onValueChange("%02d:%02d".format(hour, minute))
                                },
                                h, m, true
                            ).show()
                        },
                    enabled = false,
                    readOnly = true,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = outlinedFieldColors(accentColor),
                    trailingIcon = {
                        Icon(Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                )
            }
            else -> { // "text", "datetime", default
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = field.type != "datetime",
                    shape = RoundedCornerShape(10.dp),
                    colors = outlinedFieldColors(accentColor)
                )
            }
        }
    }
}

@Composable
private fun MultiRowSheetCard(
    sheet: DataSheet,
    rows: List<DataSheetRow>,
    editingId: String?,
    editingValues: Map<String, String>,
    ownRowLoaded: Boolean,
    isSaving: Boolean,
    savedMessage: String,
    errorMsg: String,
    blue: Color,
    onStartNew: () -> Unit,
    onStartEdit: (DataSheetRow) -> Unit,
    onDelete: (DataSheetRow) -> Unit,
    onCancelEdit: () -> Unit,
    onValueChange: (String, String) -> Unit,
    onSave: () -> Unit
) {
    val isOpen = sheet.status == "open"
    val accentColor = if (isOpen) blue else Color(0xFF8E8E93)
    val deadlineDaysLeft = remember(sheet.deadline) {
        if (sheet.deadline.isBlank()) null
        else try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val deadline = fmt.parse(sheet.deadline) ?: return@remember null
            TimeUnit.MILLISECONDS.toDays(deadline.time - System.currentTimeMillis())
        } catch (_: Exception) { null }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(sheet.title, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            modifier = Modifier.weight(1f, fill = false))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(if (isOpen) "Nyitott" else "Lezárt",
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
                        }
                    }
                    if (sheet.description.isNotBlank()) {
                        Text(sheet.description, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (sheet.deadline.isNotBlank()) {
                        val formatted = sheet.deadlineFormatted
                        val deadlineText = when {
                            deadlineDaysLeft == null -> "Határidő: $formatted"
                            deadlineDaysLeft < 0 -> "Határidő lejárt ($formatted)"
                            deadlineDaysLeft == 0L -> "Ma a határidő ($formatted)"
                            deadlineDaysLeft == 1L -> "Holnap a határidő ($formatted)"
                            else -> "Határidő: $formatted (${deadlineDaysLeft} nap)"
                        }
                        val deadlineColor = when {
                            deadlineDaysLeft == null || !isOpen -> accentColor
                            deadlineDaysLeft <= 3 -> Color(0xFFFF3B30)
                            deadlineDaysLeft <= 7 -> Color(0xFFFF9500)
                            else -> accentColor
                        }
                        Text(deadlineText, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium, color = deadlineColor)
                    }
                }
                if (isOpen && editingId == null) {
                    IconButton(onClick = onStartNew) {
                        Icon(Icons.Default.Add, contentDescription = "Új sor", tint = accentColor)
                    }
                }
            }

            if (!isOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF8E8E93).copy(alpha = 0.10f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(15.dp))
                    Text("Ez az adatszolgáltatás lezárva.", fontSize = 13.sp,
                        color = Color(0xFF8E8E93), fontWeight = FontWeight.Medium)
                }
            }

            if (!ownRowLoaded) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // Editing form (new or existing row)
            if (editingId != null) {
                HorizontalDivider()
                Text(
                    if (editingId.isEmpty()) "Új sor" else "Sor szerkesztése",
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                )
                if (sheet.fields.isEmpty()) {
                    Text("Ennek az adatszolgáltatásnak nincsenek mezői.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    sheet.fields.forEach { field ->
                        FieldInput(
                            field = field,
                            value = editingValues[field.id] ?: "",
                            enabled = true,
                            accentColor = accentColor,
                            onValueChange = { onValueChange(field.id, it) }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Mégse") }
                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) { Text(if (isSaving) "Mentés..." else "Mentés", fontWeight = FontWeight.Bold) }
                }
                if (savedMessage.isNotBlank()) {
                    Text(savedMessage, fontSize = 14.sp, color = Color(0xFF34C759),
                        fontWeight = FontWeight.SemiBold)
                }
                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, fontSize = 13.sp, color = Color(0xFFFF3B30))
                }
            } else {
                // Row list
                if (ownRowLoaded && rows.isEmpty()) {
                    Text("Még nincs beküldött sor.", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                rows.forEachIndexed { index, row ->
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${index + 1}. sor", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            val firstValue = sheet.fields.firstOrNull()
                                ?.let { row.values[it.id] }?.takeIf { it.isNotBlank() }
                            if (firstValue != null) {
                                Text(firstValue, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        if (isOpen) {
                            Row {
                                IconButton(onClick = { onStartEdit(row) }) {
                                    Icon(Icons.Default.Edit, null, tint = accentColor,
                                        modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onDelete(row) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF3B30),
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                if (isOpen && ownRowLoaded) {
                    Button(
                        onClick = onStartNew,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Új sor hozzáadása", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun outlinedFieldColors(accentColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accentColor,
    focusedLabelColor = accentColor,
    cursorColor = accentColor
)
