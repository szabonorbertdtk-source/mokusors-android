package hu.szabonorbert.mokusors.ui.calendar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import hu.szabonorbert.mokusors.util.HungarianCalendar
import hu.szabonorbert.mokusors.viewmodel.EventViewModel
import hu.szabonorbert.mokusors.viewmodel.MenuSettings
import java.text.SimpleDateFormat
import java.util.*

private val monthTitleFmt = SimpleDateFormat("yyyy. MMMM", Locale("hu"))
private val dayHeaderFmt = SimpleDateFormat("yyyy.MM.dd. EEEE", Locale("hu"))
private val timeFmt = SimpleDateFormat("HH:mm", Locale("hu"))

// HungarianCalendar utility imported from util package
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

    // Non-admin stat counters
    var openDataSheetsCount by remember { mutableIntStateOf(0) }
    var availableProgramsCount by remember { mutableIntStateOf(0) }

    DisposableEffect(isAdmin) {
        if (isAdmin) return@DisposableEffect onDispose {}
        val db = FirebaseFirestore.getInstance()
        val r1 = db.collection("dataSheets").addSnapshotListener { snap, _ ->
            openDataSheetsCount = snap?.documents?.count { doc ->
                doc.getBoolean("deleted") != true &&
                (doc.getString("status")?.lowercase() ?: "open") == "open"
            } ?: 0
        }
        val r2 = db.collection("registrationEvents").addSnapshotListener { snap, _ ->
            availableProgramsCount = snap?.documents?.count { doc ->
                doc.getBoolean("deleted") != true
            } ?: 0
        }
        onDispose { r1.remove(); r2.remove() }
    }

    // All tasks listener (task mode calendar)
    var allTasks by remember { mutableStateOf<List<SimpleTask>>(emptyList()) }
    DisposableEffect(isAdmin) {
        if (!isAdmin) { allTasks = emptyList(); return@DisposableEffect onDispose {} }
        val reg = FirebaseFirestore.getInstance().collection("deadlineTasks")
            .addSnapshotListener { snap, _ ->
                allTasks = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val status = d["status"] as? String ?: "progress"
                    if (status == "irrelevant") return@mapNotNull null
                    SimpleTask(
                        id = doc.id, title = d["title"] as? String ?: "",
                        owner = d["owner"] as? String ?: "", status = status,
                        year = (d["year"] as? Long)?.toInt() ?: 0,
                        month = (d["month"] as? Long)?.toInt() ?: 0,
                        day = (d["day"] as? Long)?.toInt() ?: 0,
                        completedDate = d["completedDate"] as? String ?: "",
                        completedTime = d["completedTime"] as? String ?: ""
                    )
                }
            }
        onDispose { reg.remove() }
    }

    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            kotlinx.coroutines.delay(cal.time.time - System.currentTimeMillis())
            now = Date()
        }
    }
    val selectedDayEvents = remember(events, selectedDate) { eventViewModel.eventsForDay(selectedDate) }
    val redCount = remember(events, now) {
        events.count { ev ->
            !ev.isVacation && ev.hasTodoList && ev.date > now &&
            ev.activeActivities.contains("kk") && !ev.kkPermissionDone &&
            !(ev.activeActivities.contains("dtk") && ev.dtkParticipationDone)
        }
    }
    val yellowCount = remember(events, now) {
        events.count { ev ->
            !ev.isVacation && ev.hasTodoList && ev.date > now &&
            ev.activeActivities.contains("kk") && !ev.kkPermissionDone &&
            ev.activeActivities.contains("dtk") && ev.dtkParticipationDone
        }
    }
    val greenCount = remember(events, now) {
        events.count { ev ->
            !ev.isVacation && ev.hasTodoList && ev.date > now &&
            ev.activeActivities.contains("kk") && ev.kkPermissionDone
        }
    }
    val thisWeekEvents = remember(events, now) {
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

        if (!isAdmin) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UserStatCard(
                        value = "$openDataSheetsCount",
                        label = "Nyitott adatszolgáltatás",
                        color = Color(0xFF007AFF),
                        modifier = Modifier.weight(1f)
                    )
                    UserStatCard(
                        value = "$availableProgramsCount",
                        label = "Elérhető program",
                        color = Color(0xFF34C759),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (isAdmin && !widgetShowTasks.value) {
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

        if (isAdmin) {
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
                appColors = appColors,
                widgetShowTasks = isAdmin && widgetShowTasks.value,
                allTasks = allTasks
            )
        }

        if (isAdmin) {
            item {
                WeeklyOverviewCard(
                    thisWeekEvents, onEventClick, appColors,
                    widgetShowTasks = widgetShowTasks.value,
                    allTasks = allTasks
                )
            }
        }
    }
    } // end Scaffold
}

// ── User stat cards (non-admin) ───────────────────────────────────────────────

@Composable
private fun UserStatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Black, color = color)
            Text(
                label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
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
                MenuBtn(Icons.Default.Inventory2, "Eszköztár", Color(0xFF34C759), Modifier.weight(1f), onInventoryClick)
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
    Box(modifier = modifier
        .clip(RoundedCornerShape(16.dp))
        .background(color.copy(alpha = 0.10f))
        .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
    ) {
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
    appColors: AppColors,
    widgetShowTasks: Boolean = false,
    allTasks: List<SimpleTask> = emptyList()
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

            MonthGrid(events, selectedDate, onDateSelected, appColors, widgetShowTasks, allTasks)
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            SelectedDayAgenda(selectedDate, selectedDayEvents, isAdmin, onEventClick, appColors, widgetShowTasks, allTasks)
        }
    }
}

// ── Month grid ────────────────────────────────────────────────────────────────

@Composable
private fun MonthGrid(
    events: List<CalendarEvent>,
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    appColors: AppColors,
    widgetShowTasks: Boolean = false,
    allTasks: List<SimpleTask> = emptyList()
) {
    val cal = Calendar.getInstance().apply { time = selectedDate }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val firstDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val today = startOfDay(Date())
    val selectedDay = startOfDay(selectedDate)

    val dayEventsMap = remember(events, year, month) {
        val map = HashMap<Int, List<CalendarEvent>>(daysInMonth * 2)
        for (dayNum in 1..daysInMonth) {
            val cellDate = makeDate(year, month, dayNum, 0, 0, 0)
            val cellEnd = makeDate(year, month, dayNum, 23, 59, 59)
            val dow = Calendar.getInstance().apply { time = cellDate }.get(Calendar.DAY_OF_WEEK)
            val transferred = HungarianCalendar.isTransferredWorkday(cellDate)
            val weekend = (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) && !transferred
            val nonWorking = weekend || HungarianCalendar.isHoliday(cellDate)
            map[dayNum] = events.filter { ev ->
                if (ev.isVacation && nonWorking) return@filter false
                ev.date <= cellEnd && ev.endDate >= cellDate
            }.sortedWith(compareBy({ if (it.isVacation) 1 else 0 }, { it.date }))
        }
        map
    }

    // Task mode: vacations per day
    val dayVacationsMap = remember(events, year, month) {
        val map = HashMap<Int, List<CalendarEvent>>(daysInMonth * 2)
        for (dayNum in 1..daysInMonth) {
            val cellDate = makeDate(year, month, dayNum, 0, 0, 0)
            val cellEnd = makeDate(year, month, dayNum, 23, 59, 59)
            val dow = Calendar.getInstance().apply { time = cellDate }.get(Calendar.DAY_OF_WEEK)
            val transferred = HungarianCalendar.isTransferredWorkday(cellDate)
            val weekend = (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) && !transferred
            val nonWorking = weekend || HungarianCalendar.isHoliday(cellDate)
            map[dayNum] = if (nonWorking) emptyList() else events.filter { ev ->
                ev.isVacation && ev.date <= cellEnd && ev.endDate >= cellDate
            }
        }
        map
    }

    // Task mode: tasks per day (month+1 because tasks store 1-based month)
    val dayTasksMap = remember(allTasks, year, month) {
        val map = HashMap<Int, List<SimpleTask>>(daysInMonth * 2)
        for (dayNum in 1..daysInMonth) {
            map[dayNum] = allTasks.filter {
                it.year == year && it.month == (month + 1) && it.day == dayNum
            }
        }
        map
    }

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
                    val dow = Calendar.getInstance().apply { time = cellDate }.get(Calendar.DAY_OF_WEEK)
                    val isTransferredWorkday = HungarianCalendar.isTransferredWorkday(cellDate)
                    val isWeekend = (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) && !isTransferredWorkday
                    val isHoliday = HungarianCalendar.isHoliday(cellDate)
                    val dayEvents = if (widgetShowTasks) dayVacationsMap[dayNum] ?: emptyList()
                                    else dayEventsMap[dayNum] ?: emptyList()
                    val dayTasks = if (widgetShowTasks) dayTasksMap[dayNum] ?: emptyList()
                                   else emptyList()
                    val isSelected = cellDate == selectedDay
                    val isToday = cellDate == today
                    val blue = appColors.statusBlue
                    val orange = Color(0xFFFF9500)

                    val maxPills = 3
                    // Tasks first, vacations below
                    val visibleTasks = dayTasks.take(maxPills)
                    val visibleEvents = dayEvents.take(maxPills - visibleTasks.size)
                    val hiddenCount = maxOf(0, dayTasks.size - visibleTasks.size) + maxOf(0, dayEvents.size - visibleEvents.size)

                    Box(
                        modifier = Modifier.weight(1f).height(76.dp)
                            .background(when {
                                isHoliday -> Color(0xFFFF3B30).copy(alpha = 0.06f)
                                isTransferredWorkday -> Color(0xFF34C759).copy(alpha = 0.05f)
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
                                        isTransferredWorkday -> Color(0xFF34C759)
                                        isWeekend -> Color(0xFF5856D6)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                            // Tasks first (urgency colors), then vacations below
                            visibleTasks.forEach { task ->
                                Text(
                                    task.title.ifEmpty { "Feladat" },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White,
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(taskUrgencyColor(task))
                                        .padding(horizontal = 2.dp, vertical = 1.dp)
                                        .alpha(if (task.status == "done") 0.65f else 1f)
                                )
                            }
                            visibleEvents.forEach { ev ->
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
                            if (hiddenCount > 0) {
                                Text("+$hiddenCount más", fontSize = 8.sp,
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
    isAdmin: Boolean, onEventClick: (CalendarEvent) -> Unit, appColors: AppColors,
    widgetShowTasks: Boolean = false, allTasks: List<SimpleTask> = emptyList()
) {
    val cal = Calendar.getInstance().apply { time = selectedDate }
    val selYear = cal.get(Calendar.YEAR)
    val selMonth = cal.get(Calendar.MONTH) + 1
    val selDay = cal.get(Calendar.DAY_OF_MONTH)

    val displayEvents = if (widgetShowTasks) events.filter { it.isVacation } else events
    val dayTasks = if (widgetShowTasks) allTasks.filter {
        it.year == selYear && it.month == selMonth && it.day == selDay &&
        it.status != "irrelevant"
    } else emptyList()

    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(dayHeaderFmt.format(selectedDate), fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (displayEvents.isEmpty() && dayTasks.isEmpty()) {
            Text(
                if (widgetShowTasks) "Nincs feladat vagy szabadság erre a napra."
                else "Nincs esemény erre a napra.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp
            )
        } else {
            // Tasks first, vacations below
            dayTasks.forEach { task ->
                val isDone = task.status == "done"
                val overdue = isTaskOverdue(task)
                val color = taskUrgencyColor(task)
                val icon = when {
                    isDone -> Icons.Default.CheckCircle
                    overdue -> Icons.Default.Error
                    else -> Icons.Default.RadioButtonUnchecked
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .alpha(if (overdue) 0.6f else 1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = icon, contentDescription = null,
                        tint = color, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            task.title.ifEmpty { "Névtelen feladat" },
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            textDecoration = if (overdue) TextDecoration.LineThrough else TextDecoration.None
                        )
                        if (task.owner.isNotBlank()) {
                            Text(task.owner, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isDone && task.completedDate.isNotEmpty()) {
                            Text(
                                "Kész: ${task.completedDate.replace("-", ".")} ${task.completedTime}",
                                fontSize = 12.sp, color = color
                            )
                        }
                    }
                }
            }
            displayEvents.forEach { EventRow(it, onEventClick, isAdmin, appColors) }
        }
    }
}

// ── Weekly overview ───────────────────────────────────────────────────────────

@Composable
private fun WeeklyOverviewCard(
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit,
    appColors: AppColors,
    widgetShowTasks: Boolean = false,
    allTasks: List<SimpleTask> = emptyList()
) {
    val weekRangeText = remember {
        val fmt = SimpleDateFormat("yyyy. MMMM d.", Locale("hu"))
        val cal = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY; time = Date() }
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val monday = cal.time
        cal.add(Calendar.DAY_OF_YEAR, 6)
        "${fmt.format(monday)} – ${fmt.format(cal.time)}"
    }

    val weekTasks = remember(allTasks) {
        val cal = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY; time = Date() }
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val weekStart = cal.time
        cal.add(Calendar.DAY_OF_YEAR, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        val weekEnd = cal.time
        allTasks.filter { task ->
            if (task.status == "irrelevant") return@filter false
            val d = makeDate(task.year, task.month - 1, task.day, 0, 0, 0)
            d >= weekStart && d <= weekEnd
        }.sortedWith(compareBy({ it.year }, { it.month }, { it.day }))
    }

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (widgetShowTasks) "Heti feladatok" else "Heti összesítő",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(weekRangeText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (widgetShowTasks) {
                if (weekTasks.isEmpty()) {
                    Text("Ezen a héten nincs határidős feladat.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                } else {
                    weekTasks.forEach { task -> WeeklyTaskRow(task) }
                }
            } else {
                if (events.isEmpty()) {
                    Text("Ezen a héten nincs esemény.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                } else {
                    val now = remember { Date() }
                    events.forEach { event ->
                        EventRow(event, onEventClick, true, appColors, isPast = event.date.before(now))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyTaskRow(task: SimpleTask) {
    val isDone = task.status == "done"
    val overdue = isTaskOverdue(task)
    val color = taskUrgencyColor(task)
    Row(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (overdue) 0.55f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                task.title.ifEmpty { "Névtelen feladat" },
                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                textDecoration = if (overdue) TextDecoration.LineThrough else TextDecoration.None
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (task.owner.isNotBlank()) {
                    Text(task.owner, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isDone && task.completedDate.isNotEmpty()) {
                    Text(
                        "Kész: ${task.completedDate.replace("-", ".")} ${task.completedTime}",
                        fontSize = 13.sp, color = color
                    )
                } else {
                    Text(
                        "%d.%02d.%02d.".format(task.year, task.month, task.day),
                        fontSize = 13.sp, color = color
                    )
                }
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
fun EventRow(
    event: CalendarEvent, onEventClick: (CalendarEvent) -> Unit,
    isAdmin: Boolean, appColors: AppColors, compact: Boolean = false,
    isPast: Boolean = false
) {
    val color = if (isPast) Color(0xFF8E8E93) else statusColor(event, appColors)
    val bgAlpha = if (event.isVacation) 0.10f else 0.08f
    Box(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (isPast) 0.45f else 1.0f)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = bgAlpha))
            .clickable { onEventClick(event) }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(11.dp).clip(CircleShape).background(color))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isPast) TextDecoration.LineThrough else TextDecoration.None)
                if (!event.isVacation && !compact) {
                    Text(timeFmt.format(event.date), fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (event.location.isNotBlank()) {
                    Text(event.location, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (event.note.isNotBlank() && !compact) {
                    Text(event.note, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                if (isAdmin && !compact) {
                    if (event.hasTodoList) {
                        ChecklistBadges(event)
                    }
                    if (event.visibleToUsers) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF30B0C7).copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("Intézmény", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF30B0C7))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChecklistBadges(event: CalendarEvent) {
    val items = event.activeActivities.map { key ->
        when (key) {
            "dtk"         -> Pair("Részvétel",        event.dtkParticipationDone)
            "kk"          -> Pair("KK engedély",      event.kkPermissionDone)
            "press"       -> Pair("Sajtómeghívó",     event.pressInviteDone)
            "ph"          -> Pair("PH háttéranyag",   event.phBackgroundDone)
            "catering"    -> Pair("Catering",         event.cateringDone)
            "gifts"       -> Pair("Ajándékok",        event.giftsDone)
            "certificate" -> Pair("Oklevél / emléklap", event.certificateDone)
            else          -> Pair(key,                false)
        }
    }
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (label, done) ->
            val tint = if (done) Color(0xFF34C759) else Color(0xFFFF3B30)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tint.copy(alpha = 0.10f))
                    .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(12.dp)
                )
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint)
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

private val widgetOwnerEmailMap: Map<String, String> = mapOf(
    "Buti Attila" to "attila.buti@kk.gov.hu",
    "Dr. Asztalos Zsuzsa" to "zsuzsa.asztalos@kk.gov.hu",
    "Dr. Csehi Roland" to "roland.csehi@kk.gov.hu",
    "Dr. Vántus Andrásné" to "andrasne.vantus@kk.gov.hu",
    "Leiterné dr. Tóth Katalin" to "katalin.toth.leiterne@kk.gov.hu",
    "Máté Gábor" to "gabor.mate@kk.gov.hu",
    "Riskó Orsolya" to "orsolya.risko@kk.gov.hu",
    "Szabó Norbert" to "norbert.szabo@kk.gov.hu",
    "Zoványi Erika" to "erika.zovanyi@kk.gov.hu"
)

private data class SimpleTask(
    val id: String, val title: String, val owner: String, val status: String,
    val year: Int = 0, val month: Int = 0, val day: Int = 0,
    val completedDate: String = "", val completedTime: String = ""
)

@Composable
private fun WidgetTodayTasksCard() {
    val orange = Color(0xFFFF9500)
    val userEmail = remember { FirebaseAuth.getInstance().currentUser?.email?.lowercase() ?: "" }
    var tasks by remember { mutableStateOf<List<SimpleTask>>(emptyList()) }

    DisposableEffect(userEmail) {
        if (userEmail.isBlank()) return@DisposableEffect onDispose {}

        fun parseTask(doc: com.google.firebase.firestore.DocumentSnapshot): SimpleTask? {
            val d = doc.data ?: return null
            val status = d["status"] as? String ?: "progress"
            if (status == "done" || status == "irrelevant") return null
            val taskDay = (d["day"] as? Long)?.toInt() ?: return null
            val taskMonth = (d["month"] as? Long)?.toInt() ?: return null
            val taskYear = (d["year"] as? Long)?.toInt() ?: return null
            return SimpleTask(id = doc.id, title = d["title"] as? String ?: "",
                owner = d["owner"] as? String ?: "", status = status,
                year = taskYear, month = taskMonth, day = taskDay)
        }

        val db = FirebaseFirestore.getInstance()
        val r1 = db.collection("deadlineTasks")
            .whereEqualTo("reminderTargetEmail", userEmail)
            .addSnapshotListener { snap, _ ->
                val primary = (snap?.documents ?: emptyList()).mapNotNull { parseTask(it) }
                tasks = (tasks.filter { t -> primary.none { it.id == t.id } } + primary)
                    .distinctBy { it.id }
                    .sortedWith(compareBy({ it.year }, { it.month }, { it.day }))
            }
        val ownerName = widgetOwnerEmailMap.entries.firstOrNull { it.value == userEmail }?.key
        val r2 = if (ownerName != null) {
            db.collection("deadlineTasks")
                .whereEqualTo("owner", ownerName)
                .addSnapshotListener { snap, _ ->
                    val legacy = (snap?.documents ?: emptyList()).mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        if ((d["reminderTargetEmail"] as? String).isNullOrBlank()) parseTask(doc)
                        else null
                    }
                    tasks = (tasks.filter { t -> legacy.none { it.id == t.id } } + legacy)
                        .distinctBy { it.id }
                        .sortedWith(compareBy({ it.year }, { it.month }, { it.day }))
                }
        } else null
        onDispose { r1.remove(); r2?.remove() }
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(orange.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SAJÁT HATÁRIDŐS FELADATOK", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = orange)
            if (tasks.isEmpty()) {
                Text("Nincs aktív határidős feladat", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            } else {
                tasks.forEach { task ->
                    val overdue = isTaskOverdue(task)
                    val color = taskUrgencyColor(task)
                    Row(
                        modifier = Modifier.alpha(if (overdue) 0.5f else 1f),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(color))
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                task.title.ifEmpty { "Névtelen feladat" },
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                textDecoration = if (overdue) TextDecoration.LineThrough else TextDecoration.None
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (task.owner.isNotBlank()) {
                                    Text(task.owner, fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    "%d.%02d.%02d.".format(task.year, task.month, task.day),
                                    fontSize = 12.sp, color = color.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun taskUrgencyColor(task: SimpleTask): Color {
    if (task.status == "done") return Color(0xFF34C759)
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val deadline = Calendar.getInstance().apply {
        set(task.year, task.month - 1, task.day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }
    val days = (deadline.timeInMillis - today.timeInMillis) / 86_400_000L
    return when {
        days < 0  -> Color(0xFFBF0000)
        days == 0L -> Color(0xFFF22020)
        days <= 3 -> Color(0xFFFF9500)
        else -> Color(0xFF3399FF)
    }
}

private fun isTaskOverdue(task: SimpleTask): Boolean {
    if (task.status == "done" || task.status == "irrelevant") return false
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val deadline = Calendar.getInstance().apply {
        set(task.year, task.month - 1, task.day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }
    return deadline.before(today)
}

fun statusColor(event: CalendarEvent, appColors: AppColors): Color {
    if (event.isVacation) return appColors.statusPurple
    if (event.eventType == EventType.PRIVATE) return Color(0xFFFF9500)
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

