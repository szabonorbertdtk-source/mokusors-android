package hu.szabonorbert.mokusors.ui.resumes

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

private val resumeUploadParseFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }
private val resumeUploadParseFmtFractional = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }
private val resumeUploadDisplayFmt = SimpleDateFormat("yyyy.MM.dd. HH:mm", Locale("hu"))

data class ResumeItem(
    val id: String,
    val name: String,
    val education: String,
    val educations: List<String>,
    val pdfName: String,
    val pdfOriginalName: String,
    val uploadedAt: String
)

private fun parseResumeDate(raw: String): Date? =
    try { resumeUploadParseFmtFractional.parse(raw) } catch (_: Exception) { null }
        ?: try { resumeUploadParseFmt.parse(raw) } catch (_: Exception) { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumesScreen(isAdmin: Boolean = false, onBack: () -> Unit) {
    val orange = Color(0xFFFF9500)
    var resumes by remember { mutableStateOf<List<ResumeItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val reg: ListenerRegistration = FirebaseFirestore.getInstance()
            .collection("resumes")
            .addSnapshotListener { snap, _ ->
                resumes = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    if (d["deleted"] as? Boolean == true) return@mapNotNull null

                    val uploadedAt = when (val v = d["uploadedAt"]) {
                        is Timestamp -> resumeUploadParseFmt.format(v.toDate())
                        is String -> v
                        else -> ""
                    }

                    val educationSingle = d["education"] as? String ?: ""
                    @Suppress("UNCHECKED_CAST")
                    val educations = (d["educations"] as? List<String>)
                        ?.filter { it.isNotBlank() }
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOf(educationSingle).filter { it.isNotBlank() }

                    ResumeItem(
                        id = doc.id,
                        name = d["name"] as? String ?: "",
                        education = educations.firstOrNull() ?: educationSingle,
                        educations = educations,
                        pdfName = d["pdfName"] as? String ?: "",
                        pdfOriginalName = d["pdfOriginalName"] as? String ?: "Önéletrajz.pdf",
                        uploadedAt = uploadedAt
                    )
                }.sortedByDescending { it.uploadedAt }
                isLoading = false
            }
        onDispose { reg.remove() }
    }

    val filtered = remember(resumes, searchText) {
        if (searchText.isBlank()) resumes
        else {
            val q = searchText.trim().lowercase()
            resumes.filter { r ->
                r.name.lowercase().contains(q) ||
                r.educations.any { it.lowercase().contains(q) }
            }
        }
    }

    fun softDelete(id: String) {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        FirebaseFirestore.getInstance().collection("resumes").document(id)
            .update(mapOf("deleted" to true, "deletedAt" to now, "deletedBy" to email))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Önéletrajzok", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Név vagy végzettség") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            )

            if (resumes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nincs önéletrajz.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nincs találat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { resume ->
                    val hasPdf = resume.pdfName.isNotBlank()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(orange.copy(alpha = 0.07f))
                            .clickable(enabled = hasPdf) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resume.pdfName)))
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                                    .background(orange.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, null, tint = orange, modifier = Modifier.size(26.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(resume.name.ifEmpty { "Névtelen" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (resume.educations.isNotEmpty()) {
                                    resume.educations.forEach { edu ->
                                        Text(edu, fontSize = 14.sp, color = orange)
                                    }
                                } else if (resume.education.isNotBlank()) {
                                    Text(resume.education, fontSize = 14.sp, color = orange)
                                }
                                if (resume.uploadedAt.isNotBlank()) {
                                    val displayDate = remember(resume.uploadedAt) {
                                        parseResumeDate(resume.uploadedAt)
                                            ?.let { resumeUploadDisplayFmt.format(it) }
                                            ?: resume.uploadedAt
                                    }
                                    Text("Feltöltve: $displayDate", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (hasPdf) {
                                    Icon(Icons.Default.OpenInNew, null, tint = orange, modifier = Modifier.size(18.dp))
                                }
                                if (isAdmin) {
                                    if (deleteConfirmId == resume.id) {
                                        TextButton(
                                            onClick = { softDelete(resume.id); deleteConfirmId = null },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF3B30))
                                        ) { Text("Biztos?", fontSize = 12.sp) }
                                    } else {
                                        IconButton(
                                            onClick = { deleteConfirmId = resume.id },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, null,
                                                tint = Color(0xFFFF3B30).copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
