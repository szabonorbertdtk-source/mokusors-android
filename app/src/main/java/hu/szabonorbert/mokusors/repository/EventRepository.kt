package hu.szabonorbert.mokusors.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import hu.szabonorbert.mokusors.model.CalendarEvent
import hu.szabonorbert.mokusors.model.EventType
import java.util.Date

fun EventType.firestoreEventType() = if (this == EventType.VACATION) "vacation" else "event"
fun EventType.firestoreEventKind() = when (this) {
    EventType.GUEST -> "guest"
    EventType.SPEECH -> "speech"
    else -> "none"
}

class EventRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listener: ListenerRegistration? = null
    private var roleListener: ListenerRegistration? = null

    fun listenToEvents(
        onRoleResolved: (isAdmin: Boolean) -> Unit,
        onEvents: (List<CalendarEvent>) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = auth.currentUser ?: run {
            onRoleResolved(false)
            onEvents(emptyList())
            return
        }

        roleListener?.remove()
        roleListener = db.collection("users").document(user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Hiba")
                    return@addSnapshotListener
                }
                val role = snapshot?.getString("role") ?: ""
                val email = user.email?.lowercase() ?: ""
                val isAdmin = role == "admin" || email == "szabonorbertdtk@gmail.com"
                onRoleResolved(isAdmin)

                listener?.remove()
                val query: Query = if (isAdmin) {
                    db.collection("events")
                } else {
                    db.collection("events").whereEqualTo("visibleToUsers", true)
                }

                listener = query.addSnapshotListener { eventsSnapshot, eventsError ->
                    if (eventsError != null) {
                        onError(eventsError.message ?: "Hiba")
                        return@addSnapshotListener
                    }
                    val events = (eventsSnapshot?.documents ?: emptyList()).mapNotNull { doc ->
                        parseEvent(doc.data ?: return@mapNotNull null, doc.id)
                    }.sortedBy { it.date }
                    onEvents(events)
                }
            }
    }

    fun stopListening() {
        listener?.remove()
        roleListener?.remove()
        listener = null
        roleListener = null
    }

    fun addEvent(
        title: String,
        date: Date,
        endDate: Date?,
        note: String,
        location: String,
        hasTodoList: Boolean,
        activities: List<String>,
        eventType: EventType,
        visibleToUsers: Boolean,
        allDay: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val data = mutableMapOf<String, Any>(
            "title" to title,
            "startsAt" to com.google.firebase.Timestamp(date),
            "allDay" to allDay,
            "note" to note,
            "location" to location,
            "colorName" to "red",
            "hasTodoList" to hasTodoList,
            "eventType" to eventType.firestoreEventType(),
            "eventKind" to eventType.firestoreEventKind(),
            "visibleToUsers" to visibleToUsers,
            "activities" to activities,
            "dtkParticipationDone" to false,
            "kkPermissionDone" to false,
            "pressInviteDone" to false,
            "phBackgroundDone" to false,
            "cateringDone" to false,
            "giftsDone" to false,
            "certificateDone" to false
        )
        if (endDate != null) {
            data["endsAt"] = com.google.firebase.Timestamp(endDate)
        }
        db.collection("events").add(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Hiba") }
    }

    fun addVacation(
        names: List<String>,
        startDate: Date,
        endDate: Date,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val data = mapOf<String, Any>(
            "title" to names.joinToString(", "),
            "startsAt" to com.google.firebase.Timestamp(startDate),
            "endsAt" to com.google.firebase.Timestamp(endDate),
            "allDay" to false,
            "note" to "",
            "location" to "",
            "colorName" to "red",
            "hasTodoList" to false,
            "eventType" to "vacation",
            "eventKind" to "none",
            "visibleToUsers" to false,
            "activities" to emptyList<String>(),
            "vacationPeople" to names,
            "dtkParticipationDone" to false,
            "kkPermissionDone" to false,
            "pressInviteDone" to false,
            "phBackgroundDone" to false,
            "cateringDone" to false,
            "giftsDone" to false,
            "certificateDone" to false
        )
        db.collection("events").add(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Hiba") }
    }

    fun updateEvent(
        firestoreID: String,
        title: String,
        date: Date,
        note: String,
        location: String,
        hasTodoList: Boolean,
        activities: List<String>,
        eventType: EventType,
        visibleToUsers: Boolean,
        allDay: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val data = mapOf<String, Any>(
            "title" to title,
            "startsAt" to com.google.firebase.Timestamp(date),
            "allDay" to allDay,
            "note" to note,
            "location" to location,
            "hasTodoList" to hasTodoList,
            "eventType" to eventType.firestoreEventType(),
            "eventKind" to eventType.firestoreEventKind(),
            "visibleToUsers" to visibleToUsers,
            "activities" to activities
        )
        db.collection("events").document(firestoreID).update(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Hiba") }
    }

    fun deleteEvent(
        firestoreID: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("events").document(firestoreID).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Hiba") }
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
        db.collection("events").document(firestoreID).update(
            mapOf(
                "dtkParticipationDone" to dtkParticipationDone,
                "kkPermissionDone" to kkPermissionDone,
                "pressInviteDone" to pressInviteDone,
                "phBackgroundDone" to phBackgroundDone,
                "cateringDone" to cateringDone,
                "giftsDone" to giftsDone,
                "certificateDone" to certificateDone
            )
        )
    }

    fun duplicateEvent(
        event: CalendarEvent,
        newDate: Date,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val data = mutableMapOf<String, Any>(
            "title" to event.title,
            "startsAt" to com.google.firebase.Timestamp(newDate),
            "allDay" to event.allDay,
            "note" to event.note,
            "location" to event.location,
            "colorName" to "red",
            "hasTodoList" to event.hasTodoList,
            "eventType" to event.eventType.firestoreEventType(),
            "eventKind" to event.eventType.firestoreEventKind(),
            "visibleToUsers" to event.visibleToUsers,
            "activities" to event.activities,
            "dtkParticipationDone" to false,
            "kkPermissionDone" to false,
            "pressInviteDone" to false,
            "phBackgroundDone" to false,
            "cateringDone" to false,
            "giftsDone" to false,
            "certificateDone" to false
        )
        db.collection("events").add(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Hiba") }
    }

    private fun parseEvent(data: Map<String, Any>, docId: String): CalendarEvent? {
        val title = data["title"] as? String ?: return null

        val startDate = (data["startsAt"] as? com.google.firebase.Timestamp)?.toDate()
            ?: parsePartsDate(data) ?: return null

        val endDate = (data["endsAt"] as? com.google.firebase.Timestamp)?.toDate()
            ?: parseEndPartsDate(data) ?: startDate

        return CalendarEvent(
            id = docId,
            firestoreID = docId,
            title = title,
            date = startDate,
            endDate = endDate,
            allDay = data["allDay"] as? Boolean ?: false,
            note = data["note"] as? String ?: "",
            location = data["location"] as? String ?: "",
            eventType = EventType.fromFirestore(
                data["eventType"] as? String,
                data["eventKind"] as? String
            ),
            visibleToUsers = data["visibleToUsers"] as? Boolean ?: false,
            hasTodoList = data["hasChecklist"] as? Boolean ?: data["hasTodoList"] as? Boolean ?: false,
            dtkParticipationDone = data["dtkParticipationDone"] as? Boolean ?: data["dtk"] as? Boolean ?: false,
            kkPermissionDone = data["kkPermissionDone"] as? Boolean ?: data["kk"] as? Boolean ?: false,
            pressInviteDone = data["pressInviteDone"] as? Boolean ?: data["press"] as? Boolean ?: false,
            phBackgroundDone = data["phBackgroundDone"] as? Boolean ?: data["ph"] as? Boolean ?: false,
            cateringDone = data["cateringDone"] as? Boolean ?: data["catering"] as? Boolean ?: false,
            giftsDone = data["giftsDone"] as? Boolean ?: data["gifts"] as? Boolean ?: false,
            certificateDone = data["certificateDone"] as? Boolean ?: data["certificate"] as? Boolean ?: false,
            activities = (data["activities"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            vacationPeople = (data["vacationPeople"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            pdfUrl = data["pdfName"] as? String ?: data["pdfFileName"] as? String ?: "",
            sourceCollection = "events"
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePartsDate(data: Map<String, Any>): Date? {
        val year = (data["year"] as? Long)?.toInt() ?: return null
        val month = (data["month"] as? Long)?.toInt() ?: return null
        val day = (data["day"] as? Long)?.toInt() ?: return null
        val time = data["time"] as? String ?: "00:00"
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return java.util.Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.time
    }

    private fun parseEndPartsDate(data: Map<String, Any>): Date? {
        val endDay = (data["endDay"] as? Long)?.toInt() ?: return null
        val year = ((data["endYear"] as? Long)?.toInt()) ?: (data["year"] as? Long)?.toInt() ?: return null
        val month = ((data["endMonth"] as? Long)?.toInt()) ?: (data["month"] as? Long)?.toInt() ?: return null
        return java.util.Calendar.getInstance().apply {
            set(year, month - 1, endDay, 23, 59, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.time
    }
}
