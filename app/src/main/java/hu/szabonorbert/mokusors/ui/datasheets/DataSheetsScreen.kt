package hu.szabonorbert.mokusors.ui.datasheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

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
    val multiRow: Boolean
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
                        multiRow = d["multiRow"] as? Boolean ?: false
                    )
                }.sortedWith(compareBy({ it.status != "open" }, { it.deadline.ifEmpty { "9999" } }))
                if (selectedSheetId == null && sheets.isNotEmpty()) {
                    selectedSheetId = sheets.first().id
                }
                isLoading = false
            }
        onDispose { reg.remove() }
    }

    // Load own row for selected sheet
    var rowListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    LaunchedEffect(selectedSheetId, uid) {
        rowListener?.remove()
        rowListener = null
        ownValues = emptyMap()
        ownRowLoaded = false
        savedMessage = ""
        errorMsg = ""
        val sheetId = selectedSheetId ?: return@LaunchedEffect
        if (uid.isBlank()) return@LaunchedEffect
        rowListener = db.collection("dataSheets").document(sheetId)
            .collection("rows").document(uid)
            .addSnapshotListener { snap, _ ->
                @Suppress("UNCHECKED_CAST")
                val vals = snap?.data?.get("values") as? Map<String, String> ?: emptyMap()
                ownValues = vals
                ownRowLoaded = true
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet picker (horizontal scroll)
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sheets) { sheet ->
                        val isSelected = sheet.id == (selectedSheet?.id)
                        val isOpen = sheet.status == "open"
                        val accentColor = if (isOpen) blue else Color(0xFF8E8E93)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) accentColor else accentColor.copy(alpha = 0.08f))
                                .border(
                                    width = if (isSelected) 0.dp else 1.dp,
                                    color = accentColor.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    selectedSheetId = sheet.id
                                    ownValues = emptyMap()
                                    savedMessage = ""
                                    errorMsg = ""
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                sheet.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else accentColor
                            )
                        }
                    }
                }
            }

            // Sheet form
            selectedSheet?.let { sheet ->
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(sheet.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (sheet.description.isNotBlank()) {
                        Text(sheet.description, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (sheet.deadline.isNotBlank()) {
                        Text("Határidő: ${sheet.deadline}", fontSize = 13.sp,
                            fontWeight = FontWeight.Medium, color = accentColor)
                    }
                }
                if (hasSavedData) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34C759),
                        modifier = Modifier.size(22.dp).padding(top = 2.dp))
                }
            }

            if (!isOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF8E8E93).copy(alpha = 0.12f))
                        .padding(10.dp)
                ) {
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
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    placeholder = { Text("éééé-hh-nn", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = RoundedCornerShape(10.dp),
                    colors = outlinedFieldColors(accentColor)
                )
            }
            "time" -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    placeholder = { Text("óó:pp", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = RoundedCornerShape(10.dp),
                    colors = outlinedFieldColors(accentColor)
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
private fun outlinedFieldColors(accentColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accentColor,
    focusedLabelColor = accentColor,
    cursorColor = accentColor
)
