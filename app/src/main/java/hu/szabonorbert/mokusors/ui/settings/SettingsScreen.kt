package hu.szabonorbert.mokusors.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import hu.szabonorbert.mokusors.viewmodel.AuthViewModel

data class NotifPrefs(
    val programs: Boolean = true,
    val dataSheets: Boolean = true,
    val resumes: Boolean = true,
    val marketplace: Boolean = true,
    val institutionalEvents: Boolean = true,
    val deadlineTasks: Boolean = true,
    val documents: Boolean = true,
    val photos: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    isAdmin: Boolean,
    onBack: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val userEmail = auth.currentUser?.email ?: ""
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE) }

    var widgetShowEvents by remember { mutableStateOf(prefs.getBoolean("widgetShowEvents", true)) }
    var widgetShowTasks by remember { mutableStateOf(prefs.getBoolean("widgetShowTasks", true)) }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var feedbackMsg by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    var notifPrefs by remember { mutableStateOf(NotifPrefs()) }

    // Load notification preferences
    LaunchedEffect(uid) {
        if (uid.isBlank()) return@LaunchedEffect
        db.collection("users").document(uid)
            .collection("settings").document("notifications")
            .addSnapshotListener { snap, _ ->
                val d = snap?.data ?: return@addSnapshotListener
                notifPrefs = NotifPrefs(
                    programs = d["programs"] as? Boolean ?: true,
                    dataSheets = d["dataSheets"] as? Boolean ?: true,
                    resumes = d["resumes"] as? Boolean ?: true,
                    marketplace = d["marketplace"] as? Boolean ?: true,
                    institutionalEvents = d["institutionalEvents"] as? Boolean ?: true,
                    deadlineTasks = d["deadlineTasks"] as? Boolean ?: true,
                    documents = d["documents"] as? Boolean ?: true,
                    photos = d["photos"] as? Boolean ?: true
                )
            }
    }

    fun saveNotifPrefs(prefs: NotifPrefs) {
        if (uid.isBlank()) return
        db.collection("users").document(uid)
            .collection("settings").document("notifications")
            .set(mapOf(
                "programs" to prefs.programs, "dataSheets" to prefs.dataSheets,
                "resumes" to prefs.resumes, "marketplace" to prefs.marketplace,
                "institutionalEvents" to prefs.institutionalEvents,
                "deadlineTasks" to prefs.deadlineTasks,
                "documents" to prefs.documents, "photos" to prefs.photos
            ))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beállítások", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Vissza") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Account section
            SettingsSection(title = "Fiók") {
                LabeledItem(label = "E-mail", value = userEmail)
                LabeledItem(label = "Szerepkör", value = if (isAdmin) "Adminisztrátor" else "Felhasználó")
            }

            // Password change
            if (!isAdmin) {
                SettingsSection(title = "Jelszó módosítása") {
                    OutlinedTextField(
                        value = currentPassword, onValueChange = { currentPassword = it },
                        label = { Text("Jelenlegi jelszó") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = newPassword, onValueChange = { newPassword = it },
                        label = { Text("Új jelszó") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = repeatPassword, onValueChange = { repeatPassword = it },
                        label = { Text("Új jelszó újra") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (feedbackMsg.isNotBlank()) Text(feedbackMsg, color = Color(0xFF34C759), fontSize = 14.sp)
                    if (errorMsg.isNotBlank()) Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    Button(
                        onClick = {
                            errorMsg = ""; feedbackMsg = ""
                            if (newPassword.length < 8) { errorMsg = "Az új jelszó legalább 8 karakter legyen."; return@Button }
                            if (newPassword != repeatPassword) { errorMsg = "Az új jelszavak nem egyeznek."; return@Button }
                            val user = auth.currentUser ?: return@Button
                            isSaving = true
                            user.reauthenticate(EmailAuthProvider.getCredential(userEmail, currentPassword))
                                .addOnSuccessListener {
                                    user.updatePassword(newPassword)
                                        .addOnSuccessListener {
                                            feedbackMsg = "A jelszó sikeresen módosítva."
                                            currentPassword = ""; newPassword = ""; repeatPassword = ""
                                            isSaving = false
                                        }
                                        .addOnFailureListener { e -> errorMsg = e.message ?: "Hiba."; isSaving = false }
                                }
                                .addOnFailureListener { e -> errorMsg = e.message ?: "Helytelen jelszó."; isSaving = false }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSaving && currentPassword.isNotBlank() && newPassword.isNotBlank() && repeatPassword.isNotBlank()
                    ) { Text(if (isSaving) "Mentés..." else "Jelszó módosítása") }
                }
            }

            // Notification preferences
            SettingsSection(title = "Értesítések") {
                NotifToggle("Programok", notifPrefs.programs) {
                    notifPrefs = notifPrefs.copy(programs = it); saveNotifPrefs(notifPrefs)
                }
                NotifToggle("Adatszolgáltatás", notifPrefs.dataSheets) {
                    notifPrefs = notifPrefs.copy(dataSheets = it); saveNotifPrefs(notifPrefs)
                }
                NotifToggle("Önéletrajzok", notifPrefs.resumes) {
                    notifPrefs = notifPrefs.copy(resumes = it); saveNotifPrefs(notifPrefs)
                }
                NotifToggle("Kereslet–Kínálat", notifPrefs.marketplace) {
                    notifPrefs = notifPrefs.copy(marketplace = it); saveNotifPrefs(notifPrefs)
                }
                NotifToggle("Esemény értesítők", notifPrefs.institutionalEvents) {
                    notifPrefs = notifPrefs.copy(institutionalEvents = it); saveNotifPrefs(notifPrefs)
                }
                if (isAdmin) {
                    NotifToggle("Határidős feladatok", notifPrefs.deadlineTasks) {
                        notifPrefs = notifPrefs.copy(deadlineTasks = it); saveNotifPrefs(notifPrefs)
                    }
                }
                NotifToggle("Dokumentumok", notifPrefs.documents) {
                    notifPrefs = notifPrefs.copy(documents = it); saveNotifPrefs(notifPrefs)
                }
                NotifToggle("Média", notifPrefs.photos) {
                    notifPrefs = notifPrefs.copy(photos = it); saveNotifPrefs(notifPrefs)
                }
            }

            // Widget preferences (admin only)
            if (isAdmin) {
                SettingsSection(title = "Widget") {
                    NotifToggle("Esemény", widgetShowEvents) {
                        widgetShowEvents = it
                        prefs.edit().putBoolean("widgetShowEvents", it).apply()
                    }
                    NotifToggle("Határidős feladat", widgetShowTasks) {
                        widgetShowTasks = it
                        prefs.edit().putBoolean("widgetShowTasks", it).apply()
                    }
                }
            }

            // App info
            SettingsSection(title = "Alkalmazás") {
                LabeledItem(label = "Verzió", value = "1.5")
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { authViewModel.logout() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Kijelentkezés", fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NotifToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content)
        }
    }
}

@Composable
private fun LabeledItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
