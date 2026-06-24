package hu.szabonorbert.mokusors.ui.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class TeacherOffer(
    val id: String,
    val institution: String,
    val teacherName: String,
    val qualification: String,
    val offeredHours: String,
    val status: String,
    val receivingInstitution: String
)

data class TeacherRequest(
    val id: String,
    val institution: String,
    val tasks: String,
    val requiredQualification: String,
    val hours: String,
    val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(onBack: () -> Unit) {
    var offers by remember { mutableStateOf<List<TeacherOffer>>(emptyList()) }
    var requests by remember { mutableStateOf<List<TeacherRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val purple = Color(0xFFAF52DE)

    DisposableEffect(Unit) {
        var offersLoaded = false
        var requestsLoaded = false

        val r1: ListenerRegistration = FirebaseFirestore.getInstance()
            .collection("teacherOffers")
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
                        receivingInstitution = d["receivingInstitution"] as? String ?: ""
                    )
                }.sortedWith(compareBy({ it.institution }, { it.teacherName }))
                offersLoaded = true
                if (offersLoaded && requestsLoaded) isLoading = false
            }

        val r2: ListenerRegistration = FirebaseFirestore.getInstance()
            .collection("teacherRequests")
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
                        status = d["status"] as? String ?: ""
                    )
                }.sortedBy { it.institution }
                requestsLoaded = true
                if (offersLoaded && requestsLoaded) isLoading = false
            }

        onDispose { r1.remove(); r2.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kereslet–Kínálat", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
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
                            OfferCard(offer = offer, purple = purple)
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
                            RequestCard(request = request, purple = purple)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferCard(offer: TeacherOffer, purple: Color) {
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
            }
        }
    }
}

@Composable
private fun RequestCard(request: TeacherRequest, purple: Color) {
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
            }
        }
    }
}
