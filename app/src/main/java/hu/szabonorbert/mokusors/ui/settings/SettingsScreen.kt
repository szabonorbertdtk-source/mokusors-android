package hu.szabonorbert.mokusors.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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

// User notification preferences — 6 categories matching iOS userNotifItems
data class NotifPrefs(
    val programs: Boolean = true,
    val dataSheets: Boolean = true,
    val resumes: Boolean = true,
    val marketplace: Boolean = true,
    val institutionalEvents: Boolean = true,
    val inventory: Boolean = true
)

// Admin combined menu+notification preference item — mirrors iOS DtkPrefItem
data class AdminPrefItem(
    val id: String,
    val label: String,
    val menuKey: String?,   // null if notifOnly
    val notifKey: String?,  // null if menuOnly
    val notifOnly: Boolean = false,
    val menuOnly: Boolean = false
)

private val adminPrefItems = listOf(
    AdminPrefItem("institutionalEvents", "Esemény (intézményi)",  menuKey = null,           notifKey = "institutionalEvents", notifOnly = true),
    AdminPrefItem("tasks",               "Határidős feladat",     menuKey = "tasks",         notifKey = "deadlineTasks"),
    AdminPrefItem("dataSheets",          "Adatszolgáltatás",      menuKey = "dataSheets",    notifKey = "dataSheets"),
    AdminPrefItem("registrations",       "Program",               menuKey = "registrations", notifKey = "programs"),
    AdminPrefItem("resumes",             "Önéletrajz",            menuKey = "resumes",       notifKey = "resumes"),
    AdminPrefItem("photos",              "Média",                 menuKey = "photos",        notifKey = null,       menuOnly = true),
    AdminPrefItem("documents",           "Dokumentumok",          menuKey = "documents",     notifKey = null,       menuOnly = true),
    AdminPrefItem("marketplace",         "Kereslet–kínálat",      menuKey = "marketplace",   notifKey = "marketplace"),
    AdminPrefItem("inventory",           "Eszköztár",             menuKey = "inventory",     notifKey = "inventory"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    isAdmin: Boolean,
    darkModeOverride: Int = -1,
    onDarkModeChange: (Int) -> Unit = {},
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

    // ── User notification prefs (non-admin) ──────────────────────────────────
    var notifPrefs by remember { mutableStateOf(NotifPrefs()) }

    DisposableEffect(uid, isAdmin) {
        if (uid.isBlank() || isAdmin) return@DisposableEffect onDispose {}
        val reg = db.collection("users").document(uid)
            .collection("settings").document("notifications")
            .addSnapshotListener { snap, _ ->
                val d = snap?.data ?: return@addSnapshotListener
                notifPrefs = NotifPrefs(
                    programs = d["programs"] as? Boolean ?: true,
                    dataSheets = d["dataSheets"] as? Boolean ?: true,
                    resumes = d["resumes"] as? Boolean ?: true,
                    marketplace = d["marketplace"] as? Boolean ?: true,
                    institutionalEvents = d["institutionalEvents"] as? Boolean ?: true,
                    inventory = d["inventory"] as? Boolean ?: true
                )
            }
        onDispose { reg.remove() }
    }

    fun saveNotifPrefs(p: NotifPrefs) {
        if (uid.isBlank() || isAdmin) return
        db.collection("users").document(uid)
            .collection("settings").document("notifications")
            .set(mapOf(
                "programs" to p.programs,
                "dataSheets" to p.dataSheets,
                "resumes" to p.resumes,
                "marketplace" to p.marketplace,
                "institutionalEvents" to p.institutionalEvents,
                "inventory" to p.inventory
            ))
    }

    // ── Admin combined menu+notification prefs ───────────────────────────────
    // Key = AdminPrefItem.id, Value = current enabled state
    var adminPrefs by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var adminPrefsLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(uid, isAdmin) {
        if (uid.isBlank() || !isAdmin) return@LaunchedEffect
        val userRef = db.collection("users").document(uid)
        var menuData: Map<String, Any> = emptyMap()
        var notifData: Map<String, Any> = emptyMap()
        var menuLoaded = false
        var notifLoaded = false

        fun merge() {
            if (!menuLoaded || !notifLoaded) return
            val combined = mutableMapOf<String, Boolean>()
            for (item in adminPrefItems) {
                combined[item.id] = when {
                    item.notifOnly -> notifData[item.notifKey ?: ""] as? Boolean ?: true
                    else -> menuData[item.menuKey ?: ""] as? Boolean ?: (item.id != "documents")
                }
            }
            adminPrefs = combined
            adminPrefsLoaded = true
        }

        userRef.collection("settings").document("menu").get()
            .addOnSuccessListener { snap -> menuData = snap.data ?: emptyMap(); menuLoaded = true; merge() }
            .addOnFailureListener { menuLoaded = true; merge() }

        userRef.collection("settings").document("notifications").get()
            .addOnSuccessListener { snap -> notifData = snap.data ?: emptyMap(); notifLoaded = true; merge() }
            .addOnFailureListener { notifLoaded = true; merge() }
    }

    fun saveAdminPref(item: AdminPrefItem, enabled: Boolean) {
        if (uid.isBlank()) return
        adminPrefs = adminPrefs.toMutableMap().also { it[item.id] = enabled }
        val userRef = db.collection("users").document(uid)
        if (!item.notifOnly && item.menuKey != null) {
            userRef.collection("settings").document("menu")
                .set(mapOf(item.menuKey to enabled), com.google.firebase.firestore.SetOptions.merge())
        }
        if (item.notifKey != null) {
            userRef.collection("settings").document("notifications")
                .set(mapOf(item.notifKey to enabled), com.google.firebase.firestore.SetOptions.merge())
        }
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
            // ── Naptár mód (admin only) ───────────────────────────────────────
            if (isAdmin) {
                SettingsSection(title = "Naptár mód") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(false to "Esemény", true to "Határidős feladat").forEach { (isTask, label) ->
                            val selected = widgetShowTasks == isTask
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable {
                                        widgetShowTasks = isTask
                                        widgetShowEvents = !isTask
                                        prefs.edit()
                                            .putBoolean("widgetShowTasks", isTask)
                                            .putBoolean("widgetShowEvents", !isTask)
                                            .apply()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label, fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        "Esemény nézetben a naptár az eseményeket mutatja. Feladat nézetben a határidős feladatokat és szabadságokat.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Megjelenítés ─────────────────────────────────────────────────
            SettingsSection(title = "Megjelenítés") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Megjelenés", fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(-1 to "Auto", 0 to "Világos", 1 to "Sötét").forEach { (value, label) ->
                            val selected = darkModeOverride == value
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onDarkModeChange(value) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    label, fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Fiók ─────────────────────────────────────────────────────────
            SettingsSection(title = "Fiók") {
                LabeledItem(label = "E-mail", value = userEmail)
                LabeledItem(label = "Szerepkör", value = if (isAdmin) "Adminisztrátor" else "Felhasználó")
            }

            // ── Jelszó módosítása (mindenki) ─────────────────────────────────
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

            // ── Értesítések / Menü ───────────────────────────────────────────
            if (isAdmin) {
                // Admin: combined menu + notification toggles (iOS adminNotifItems minta)
                SettingsSection(title = "Menü és értesítések") {
                    if (!adminPrefsLoaded) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        adminPrefItems.forEach { item ->
                            val enabled = adminPrefs[item.id] ?: (item.id != "documents")
                            val subtitle = when {
                                item.notifOnly -> "Csak értesítés"
                                item.menuOnly -> "Csak menü"
                                else -> "Menü + értesítés"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.label, fontSize = 15.sp)
                                    Text(subtitle, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { saveAdminPref(item, it) }
                                )
                            }
                        }
                    }
                }
            } else {
                // User: 6 notification categories matching iOS userNotifItems
                SettingsSection(title = "Értesítések") {
                    NotifToggle("Esemény (intézményi)", notifPrefs.institutionalEvents) {
                        notifPrefs = notifPrefs.copy(institutionalEvents = it); saveNotifPrefs(notifPrefs)
                    }
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
                    NotifToggle("Eszköztár", notifPrefs.inventory) {
                        notifPrefs = notifPrefs.copy(inventory = it); saveNotifPrefs(notifPrefs)
                    }
                }
            }

            // ── Alkalmazás info ───────────────────────────────────────────────
            SettingsSection(title = "Alkalmazás") {
                val ctx = LocalContext.current
                val versionName = remember {
                    try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" }
                    catch (_: Exception) { "" }
                }
                LabeledItem(label = "Verzió", value = versionName)
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
        Text(
            title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
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
