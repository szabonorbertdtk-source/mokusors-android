package hu.szabonorbert.mokusors.ui.event

import hu.szabonorbert.mokusors.model.EventType

internal val activityKeys = listOf("dtk", "kk", "press", "ph", "catering", "gifts", "certificate")
internal val activityLabels = mapOf(
    "dtk" to "DTK részvétel",
    "kk" to "KK engedély",
    "press" to "Sajtómeghívó",
    "ph" to "PH háttéranyag",
    "catering" to "Catering",
    "gifts" to "Ajándékok (virág, könyv)",
    "certificate" to "Oklevél / emléklap"
)
internal val eventTypeOptions = listOf(
    EventType.NONE to "Nincs",
    EventType.GUEST to "Vendég",
    EventType.SPEECH to "Köszöntőbeszéd",
    EventType.VACATION to "Szabadság"
)
