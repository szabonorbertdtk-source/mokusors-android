package hu.szabonorbert.mokusors.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import hu.szabonorbert.mokusors.model.CalendarEvent
import hu.szabonorbert.mokusors.model.EventType
import hu.szabonorbert.mokusors.repository.EventRepository
import hu.szabonorbert.mokusors.util.HungarianCalendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.Date

data class MenuSettings(
    val tasks: Boolean = true,
    val registrations: Boolean = true,
    val dataSheets: Boolean = true,
    val marketplace: Boolean = true,
    val resumes: Boolean = true,
    val photos: Boolean = true,
    val documents: Boolean = false,
    val inventory: Boolean = true
)

class EventViewModel : ViewModel() {

    private val repository = EventRepository()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var menuListener: ListenerRegistration? = null

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    private val _adminResolved = MutableStateFlow(false)
    val adminResolved: StateFlow<Boolean> = _adminResolved

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _selectedDate = MutableStateFlow(today())
    val selectedDate: StateFlow<Date> = _selectedDate

    private val _menuSettings = MutableStateFlow(MenuSettings())
    val menuSettings: StateFlow<MenuSettings> = _menuSettings

    private var hasStartedMenuListener = false

    fun startListening() {
        repository.listenToEvents(
            onRoleResolved = { isAdmin ->
                _isAdmin.value = isAdmin
                _adminResolved.value = true
                if (!hasStartedMenuListener) {
                    hasStartedMenuListener = true
                    startMenuSettingsListener(isAdmin)
                }
            },
            onEvents = { events ->
                _events.value = events
                _isLoading.value = false
            },
            onError = { _isLoading.value = false }
        )
    }

    private fun startMenuSettingsListener(isAdmin: Boolean) {
        menuListener?.remove()
        val uid = auth.currentUser?.uid ?: return
        val isRegularUser = !isAdmin

        if (isRegularUser) {
            menuListener = db.collection("systemSettings").document("userMenu")
                .addSnapshotListener { snap, _ ->
                    val s = (snap?.data?.get("settings") as? Map<*, *>) ?: emptyMap<String, Any>()
                    _menuSettings.value = MenuSettings(
                        tasks = s["tasks"] as? Boolean ?: false,
                        registrations = s["registrations"] as? Boolean ?: true,
                        dataSheets = s["dataSheets"] as? Boolean ?: true,
                        marketplace = s["marketplace"] as? Boolean ?: true,
                        resumes = s["resumes"] as? Boolean ?: true,
                        photos = s["photos"] as? Boolean ?: true,
                        documents = s["documents"] as? Boolean ?: false,
                        inventory = s["inventory"] as? Boolean ?: true
                    )
                }
        } else {
            // Admin: read menu visibility from settings/menu (same keys as iOS)
            menuListener = db.collection("users").document(uid)
                .collection("settings").document("menu")
                .addSnapshotListener { snap, _ ->
                    val d = snap?.data ?: emptyMap<String, Any>()
                    _menuSettings.value = MenuSettings(
                        tasks = d["tasks"] as? Boolean ?: true,
                        registrations = d["registrations"] as? Boolean ?: true,
                        dataSheets = d["dataSheets"] as? Boolean ?: true,
                        marketplace = d["marketplace"] as? Boolean ?: true,
                        resumes = d["resumes"] as? Boolean ?: true,
                        photos = d["photos"] as? Boolean ?: true,
                        documents = d["documents"] as? Boolean ?: false,
                        inventory = d["inventory"] as? Boolean ?: true
                    )
                }
        }
    }

    fun selectDate(date: Date) {
        _selectedDate.value = startOfDay(date)
    }

    fun eventsForDay(date: Date): List<CalendarEvent> {
        val isNonWorking = HungarianCalendar.isNonWorkingDay(date)
        val dayStart = startOfDay(date)
        val dayEnd = endOfDay(date)
        return _events.value
            .filter { event ->
                if (event.isVacation && isNonWorking) return@filter false
                event.date <= dayEnd && event.endDate >= dayStart
            }
            .sortedWith(compareBy({ if (it.isVacation) 1 else 0 }, { it.date }))
    }

    fun upcomingEvents(days: Int = 30): List<CalendarEvent> {
        val now = Date()
        val future = Calendar.getInstance().apply { time = now; add(Calendar.DAY_OF_YEAR, days) }.time
        return _events.value.filter { !it.isVacation && it.date > now && it.date <= future }
            .sortedBy { it.date }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun addEvent(
        title: String,
        date: Date,
        endDate: Date?,
        note: String,
        location: String,
        organizer: String = "",
        hasTodoList: Boolean,
        activities: List<String>,
        eventType: EventType,
        visibleToUsers: Boolean,
        allDay: Boolean,
        pdfUrl: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        repository.addEvent(
            title, date, endDate, note, location, organizer, hasTodoList, activities,
            eventType, visibleToUsers, allDay, pdfUrl, onSuccess, onError
        )
    }

    fun addVacation(
        names: List<String>,
        startDate: Date,
        endDate: Date,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        repository.addVacation(names, startDate, endDate, onSuccess, onError)
    }

    fun updateEvent(
        firestoreID: String,
        title: String,
        date: Date,
        note: String,
        location: String,
        organizer: String = "",
        hasTodoList: Boolean,
        activities: List<String>,
        eventType: EventType,
        visibleToUsers: Boolean,
        allDay: Boolean,
        pdfUrl: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        repository.updateEvent(
            firestoreID, title, date, note, location, organizer, hasTodoList, activities,
            eventType, visibleToUsers, allDay, pdfUrl, onSuccess, onError
        )
    }

    fun deleteEvent(
        firestoreID: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        repository.deleteEvent(firestoreID, onSuccess, onError)
    }

    fun updateChecklist(
        firestoreID: String,
        dtkParticipationDone: Boolean,
        kkPermissionDone: Boolean,
        pressInviteDone: Boolean,
        phBackgroundDone: Boolean,
        cateringDone: Boolean,
        giftsDone: Boolean,
        certificateDone: Boolean
    ) {
        // Optimistic local update
        _events.value = _events.value.map { event ->
            if (event.firestoreID == firestoreID) {
                event.copy(
                    dtkParticipationDone = dtkParticipationDone,
                    kkPermissionDone = kkPermissionDone,
                    pressInviteDone = pressInviteDone,
                    phBackgroundDone = phBackgroundDone,
                    cateringDone = cateringDone,
                    giftsDone = giftsDone,
                    certificateDone = certificateDone
                )
            } else event
        }
        // Persist to Firestore
        repository.updateChecklist(
            firestoreID,
            dtkParticipationDone, kkPermissionDone, pressInviteDone,
            phBackgroundDone, cateringDone, giftsDone, certificateDone
        )
    }

    fun duplicateEvent(
        event: CalendarEvent,
        newDate: Date,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        repository.duplicateEvent(event, newDate, onSuccess, onError)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
        menuListener?.remove()
    }

    private fun today() = startOfDay(Date())

    private fun startOfDay(date: Date) = Calendar.getInstance().apply {
        time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time

    private fun endOfDay(date: Date) = Calendar.getInstance().apply {
        time = date; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.time

    // CalendarEvent.isVacation is a member property of CalendarEvent
}
