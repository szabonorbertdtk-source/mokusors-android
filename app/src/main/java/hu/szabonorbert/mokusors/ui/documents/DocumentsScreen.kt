package hu.szabonorbert.mokusors.ui.documents

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class AppDocument(
    val id: String,
    val title: String,
    val fileName: String,
    val fileUrl: String,
    val fileType: String,
    val uploadedBy: String,
    val uploadedAt: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(isAdmin: Boolean = false, onBack: () -> Unit) {
    var docs by remember { mutableStateOf<List<AppDocument>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val brown = Color(0xFF8E6B4F)
    val userRole = if (isAdmin) "admin" else "user"

    DisposableEffect(Unit) {
        val reg: ListenerRegistration = FirebaseFirestore.getInstance()
            .collection("documents")
            .addSnapshotListener { snap, _ ->
                docs = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val fileUrl = d["fileUrl"] as? String ?: ""
                    if (fileUrl.isBlank()) return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val visibility = d["visibility"] as? List<String> ?: emptyList()
                    // Admin sees everything; others filtered by visibility (empty = everyone)
                    if (!isAdmin && visibility.isNotEmpty() && !visibility.contains(userRole)) {
                        return@mapNotNull null
                    }
                    AppDocument(
                        id = doc.id,
                        title = d["title"] as? String ?: d["fileName"] as? String ?: "",
                        fileName = d["fileName"] as? String ?: "",
                        fileUrl = fileUrl,
                        fileType = d["fileType"] as? String ?: "",
                        uploadedBy = d["uploadedBy"] as? String ?: "",
                        uploadedAt = d["uploadedAt"] as? String ?: ""
                    )
                }.sortedByDescending { it.uploadedAt }
                isLoading = false
            }
        onDispose { reg.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dokumentumok", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (docs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nincs dokumentum.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(docs) { doc ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(brown.copy(alpha = 0.07f))
                        .clickable(enabled = doc.fileUrl.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(doc.fileUrl)))
                        }
                        .padding(14.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(brown.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Description, null, tint = brown, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(doc.title.ifEmpty { doc.fileName }.ifEmpty { "Névtelen dokumentum" },
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (doc.uploadedBy.isNotBlank()) {
                                Text(doc.uploadedBy, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (doc.fileUrl.isNotBlank()) {
                            Icon(Icons.Default.OpenInNew, null, tint = brown, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
