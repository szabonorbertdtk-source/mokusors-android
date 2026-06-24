package hu.szabonorbert.mokusors.ui.calendar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import hu.szabonorbert.mokusors.model.CalendarEvent
import hu.szabonorbert.mokusors.model.EventType
import hu.szabonorbert.mokusors.ui.theme.AppColors
import hu.szabonorbert.mokusors.ui.theme.LocalAppColors
import hu.szabonorbert.mokusors.viewmodel.EventViewModel
import hu.szabonorbert.mokusors.viewmodel.MenuSettings
import java.text.SimpleDateFormat
import java.util.*

private val monthTitleFmt = SimpleDateFormat("yyyy. MMMM", Locale("hu"))
private val dayHeaderFmt = SimpleDateFormat("yyyy.MM.dd. EEEE", Locale("hu"))
private val timeFmt = SimpleDateFormat("HH:mm", Locale("hu"))
private val holidayFmt = SimpleDateFormat("MM-dd", Locale.US)

private val hungarianHolidays = setOf(
    "01-01","03-15","05-01","08-20","10-23","11-01","12-24","12-25","12-26"
)
private val weekHeaders = listOf("H","K","Sze","Cs","P","Szo","V")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    eventViewModel: EventViewModel,
    onEventClick: (CalendarEvent) -> Unit,
    onSettingsClick: () -> Unit,
    onTasksClick: () -> Unit = {},
    onProgramClick: () -> Unit = {},
    onDataSheetsClick: () -> Unit = {},
    onMarketplaceClick: () -> Unit = {},
    onResumesClick: () -> Unit = {},
    onPhotosClick: () -> Unit = {},
    onDocumentsClick: () -> Unit = {},
    onInventoryClick: () -> Unit = {},
    onAddEventClick: () -> Unit = {}
) {
    val events by eventViewModel.events.collectAsState()
    val selectedDate by eventViewModel.selectedDate.collectAsState()
    val isAdmin by eventViewModel.isAdmin.collectAsState()
    val isLoading by eventViewModel.isLoading.collectAsState()
    val menuSettings by eventViewModel.menuSettings.collectAsState()
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE) }
    val widgetShowEvents = remember { mutableStateOf(prefs.getBoolean("widgetShowEvents", true)) }
    val widgetShowTasks = remember { mutableStateOf(prefs.getBoolean("widgetShowTasks", true)) }

    // Refresh widget prefs when screen is composed (in case Settings changed them)
    LaunchedEffect(Unit) {
        widgetShowEvents.value = prefs.getBoolean("widgetShowEvents", true)
        widgetShowTasks.value = prefs.getBoolean("widgetShowTasks", true)
    }

    // Sheet state for filtered events (status card click)
    var filteredSheetTitle by remember { mutableStateOf("") }
    var filteredSheetEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showFilteredSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { eventViewModel.startListening() }

    val now = Date()
    val selectedDayEvents = remember(events, selectedDate) { eventViewModel.eventsForDay(selectedDate) }
    val redCount = remember(events) {
        events.count { ev ->
            !ev.isVacation && ev.hasTodoList && ev.date > now &&
            ev.activeActivities.contains("kk") && !ev.kkPermissionDone &&
            !(ev.activeActivities.contains("dtk") && ev.dtkParticipationDone)
        }
    }
    val yellowCount = remember(events) {
        events.count { ev ->
            !ev.isVacation && ev.hasTodoList && ev.date > now &&
            ev.activeActivities.contains("kk") && !ev.kkPermissionDone &&
            ev.activeActivities.contains("dtk") && ev.dtkParticipationDone
        }
    }
    val greenCount = remember(events) {
        events.count { ev ->
            !ev.isVacation && ev.hasTodoList && ev.date > now &&
            ev.activeActivities.contains("kk") && ev.kkPermissionDone
        }
    }
    val thisWeekEvents = remember(events) {
        val cal = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY }
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val weekStart = cal.time
        cal.add(Calendar.DAY_OF_YEAR, 6); cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        val weekEnd = cal.time
        events.filter { !it.isVacation && it.date >= weekStart && it.date <= weekEnd }.sortedBy { it.date }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = appColors.statusBlue)
        }
        return
    }

    if (showFilteredSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilteredSheet = false },
            sheetState = sheetState
        ) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text(filteredSheetTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp))
                if (filteredSheetEvents.isEmpty()) {
                    Text("Nincs ilyen esemény.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    filteredSheetEvents.forEach { ev ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showFilteredSheet = false; onEventClick(ev) }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape)
                                .background(statusColor(ev, appColors)))
                            Column {
                                Text(ev.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(dayHeaderFmt.format(ev.date), fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    Scaffold { scaffoldPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MenuBar(
                isAdmin = isAdmin,
                menu = menuSettings,
                appColors = appColors,
                onTasksClick = onTasksClick,
                onProgramClick = onProgramClick,
                onDataSheetsClick = onDataSheetsClick,
                onMarketplaceClick = onMarketplaceClick,
                onResumesClick = onResumesClick,
                onPhotosClick = onPhotosClick,
                onDocumentsClick = onDocumentsClick,
                onInventoryClick = onInventoryClick,
                onSettingsClick = onSettingsClick
            )
        }

        if (isAdmin && widgetShowEvents.value) {
            item {
                StatusCardsRow(
                    red = redCount, yellow = yellowCount, green = greenCount,
                    events = events, now = now,
                    onAddEventClick = onAddEventClick,
                    onCardClick = { title, evList ->
                        filteredSheetTitle = title
                        filteredSheetEvents = evList
                        showFilteredSheet = true
                    }
                )
            }
        }

        if (isAdmin && widgetShowTasks.value) {
            item { WidgetTodayTasksCard() }
        }

        item {
            CalendarCard(
                events = events,
                selectedDate = selectedDate,
                onDateSelected = { eventViewModel.selectDate(it) },
                onEventClick = onEventClick,
                selectedDayEvents = selectedDayEvents,
                isAdmin = isAdmin,
                appColors = appColors
            )
        }

        if (isAdmin) {
            item { WeeklyOverviewCard(thisWeekEvents, onEventClick, appColors) }
        }
    }
    } // end Scaffold
}

// ── Menu bar ──────────────────────────────────────────────────────────────────

@Composable
private fun MenuBar(
    isAdmin: Boolean,
    menu: MenuSettings, appColors: AppColors,
    onTasksClick: () -> Unit, onProgramClick: () -> Unit,
    onDataSheetsClick: () -> Unit, onMarketplaceClick: () -> Unit,
    onResumesClick: () -> Unit, onPhotosClick: () -> Unit,
    onDocumentsClick: () -> Unit, onInventoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isAdmin && menu.tasks)
                MenuBtn(Icons.Default.Checklist, "Feladatok", appColors.statusRed, Modifier.weight(1f), onTasksClick)
            if (menu.registrations)
                MenuBtn(Icons.Default.CalendarMonth, "Program", appColors.statusGreen, Modifier.weight(1f), onProgramClick)
            if (menu.dataSheets)
                MenuBtn(Icons.Default.Description, "Adatszolgáltatás", appColors.statusBlue, Modifier.weight(1f), onDataSheetsClick)
            if (menu.marketplace)
                MenuBtn(Icons.Default.SwapHoriz, "Kereslet-kínálat", Color(0xFFAF52DE), Modifier.weight(1f), onMarketplaceClick)
            if (menu.resumes)
                MenuBtn(Icons.Default.Article, "Önéletrajz", Color(0xFFFF9500), Modifier.weight(1f), onResumesClick)
            if (menu.photos)
                MenuBtn(Icons.Default.Photo, "Média", Color(0xFF30B0C7), Modifier.weight(1f), onPhotosClick)
            if (menu.inventory)
                MenuBtn(Icons.Default.Inventory2, "Leltár", Color(0xFF34C759), Modifier.weight(1f), onInventoryClick)
            if (menu.documents)
                MenuBtn(Icons.Default.Folder, "Backoffice", Color(0xFF5856D6), Modifier.weight(1f), onDocumentsClick)
            MenuBtn(Icons.Default.Settings, "Beállítások", Color(0xFF8E8E93), Modifier.weight(1f), onSettingsClick)
        }
    }
}

@Composable
private fun MenuBtn(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
    }
}

// ── Status cards ──────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusCardsRow(
    red: Int, yellow: Int, green: Int,
    events: List<CalendarEvent>,
    now: Date,
    onAddEventClick: () -> Unit,
    onCardClick: (String, List<CalendarEvent>) -> Unit
) {
    val c = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusCard(
                title = "Engedélyezésre\nvár", count = red, color = c.statusRed,
                modifier = Modifier.weight(1f),
                onClick = {
                    onCardClick("Engedélyezésre vár", events.filter {
                        !it.isVacation && it.hasTodoList && it.date > now &&
                        it.activeActivities.contains("kk") && !it.kkPermissionDone &&
                        !(it.activeActivities.contains("dtk") && it.dtkParticipationDone)
                    })
                }
            )
            StatusCard(
                title = "KK\nengedélyre vár", count = yellow, color = c.statusYellow,
                modifier = Modifier.weight(1f),
                onClick = {
                    onCardClick("KK engedélyezésre vár", events.filter {
                        !it.isVacation && it.hasTodoList && it.date > now &&
                        it.activeActivities.contains("kk") && !it.kkPermissionDone &&
                        it.activeActivities.contains("dtk") && it.dtkParticipationDone
                    })
                }
            )
            StatusCard(
                title = "KK által\nengedélyezve", count = green, color = c.statusGreen,
                modifier = Modifier.weight(1f),
                onClick = {
                    onCardClick("KK által engedélyezve", events.filter {
                        !it.isVacation && it.hasTodoList && it.date > now &&
                        it.activeActivities.contains("kk") && it.kkPermissionDone
                    })
                }
            )
        }
        Button(
            onClick = onAddEventClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.statusBlue)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Új esemény", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusCard(title: String, count: Int, color: Color, modifier: Modifier, onClick: () -> Unit = {}) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f))
        .clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(title.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, lineHeight = 12.sp)
            Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Calendar card ─────────────────────────────────────────────────────────────

@Composable
private fun CalendarCard(
    events: List<CalendarEvent>,
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    selectedDayEvents: List<CalendarEvent>,
    isAdmin: Boolean,
    appColors: AppColors
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply { time = selectedDate }
                    cal.add(Calendar.MONTH, -1)
                    onDateSelected(cal.time)
                }) { Icon(Icons.Default.ChevronLeft, null, tint = appColors.statusBlue) }
                Text(
                    text = monthTitleFmt.format(selectedDate),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply { time = selectedDate }
                    cal.add(Calendar.MONTH, 1)
                    onDateSelected(cal.time)
                }) { Icon(Icons.Default.ChevronRight, null, tint = appColors.statusBlue) }
            }

            MonthGrid(events, selectedDate, onDateSelected, appColors)
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            SelectedDayAgenda(selectedDate, selectedDayEvents, isAdmin, onEventClick, appColors)
        }
    }
}

// ── Month grid ────────────────────────────────────────────────────────────────

@Composable
private fun MonthGrid(
    events: List<CalendarEvent>,
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    appColors: AppColors
) {
    val cal = Calendar.getInstance().apply { time = selectedDate }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val firstDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val today = startOfDay(Date())
    val selectedDay = startOfDay(selectedDate)

    Column {
        Row(Modifier.fillMaxWidth()) {
            weekHeaders.forEach { h ->
                Text(h, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        val rows = ((firstDow + daysInMonth) + 6) / 7
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val idx = row * 7 + col
                    val dayNum = idx - firstDow + 1
                    if (dayNum < 1 || dayNum > daysInMonth) { Box(Modifier.weight(1f).height(76.dp)); continue }

                    val cellDate = makeDate(year, month, dayNum, 0, 0, 0)
                    val cellEnd = makeDate(year, month, dayNum, 23, 59, 59)
                    val dow = Calendar.getInstance().apply { time = cellDate }.get(Calendar.DAY_OF_WEEK)
                    val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
                    val dayEvents = events.filter { event ->
                        if (event.isVacation && isWeekend) return@filter false
                        event.date <= cellEnd && event.endDate >= cellDate
                    }.sortedWith(compareBy({ if (it.isVacation) 1 else 0 }, { it.date }))
                    val isSelected = cellDate == selectedDay
                    val isToday = cellDate == today
                    val isHoliday = hungarianHolidays.contains(holidayFmt.format(cellDate))
                    val blue = appColors.statusBlue

                    Box(
                        modifier = Modifier.weight(1f).height(76.dp)
                            .background(when {
                                isHoliday -> Color(0xFFFF3B30).copy(alpha = 0.06f)
                                isWeekend -> Color(0xFF5856D6).copy(alpha = 0.04f)
                                else -> Color.Transparent
                            })
                            .clickable { onDateSelected(cellDate) }
                            .padding(horizontal = 2.dp, vertical = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(22.dp)
                                    .clip(CircleShape)
                                    .background(when {
                                        isToday -> blue
                                        isSelected -> blue.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    }),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    dayNum.toString(), fontSize = 12.sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isToday -> Color.White
                                        isSelected -> blue
                                        isHoliday -> appColors.statusRed
                                        isWeekend -> Color(0xFF5856D6)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                            dayEvents.take(3).forEach { ev ->
                                Text(
                                    ev.title,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White,
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(statusColor(ev, appColors))
                                        .padding(horizontal = 2.dp, vertical = 1.dp)
                                )
                            }
                            if (dayEvents.size > 3) {
                                Text("+${dayEvents.size - 3} más", fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Selected day agenda ───────────────────────────────────────────────────────

@Composable
private fun SelectedDayAgenda(
    selectedDate: Date, events: List<CalendarEvent>,
    isAdmin: Boolean, onEventClick: (CalendarEvent) -> Unit, appColors: AppColors
) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(dayHeaderFmt.format(selectedDate), fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (events.isEmpty()) {
            Text("Nincs esemény erre a napra.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        } else {
            events.forEach { EventRow(it, onEventClick, isAdmin, appColors) }
        }
    }
}

// ── Weekly overview ───────────────────────────────────────────────────────────

@Composable
private fun WeeklyOverviewCard(events: List<CalendarEvent>, onEventClick: (CalendarEvent) -> Unit, appColors: AppColors) {
    val weekRangeText = remember {
        val fmt = SimpleDateFormat("yyyy. MMMM d.", Locale("hu"))
        val cal = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY; time = Date() }
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val monday = cal.time
        cal.add(Calendar.DAY_OF_YEAR, 6)
        "${fmt.format(monday)} – ${fmt.format(cal.time)}"
    }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Heti összesítő", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(weekRangeText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (events.isEmpty()) {
                Text("Ezen a héten nincs esemény.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            } else {
                events.forEach { EventRow(it, onEventClick, true, appColors) }
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
fun EventRow(
    event: CalendarEvent, onEventClick: (CalendarEvent) -> Unit,
    isAdmin: Boolean, appColors: AppColors, compact: Boolean = false
) {
    val color = statusColor(event, appColors)
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.08f))
            .clickable { onEventClick(event) }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!event.isVacation && !compact) {
                    Text(timeFmt.format(event.date), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (event.location.isNotBlank()) {
                    Text(event.location, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (isAdmin && !compact) {
                    if (event.hasTodoList) Text("${event.completedTaskCount}/${event.totalTaskCount} kész", fontSize = 13.sp, color = color)
                    else Text("Nincs teendőlista", fontSize = 13.sp, color = appColors.statusBlue)
                    if (event.visibleToUsers) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF30B0C7).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Intézmény", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF30B0C7))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) { Column(content = content) }
}

// ── Widget: today's deadline tasks ────────────────────────────────────────────

private data class SimpleTask(val id: String, val title: String, val owner: String, val status: String)

@Composable
private fun WidgetTodayTasksCard() {
    val orange = Color(0xFFFF9500)
    val userEmail = remember { FirebaseAuth.getInstance().currentUser?.email?.lowercase() ?: "" }
    var tasks by remember { mutableStateOf<List<SimpleTask>>(emptyList()) }

    DisposableEffect(userEmail) {
        if (userEmail.isBlank()) return@DisposableEffect onDispose {}
        val today = Calendar.getInstance()
        val day = today.get(Calendar.DAY_OF_MONTH)
        val month = today.get(Calendar.MONTH) + 1
        val year = today.get(Calendar.YEAR)

        val reg: ListenerRegistration = FirebaseFirestore.getInstance()
            .collection("deadlineTasks")
            .whereEqualTo("reminderTargetEmail", userEmail)
            .addSnapshotListener { snap, _ ->
                tasks = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val status = d["status"] as? String ?: "progress"
                    if (status == "done" || status == "irrelevant") return@mapNotNull null
                    val taskDay = (d["day"] as? Long)?.toInt() ?: return@mapNotNull null
                    val taskMonth = (d["month"] as? Long)?.toInt() ?: return@mapNotNull null
                    val taskYear = (d["year"] as? Long)?.toInt() ?: return@mapNotNull null
                    if (taskDay != day || taskMonth != month || taskYear != year) return@mapNotNull null
                    SimpleTask(
                        id = doc.id,
                        title = d["title"] as? String ?: "",
                        owner = d["owner"] as? String ?: "",
                        status = status
                    )
                }
            }
        onDispose { reg.remove() }
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(orange.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("HATÁRIDŐS FELADAT · MA", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = orange)
            if (tasks.isEmpty()) {
                Text("Nincs mai határidős feladat", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            } else {
                tasks.forEach { task ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(orange)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(task.title.ifEmpty { "Névtelen feladat" },
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (task.owner.isNotBlank()) {
                                Text(task.owner, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun statusColor(event: CalendarEvent, appColors: AppColors): Color {
    if (event.isVacation) return appColors.statusPurple
    if (!event.hasTodoList) return if (event.visibleToUsers) appColors.statusBlue else appColors.statusGray
    val hasKK = event.activeActivities.contains("kk")
    val hasDTK = event.activeActivities.contains("dtk")
    if (!hasKK && !hasDTK) return appColors.statusBlue
    if (event.kkPermissionDone) return appColors.statusGreen
    if (hasDTK && event.dtkParticipationDone) return appColors.statusYellow
    return appColors.statusRed
}

private fun startOfDay(date: Date) = Calendar.getInstance().apply {
    time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.time

private fun makeDate(year: Int, month: Int, day: Int, h: Int, m: Int, s: Int) =
    Calendar.getInstance().apply { set(year, month, day, h, m, s); set(Calendar.MILLISECOND, 0) }.time

private val CalendarEvent.isVacation get() = eventType == EventType.VACATION
