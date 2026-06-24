package hu.szabonorbert.mokusors.model

import java.util.Date

data class EventAttachment(
    val url: String,
    val originalName: String,
    val fileType: String
) {
    val label: String get() = when {
        fileType == "application/pdf" -> "PDF"
        "word" in fileType -> "DOC"
        "excel" in fileType || "spreadsheet" in fileType -> "XLS"
        "powerpoint" in fileType || "presentation" in fileType -> "PPT"
        else -> "FILE"
    }
}

data class CalendarEvent(
    val id: String = "",
    val firestoreID: String = "",
    val title: String = "",
    val date: Date = Date(),
    val endDate: Date = Date(),
    val allDay: Boolean = false,
    val note: String = "",
    val location: String = "",
    val eventType: EventType = EventType.NONE,
    val visibleToUsers: Boolean = false,
    val hasTodoList: Boolean = false,
    val dtkParticipationDone: Boolean = false,
    val kkPermissionDone: Boolean = false,
    val pressInviteDone: Boolean = false,
    val phBackgroundDone: Boolean = false,
    val cateringDone: Boolean = false,
    val giftsDone: Boolean = false,
    val certificateDone: Boolean = false,
    val activities: List<String> = emptyList(),
    val vacationPeople: List<String> = emptyList(),
    val pdfUrl: String = "",
    val attachments: List<EventAttachment> = emptyList(),
    val sourceCollection: String = "events"
) {
    val isVacation get() = eventType == EventType.VACATION

    val activeActivities get() = activities.ifEmpty {
        listOf("dtk", "kk", "press", "ph", "catering", "gifts", "certificate")
    }

    val completedTaskCount get(): Int {
        if (!hasTodoList || isVacation) return 0
        return listOfNotNull(
            if (activeActivities.contains("dtk") && dtkParticipationDone) true else null,
            if (activeActivities.contains("kk") && kkPermissionDone) true else null,
            if (activeActivities.contains("press") && pressInviteDone) true else null,
            if (activeActivities.contains("ph") && phBackgroundDone) true else null,
            if (activeActivities.contains("catering") && cateringDone) true else null,
            if (activeActivities.contains("gifts") && giftsDone) true else null,
            if (activeActivities.contains("certificate") && certificateDone) true else null,
        ).size
    }

    val totalTaskCount get() = if (hasTodoList && !isVacation) activeActivities.size else 0
}

enum class EventType {
    NONE, GUEST, SPEECH, VACATION;

    companion object {
        fun fromFirestore(eventType: String?, eventKind: String?): EventType {
            if (eventType == "vacation") return VACATION
            if (eventKind == "guest") return GUEST
            if (eventKind == "speech") return SPEECH
            return NONE
        }
    }
}
