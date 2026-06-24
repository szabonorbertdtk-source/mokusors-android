package hu.szabonorbert.mokusors.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class InventoryItem(
    val id: String,
    val title: String,
    val description: String,
    val dimensions: String,
    val condition: String,
    val imageUrl: String,
    val createdBy: String,
    val createdByName: String,
    val institutionName: String,
    val createdAt: com.google.firebase.Timestamp?,
    val reservedBy: String?,
    val reservedByName: String?,
    val reservedAt: String?,
    val deleted: Boolean = false
)

private val conditions = listOf(
    "all" to "Összes",
    "új" to "Új",
    "jó" to "Jó állapotú",
    "használt" to "Használt"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(isAdmin: Boolean, onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUid = auth.currentUser?.uid ?: ""
    val currentName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: ""

    var items by remember { mutableStateOf<List<InventoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var filterCondition by remember { mutableStateOf("all") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<InventoryItem?>(null) }

    val teal = Color(0xFF30B0C7)

    DisposableEffect(Unit) {
        val reg: ListenerRegistration = db.collection("inventory")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                items = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    if (d["deleted"] as? Boolean == true) return@mapNotNull null
                    InventoryItem(
                        id = doc.id,
                        title = d["title"] as? String ?: "",
                        description = d["description"] as? String ?: "",
                        dimensions = d["dimensions"] as? String ?: "",
                        condition = d["condition"] as? String ?: "jó",
                        imageUrl = d["imageUrl"] as? String ?: "",
                        createdBy = d["createdBy"] as? String ?: "",
                        createdByName = d["createdByName"] as? String ?: "",
                        institutionName = d["institutionName"] as? String ?: "",
                        createdAt = d["createdAt"] as? com.google.firebase.Timestamp,
                        reservedBy = d["reservedBy"] as? String,
                        reservedByName = d["reservedByName"] as? String,
                        reservedAt = d["reservedAt"] as? String
                    )
                }
                isLoading = false
            }
        onDispose { reg.remove() }
    }

    val filtered = remember(items, search, filterCondition) {
        items.filter { item ->
            if (filterCondition != "all" && item.condition != filterCondition) return@filter false
            if (search.isBlank()) return@filter true
            val q = search.lowercase()
            item.title.lowercase().contains(q) ||
                item.description.lowercase().contains(q) ||
                item.institutionName.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leltár", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, "Új eszköz")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Keresés...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Condition filter chips
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                conditions.forEach { (key, label) ->
                    FilterChip(
                        selected = filterCondition == key,
                        onClick = { filterCondition = key },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = teal)
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (items.isEmpty()) "Még nincs eszköz a leltárban."
                        else "Nincs találat.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        InventoryCard(
                            item = item,
                            currentUid = currentUid,
                            isAdmin = isAdmin,
                            teal = teal,
                            onReserve = {
                                db.collection("inventory").document(item.id).update(
                                    mapOf(
                                        "reservedBy" to currentUid,
                                        "reservedByName" to currentName,
                                        "reservedAt" to java.text.SimpleDateFormat(
                                            "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US
                                        ).format(java.util.Date())
                                    )
                                )
                                sendInventoryNotification(auth, "reserved", item.title, item.createdBy)
                            },
                            onUnreserve = {
                                db.collection("inventory").document(item.id).update(
                                    mapOf(
                                        "reservedBy" to FieldValue.delete(),
                                        "reservedByName" to FieldValue.delete(),
                                        "reservedAt" to FieldValue.delete()
                                    )
                                )
                            },
                            onEdit = { selectedItem = item },
                            onDelete = {
                                db.collection("inventory").document(item.id)
                                    .update("deleted", true)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        InventoryFormDialog(
            item = null,
            onDismiss = { showAddDialog = false },
            onSave = { title, desc, dims, cond, imgUrl ->
                val userDoc = db.collection("users").document(currentUid)
                userDoc.get().addOnSuccessListener { snap ->
                    val institution = snap?.getString("institutionName") ?: ""
                    db.collection("inventory").add(
                        mapOf(
                            "title" to title,
                            "description" to desc,
                            "dimensions" to dims,
                            "condition" to cond,
                            "imageUrl" to imgUrl,
                            "createdBy" to currentUid,
                            "createdByName" to currentName,
                            "institutionName" to institution,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "reservedBy" to null,
                            "reservedByName" to null,
                            "reservedAt" to null,
                            "deleted" to false
                        )
                    )
                    sendInventoryNotification(auth, "new_item", title, null)
                }
                showAddDialog = false
            }
        )
    }

    selectedItem?.let { item ->
        InventoryFormDialog(
            item = item,
            onDismiss = { selectedItem = null },
            onSave = { title, desc, dims, cond, imgUrl ->
                db.collection("inventory").document(item.id).update(
                    mapOf(
                        "title" to title,
                        "description" to desc,
                        "dimensions" to dims,
                        "condition" to cond,
                        "imageUrl" to imgUrl
                    )
                )
                selectedItem = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryCard(
    item: InventoryItem,
    currentUid: String,
    isAdmin: Boolean,
    teal: Color,
    onReserve: () -> Unit,
    onUnreserve: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isReserved = !item.reservedBy.isNullOrEmpty()
    val isMyReservation = item.reservedBy == currentUid
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val conditionColor = when (item.condition) {
        "új" -> Color(0xFF34C759)
        "jó" -> Color(0xFF007AFF)
        else -> Color(0xFFFF9500)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Image or placeholder
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(teal.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = teal,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (item.institutionName.isNotBlank()) {
                        Text(item.institutionName, fontSize = 13.sp, color = teal)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(conditionColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                item.condition.replaceFirstChar { it.uppercase() },
                                fontSize = 11.sp,
                                color = conditionColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (isReserved) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFF3B30).copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Lefoglalt", fontSize = 11.sp, color = Color(0xFFFF3B30), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (isAdmin) {
                    Column(horizontalAlignment = Alignment.End) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp),
                                tint = Color(0xFFFF3B30))
                        }
                    }
                }
            }

            if (item.description.isNotBlank()) {
                Text(item.description, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.dimensions.isNotBlank()) {
                Text("Méretek: ${item.dimensions}", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (isReserved) {
                Text(
                    "Lefoglalta: ${item.reservedByName ?: ""}",
                    fontSize = 13.sp,
                    color = Color(0xFFFF3B30)
                )
            }

            // Reserve / unreserve button
            if (!isAdmin || isMyReservation || !isReserved) {
                Button(
                    onClick = if (isReserved && isMyReservation) onUnreserve else onReserve,
                    enabled = !isReserved || isMyReservation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMyReservation) Color(0xFFFF3B30) else teal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        when {
                            isMyReservation -> "Foglalás visszavonása"
                            isReserved -> "Már lefoglalt"
                            else -> "Lefoglalom"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        BasicAlertDialog(onDismissRequest = { showDeleteConfirm = false }) {
            Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(0.dp)) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Törlés", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Biztosan törölni szeretnéd a(z) ${item.title} eszközt?")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text("Mégse") }
                        TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                            Text("Törlés", color = Color(0xFFFF3B30))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryFormDialog(
    item: InventoryItem?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(item?.title ?: "") }
    var description by remember { mutableStateOf(item?.description ?: "") }
    var dimensions by remember { mutableStateOf(item?.dimensions ?: "") }
    var condition by remember { mutableStateOf(item?.condition ?: "jó") }
    var imageUrl by remember { mutableStateOf(item?.imageUrl ?: "") }

    val conditionOptions = listOf("új", "jó", "használt")

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(0.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (item == null) "Új eszköz" else "Eszköz szerkesztése",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Megnevezés *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Leírás") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedTextField(
                    value = dimensions,
                    onValueChange = { dimensions = it },
                    label = { Text("Méretek") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Kép URL (opcionális)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Állapot", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    conditionOptions.forEach { opt ->
                        FilterChip(
                            selected = condition == opt,
                            onClick = { condition = opt },
                            label = { Text(opt.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("Mégse") }
                    Button(
                        onClick = { if (title.isNotBlank()) onSave(title, description, dimensions, condition, imageUrl) },
                        enabled = title.isNotBlank()
                    ) { Text("Mentés") }
                }
            }
        }
    }
}

private fun sendInventoryNotification(
    auth: FirebaseAuth,
    type: String,
    itemTitle: String,
    ownerUid: String?
) {
    auth.currentUser?.getIdToken(false)?.addOnSuccessListener { result ->
        val token = result.token ?: return@addOnSuccessListener
        Thread {
            try {
                val url = URL("https://mokusors-admin.vercel.app/api/inventory/notify")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.doOutput = true
                val body = JSONObject().apply {
                    put("type", type)
                    put("itemTitle", itemTitle)
                    if (ownerUid != null) put("ownerUid", ownerUid)
                }
                conn.outputStream.write(body.toString().toByteArray())
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }.start()
    }
}
