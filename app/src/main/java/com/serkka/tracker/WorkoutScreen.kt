@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.serkka.tracker

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun WavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    isPlaying: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = "progress"
    )

    Box(modifier = modifier.height(12.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2
            drawLine(
                color = trackColor,
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width * animatedProgress.coerceIn(0f, 1f)
            if (width <= 0f) return@Canvas

            val centerY = size.height / 2
            val amplitude = if (isPlaying) 1.dp.toPx() else 0f
            val wavelength = 50.dp.toPx()

            val path = Path().apply {
                val startY = if (isPlaying) centerY + amplitude * sin(waveOffset) else centerY
                moveTo(0f, startY)
                if (isPlaying && amplitude > 0f) {
                    var x = 0f
                    while (x < width) {
                        val y = centerY + amplitude * sin(x * (2 * Math.PI.toFloat() / wavelength) + waveOffset)
                        lineTo(x, y)
                        x += 2f
                    }
                } else {
                    lineTo(width, centerY)
                }
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun ElasticColumnWrapper(
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }
}


/** One full turn of the music widget's album art while a song plays. */
private const val ALBUM_ART_TURN_MS = 20_000

/**
 * Size of the music widget's album art, and of the music app button that sits in its place while the
 * widget is hidden. Change this one number for both (keep it under MUSIC_WIDGET_HEIGHT).
 */
private val ALBUM_ART_SIZE = 44.dp

/** Height of the music widget; its launch button sits in a frame this size, where the album art is. */
private val MUSIC_WIDGET_HEIGHT = 64.dp

/** Gap left of the album art, equal to the gap above and below it, so it sits evenly in the pill's end. */
private val ALBUM_ART_INSET = (MUSIC_WIDGET_HEIGHT - ALBUM_ART_SIZE) / 2

@SuppressLint("UseKtx")
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    themeViewModel: ThemeViewModel = viewModel(),
    timerViewModel: WorkoutTimerViewModel = viewModel(),
    subscriptionViewModel: SubscriptionViewModel = viewModel(),
    stepsViewModel: StepsViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun showUndoSnackbar(message: String, onUndo: () -> Unit) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
    val isSubscribed by subscriptionViewModel.isSubscribed.collectAsState()
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    val ctx = LocalContext.current
    val trackerPrefs = remember { PreferencesManager.getInstance(ctx).tracker }
    var weightCardVisible by remember { mutableStateOf(trackerPrefs.getBoolean("weight_card_visible", true)) }
    val setWeightCardVisible: (Boolean) -> Unit = { visible ->
        weightCardVisible = visible
        trackerPrefs.edit().putBoolean("weight_card_visible", visible).apply()
    }

    LaunchedEffect(Unit) {
        subscriptionViewModel.refreshStatus()
    }
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    // Swipeable screens are hosted in a HorizontalPager under a single NavHost
    // destination ("swipe_host"). Non-swipe routes (Settings, Notes, etc.)
    // remain as individual NavHost composables.
    val swipeScreens = remember {
        // The four main pages plus More (Stats, Calendar and Sessions open from there: moreRoutes)
        listOf(
            Screen.WorkoutTimer.name,
            Screen.Workouts.name,
            Screen.Summary.name,
            Screen.WeightTracking.name,
            Screen.More.name
        )
    }
    val pagerHostRoute = "swipe_host"
    // Pages opened from the top bar icons; they drop in from the top (NavHost transitions)
    val topBarRoutes = remember { setOf(Screen.Tools.name, Screen.Notes.name, Screen.Settings.name) }
    val pagerState = rememberPagerState(
        initialPage = swipeScreens.indexOf(Screen.Summary.name),
        pageCount = { swipeScreens.size }
    )
    // True while the user is touching the weight chart, so the pager won't page mid-gesture.
    var chartInteracting by remember { mutableStateOf(false) }

    val currentRoute = when (val r = currentBackStackEntry?.destination?.route) {
        pagerHostRoute, null -> swipeScreens[pagerState.currentPage]
        else -> r
    }

    val workoutsListState = rememberLazyListState()
    val summaryListState  = rememberLazyListState()
    val weightListState   = rememberLazyListState()
    val notesListState    = rememberLazyListState()
    val toolsListState    = rememberLazyListState()
    val sessionsListState = rememberLazyListState()
    val calendarListState = rememberLazyListState()

    var searchQuery          by remember { mutableStateOf("") }
    var searchExpanded       by remember { mutableStateOf(false) }

    // Dragging the Workouts list while searching puts the keyboard away so results are visible.
    // Only the keyboard: clearing focus would close the search (the field closes on focus loss).
    val keyboardController = LocalSoftwareKeyboardController.current
    val workoutsDragged by workoutsListState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(workoutsDragged) {
        if (workoutsDragged && searchExpanded) {
            keyboardController?.hide()
        }
    }

    // Back closes an open search instead of reaching the activity's "Exit app?" handler. Declared
    // after (inside) that one, so it takes priority while enabled.
    BackHandler(enabled = searchExpanded) {
        searchQuery = ""
        searchExpanded = false
    }

    // Leaving Workouts closes its search
    LaunchedEffect(currentRoute) {
        if (currentRoute != Screen.Workouts.name) {
            searchExpanded = false
            searchQuery = ""
        }
    }


    val workouts        by viewModel.allWorkouts.collectAsState()

    // Jump the Workouts list to the top once a just-added entry arrives (copy/duplicate).
    // requestScrollToItem beats LazyColumn's keep-position-by-key behaviour, which would
    // otherwise hold the old first row in view and leave the new entry above it.
    var scrollWorkoutsToTopOnChange by remember { mutableStateOf(false) }

    // PB "peak moment" banner (PbCelebration.kt); null = hidden
    var pbCelebration by remember { mutableStateOf<PbCelebrationData?>(null) }

    // In-app updater: check GitHub for a newer release at most once a day (AppUpdater.kt)
    // Update check each time the app comes to the screen (AppUpdater throttles it to once a day)
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        AppUpdater.autoCheck(ctx)
        onPauseOrDispose { }
    }
    LaunchedEffect(workouts) {
        if (scrollWorkoutsToTopOnChange) {
            workoutsListState.requestScrollToItem(0)
            scrollWorkoutsToTopOnChange = false
        }
    }
    val bodyWeights     by viewModel.allBodyWeights.collectAsState()
    val notesList       by viewModel.allNotes.collectAsState()
    val workoutSessions by viewModel.allSessions.collectAsState()
    val scheduleItems   by viewModel.allScheduleItems.collectAsState()

    val workoutHistory = remember(workouts) {
        workouts.asSequence()
            .sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.id })
            .distinctBy { it.exerciseName }
            .toList()
    }

    var showAddWorkoutDialog by remember { mutableStateOf(false) }
    var showAddWeightDialog  by remember { mutableStateOf(false) }
    var showAddNoteDialog    by remember { mutableStateOf(false) }
    var showAddSessionDialog by remember { mutableStateOf(false) }
    var editingWorkout       by remember { mutableStateOf<Workout?>(null) }
    var copyingWorkout       by remember { mutableStateOf<Workout?>(null) }
    var editingWeight        by remember { mutableStateOf<BodyWeight?>(null) }
    var editingNote          by remember { mutableStateOf<Note?>(null) }
    var workoutToDelete      by remember { mutableStateOf<Workout?>(null) }
    var weightToDelete       by remember { mutableStateOf<BodyWeight?>(null) }
    var noteToDelete         by remember { mutableStateOf<Note?>(null) }
    var sessionToDelete      by remember { mutableStateOf<WorkoutSession?>(null) }
    var sessionToEdit        by remember { mutableStateOf<WorkoutSession?>(null) }
    // Settings → Music: the one app the music widget follows (MusicApps.kt)
    val musicApp by MusicApps.selected(LocalContext.current).collectAsState()

    val currentSong    by MediaRepository.getInstance().currentSong.collectAsState()
    val timerIsRunning by timerViewModel.isRunning.collectAsState()
    val timerElapsed   by timerViewModel.elapsedSeconds.collectAsState()
    val primaryColor   = rememberAccentColor(themeViewModel)   // ThemeViewModel.kt (Auto + rainbow)

    // User-dismissed flag for the music widget — auto-resets when a song starts playing.
    // Declared here so both the NavHost (timer screen padding) and the widget gate use it.
    val musicDismissed by MediaRepository.getInstance().widgetHidden.collectAsState()

    fun navigate(route: String) {
        if (route in swipeScreens) {
            val targetPage = swipeScreens.indexOf(route)
            if (navController.currentDestination?.route != pagerHostRoute) {
                navController.navigate(pagerHostRoute) {
                    popUpTo(pagerHostRoute) { inclusive = false }
                    launchSingleTop = true
                }
            }
            coroutineScope.launch {
                pagerState.animateScrollToPage(targetPage, animationSpec = tween(Motion.MEDIUM))
            }
        } else {
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
    }


    run {
        Scaffold(
            topBar = {},
            bottomBar = {},
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = 100.dp)
                )
            },
            contentWindowInsets = WindowInsets(0)
        ) { innerPadding ->
            val topBarBaseHeight = 55.dp
            val statusBarHeight = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
            val totalTopPadding = topBarBaseHeight + statusBarHeight
            // Room under the last item for the bottom bar plus the music widget (or its launch
            // button) above it, so the end of every list can scroll clear of them.
            val contentBottomPadding = 164.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {

            // Haptic when the pager settles on a new page (matches the old swipe-to-navigate feel)
            var lastSettledPage by remember { mutableIntStateOf(pagerState.currentPage) }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    if (page != lastSettledPage) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        lastSettledPage = page
                    }
                }
            }

            // Page area: a sheet with rounded top corners under the solid top bar (same style as
            // Overtime). One sheet for every page, so swiping and page transitions happen inside it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = totalTopPadding)
                    .clip(TrackerShapes.Sheet)
                    .background(MaterialTheme.colorScheme.background)
            ) {
            NavHost(
                navController = navController,
                startDestination = pagerHostRoute,
                modifier = Modifier.fillMaxSize(),
                // The swipe pages live in one destination (swipe_host); the pages opened from the
                // top bar (Tools, Notes, Settings) drop in from the top with a fade, and slide back
                // up as they close. Everything else just cross-fades.
                enterTransition = {
                    if (targetState.destination.route in topBarRoutes)
                        slideInVertically(animationSpec = tween(Motion.MEDIUM, easing = LinearOutSlowInEasing)) { -it / 8 } +
                            fadeIn(animationSpec = tween(Motion.MEDIUM))
                    else fadeIn(animationSpec = tween(Motion.MEDIUM))
                },
                exitTransition = {
                    val leavingTopBarPage = initialState.destination.route in topBarRoutes &&
                        targetState.destination.route !in topBarRoutes
                    if (leavingTopBarPage)
                        slideOutVertically(animationSpec = tween(Motion.SHORT, easing = FastOutLinearInEasing)) { -it / 8 } +
                            fadeOut(animationSpec = tween(Motion.SHORT))
                    else fadeOut(animationSpec = tween(Motion.SHORT))
                }
            ) {
                composable(pagerHostRoute) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = !chartInteracting,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            snapAnimationSpec = tween(Motion.SHORT)
                        )
                    ) { page ->
                        // Lets entrance animations (chart draw-in, calendar, steps) start when the page is shown
                        CompositionLocalProvider(LocalPageVisible provides (pagerState.currentPage == page)) {
                        when (swipeScreens[page]) {
                            Screen.WorkoutTimer.name -> {
                                val musicVisible = currentSong.title != null &&
                                    currentSong.packageName == musicApp.packageName &&
                                    !musicDismissed
                                WorkoutTimerScreen(
                                    timerViewModel  = timerViewModel,
                                    bottomPadding   = if (musicVisible) 88.dp else 0.dp,
                                    topPadding = 0.dp,
                                    onSaveLocally = { name, type, startMs, duration, hrSamples ->
                                        // "End" moment: short summary banner (same component as the PB one)
                                        pbCelebration = workoutSavedSummary(duration, workouts, workoutSessions)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.addWorkoutSession(name, type, startMs, duration, hrSamples)
                                        navigate(Screen.Sessions.name)   // show the saved session
                                    }
                                )
                            }
                            Screen.Workouts.name -> {
                                ElasticColumnWrapper {
                                    val filteredWorkouts = remember(workouts, searchQuery) {
                                        if (searchQuery.isBlank()) workouts
                                        else workouts.filter {
                                            it.exerciseName.contains(searchQuery, ignoreCase = true)
                                        }
                                    }
                                    WorkoutListContent(
                                        workouts = filteredWorkouts,
                                        primaryColor = primaryColor,
                                        onDelete  = { workoutToDelete = it },
                                        onEdit    = { editingWorkout = it },
                                        // Copy / Duplicate from search results also closes the search,
                                        // so you land back on the full list with the new entry on top.
                                        onCopy    = {
                                            copyingWorkout = it
                                            searchQuery = ""
                                            searchExpanded = false
                                            workoutsListState.requestScrollToItem(0)
                                        },
                                        onDuplicate = { w ->
                                            searchQuery = ""
                                            searchExpanded = false
                                            workoutsListState.requestScrollToItem(0)
                                            scrollWorkoutsToTopOnChange = true
                                            viewModel.addWorkout(
                                                w.exerciseName, w.sets, w.reps, w.weight,
                                                System.currentTimeMillis(), w.isPersonalBest, w.weightUnit, w.notes, w.rpe
                                            )
                                        },
                                        onTogglePB = { w ->
                                            if (!w.isPersonalBest) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                pbCelebration = pbCelebrationFor(w.exerciseName, w.sets, w.reps, w.weight, w.weightUnit, workouts, excludeId = w.id)
                                            }
                                            viewModel.updateWorkout(w.copy(isPersonalBest = !w.isPersonalBest))
                                        },
                                        listState = workoutsListState,
                                        topPadding = 0.dp,
                                        bottomPadding = contentBottomPadding,
                                        searchQuery = searchQuery,
                                        searchOpen = searchExpanded,
                                        onPickSearch = { searchQuery = it },
                                        // Pull down at the top of the list to open search (thumb-friendly)
                                        onPullToSearch = if (searchExpanded) null else { { searchExpanded = true } }
                                    )
                                }
                            }
                            Screen.Summary.name -> {
                                ElasticColumnWrapper {
                                    SummaryPage(
                                        workouts = workouts,
                                        bodyWeights = bodyWeights,
                                        workoutSessions = workoutSessions,
                                        stepsViewModel = stepsViewModel,
                                        primaryColor = primaryColor,
                                        onNavigateToWeightTracking = { navigate(Screen.WeightTracking.name) },
                                        onNavigateToSessions = { navigate(Screen.Sessions.name) },
                                        listState = summaryListState,
                                        topPadding = 0.dp,
                                        bottomPadding = contentBottomPadding,
                                        weightCardVisible = weightCardVisible,
                                        onHideWeightCard = { setWeightCardVisible(false) }
                                    )
                                }
                            }
                            Screen.WeightTracking.name -> {
                                ElasticColumnWrapper {
                                    WeightTrackingPage(
                                        bodyWeights = bodyWeights,
                                        primaryColor = primaryColor,
                                        onWeightClick  = { editingWeight = it },
                                        onWeightDelete = { weightToDelete = it },
                                        listState = weightListState,
                                        topPadding = 0.dp,
                                        bottomPadding = contentBottomPadding,
                                        onChartTouch = { chartInteracting = it }
                                    )
                                }
                            }
                            Screen.More.name -> {
                                MorePage(
                                    primaryColor = primaryColor,
                                    topPadding = 0.dp,
                                    bottomPadding = contentBottomPadding,
                                    onOpen = { navigate(it) }
                                )
                            }
                        }
                        }
                    }
                }
                // Opened from the More tab (moreRoutes); back returns to it
                composable(Screen.WorkoutStats.name) {
                    WorkoutStatsPage(workouts, primaryColor, topPadding = 0.dp, bottomPadding = contentBottomPadding)
                }
                composable(Screen.Calendar.name) {
                    CalendarPage(
                        workoutSessions = workoutSessions,
                        workouts = workouts,
                        primaryColor = primaryColor,
                        topPadding = 0.dp,
                        bottomPadding = contentBottomPadding,
                        listState = calendarListState
                    )
                }
                composable(Screen.Sessions.name) {
                    ElasticColumnWrapper {
                        SessionsPage(
                            sessions = workoutSessions,
                            primaryColor = primaryColor,
                            onDelete = { sessionToDelete = it },
                            onEdit = { sessionToEdit = it },
                            listState = sessionsListState,
                            topPadding = 0.dp,
                            bottomPadding = contentBottomPadding
                        )
                    }
                }
                composable(Screen.Notes.name) {
                    ElasticColumnWrapper {
                        NotesPage(
                            notes = notesList,
                            primaryColor = primaryColor,
                            onNoteClick  = { editingNote = it },
                            onNoteDelete = { noteToDelete = it },
                            listState = notesListState,
                            topPadding = 0.dp,
                            bottomPadding = contentBottomPadding
                        )
                    }
                }
                composable(Screen.Tools.name) {
                    ElasticColumnWrapper {
                        ToolsPage(
                            scheduleItems = scheduleItems,
                            primaryColor = primaryColor,
                            onAddScheduleItem = { viewModel.addScheduleItem(it) },
                            onToggleScheduleItem = { viewModel.toggleScheduleItem(it) },
                            onRenameScheduleItem = { item, text -> viewModel.renameScheduleItem(item, text) },
                            onDeleteScheduleItem = { viewModel.deleteScheduleItem(it) },
                            onResetSchedule = { viewModel.resetSchedule() },
                            workouts = workouts,
                            onRenameExercise = { oldName, newName ->
                                // Keep a Workout Stats "hidden" mark attached to the renamed exercise
                                val hiddenBefore = trackerPrefs.getStringSet(PREF_HIDDEN_STATS_EXERCISES, null)?.toSet() ?: emptySet()
                                if (oldName in hiddenBefore) {
                                    trackerPrefs.edit().putStringSet(PREF_HIDDEN_STATS_EXERCISES, hiddenBefore - oldName + newName).apply()
                                }
                                viewModel.renameExercise(oldName, newName) { ids ->
                                    showUndoSnackbar("Renamed \"$oldName\" → \"$newName\" (${ids.size})") {
                                        viewModel.undoRenameExercise(ids, oldName)
                                        trackerPrefs.edit().putStringSet(PREF_HIDDEN_STATS_EXERCISES, hiddenBefore).apply()
                                    }
                                }
                            },
                            listState = toolsListState,
                            topPadding = 0.dp,
                            bottomPadding = contentBottomPadding
                        )
                    }
                }
                composable(Screen.Settings.name) {
                    SettingsPage(
                        primaryColor = primaryColor,
                        themeViewModel = themeViewModel,
                        viewModel = viewModel,
                        topPadding = 0.dp,
                        bottomPadding = contentBottomPadding,
                        isSubscribed = isSubscribed,
                        onSubscribe = { showSubscriptionDialog = true },
                        onRecheckWhitelist = { subscriptionViewModel.recheckWhitelist() }
                    )
                }
            }
            }

            // Page background; the bottom bar shades from it.
            val bgColor = MaterialTheme.colorScheme.background

            // ── Top Bar ───────────────────────────────────────────────────────
            // Solid, in the colour the page sheet's rounded corners sit on.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .statusBarsPadding()
                    .height(topBarBaseHeight)
            ) {
                TopAppBar(
                    title = {
                        if (searchExpanded && currentRoute == Screen.Workouts.name) {
                            val focusRequester = remember { FocusRequester() }
                            var hadFocus by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search workouts…") },
                                singleLine = true,
                                leadingIcon = {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        searchExpanded = false
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                                    }
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                shape = MaterialTheme.shapes.large,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryColor,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 8.dp)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { state ->
                                        if (state.isFocused) {
                                            hadFocus = true
                                        } else if (hadFocus) {
                                            searchQuery = ""
                                            searchExpanded = false
                                        }
                                    },
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                modifier = Modifier.padding(start = 8.dp),
                                text = Screen.entries.find { it.name == currentRoute }?.title ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    windowInsets = WindowInsets(0),
                    actions = {
                        if (searchExpanded && currentRoute == Screen.Workouts.name) return@TopAppBar
                        AnimatedVisibility(
                            visible = currentRoute == Screen.Settings.name,
                            enter   = fadeIn() + slideInHorizontally { it },
                            exit    = fadeOut() + slideOutHorizontally { it }
                        ) {
                            val showWelcomeInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = {
                                    ctx.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().remove("welcome_skip").apply()
                                    val pm = ctx.packageManager
                                    val launchIntent = pm.getLaunchIntentForPackage(ctx.packageName)
                                    launchIntent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(launchIntent)
                                    (ctx as? Activity)?.finish()
                                },
                                interactionSource = showWelcomeInteractionSource,
                                modifier = Modifier.size(42.dp).bounceClick(showWelcomeInteractionSource)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Show welcome screen", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        val stepsCardVisible by stepsViewModel.isCardVisible.collectAsState()
                        AnimatedVisibility(
                            visible = currentRoute == Screen.Summary.name && stepsViewModel.isAvailable && !stepsCardVisible,
                            enter   = fadeIn() + slideInHorizontally { it },
                            exit    = fadeOut() + slideOutHorizontally { it }
                        ) {
                            val showStepsInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = { stepsViewModel.setCardVisible(true) },
                                interactionSource = showStepsInteractionSource,
                                modifier = Modifier.size(42.dp).bounceClick(showStepsInteractionSource)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = "Show steps", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        AnimatedVisibility(
                            visible = currentRoute == Screen.Summary.name && !weightCardVisible,
                            enter   = fadeIn() + slideInHorizontally { it },
                            exit    = fadeOut() + slideOutHorizontally { it }
                        ) {
                            val showWeightInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = { setWeightCardVisible(true) },
                                interactionSource = showWeightInteractionSource,
                                modifier = Modifier.size(42.dp).bounceClick(showWeightInteractionSource)
                            ) {
                                Icon(Icons.Default.MonitorWeight, contentDescription = "Show weight", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        AnimatedVisibility(
                            visible = currentRoute == Screen.Workouts.name && !searchExpanded,
                            enter   = fadeIn() + slideInHorizontally { it },
                            exit    = fadeOut() + slideOutHorizontally { it }
                        ) {
                            val searchInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = { searchExpanded = true },
                                interactionSource = searchInteractionSource,
                                modifier = Modifier.size(42.dp).bounceClick(searchInteractionSource)
                            ) {
                                Icon(Icons.Default.Search, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        val toolsInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { navigate(Screen.Tools.name) },
                            interactionSource = toolsInteractionSource,
                            modifier = Modifier.size(42.dp).bounceClick(toolsInteractionSource)
                        ) {
                            Icon(Icons.Default.Handyman, contentDescription = "Tools", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }

                        val notesInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { navigate(Screen.Notes.name) },
                            interactionSource = notesInteractionSource,
                            modifier = Modifier.size(42.dp).bounceClick(notesInteractionSource)
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = "Notes", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }

                        val settingsInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { navigate(Screen.Settings.name) },
                            interactionSource = settingsInteractionSource,
                            modifier = Modifier.size(42.dp).bounceClick(settingsInteractionSource)
                        ) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            // ── PB celebration banner (under the top bar, above content) ───────
            PbCelebrationBanner(
                data = pbCelebration,
                onDismiss = { pbCelebration = null },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = totalTopPadding + 8.dp)
            )

            // In-app update dialogs (available / downloading / install permission / up to date)
            AppUpdateHost()
            // One-time "What's new" popup after updating to a version with notes (WhatsNew.kt)
            WhatsNewHost()

            // ── Dialogs ───────────────────────────────────────────────────────

            if (showAddWorkoutDialog) {
                WorkoutDialog(
                    history = workoutHistory,
                    onDismiss = { showAddWorkoutDialog = false },
                    onConfirm = { exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes, rpe ->
                        if (isPB) pbCelebration = pbCelebrationFor(exercise, sets, reps, weight, weightUnit, workouts)
                        viewModel.addWorkout(exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes, rpe)
                        if (isPB) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showAddWorkoutDialog = false
                    }
                )
            }

            if (showAddWeightDialog) {
                BodyWeightDialog(
                    initialWeight = viewModel.weightInput,
                    onDismiss = { showAddWeightDialog = false },
                    onConfirm = { weight, dateMillis, notes ->
                        viewModel.addBodyWeight(weight, dateMillis, notes)
                        showAddWeightDialog = false
                    }
                )
            }

            if (showAddNoteDialog) {
                NoteDialog(
                    onDismiss = { showAddNoteDialog = false },
                    onConfirm = { title, content, dateMillis ->
                        viewModel.addNote(title, content, dateMillis)
                        showAddNoteDialog = false
                    }
                )
            }

            if (showAddSessionDialog) {
                AddSessionDialog(
                    primaryColor = primaryColor,
                    onDismiss = { showAddSessionDialog = false },
                    onSave = { name, type, dateMs, duration ->
                        viewModel.addWorkoutSession(name, type, dateMs, duration)
                        showAddSessionDialog = false
                    }
                )
            }

            editingWorkout?.let { workout ->
                WorkoutDialog(
                    workout = workout,
                    history = workoutHistory,
                    onDismiss = { editingWorkout = null },
                    onConfirm = { exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes, rpe ->
                        val wasPB = workout.isPersonalBest
                        viewModel.updateWorkout(workout.copy(
                            exerciseName = exercise, sets = sets, reps = reps, weight = weight,
                            date = dateMillis, isPersonalBest = isPB, weightUnit = weightUnit, notes = notes, rpe = rpe
                        ))
                        if (isPB && !wasPB) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pbCelebration = pbCelebrationFor(exercise, sets, reps, weight, weightUnit, workouts, excludeId = workout.id)
                        }
                        editingWorkout = null
                    },
                    onDelete = { workoutToDelete = workout; editingWorkout = null },
                    onCopy   = { copyingWorkout  = workout; editingWorkout = null }
                )
            }

            copyingWorkout?.let { workout ->
                WorkoutDialog(
                    workout = workout.copy(id = 0, date = System.currentTimeMillis()),
                    history = workoutHistory,
                    titleOverride = "Copy Workout",
                    onDismiss = { copyingWorkout = null },
                    onConfirm = { exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes, rpe ->
                        scrollWorkoutsToTopOnChange = true
                        if (isPB) pbCelebration = pbCelebrationFor(exercise, sets, reps, weight, weightUnit, workouts)
                        viewModel.addWorkout(exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes, rpe)
                        if (isPB) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        copyingWorkout = null
                    }
                )
            }

            editingWeight?.let { bodyWeight ->
                BodyWeightDialog(
                    bodyWeight = bodyWeight,
                    onDismiss  = { editingWeight = null },
                    onConfirm  = { weight, dateMillis, notes ->
                        viewModel.updateBodyWeight(bodyWeight.copy(weight = weight, date = dateMillis, notes = notes))
                        editingWeight = null
                    }
                )
            }

            editingNote?.let { note ->
                NoteDialog(
                    note      = note,
                    onDismiss = { editingNote = null },
                    onConfirm = { title, content, dateMillis ->
                        viewModel.updateNote(note.copy(title = title, content = content, date = dateMillis))
                        editingNote = null
                    },
                    onDelete = { noteToDelete = note; editingNote = null }
                )
            }

            LaunchedEffect(workoutToDelete) {
                val workout = workoutToDelete ?: return@LaunchedEffect
                viewModel.deleteWorkout(workout)
                workoutToDelete = null
                showUndoSnackbar("${workout.exerciseName} workout deleted") {
                    viewModel.restoreWorkout(workout)
                }
            }

            LaunchedEffect(weightToDelete) {
                val bodyWeight = weightToDelete ?: return@LaunchedEffect
                viewModel.deleteBodyWeight(bodyWeight)
                weightToDelete = null
                showUndoSnackbar("Weight entry deleted") {
                    viewModel.restoreBodyWeight(bodyWeight)
                }
            }

            LaunchedEffect(noteToDelete) {
                val note = noteToDelete ?: return@LaunchedEffect
                viewModel.deleteNote(note)
                noteToDelete = null
                showUndoSnackbar("Note deleted") {
                    viewModel.restoreNote(note)
                }
            }

            LaunchedEffect(sessionToDelete) {
                val session = sessionToDelete ?: return@LaunchedEffect
                viewModel.deleteWorkoutSession(session)
                sessionToDelete = null
                showUndoSnackbar("\"${session.name}\" deleted") {
                    viewModel.restoreWorkoutSession(session)
                }
            }

            sessionToEdit?.let { session ->
                EditSessionDialog(
                    session = session,
                    primaryColor = primaryColor,
                    onDismiss = { sessionToEdit = null },
                    onSave = { updated ->
                        viewModel.updateWorkoutSession(updated)
                        sessionToEdit = null
                    }
                )
            }

            // ── Music widget (above the bottom bar) ──────────────────────────
            // Width of the bottom bar's row, reported by PillNavBar, so the widget matches it
            var navRowWidth by remember { mutableStateOf<androidx.compose.ui.unit.Dp?>(null) }
            val hasMusicWidget = currentSong.title != null &&
                currentSong.packageName == musicApp.packageName &&
                !musicDismissed

            // Just above the bottom bar's row
            val widgetBottomPadding = NAV_BAR_SPACE + 12.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = widgetBottomPadding)
            ) {
                // Floating music app launch button — shown when the widget is hidden. Opens the app
                // chosen in Settings → Music (MusicApps.kt): Spotify or Metrolist.
                // Tap behavior: try toggling playback first; if the app is killed (or
                // playback doesn't actually start within ~500ms) launch the app instead.
                // Sits exactly where the widget's album art is, so the widget grows out of it.
                AnimatedVisibility(
                    visible = !hasMusicWidget,
                    enter = fadeIn(tween(Motion.SHORT)),
                    exit = fadeOut(tween(Motion.SHORT)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(navRowWidth?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
                        .height(MUSIC_WIDGET_HEIGHT)
                ) {
                    Box(Modifier.fillMaxSize()) {
                    val noteInteractionSource = remember { MutableInteractionSource() }
                    fun launchMusicApp() {
                        ctx.packageManager.getLaunchIntentForPackage(musicApp.packageName)
                            ?.apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(this)
                                MediaRepository.getInstance().setWidgetHidden(false)
                            } ?: android.widget.Toast.makeText(
                                ctx, "${musicApp.label} is not installed",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = ALBUM_ART_INSET)
                            .size(ALBUM_ART_SIZE)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = noteInteractionSource,
                                indication = ripple(bounded = true),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (currentSong.packageName != musicApp.packageName) {
                                        // No session from the chosen app — it's killed (or another
                                        // app is playing): open the chosen app instead of toggling
                                        launchMusicApp()
                                    } else {
                                        // Session alive — try to resume; if play silently
                                        // no-ops (process dead), fall back to launching.
                                        MediaRepository.getInstance().togglePlayPause()
                                        coroutineScope.launch {
                                            delay(500.milliseconds)
                                            if (!MediaRepository.getInstance()
                                                    .currentSong.value.isPlaying
                                            ) launchMusicApp()
                                        }
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // The chosen app's own launcher icon from the phone (Spotify and Metrolist
                        // alike), so it matches the home screen, themed icons included
                        val appIcon = remember(musicApp) {
                            runCatching {
                                ctx.packageManager.getApplicationIcon(musicApp.packageName)
                                    .toBitmap(144, 144).asImageBitmap()
                            }.getOrNull()
                        }
                        if (appIcon != null) {
                            androidx.compose.foundation.Image(
                                bitmap = appIcon,
                                contentDescription = "Open ${musicApp.label}",
                                modifier = Modifier.size(ALBUM_ART_SIZE)
                            )
                        } else {
                            // Not installed: a plain note on the surface colour
                            Box(
                                Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = "Open ${musicApp.label}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    }
                }
                val musicCirclePx = with(LocalDensity.current) { MUSIC_WIDGET_HEIGHT.roundToPx() }
                Box(Modifier.align(Alignment.BottomCenter).then(navRowWidth?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())) {
                AnimatedVisibility(
                    visible = hasMusicWidget,
                    enter = expandHorizontally(
                        animationSpec = tween(Motion.MEDIUM),
                        expandFrom = Alignment.Start,
                        initialWidth = { musicCirclePx }
                    ) + fadeIn(tween(Motion.SHORT)),
                    exit = shrinkHorizontally(
                        animationSpec = tween(Motion.SHORT),
                        shrinkTowards = Alignment.Start,
                        targetWidth = { musicCirclePx }
                    ) + fadeOut(tween(Motion.SHORT)),
                    modifier = Modifier.graphicsLayer {
                        shadowElevation = 6.dp.toPx()
                        shape = TrackerShapes.Pill
                        clip = true
                    }
                ) {
                    val musicInteractionSource = remember { MutableInteractionSource() }
                    val dragX = remember { Animatable(0f) }
                    var swipeDirection by remember { mutableIntStateOf(1) }
                    var swipeConfirmed by remember { mutableIntStateOf(0) }

                    Box(
                        modifier = Modifier
                            // Same width as the bottom bar's row (tabs + button), so they line up
                            .then(navRowWidth?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { coroutineScope.launch { dragX.snapTo(0f) } },
                                    onDragEnd = {
                                        if (dragX.value < -100f) {
                                            swipeDirection = 1
                                            swipeConfirmed++
                                            MediaRepository.getInstance().nextTrack()
                                        } else if (dragX.value > 100f) {
                                            swipeDirection = -1
                                            swipeConfirmed++
                                            MediaRepository.getInstance().previousTrack()
                                        }
                                        coroutineScope.launch {
                                            dragX.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
                                        }
                                    },
                                    onHorizontalDrag = { _, delta ->
                                        coroutineScope.launch { dragX.snapTo(dragX.value + delta) }
                                    }
                                )
                            }
                    ) {
                        val songKey = "${currentSong.title}|${currentSong.artist}"

                        // Colours like Metrolist's mini player: a tonal scheme from the album art
                        // (ThemeViewModel, once per song), or from the user's accent when there's none.
                        // Body = primaryContainer with onPrimaryContainer text; play = primary with an
                        // onPrimary icon; next = onPrimary with a primary icon. Song changes fade over.
                        val albumColors by themeViewModel.albumColors.collectAsState()
                        val fallbackColors = remember(primaryColor) { tonalColors(primaryColor.toArgb()) }
                        val colors = albumColors ?: fallbackColors
                        val widgetBody by animateColorAsState(colors.primaryContainer, tween(Motion.LONG), label = "widgetBody")
                        val widgetText by animateColorAsState(colors.onPrimaryContainer, tween(Motion.LONG), label = "widgetText")
                        val widgetPrimary by animateColorAsState(colors.primary, tween(Motion.LONG), label = "widgetPrimary")
                        val widgetOnPrimary by animateColorAsState(colors.onPrimary, tween(Motion.LONG), label = "widgetOnPrimary")
                        Surface(
                            color = widgetBody,
                            shape = TrackerShapes.Pill,
                            modifier = Modifier.fillMaxWidth()
                                .clip(TrackerShapes.Pill)
                                .clickable(
                                    interactionSource = musicInteractionSource,
                                    indication = ripple(bounded = true),
                                    onClick = { MediaRepository.getInstance().openApp() }
                                )
                        ) {
                            // clipToBounds on the Column so sliding text is clipped at the
                            // widget boundary — text can freely travel the full widget width
                            Column(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                                // Cache: only store non-null bitmaps, never overwrite with null
                                val artCache = remember { mutableMapOf<String, android.graphics.Bitmap>() }
                                currentSong.albumArt?.let { artCache[songKey] = it }
                                if (artCache.size > 3) {
                                    artCache.keys.firstOrNull { it != songKey }?.let { old ->
                                        artCache[old]?.recycle()
                                        artCache.remove(old)
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(MUSIC_WIDGET_HEIGHT)
                                        .padding(start = ALBUM_ART_INSET, end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    // Gate: only advance once the new bitmap has arrived so neither
                                    // art nor text flash blank while the app sends metadata in stages
                                    var displayedKey by remember { mutableStateOf(songKey) }
                                    LaunchedEffect(songKey, currentSong.albumArt) {
                                        if (currentSong.albumArt != null) displayedKey = songKey
                                    }

                                    // Album art (slow crossfade keyed on displayedKey) with the hide
                                    // button on top while paused. Both in one 48 dp slot in the Row,
                                    // so the overlay always lines up with the art exactly.
                                    // Cookie-shaped like Metrolist's, turning slowly while the song plays
                                    // and stopping where it is on pause.
                                    val artRotation = remember { Animatable(0f) }
                                    LaunchedEffect(currentSong.isPlaying) {
                                        if (!currentSong.isPlaying) {
                                            // Paused: ease on to the next angle where the 9 lobes line up
                                            val settled = kotlin.math.ceil(artRotation.value / 40f) * 40f
                                            artRotation.animateTo(settled, tween(Motion.MEDIUM))
                                            artRotation.snapTo(artRotation.value % 360f)
                                        }
                                        while (currentSong.isPlaying) {
                                            artRotation.animateTo(
                                                artRotation.value + 360f,
                                                tween(ALBUM_ART_TURN_MS, easing = LinearEasing)
                                            )
                                            artRotation.snapTo(artRotation.value % 360f)
                                        }
                                    }
                                    if (artCache.isNotEmpty() || !currentSong.isPlaying) {
                                        Box(
                                            Modifier
                                                .size(ALBUM_ART_SIZE)
                                                // Only the outline turns (read here, so no recomposition per frame)
                                                .graphicsLayer {
                                                    shape = TrackerShapes.cookie(artRotation.value)
                                                    clip = true
                                                }
                                        ) {
                                            if (artCache.isNotEmpty()) {
                                                AnimatedContent(
                                                    targetState = displayedKey,
                                                    transitionSpec = {
                                                        fadeIn(tween(Motion.LONG)) togetherWith fadeOut(tween(Motion.LONG))
                                                    },
                                                    label = "AlbumArt"
                                                ) { key ->
                                                    artCache[key]?.let { bitmap ->
                                                        androidx.compose.foundation.Image(
                                                            bitmap = bitmap.asImageBitmap(),
                                                            contentDescription = "Album art",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.size(ALBUM_ART_SIZE)
                                                        )
                                                    }
                                                }
                                            }
                                            // Hide button, only while paused
                                            if (!currentSong.isPlaying) {
                                                val closeInteractionSource = remember { MutableInteractionSource() }
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .background(Color.Black.copy(alpha = 0.7f))
                                                        .clickable(
                                                            interactionSource = closeInteractionSource,
                                                            indication = ripple(bounded = true),
                                                            onClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                MediaRepository.getInstance().setWidgetHidden(true)
                                                            }
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Hide music widget",
                                                        modifier = Modifier.size(32.dp),
                                                        tint = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val outOffset = remember { Animatable(0f) }
                                    val outAlpha  = remember { Animatable(0f) }
                                    val inOffset  = remember { Animatable(0f) }
                                    val inAlpha   = remember { Animatable(1f) }

                                    var outTitle  by remember { mutableStateOf(currentSong.title  ?: "") }
                                    var outArtist by remember { mutableStateOf(currentSong.artist ?: "") }
                                    var inTitle   by remember { mutableStateOf(currentSong.title  ?: "") }
                                    var inArtist  by remember { mutableStateOf(currentSong.artist ?: "") }

                                    // songKey is a plain val, so the coroutine below would only ever see the
                                    // value from when it launched; this State tracks the latest one across the
                                    // delay. Kept as a State (read via .value) rather than `by` so it's explicit
                                    // that each read is fresh.
                                    val latestSongKey = rememberUpdatedState(songKey)

                                    // Step 1: finger lifts — snapshot current text into outgoing layer,
                                    // hide incoming layer immediately so old text never shows in both
                                    LaunchedEffect(swipeConfirmed) {
                                        if (swipeConfirmed == 0) return@LaunchedEffect
                                        val d = swipeDirection
                                        val keyBeforeSwipe = latestSongKey.value
                                        outTitle  = inTitle
                                        outArtist = inArtist
                                        outOffset.snapTo(0f)
                                        outAlpha.snapTo(1f)
                                        inAlpha.snapTo(0f)   // hide incoming until new song text is ready
                                        inOffset.snapTo(800f * d)
                                        launch { outOffset.animateTo(-600f * d, tween(100, easing = FastOutLinearInEasing)) }
                                        launch { outAlpha.animateTo(0f, tween(100)) }
                                        // Fallback: if song doesn't change (e.g. same song restarts),
                                        // restore the incoming layer so text doesn't stay blank
                                        delay(600.milliseconds)
                                        if (latestSongKey.value == keyBeforeSwipe && inAlpha.value == 0f) {
                                            inTitle  = currentSong.title  ?: ""
                                            inArtist = currentSong.artist ?: ""
                                            inAlpha.snapTo(1f)
                                            inOffset.snapTo(0f)
                                        }
                                    }

                                    // Step 2: new song confirmed — update incoming text and slide it in
                                    LaunchedEffect(displayedKey) {
                                        val d = swipeDirection
                                        inTitle  = currentSong.title  ?: ""
                                        inArtist = currentSong.artist ?: ""
                                        inOffset.snapTo(800f * d)
                                        inAlpha.snapTo(1f)
                                        inOffset.animateTo(0f, tween(100, easing = FastOutSlowInEasing))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp)
                                            .graphicsLayer { translationX = dragX.value * 0.4f }
                                    ) {
                                        // Outgoing layer
                                        Column(modifier = Modifier.graphicsLayer {
                                            translationX = outOffset.value
                                            alpha = outAlpha.value
                                        }) {
                                            Text(
                                                text = outTitle,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                color = widgetText,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = outArtist,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = widgetText.copy(alpha = 0.7f)
                                            )
                                        }
                                        // Incoming layer
                                        Column(modifier = Modifier.graphicsLayer {
                                            translationX = inOffset.value
                                            alpha = inAlpha.value
                                        }) {
                                            Text(
                                                text = inTitle,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                color = widgetText,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = inArtist,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = widgetText.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    // Play/pause: light circle, dark icon
                                    val playInteractionSource = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                            .background(widgetPrimary)
                                            .clickable(
                                                interactionSource = playInteractionSource,
                                                indication = ripple(bounded = true),
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    MediaRepository.getInstance().togglePlayPause()
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (currentSong.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = widgetOnPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    // Next: dark circle, light icon (slides the text like a swipe)
                                    val nextInteractionSource = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                            .background(widgetOnPrimary)
                                            .clickable(
                                                interactionSource = nextInteractionSource,
                                                indication = ripple(bounded = true),
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    swipeDirection = 1
                                                    swipeConfirmed++
                                                    MediaRepository.getInstance().nextTrack()
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next track",
                                            tint = widgetPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }

            }

            // ── Bottom bar: tabs pill + the page's button (same style as Overtime) ──
            // The button adds on pages with an add sheet and opens Settings everywhere else;
            // on Settings itself it goes back to the page Settings was opened from.
            val navFab = when (currentRoute) {
                Screen.Workouts.name -> NavFab(Icons.Default.Add, "Add workout") { showAddWorkoutDialog = true }
                Screen.WeightTracking.name -> NavFab(Icons.Default.MonitorWeight, "Add weight") {
                    viewModel.prepareNewEntry(); showAddWeightDialog = true
                }
                Screen.Notes.name -> NavFab(Icons.Default.Add, "Add note") { showAddNoteDialog = true }
                Screen.Sessions.name -> NavFab(Icons.Default.Add, "Add session") { showAddSessionDialog = true }
                Screen.Settings.name -> NavFab(Icons.AutoMirrored.Filled.ArrowBack, "Back") { navController.popBackStack() }
                else -> NavFab(Icons.Default.Settings, "Settings") { navigate(Screen.Settings.name) }
            }
            PillNavBar(
                tabs = listOf(
                    NavTab(Screen.WorkoutTimer.name, Icons.Default.Timer, "Timer"),
                    NavTab(Screen.Workouts.name, Icons.Default.FitnessCenter, "Workouts"),
                    NavTab(Screen.Summary.name, Icons.Default.Home, "Home"),
                    NavTab(Screen.WeightTracking.name, Icons.Default.MonitorWeight, "Weight"),
                    NavTab(Screen.More.name, Icons.Default.MoreHoriz, "More")
                ),
                // On the swipe pages the highlight follows the pager, mid-swipe too (the tabs are in
                // pager order); Stats, Calendar and Sessions belong to More; Tools, Notes and
                // Settings to no tab
                highlightPosition = {
                    val route = currentBackStackEntry?.destination?.route
                    when {
                        route == null || route == pagerHostRoute ->
                            pagerState.currentPage + pagerState.currentPageOffsetFraction
                        route in moreRoutes -> swipeScreens.indexOf(Screen.More.name).toFloat()
                        else -> null
                    }
                },
                accent = primaryColor,
                timerRoute = Screen.WorkoutTimer.name,
                timerText = if (timerIsRunning) formatTabTime(timerElapsed) else null,
                fab = navFab?.let { fab ->
                    fab.copy(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        fab.onClick()
                    })
                },
                onSelect = { navigate(it) },
                onRowWidth = { navRowWidth = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // ── Subscription dialog ──────────────────────────────────────
            if (showSubscriptionDialog) {
                SubscriptionDialog(
                    onDismiss = { showSubscriptionDialog = false },
                    onSubscribe = {
                        showSubscriptionDialog = false
                        activity?.let { act -> subscriptionViewModel.launchPurchase(act) }
                    },
                    formattedPrice = subscriptionViewModel.getFormattedPrice(),
                    primaryColor = primaryColor
                )
            }
            }
        }
    }
}
