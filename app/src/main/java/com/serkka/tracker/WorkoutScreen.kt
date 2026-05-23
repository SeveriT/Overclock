@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.serkka.tracker

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.serkka.tracker.ui.theme.DarkSurfaceColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment


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


@SuppressLint("UseKtx")
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    stravaViewModel: StravaViewModel = viewModel(),
    themeViewModel: ThemeViewModel = viewModel(),
    timerViewModel: WorkoutTimerViewModel = viewModel(),
    aiViewModel: AiViewModel = viewModel(),
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
    val activity = LocalContext.current as? Activity

    val ctx = LocalContext.current
    val trackerPrefs = remember { PreferencesManager.getInstance(ctx).tracker }
    var weightCardVisible by remember { mutableStateOf(trackerPrefs.getBoolean("weight_card_visible", true)) }
    var pbConfettiTrigger by remember { mutableIntStateOf(0) }
    val setWeightCardVisible: (Boolean) -> Unit = { visible ->
        weightCardVisible = visible
        trackerPrefs.edit().putBoolean("weight_card_visible", visible).apply()
    }

    LaunchedEffect(Unit) {
        stravaViewModel.checkAndFetchActivities()
        subscriptionViewModel.refreshStatus()
    }
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    // Swipeable screens are hosted in a HorizontalPager under a single NavHost
    // destination ("swipe_host"). Non-swipe routes (Settings, AiAssistant, etc.)
    // remain as individual NavHost composables.
    val swipeScreens = remember {
        listOf(
            Screen.WorkoutTimer.name,
            Screen.Workouts.name,
            Screen.Summary.name,
            Screen.WeightTracking.name,
            Screen.StravaCalendar.name,
            Screen.Sessions.name
        )
    }
    val pagerHostRoute = "swipe_host"
    val pagerState = rememberPagerState(
        initialPage = swipeScreens.indexOf(Screen.Summary.name),
        pageCount = { swipeScreens.size }
    )

    val currentRoute = when (val r = currentBackStackEntry?.destination?.route) {
        pagerHostRoute, null -> swipeScreens[pagerState.currentPage]
        else -> r
    }

    val workoutsListState = rememberLazyListState()
    val summaryListState  = rememberLazyListState()
    val weightListState   = rememberLazyListState()
    val notesListState    = rememberLazyListState()
    val sessionsListState = rememberLazyListState()
    val calendarListState = rememberLazyListState()

    // ── Navbar collapse on scroll down ───────────────────────────────────────
    val activeListState = when (currentRoute) {
        Screen.Workouts.name       -> workoutsListState
        Screen.WeightTracking.name -> weightListState
        Screen.Notes.name          -> notesListState
        Screen.Sessions.name       -> sessionsListState
        Screen.StravaCalendar.name -> calendarListState
        else                       -> null
    }

    var isNavBarVisible by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeListState) {
        if (activeListState == null) {
            isNavBarVisible = true
            return@LaunchedEffect
        }
        snapshotFlow {
            Triple(
                activeListState.firstVisibleItemIndex,
                activeListState.firstVisibleItemScrollOffset,
                activeListState.canScrollForward
            )
        }.collect { (index, offset, canScrollForward) ->
            val scrollingDown = index > previousIndex || (index == previousIndex && offset > previousOffset + 5)
            val scrollingUp = index < previousIndex || (index == previousIndex && offset < previousOffset - 5)

            if (scrollingDown && index > 0) {
                isNavBarVisible = false
            } else if (scrollingUp && canScrollForward) {
                isNavBarVisible = true
            }

            previousIndex = index
            previousOffset = offset
        }
    }

    var searchQuery          by remember { mutableStateOf("") }
    var searchExpanded       by remember { mutableStateOf(false) }

    // Reset navbar visibility on screen change
    LaunchedEffect(currentRoute) {
        isNavBarVisible = true
        previousIndex = 0
        previousOffset = 0
        if (currentRoute != Screen.Workouts.name) {
            searchExpanded = false
            searchQuery = ""
        }
    }

    val systemNavBarHeight = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val navBarOffsetY by animateDpAsState(
        targetValue = if (isNavBarVisible) 0.dp else 100.dp + systemNavBarHeight,
        animationSpec = tween(200),
        label = "navBarOffset"
    )

    val workouts        by viewModel.allWorkouts.collectAsState()
    val bodyWeights     by viewModel.allBodyWeights.collectAsState()
    val notesList       by viewModel.allNotes.collectAsState()
    val workoutSessions by viewModel.allSessions.collectAsState()
    val stravaActivities by stravaViewModel.activities.collectAsState()

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

    val currentSong    by MediaRepository.getInstance().currentSong.collectAsState()
    val timerIsRunning by timerViewModel.isRunning.collectAsState()
    val timerElapsed   by timerViewModel.elapsedSeconds.collectAsState()
    val primaryColor   by themeViewModel.primaryColor.collectAsState()

    // User-dismissed flag for the music widget — auto-resets when a song starts playing.
    // Declared here so both the NavHost (timer screen padding) and the widget gate use it.
    var musicDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(currentSong.isPlaying) {
        if (currentSong.isPlaying) musicDismissed = false
    }

    fun navigate(route: String) {
        if (route in swipeScreens) {
            val targetPage = swipeScreens.indexOf(route)
            if (navController.currentDestination?.route != pagerHostRoute) {
                navController.navigate(pagerHostRoute) {
                    popUpTo(pagerHostRoute) { inclusive = false }
                    launchSingleTop = true
                }
            }
            coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
        } else {
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    val navBarColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = Color.Transparent,
    )

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
            val contentBottomPadding by animateDpAsState(
                targetValue = if (isNavBarVisible) 190.dp else 140.dp,
                animationSpec = tween(300),
                label = "contentBottom"
            )

            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

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

            NavHost(
                navController = navController,
                startDestination = pagerHostRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    val from = swipeScreens.indexOf(initialState.destination.route)
                    val to = swipeScreens.indexOf(targetState.destination.route)
                    when {
                        from == -1 || to == -1 ->
                            fadeIn(animationSpec = tween(260))
                        to > from ->
                            slideInHorizontally(animationSpec = tween(260)) { it / 6 } +
                                fadeIn(animationSpec = tween(260))
                        else ->
                            slideInHorizontally(animationSpec = tween(260)) { -it / 6 } +
                                fadeIn(animationSpec = tween(260))
                    }
                },
                exitTransition = {
                    val from = swipeScreens.indexOf(initialState.destination.route)
                    val to = swipeScreens.indexOf(targetState.destination.route)
                    when {
                        from == -1 || to == -1 ->
                            fadeOut(animationSpec = tween(200))
                        to > from ->
                            slideOutHorizontally(animationSpec = tween(260)) { -it / 6 } +
                                fadeOut(animationSpec = tween(200))
                        else ->
                            slideOutHorizontally(animationSpec = tween(260)) { it / 6 } +
                                fadeOut(animationSpec = tween(200))
                    }
                }
            ) {
                composable(pagerHostRoute) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1
                    ) { page ->
                        when (swipeScreens[page]) {
                            Screen.WorkoutTimer.name -> {
                                val musicVisible = currentSong.title != null &&
                                    currentSong.packageName == "com.spotify.music" &&
                                    !musicDismissed
                                WorkoutTimerScreen(
                                    timerViewModel  = timerViewModel,
                                    stravaViewModel = stravaViewModel,
                                    bottomPadding   = if (musicVisible) 88.dp else 0.dp,
                                    topPadding = totalTopPadding,
                                    onSaveLocally = { name, type, startMs, duration ->
                                        viewModel.addWorkoutSession(name, type, startMs, duration)
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
                                        onCopy    = { copyingWorkout = it },
                                        onDuplicate = { w ->
                                            viewModel.addWorkout(
                                                w.exerciseName, w.sets, w.reps, w.weight,
                                                System.currentTimeMillis(), w.isPersonalBest, w.weightUnit, w.notes
                                            )
                                        },
                                        onTogglePB = { viewModel.updateWorkout(it.copy(isPersonalBest = !it.isPersonalBest)) },
                                        listState = workoutsListState,
                                        topPadding = totalTopPadding,
                                        bottomPadding = contentBottomPadding
                                    )
                                }
                            }
                            Screen.Summary.name -> {
                                ElasticColumnWrapper {
                                    SummaryPage(
                                        workouts = workouts,
                                        bodyWeights = bodyWeights,
                                        workoutSessions = workoutSessions,
                                        stravaViewModel = stravaViewModel,
                                        stepsViewModel = stepsViewModel,
                                        primaryColor = primaryColor,
                                        onWorkoutEdit   = { editingWorkout = it },
                                        onWorkoutDelete = { workoutToDelete = it },
                                        onWorkoutCopy   = { copyingWorkout = it },
                                        onNavigateToWeightTracking = { navigate(Screen.WeightTracking.name) },
                                        onNavigateToSessions = { navigate(Screen.Sessions.name) },
                                        onNavigateToReps = { navigate(Screen.Workouts.name) },
                                        listState = summaryListState,
                                        topPadding = totalTopPadding,
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
                                        topPadding = totalTopPadding,
                                        bottomPadding = contentBottomPadding
                                    )
                                }
                            }
                            Screen.StravaCalendar.name -> {
                                StravaCalendarPage(
                                    stravaViewModel = stravaViewModel,
                                    workoutSessions = workoutSessions,
                                    primaryColor = primaryColor,
                                    topPadding = totalTopPadding,
                                    bottomPadding = contentBottomPadding,
                                    listState = calendarListState
                                )
                            }
                            Screen.Sessions.name -> {
                                ElasticColumnWrapper {
                                    SessionsPage(
                                        sessions = workoutSessions,
                                        stravaActivities = stravaActivities,
                                        primaryColor = primaryColor,
                                        onDelete = { sessionToDelete = it },
                                        onEdit = { sessionToEdit = it },
                                        listState = sessionsListState,
                                        topPadding = totalTopPadding,
                                        bottomPadding = contentBottomPadding
                                    )
                                }
                            }
                        }
                    }
                }
                composable(Screen.WorkoutStats.name) {
                    WorkoutStatsPage(workouts, primaryColor, topPadding = totalTopPadding, bottomPadding = contentBottomPadding)
                }
                composable(Screen.Notes.name) {
                    ElasticColumnWrapper {
                        NotesPage(
                            notes = notesList,
                            primaryColor = primaryColor,
                            onNoteClick  = { editingNote = it },
                            onNoteDelete = { noteToDelete = it },
                            listState = notesListState,
                            topPadding = totalTopPadding,
                            bottomPadding = contentBottomPadding
                        )
                    }
                }
                composable(Screen.Settings.name) {
                    SettingsPage(
                        primaryColor = primaryColor,
                        themeViewModel = themeViewModel,
                        stravaViewModel = stravaViewModel,
                        viewModel = viewModel,
                        topPadding = totalTopPadding,
                        bottomPadding = contentBottomPadding,
                        isSubscribed = isSubscribed,
                        onSubscribe = { showSubscriptionDialog = true },
                        onRecheckWhitelist = { subscriptionViewModel.recheckWhitelist() }
                    )
                }
                composable(Screen.AiAssistant.name) {
                    AiAssistantPage(
                        workoutViewModel = viewModel,
                        aiViewModel = aiViewModel,
                        workouts = workouts,
                        primaryColor = primaryColor,
                        topPadding = totalTopPadding,
                        bottomPadding = contentBottomPadding,
                        dailyLimit = aiViewModel.dailyRequestLimit
                    )
                }
            }

            // ── Top Bar (Overlay) ─────────────────────────────────────────────
            val bgColor = MaterialTheme.colorScheme.background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to bgColor,
                                0.5f to bgColor.copy(alpha = 0.9f),
                                1.0f to Color.Transparent
                            )
                        )
                    )
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
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Close search")
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
                                    .heightIn(max = 48.dp)
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
                            visible = timerIsRunning && currentRoute != Screen.WorkoutTimer.name,
                            enter   = fadeIn() + slideInHorizontally { it },
                            exit    = fadeOut() + slideOutHorizontally { it }
                        ) {
                            val infinitePulse = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infinitePulse.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.02f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )

                            val assistInteractionSource = remember { MutableInteractionSource() }
                            AssistChip(
                                onClick = { navigate(Screen.WorkoutTimer.name) },
                                label = {
                                    Text(
                                        text = formatElapsed(timerElapsed),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(14.dp))
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    labelColor = MaterialTheme.colorScheme.surface,
                                    leadingIconContentColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.padding(end = 4.dp).height(28.dp)
                                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                                    .bounceClick(assistInteractionSource)
                            )
                        }
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
                                Icon(Icons.Default.DirectionsWalk, contentDescription = "Show steps", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
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
                            val aiInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = {
                                    if (isSubscribed) navigate(Screen.AiAssistant.name)
                                    else showSubscriptionDialog = true
                                },
                                interactionSource = aiInteractionSource,
                                modifier = Modifier.size(42.dp).bounceClick(aiInteractionSource)
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        AnimatedVisibility(
                            visible = currentRoute == Screen.Workouts.name && !searchExpanded,
                            enter   = fadeIn() + slideInHorizontally { it },
                            exit    = fadeOut() + slideOutHorizontally { it }
                        ) {
                            val statsInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = { navigate(Screen.WorkoutStats.name) },
                                interactionSource = statsInteractionSource,
                                modifier = Modifier.size(42.dp).bounceClick(statsInteractionSource)
                            ) {
                                Icon(Icons.Default.BarChart, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
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
                        AnimatedVisibility(
                            visible = currentRoute == Screen.StravaCalendar.name,
                            enter   = fadeIn() + slideInHorizontally { it },
                            exit    = fadeOut() + slideOutHorizontally { it }
                        ) {
                            val sessionsInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = { navigate(Screen.Sessions.name) },
                                interactionSource = sessionsInteractionSource,
                                modifier = Modifier.size(42.dp).bounceClick(sessionsInteractionSource)
                            ) {
                                Icon(Icons.Default.History, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        val notesInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { navigate(Screen.Notes.name) },
                            interactionSource = notesInteractionSource,
                            modifier = Modifier.size(42.dp).bounceClick(notesInteractionSource)
                        ) {
                            Icon(Icons.Default.Create, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
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

            // ── Dialogs ───────────────────────────────────────────────────────

            if (showAddWorkoutDialog) {
                WorkoutDialog(
                    history = workoutHistory,
                    onDismiss = { showAddWorkoutDialog = false },
                    onConfirm = { exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes ->
                        viewModel.addWorkout(exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes)
                        if (isPB) {
                            pbConfettiTrigger++
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
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
                    onConfirm = { exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes ->
                        val wasPB = workout.isPersonalBest
                        viewModel.updateWorkout(workout.copy(
                            exerciseName = exercise, sets = sets, reps = reps, weight = weight,
                            date = dateMillis, isPersonalBest = isPB, weightUnit = weightUnit, notes = notes
                        ))
                        if (isPB && !wasPB) {
                            pbConfettiTrigger++
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    onDismiss = { copyingWorkout = null },
                    onConfirm = { exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes ->
                        viewModel.addWorkout(exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes)
                        if (isPB) {
                            pbConfettiTrigger++
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
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

            ConfettiOverlay(trigger = pbConfettiTrigger, modifier = Modifier.fillMaxSize())

            // ── Music widget + FAB ────────────────────────────────────────────
            val fabScreens = setOf(Screen.Workouts.name, Screen.WeightTracking.name, Screen.Notes.name, Screen.Sessions.name)
            val hasMusicWidget = currentSong.title != null &&
                currentSong.packageName == "com.spotify.music" &&
                !musicDismissed

            val widgetBottomPadding by animateDpAsState(
                targetValue = if (isNavBarVisible) 80.dp else 16.dp,
                animationSpec = tween(300),
                label = "widgetBottom"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (!musicDismissed) widgetBottomPadding else widgetBottomPadding + 10.dp)
            ) {
                // Floating Spotify launch button — shown when the widget is hidden.
                // Tap behavior: try toggling playback first; if Spotify is killed (or
                // playback doesn't actually start within ~700ms) launch the app instead.
                if (!hasMusicWidget) {
                    val noteInteractionSource = remember { MutableInteractionSource() }
                    fun launchSpotify() {
                        ctx.packageManager.getLaunchIntentForPackage("com.spotify.music")
                            ?.apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(this)
                                musicDismissed = false
                            } ?: android.widget.Toast.makeText(
                                ctx, "Spotify is not installed",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp, bottom = 11.dp, top = 6.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1DB954))
                            .clickable(
                                interactionSource = noteInteractionSource,
                                indication = ripple(bounded = true),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (currentSong.packageName == null) {
                                        // No active media session — Spotify is killed
                                        launchSpotify()
                                    } else {
                                        // Session alive — try to resume; if play silently
                                        // no-ops (process dead), fall back to launching.
                                        MediaRepository.getInstance().togglePlayPause()
                                        coroutineScope.launch {
                                            delay(500)
                                            if (!MediaRepository.getInstance()
                                                    .currentSong.value.isPlaying
                                            ) launchSpotify()
                                        }
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = "Open Spotify",
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF191414)
                        )
                    }
                }
                if (hasMusicWidget) {
                    val musicInteractionSource = remember { MutableInteractionSource() }
                    val dragX = remember { Animatable(0f) }
                    var swipeDirection by remember { mutableIntStateOf(1) }
                    var swipeConfirmed by remember { mutableIntStateOf(0) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (isNavBarVisible && currentRoute in fabScreens) 70.dp else 0.dp)
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

                        // Extract accent color from album art
                        val albumAccent by remember(songKey, currentSong.albumArt) {
                            mutableStateOf(
                                currentSong.albumArt?.let { bmp ->
                                    val palette = androidx.palette.graphics.Palette.from(bmp)
                                        .maximumColorCount(16)
                                        .generate()
                                    val rgb = palette.getVibrantColor(
                                        palette.getMutedColor(
                                            palette.getDominantColor(0)
                                        )
                                    )
                                    if (rgb != 0) Color(rgb) else null
                                }
                            )
                        }
                        val musicAccent by animateColorAsState(
                            targetValue = albumAccent ?: primaryColor,
                            animationSpec = tween(600),
                            label = "musicAccent"
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(MaterialTheme.shapes.large)
                                .background(Color.Black.copy(alpha = 0.97f))
                        )
                        Surface(
                            color = musicAccent.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
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
                                        .height(64.dp)
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    // Gate: only advance once the new bitmap has arrived so neither
                                    // art nor text flash blank while Spotify sends metadata in stages
                                    var displayedKey by remember { mutableStateOf(songKey) }
                                    LaunchedEffect(songKey, currentSong.albumArt) {
                                        if (currentSong.albumArt != null) displayedKey = songKey
                                    }

                                    // Album art: slow crossfade keyed on displayedKey
                                    if (artCache.isNotEmpty()) {
                                        AnimatedContent(
                                            targetState = displayedKey,
                                            transitionSpec = {
                                                fadeIn(tween(600)) togetherWith fadeOut(tween(600))
                                            },
                                            label = "AlbumArt"
                                        ) { key ->
                                            artCache[key]?.let { bitmap ->
                                                androidx.compose.foundation.Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = "Album art",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
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

                                    // Step 1: finger lifts — snapshot current text into outgoing layer,
                                    // hide incoming layer immediately so old text never shows in both
                                    LaunchedEffect(swipeConfirmed) {
                                        if (swipeConfirmed == 0) return@LaunchedEffect
                                        val d = swipeDirection
                                        val keyBeforeSwipe = songKey
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
                                        delay(600)
                                        if (songKey == keyBeforeSwipe && inAlpha.value == 0f) {
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
                                            .padding(horizontal = 10.dp)
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
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = outArtist,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = inArtist,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    val playInteractionSource = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier.size(52.dp).clip(CircleShape)
                                            .clickable(
                                                interactionSource = playInteractionSource,
                                                indication = ripple(bounded = false, radius = 26.dp),
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
                                            tint = if (currentSong.isPlaying) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                val position1 = currentSong.position?.toFloat() ?: 0f
                                val duration1 = currentSong.duration?.toFloat() ?: 1f

                                WavyProgressIndicator(
                                    progress = if (duration1 > 0) position1 / duration1 else 0f,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 10.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    trackColor = DarkSurfaceColor,
                                    isPlaying = currentSong.isPlaying
                                )
                            }
                        }

                        // Hide button — overlays the album art (matches its 44dp size and
                        // 8dp rounded shape), only while paused.
                        if (!currentSong.isPlaying) {
                            val closeInteractionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 10.dp, top = 10.dp)
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .clickable(
                                        interactionSource = closeInteractionSource,
                                        indication = ripple(bounded = true),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            musicDismissed = true
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

                AnimatedVisibility(
                    visible = isNavBarVisible && currentRoute in fabScreens,
                    enter = fadeIn() + scaleIn(),
                    exit  = fadeOut() + scaleOut(),
                    modifier = Modifier.align(if (hasMusicWidget) Alignment.TopEnd else Alignment.TopEnd)
                ) {
                    val fabInteractionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .bounceClick(fabInteractionSource)
                            .clip(MaterialTheme.shapes.large)
                    ) {
                        Surface(
                            color = primaryColor,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                        .fillMaxSize()
                                        .combinedClickable(
                                    interactionSource = fabInteractionSource,
                                    indication = ripple(),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        when (currentRoute) {
                                            Screen.Workouts.name -> showAddWorkoutDialog = true
                                            Screen.WeightTracking.name -> { viewModel.prepareNewEntry(); showAddWeightDialog = true }
                                            Screen.Notes.name -> showAddNoteDialog = true
                                            Screen.Sessions.name -> showAddSessionDialog = true
                                        }
                                    }
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (currentRoute == Screen.WeightTracking.name)
                                        Icons.Default.MonitorWeight else Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }
            }

            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                contentColor = primaryColor,
                windowInsets = WindowInsets(0),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset(y = navBarOffsetY)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.25f to bgColor.copy(alpha = 0.5f),
                                0.5f to bgColor.copy(alpha = 0.8f),
                                0.8f to bgColor,
                                1.0f to bgColor
                            )
                        )
                    )
                    .navigationBarsPadding()
            ) {
                Spacer(modifier = Modifier.width(4.dp))
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Timer, null) },
                    label = { Text("Timer") },
                    selected = currentRoute == Screen.WorkoutTimer.name,
                    onClick  = { navigate(Screen.WorkoutTimer.name) },
                    colors = navBarColors
                )
                NavigationBarItem(
                    icon = { Icon(ImageVector.vectorResource(R.drawable.ic_weight_training), null) },
                    label = { Text("Workouts") },
                    selected = currentRoute == Screen.Workouts.name,
                    onClick  = { navigate(Screen.Workouts.name) },
                    colors = navBarColors
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dashboard, null) },
                    label = { Text("Home") },
                    selected = currentRoute == Screen.Summary.name,
                    onClick  = { navigate(Screen.Summary.name) },
                    colors = navBarColors
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.MonitorWeight, null) },
                    label = { Text("Weight") },
                    selected = currentRoute == Screen.WeightTracking.name,
                    onClick  = { navigate(Screen.WeightTracking.name) },
                    colors = navBarColors
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CalendarMonth, null) },
                    label = { Text("Calendar") },
                    selected = currentRoute == Screen.StravaCalendar.name,
                    onClick  = { navigate(Screen.StravaCalendar.name) },
                    colors = navBarColors
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

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
