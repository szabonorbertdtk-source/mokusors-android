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
import androidx.compose.material.icons.filled.*
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class AppDocumentFolder(
    val id: String,
    val name: String,
    val visibility: List<String>,
    val adminVisibility: List<String>
)

data class AppDocument(
    val id: String,
    val title: String,
    val fileName: String,
    val fileUrl: String,
    val fileType: String,
    val uploadedBy: String,
    val uploadedAt: String,
    val folderId: String?,
    val adminVisibility: List<String> = emptyList()
)

private val uploadDateParseFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
private val uploadDateDisplayFmt = SimpleDateFormat("yyyy. MMM. d.", Locale("hu"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(isAdmin: Boolean = false, onBack: () -> Unit) {
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    var folders by remember { mutableStateOf<List<AppDocumentFolder>>(emptyList()) }
    var docs by remember { mutableStateOf<List<AppDocument>>(emptyList()) }
    var foldersLoaded by remember { mutableStateOf(false) }
    var docsLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val brown = Color(0xFF8E6B4F)
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    DisposableEffect(Unit) {
        val db = FirebaseFirestore.getInstance()

        val folderReg: ListenerRegistration = db.collection("documentFolders")
            .addSnapshotListener { snap, _ ->
                folders = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val visibility = d["visibility"] as? List<String> ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val adminVisibility = d["adminVisibility"] as? List<String> ?: emptyList()
                    if (isAdmin) {
                        // Admin: only show if adminVisibility is empty (all admins) OR contains this UID
                        if (adminVisibility.isNotEmpty() && !adminVisibility.contains(currentUid)) {
                            return@mapNotNull null
                        }
                    } else {
                        if (visibility.isNotEmpty() && !visibility.contains("user")) {
                            return@mapNotNull null
                        }
                    }
                    AppDocumentFolder(
                        id = doc.id,
                        name = d["name"] as? String ?: "",
                        visibility = visibility,
                        adminVisibility = adminVisibility
                    )
                }.sortedBy { it.name }
                foldersLoaded = true
            }

        val docReg: ListenerRegistration = db.collection("documents")
            .addSnapshotListener { snap, _ ->
                docs = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val fileUrl = d["fileUrl"] as? String ?: ""
                    if (fileUrl.isBlank()) return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val visibility = d["visibility"] as? List<String> ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val docAdminVisibility = d["adminVisibility"] as? List<String> ?: emptyList()
                    if (isAdmin) {
                        if (docAdminVisibility.isNotEmpty() && !docAdminVisibility.contains(currentUid)) {
                            return@mapNotNull null
                        }
                    } else {
                        if (visibility.isNotEmpty() && !visibility.contains("user")) {
                            return@mapNotNull null
                        }
                    }
                    AppDocument(
                        id = doc.id,
                        title = d["title"] as? String ?: d["fileName"] as? String ?: "",
                        fileName = d["fileName"] as? String ?: "",
                        fileUrl = fileUrl,
                        fileType = d["fileType"] as? String ?: "",
                        uploadedBy = d["uploadedBy"] as? String ?: "",
                        uploadedAt = d["uploadedAt"] as? String ?: "",
                        folderId = d["folderId"] as? String,
                        adminVisibility = docAdminVisibility
                    )
                }.sortedByDescending { it.uploadedAt }
                docsLoaded = true
            }

        onDispose {
            folderReg.remove()
            docReg.remove()
        }
    }

    val isLoading = !foldersLoaded || !docsLoaded
    val unfolderedDocs = docs.filter { doc -> doc.folderId == null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backoffice", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (folders.isEmpty() && docs.isEmpty()) {
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
            folders.forEach { folder ->
                val folderDocs = docs.filter { it.folderId == folder.id }
                val isExpanded = expandedFolders[folder.id] ?: false

                item(key = "folder_${folder.id}") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(brown.copy(alpha = 0.10f))
                            .clickable { expandedFolders[folder.id] = !isExpanded }
                            .padding(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                    .background(brown.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                    null, tint = brown, modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(folder.name.ifEmpty { "Névtelen mappa" },
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "${folderDocs.size} dokumentum",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, tint = brown, modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (isExpanded) {
                    items(folderDocs, key = { "doc_${it.id}" }) { doc ->
                        Box(Modifier.padding(start = 12.dp)) {
                            DocRow(doc, brown, context)
                        }
                    }
                }
            }

            items(unfolderedDocs, key = { "unfol_${it.id}" }) { doc ->
                DocRow(doc, brown, context, large = true)
            }
        }
    }
}

@Composable
private fun DocRow(
    doc: AppDocument,
    brown: Color,
    context: android.content.Context,
    large: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (large) brown.copy(alpha = 0.07f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(enabled = doc.fileUrl.isNotBlank()) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(doc.fileUrl)))
                } catch (e: android.content.ActivityNotFoundException) {
                    // nincs alkalmazás a fájl megnyitásához — csendesen kihagyjuk
                }
            }
            .padding(if (large) 14.dp else 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (large) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(brown.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Description, null, tint = brown, modifier = Modifier.size(22.dp))
                }
            } else {
                Icon(Icons.Default.Description, null, tint = brown, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    doc.title.ifEmpty { doc.fileName }.ifEmpty { "Névtelen dokumentum" },
                    fontWeight = if (large) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (large) 16.sp else 14.sp
                )
                val metaParts = buildList {
                    if (doc.uploadedBy.isNotBlank()) add(doc.uploadedBy)
                    if (doc.uploadedAt.isNotBlank()) {
                        val formatted = try {
                            val d = uploadDateParseFmt.parse(doc.uploadedAt)
                            if (d != null) uploadDateDisplayFmt.format(d) else null
                        } catch (_: Exception) { null }
                        if (formatted != null) add(formatted)
                    }
                }
                if (metaParts.isNotEmpty()) {
                    Text(metaParts.joinToString(" · "),
                        fontSize = if (large) 13.sp else 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (doc.fileUrl.isNotBlank()) {
                Icon(Icons.Default.OpenInNew, null, tint = brown,
                    modifier = Modifier.size(if (large) 18.dp else 16.dp))
            }
        }
    }
}
