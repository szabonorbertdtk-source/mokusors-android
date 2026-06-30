package hu.szabonorbert.mokusors

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.firestore.FirebaseFirestore
import hu.szabonorbert.mokusors.BuildConfig
import hu.szabonorbert.mokusors.model.CalendarEvent
import hu.szabonorbert.mokusors.service.AppNotificationManager
import hu.szabonorbert.mokusors.service.MokusorsFirebaseMessagingService
import hu.szabonorbert.mokusors.ui.calendar.CalendarScreen
import hu.szabonorbert.mokusors.ui.datasheets.DataSheetsScreen
import hu.szabonorbert.mokusors.ui.documents.DocumentsScreen
import hu.szabonorbert.mokusors.ui.event.AddEventScreen
import hu.szabonorbert.mokusors.ui.event.EditEventScreen
import hu.szabonorbert.mokusors.ui.event.EventDetailScreen
import hu.szabonorbert.mokusors.ui.inventory.InventoryScreen
import hu.szabonorbert.mokusors.ui.login.LoginScreen
import hu.szabonorbert.mokusors.ui.marketplace.MarketplaceScreen
import hu.szabonorbert.mokusors.ui.photos.PhotosScreen
import hu.szabonorbert.mokusors.ui.registrations.RegistrationsScreen
import hu.szabonorbert.mokusors.ui.resumes.ResumesScreen
import hu.szabonorbert.mokusors.ui.settings.SettingsScreen
import hu.szabonorbert.mokusors.ui.tasks.TasksScreen
import hu.szabonorbert.mokusors.ui.theme.MokusorsTheme
import hu.szabonorbert.mokusors.viewmodel.AuthState
import hu.szabonorbert.mokusors.viewmodel.AuthViewModel
import hu.szabonorbert.mokusors.viewmodel.EventViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — token still registered */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        setContent {
            val prefs = remember { getSharedPreferences("widget_prefs", Context.MODE_PRIVATE) }
            val systemDark = isSystemInDarkTheme()
            var darkModeOverride by remember { mutableStateOf(prefs.getInt("dark_mode_override", -1)) }
            val isDarkTheme = when (darkModeOverride) {
                0 -> false
                1 -> true
                else -> systemDark
            }
            MokusorsTheme(darkTheme = isDarkTheme) {
                MokusorsApp(
                    isDarkMode = isDarkTheme,
                    darkModeOverride = darkModeOverride,
                    onDarkModeChange = { override ->
                        darkModeOverride = override
                        prefs.edit().putInt("dark_mode_override", override).apply()
                    },
                    onRoleResolved = { isAdmin ->
                        AppNotificationManager.start(this, isAdmin)
                        MokusorsFirebaseMessagingService.registerToken(this)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppNotificationManager.stop()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

fun compareVersions(a: String, b: String): Int {
    val aParts = a.trim().split(".").map { it.toIntOrNull() ?: 0 }
    val bParts = b.trim().split(".").map { it.toIntOrNull() ?: 0 }
    val len = maxOf(aParts.size, bParts.size)
    for (i in 0 until len) {
        val diff = (aParts.getOrElse(i) { 0 }) - (bParts.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}

@Composable
fun MokusorsApp(
    isDarkMode: Boolean = false,
    darkModeOverride: Int = -1,
    onDarkModeChange: (Int) -> Unit = {},
    onRoleResolved: (Boolean) -> Unit = { _ -> }
) {
    val authViewModel: AuthViewModel = viewModel()
    val eventViewModel: EventViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()
    val isAdmin by eventViewModel.isAdmin.collectAsState()
    val adminResolved by eventViewModel.adminResolved.collectAsState()
    val navController = rememberNavController()
    var selectedEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var eventToEdit by remember { mutableStateOf<CalendarEvent?>(null) }
    var updateRequired by remember { mutableStateOf(false) }
    var updateStoreUrl by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(adminResolved, isAdmin) {
        if (adminResolved) onRoleResolved(isAdmin)
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) {
            FirebaseFirestore.getInstance()
                .collection("systemSettings").document("androidVersion")
                .get()
                .addOnSuccessListener { snap ->
                    val minVersion = snap.getString("minVersion") ?: return@addOnSuccessListener
                    val storeUrl = snap.getString("storeUrl") ?: return@addOnSuccessListener
                    if (minVersion.isBlank()) return@addOnSuccessListener
                    if (compareVersions(BuildConfig.VERSION_NAME, minVersion) < 0) {
                        updateStoreUrl = storeUrl
                        updateRequired = true
                    }
                }
        }
    }

    if (updateRequired) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Frissítés szükséges") },
            text = { Text("Az alkalmazás egy régebbi verzióját használod. A folytatáshoz telepítsd a legújabb verziót a Play Store-ból.") },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateStoreUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }) { Text("Frissítés") }
            }
        )
        return
    }

    when (authState) {
        is AuthState.Loading -> {}
        is AuthState.LoggedOut, is AuthState.Error -> LoginScreen(authViewModel)
        is AuthState.LoggedIn -> {
            NavHost(navController, startDestination = "calendar") {
                composable("calendar") {
                    CalendarScreen(
                        eventViewModel = eventViewModel,
                        onEventClick = { selectedEvent = it; navController.navigate("event_detail") },
                        onSettingsClick = { navController.navigate("settings") },
                        onTasksClick = { navController.navigate("tasks") },
                        onProgramClick = { navController.navigate("registrations") },
                        onDataSheetsClick = { navController.navigate("datasheets") },
                        onMarketplaceClick = { navController.navigate("marketplace") },
                        onResumesClick = { navController.navigate("resumes") },
                        onPhotosClick = { navController.navigate("photos") },
                        onDocumentsClick = { navController.navigate("documents") },
                        onInventoryClick = { navController.navigate("inventory") },
                        onAddEventClick = { navController.navigate("add_event") }
                    )
                }
                composable("event_detail") {
                    selectedEvent?.let { event ->
                        EventDetailScreen(
                            event = event,
                            eventViewModel = eventViewModel,
                            onBack = { navController.popBackStack() },
                            onEditEvent = { ev ->
                                eventToEdit = ev
                                navController.navigate("edit_event")
                            }
                        )
                    }
                }
                composable("add_event") {
                    AddEventScreen(
                        eventViewModel = eventViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("edit_event") {
                    eventToEdit?.let { ev ->
                        EditEventScreen(
                            event = ev,
                            eventViewModel = eventViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable("settings") {
                    SettingsScreen(
                        authViewModel = authViewModel,
                        isAdmin = isAdmin,
                        darkModeOverride = darkModeOverride,
                        onDarkModeChange = onDarkModeChange,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("tasks") {
                    TasksScreen(isAdmin = isAdmin) { navController.popBackStack() }
                }
                composable("registrations") {
                    RegistrationsScreen(isAdmin = isAdmin) { navController.popBackStack() }
                }
                composable("datasheets") { DataSheetsScreen(isAdmin = isAdmin) { navController.popBackStack() } }
                composable("marketplace") { MarketplaceScreen(isAdmin = isAdmin) { navController.popBackStack() } }
                composable("resumes") {
                    ResumesScreen(isAdmin = isAdmin) { navController.popBackStack() }
                }
                composable("photos") { PhotosScreen { navController.popBackStack() } }
                composable("documents") {
                    DocumentsScreen(isAdmin = isAdmin) { navController.popBackStack() }
                }
                composable("inventory") {
                    InventoryScreen(isAdmin = isAdmin) { navController.popBackStack() }
                }
            }
        }
    }
}
