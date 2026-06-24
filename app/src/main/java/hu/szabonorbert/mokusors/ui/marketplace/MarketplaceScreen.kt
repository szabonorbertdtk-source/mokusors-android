package hu.szabonorbert.mokusors.ui.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class TeacherOffer(
    val id: String,
    val institution: String,
    val teacherName: String,
    val qualification: String,
    val offeredHours: String,
    val status: String,
    val receivingInstitution: String,
    val creatorId: String = ""
)

data class TeacherRequest(
    val id: String,
    val institution: String,
    val tasks: String,
    val requiredQualification: String,
    val hours: String,
    val status: String,
    val creatorId: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(isAdmin: Boolean = false, onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val userEmail = remember { FirebaseAuth.getInstance().currentUser?.email ?: "" }
    val userName = remember { FirebaseAuth.getInstance().currentUser?.displayName ?: userEmail }

    var offers by remember { mutableStateOf<List<TeacherOffer>>(emptyList()) }
    var requests by remember { mutableStateOf<List<TeacherRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val purple = Color(0xFFAF52DE)

    // Dialog states
    var showNewTypeDialog by remember { mutableStateOf(false) }
    var editingOffer by remember { mutableStateOf<TeacherOffer?>(null) }
    var editingRequest by remember { mutableStateOf<TeacherRequest?>(null) }
    var showNewOfferForm by remember { mutableStateOf(false) }
    var showNewRequestForm by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        var offersLoaded = false
        var requestsLoaded = false

        val r1: ListenerRegistration = db.collection("teacherOffers")
            .addSnapshotListener { snap, _ ->
                offers = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    if (d["deleted"] as? Boolean == true) return@mapNotNull null
                    TeacherOffer(
                        id = doc.id,
                        institution = d["institution"] as? String ?: "",
                        teacherName = d["teacherName"] as? String ?: "",
                        qualification = d["qualification"] as? String ?: "",
                        offeredHours = d["offeredHours"] as? String ?: "",
                        status = d["status"] as? String ?: "",
                        receivingInstitution = d["receivingInstitution"] as? String ?: "",
                        creatorId = d["creatorId"] as? String ?: ""
                    )
                }.sortedWith(compareBy({ it.institution }, { it.teacherName }))
                offersLoaded = true
                if (offersLoaded && requestsLoaded) isLoading = false
            }

        val r2: ListenerRegistration = db.collection("teacherRequests")
            .addSnapshotListener { snap, _ ->
                requests = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    if (d["deleted"] as? Boolean == true) return@mapNotNull null
                    TeacherRequest(
                        id = doc.id,
                        institution = d["institution"] as? String ?: "",
                        tasks = d["tasks"] as? String ?: "",
                        requiredQualification = d["requiredQualification"] as? String ?: "",
                        hours = d["hours"] as? String ?: "",
                        status = d["status"] as? String ?: "",
                        creatorId = d["creatorId"] as? String ?: ""
                    )
                }.sortedBy { it.institution }
                requestsLoaded = true
                if (offersLoaded && requestsLoaded) isLoading = false
            }

        onDispose { r1.remove(); r2.remove() }
    }

    fun nowUtc() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    fun saveOffer(id: String?, institution: String, teacherName: String,
                  qualification: String, offeredHours: String, receivingInstitution: String) {
        val data = mutableMapOf<String, Any>(
            "institution" to institution, "teacherName" to teacherName,
            "qualification" to qualification, "offeredHours" to offeredHours,
            "receivingInstitution" to receivingInstitution, "status" to "active",
            "updatedAt" to nowUtc()
        )
        if (id == null) {
            data["creatorId"] = uid
            data["creatorEmail"] = userEmail
            data["creatorName"] = userName
            data["createdAt"] = nowUtc()
            db.collection("teacherOffers").add(data)
        } else {
            db.collection("teacherOffers").document(id).update(data)
        }
    }

    fun saveRequest(id: String?, institution: String, tasks: String,
                    requiredQualification: String, hours: String) {
        val data = mutableMapOf<String, Any>(
            "institution" to institution, "tasks" to tasks,
            "requiredQualification" to requiredQualification, "hours" to hours,
            "status" to "active", "updatedAt" to nowUtc()
        )
        if (id == null) {
            data["creatorId"] = uid
            data["creatorEmail"] = userEmail
            data["creatorName"] = userName
            data["createdAt"] = nowUtc()
            db.collection("teacherRequests").add(data)
        } else {
            db.collection("teacherRequests").document(id).update(data)
        }
    }

    fun deleteOffer(id: String) {
        db.collection("teacherOffers").document(id).update("deleted", true)
    }

    fun deleteRequest(id: String) {
        db.collection("teacherRequests").document(id).update("deleted", true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kereslet–Kínálat", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewTypeDialog = true },
                containerColor = purple
            ) {
                Icon(Icons.Default.Add, contentDescription = "Új hirdetés", tint = Color.White)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Kínálat (${offers.size})", modifier = Modifier.padding(vertical = 12.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Kereslet (${requests.size})", modifier = Modifier.padding(vertical = 12.dp))
                }
            }
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }
            if (selectedTab == 0) {
                if (offers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nincs kínálat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(offers) { offer ->
                            val canEdit = isAdmin || offer.creatorId == uid
                            OfferCard(
                                offer = offer, purple = purple, canEdit = canEdit,
                                onEdit = { editingOffer = offer },
                                onDelete = { deleteOffer(offer.id) }
                            )
                        }
                    }
                }
            } else {
                if (requests.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nincs kereslet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(requests) { request ->
                            val canEdit = isAdmin || request.creatorId == uid
                            RequestCard(
                                request = request, purple = purple, canEdit = canEdit,
                                onEdit = { editingRequest = request },
                                onDelete = { deleteRequest(request.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Choose type dialog
    if (showNewTypeDialog) {
        AlertDialog(
            onDismissRequest = { showNewTypeDialog = false },
            title = { Text("Új hirdetés típusa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showNewTypeDialog = false; showNewOfferForm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = purple)
                    ) { Text("Kínálat (tanár kiadása)") }
                    OutlinedButton(
                        onClick = { showNewTypeDialog = false; showNewRequestForm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Kereslet (tanárt keres)") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNewTypeDialog = false }) { Text("Mégse") }
            }
        )
    }

    // New offer form
    if (showNewOfferForm) {
        OfferFormDialog(
            offer = null, purple = purple,
            onDismiss = { showNewOfferForm = false },
            onSave = { institution, teacherName, qualification, offeredHours, receivingInstitution ->
                saveOffer(null, institution, teacherName, qualification, offeredHours, receivingInstitution)
                showNewOfferForm = false
            }
        )
    }

    // Edit offer form
    editingOffer?.let { offer ->
        OfferFormDialog(
            offer = offer, purple = purple,
            onDismiss = { editingOffer = null },
            onSave = { institution, teacherName, qualification, offeredHours, receivingInstitution ->
                saveOffer(offer.id, institution, teacherName, qualification, offeredHours, receivingInstitution)
                editingOffer = null
            }
        )
    }

    // New request form
    if (showNewRequestForm) {
        RequestFormDialog(
            request = null, purple = purple,
            onDismiss = { showNewRequestForm = false },
            onSave = { institution, tasks, requiredQualification, hours ->
                saveRequest(null, institution, tasks, requiredQualification, hours)
                showNewRequestForm = false
            }
        )
    }

    // Edit request form
    editingRequest?.let { request ->
        RequestFormDialog(
            request = request, purple = purple,
            onDismiss = { editingRequest = null },
            onSave = { institution, tasks, requiredQualification, hours ->
                saveRequest(request.id, institution, tasks, requiredQualification, hours)
                editingRequest = null
            }
        )
    }
}

@Composable
private fun OfferCard(
    offer: TeacherOffer,
    purple: Color,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(purple.copy(alpha = 0.07f))
            .padding(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(purple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SwapHoriz, null, tint = purple, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(offer.teacherName.ifEmpty { offer.institution }.ifEmpty { "Névtelen" },
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (offer.institution.isNotBlank()) {
                    Text(offer.institution, fontSize = 14.sp, color = purple)
                }
                if (offer.qualification.isNotBlank()) {
                    Text(offer.qualification, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (offer.offeredHours.isNotBlank()) {
                    Text("${offer.offeredHours} óra", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (offer.receivingInstitution.isNotBlank()) {
                    Text("→ ${offer.receivingInstitution}", fontSize = 13.sp, color = purple)
                }
                if (offer.status.isNotBlank() && offer.status != "active") {
                    val statusLabel = when (offer.status) {
                        "filled" -> "Betöltve"
                        "expired" -> "Lejárt"
                        "inactive" -> "Inaktív"
                        else -> offer.status
                    }
                    Box(Modifier.clip(RoundedCornerShape(50))
                        .background(Color(0xFF8E8E93).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(statusLabel, fontSize = 11.sp, color = Color(0xFF8E8E93), fontWeight = FontWeight.SemiBold)
                    }
                }
                if (canEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = onEdit,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Szerkesztés", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3B30))
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Törlés", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: TeacherRequest,
    purple: Color,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(purple.copy(alpha = 0.07f))
            .padding(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(purple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SwapHoriz, null, tint = purple, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(request.institution.ifEmpty { "Névtelen intézmény" },
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (request.requiredQualification.isNotBlank()) {
                    Text(request.requiredQualification, fontSize = 14.sp, color = purple)
                }
                if (request.tasks.isNotBlank()) {
                    Text(request.tasks, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (request.hours.isNotBlank()) {
                    Text("${request.hours} óra", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (canEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = onEdit,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Szerkesztés", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3B30))
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Törlés", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferFormDialog(
    offer: TeacherOffer?,
    purple: Color,
    onDismiss: () -> Unit,
    onSave: (institution: String, teacherName: String, qualification: String,
             offeredHours: String, receivingInstitution: String) -> Unit
) {
    var institution by remember { mutableStateOf(offer?.institution ?: "") }
    var teacherName by remember { mutableStateOf(offer?.teacherName ?: "") }
    var qualification by remember { mutableStateOf(offer?.qualification ?: "") }
    var offeredHours by remember { mutableStateOf(offer?.offeredHours ?: "") }
    var receivingInstitution by remember { mutableStateOf(offer?.receivingInstitution ?: "") }
    var errorMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (offer == null) "Új kínálat" else "Kínálat szerkesztése",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)

                OutlinedTextField(value = institution, onValueChange = { institution = it },
                    label = { Text("Intézmény neve *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = teacherName, onValueChange = { teacherName = it },
                    label = { Text("Tanár neve *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = qualification, onValueChange = { qualification = it },
                    label = { Text("Végzettség / szaktárgy") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = offeredHours, onValueChange = { offeredHours = it },
                    label = { Text("Felajánlott óraszám") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = receivingInstitution, onValueChange = { receivingInstitution = it },
                    label = { Text("Fogadó intézmény (ha ismert)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = Color(0xFFFF3B30), fontSize = 13.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)) { Text("Mégse") }
                    Button(
                        onClick = {
                            when {
                                institution.isBlank() -> errorMsg = "Adja meg az intézmény nevét."
                                teacherName.isBlank() -> errorMsg = "Adja meg a tanár nevét."
                                else -> onSave(institution, teacherName, qualification, offeredHours, receivingInstitution)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = purple)
                    ) { Text("Mentés", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun RequestFormDialog(
    request: TeacherRequest?,
    purple: Color,
    onDismiss: () -> Unit,
    onSave: (institution: String, tasks: String, requiredQualification: String, hours: String) -> Unit
) {
    var institution by remember { mutableStateOf(request?.institution ?: "") }
    var tasks by remember { mutableStateOf(request?.tasks ?: "") }
    var requiredQualification by remember { mutableStateOf(request?.requiredQualification ?: "") }
    var hours by remember { mutableStateOf(request?.hours ?: "") }
    var errorMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (request == null) "Új kereslet" else "Kereslet szerkesztése",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)

                OutlinedTextField(value = institution, onValueChange = { institution = it },
                    label = { Text("Intézmény neve *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = tasks, onValueChange = { tasks = it },
                    label = { Text("Feladatok / tantárgyak") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 2)
                OutlinedTextField(value = requiredQualification, onValueChange = { requiredQualification = it },
                    label = { Text("Elvárt végzettség") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = hours, onValueChange = { hours = it },
                    label = { Text("Keresett óraszám") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = Color(0xFFFF3B30), fontSize = 13.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)) { Text("Mégse") }
                    Button(
                        onClick = {
                            if (institution.isBlank()) errorMsg = "Adja meg az intézmény nevét."
                            else onSave(institution, tasks, requiredQualification, hours)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = purple)
                    ) { Text("Mentés", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
