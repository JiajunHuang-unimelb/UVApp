package com.example.uvapp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.uvapp.data.preferences.DataStoreIndoorLocationRepository
import com.example.uvapp.platform.alerts.IndoorSuggestionNotifier
import com.example.uvapp.platform.alerts.SunProtectionNotifier
import com.example.uvapp.platform.alerts.SunscreenAppliedEvents
import com.example.uvapp.viewmodel.IndoorLocationsViewModel
import com.example.uvapp.ui.components.IndoorDemoToggle
import com.example.uvapp.ui.components.IndoorLocationsPanel
import com.example.uvapp.ui.components.IndoorSuggestionDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uvapp.WeeklyExposureWidget
import com.example.uvapp.data.history.ExposureHistoryRepositoryFactory
import com.example.uvapp.data.nominatim.PlaceRepositoryFactory
import com.example.uvapp.data.preferences.DataStoreUserPreferencesRepository
import com.example.uvapp.data.repository.UvRepositoryFactory
import com.example.uvapp.platform.alerts.AndroidExposureAlertGateway
import com.example.uvapp.domain.environment.EnhancedSensingPermissions
import com.example.uvapp.platform.environment.AndroidEnvironmentContextProvider
import com.example.uvapp.platform.environment.AndroidExposureMonitoringController
import com.example.uvapp.platform.environment.AndroidMicrophoneEnvironmentMonitor
import com.example.uvapp.platform.location.FusedCurrentLocationProvider
import com.example.uvapp.ui.components.BottomNav
import com.example.uvapp.ui.components.SearchDialogOverlay
import com.example.uvapp.ui.components.TopLoadingBar
import com.example.uvapp.ui.location.rememberLocationPermissionRequester
import com.example.uvapp.ui.microphone.rememberMicrophonePermissionRequester
import com.example.uvapp.ui.camera.rememberCameraPermissionRequester
import com.example.uvapp.ui.activityrecognition.rememberActivityRecognitionPermissionRequester
import com.example.uvapp.ui.screens.ForecastScreen
import com.example.uvapp.ui.screens.SunLogScreen
import com.example.uvapp.ui.screens.HomeScreen
import com.example.uvapp.ui.screens.SettingsScreen
import com.example.uvapp.ui.theme.UvAppTheme
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.ForecastViewModel
import com.example.uvapp.viewmodel.MainViewModel
import com.example.uvapp.viewmodel.SUN_LOG_WEEK_COUNT
import com.example.uvapp.viewmodel.SettingsViewModel
import com.example.uvapp.viewmodel.Tab
import kotlinx.coroutines.delay

/**
 * App shell: theme wiring, shared chrome (loading bar, refresh, bottom nav),
 * tab content and the search-dialog overlay. ViewModels are activity-scoped;
 * Home and Forecast share the same source of truth via MainViewModel.
 * [requestedTab] comes from a launch intent (the weekly widget); it is applied once
 * and then cleared through [onRequestedTabHandled].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UVAppRoot(
    requestedTab: Tab? = null,
    onRequestedTabHandled: () -> Unit = {},
) {
    val applicationContext = LocalContext.current.applicationContext
    val locationProvider =
        remember(applicationContext) { FusedCurrentLocationProvider(applicationContext) }
    val forecastRepository =
        remember(applicationContext) { UvRepositoryFactory.create(applicationContext) }
    val placeRepository =
        remember(applicationContext) { PlaceRepositoryFactory.create(applicationContext) }
    val preferencesRepository =
        remember(applicationContext) { DataStoreUserPreferencesRepository(applicationContext) }
    val environmentContextProvider = AndroidEnvironmentContextProvider
    val monitoringController =
        remember(applicationContext) {
            AndroidExposureMonitoringController(applicationContext)
        }
    val alertGateway =
        remember(applicationContext) { AndroidExposureAlertGateway(applicationContext) }
    val microphoneMonitor =
        remember(applicationContext) {
            AndroidMicrophoneEnvironmentMonitor(applicationContext)
        }
    val historyRepository =
        remember(applicationContext) {
            ExposureHistoryRepositoryFactory.create(applicationContext)
        }
    val sunProtectionNotifier =
        remember(applicationContext) { SunProtectionNotifier(applicationContext) }

    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(preferencesRepository)
    }

    val mainViewModel: MainViewModel = viewModel {
        MainViewModel(
            settingsViewModel = settingsViewModel,
            locationProvider = locationProvider,
            forecastRepository = forecastRepository,
            placeRepository = placeRepository,
            environmentContextProvider = environmentContextProvider,
            monitoringController = monitoringController,
            alertGateway = alertGateway,
            historyRepository = historyRepository,
            onHistorySaved = {
                WeeklyExposureWidget().updateAll(applicationContext)
            },
            onSunProtectionAlert = sunProtectionNotifier::show,
            sunscreenAppliedEvents = SunscreenAppliedEvents.events,
        )
    }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            // The widget shows this week's total, so it always opens Sun log on this week.
            if (requestedTab == Tab.SUN_LOG) mainViewModel.onSunLogPageSettled(SUN_LOG_WEEK_COUNT - 1)
            mainViewModel.onTabSelected(requestedTab)
            onRequestedTabHandled()
        }
    }

    val forecastViewModel: ForecastViewModel = viewModel {
        ForecastViewModel(settingsViewModel, mainViewModel)
    }

    val indoorRepository =
        remember { DataStoreIndoorLocationRepository(applicationContext) }
    val notifier =
        remember { IndoorSuggestionNotifier(applicationContext) }

    val indoorViewModel: IndoorLocationsViewModel = viewModel {
        IndoorLocationsViewModel(
            repository = indoorRepository,
            location = FusedCurrentLocationProvider(
                applicationContext,
                freshOnly = true,
            ),
            main = mainViewModel,
            notifySuggestion = notifier::show,
            reportIndoorProximity = { near ->
                AndroidEnvironmentContextProvider.updateIndoorProximity(near == true)
            },
        )
    }

    val indoorState by indoorViewModel.state.collectAsStateWithLifecycle()

    val requestSave = rememberLocationPermissionRequester(
        onPermissionGranted = indoorViewModel::requestSave,
        onPermissionDenied = { indoorViewModel.permissionDenied() },
    )

    val notificationPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            indoorViewModel.enableSuggestions(true)
        }

    val enableSuggestions: () -> Unit = {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(
                android.Manifest.permission.POST_NOTIFICATIONS,
            )
        } else {
            indoorViewModel.enableSuggestions(true)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle = lifecycleOwner.lifecycle

    DisposableEffect(lifecycle, indoorViewModel) {
        fun updateVisibility() {
            indoorViewModel.setVisible(
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            )
        }

        val observer = LifecycleEventObserver { _, _ ->
            updateVisibility()
        }

        lifecycle.addObserver(observer)
        updateVisibility()

        onDispose {
            lifecycle.removeObserver(observer)
            indoorViewModel.setVisible(false)
        }
    }

    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

    // Enhanced Sensing rationale dialog state.
    var showEnhanceSensingRationale by remember {
        mutableStateOf(false)
    }
    var enhancedPermissions by remember {
        mutableStateOf(EnhancedSensingPermissions())
    }

    val finishEnhancedPermissionRequest: (EnhancedSensingPermissions) -> Unit = { updated ->
        enhancedPermissions = updated
        settingsViewModel.setEnhancedSensingEnabled(updated.hasAnyGrantedCapability)
        if (mainViewModel.state.value.exposureStarted) {
            // Refresh permission-dependent registrations without restarting the exposure session.
            monitoringController.start()
        }
    }

    // Each denial continues the chain so one optional sensor never disables the others.

    // Step 3: Activity Recognition (Android 10+).
    val requestActivityRecognition =
        rememberActivityRecognitionPermissionRequester(
            onPermissionGranted = {
                finishEnhancedPermissionRequest(
                    enhancedPermissions.copy(activityRecognitionGranted = true),
                )
            },
            onPermissionDenied = {
                finishEnhancedPermissionRequest(
                    enhancedPermissions.copy(activityRecognitionGranted = false),
                )
            },
        )

    // Step 2: Camera.
    val requestCamera =
        rememberCameraPermissionRequester(
            onPermissionGranted = {
                enhancedPermissions = enhancedPermissions.copy(cameraGranted = true)
                // Camera OK. Activity Recognition only exists on Android Q+.
                if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.Q
                ) {
                    requestActivityRecognition()
                } else {
                    // Older Android: no ACTIVITY_RECOGNITION permission needed.
                    finishEnhancedPermissionRequest(
                        enhancedPermissions.copy(activityRecognitionGranted = true),
                    )
                }
            },
            onPermissionDenied = {
                enhancedPermissions = enhancedPermissions.copy(cameraGranted = false)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    requestActivityRecognition()
                } else {
                    finishEnhancedPermissionRequest(
                        enhancedPermissions.copy(activityRecognitionGranted = true),
                    )
                }
            },
        )

    // Step 1: Microphone.
    val requestMicrophone =
        rememberMicrophonePermissionRequester(
            onPermissionGranted = {
                enhancedPermissions = enhancedPermissions.copy(microphoneGranted = true)
                requestCamera()
            },
            onPermissionDenied = {
                enhancedPermissions = enhancedPermissions.copy(microphoneGranted = false)
                requestCamera()
            },
        )

    val toggleEnhancedSensing: () -> Unit = {
        val currentEnabled = settingsState.enhancedSensingEnabled
        val wantEnable = !currentEnabled

        if (wantEnable) {
            // User wants ON: show rationale dialog first.
            showEnhanceSensingRationale = true
        } else {
            // User wants OFF: stop hardware directly, no permission flow.
            settingsViewModel.setEnhancedSensingEnabled(false)
            microphoneMonitor.stop()
            // TODO later: cameraMonitor.stop()
        }
    }

    DisposableEffect(
        lifecycle,
        microphoneMonitor,
        settingsState.enhancedSensingEnabled,
    ) {
        fun updateOptionalSensors() {
            if (
                settingsState.enhancedSensingEnabled &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                microphoneMonitor.start()
            } else {
                microphoneMonitor.stop()
            }
        }

        val observer = LifecycleEventObserver { _, _ ->
            updateOptionalSensors()
        }

        lifecycle.addObserver(observer)
        updateOptionalSensors()

        onDispose {
            lifecycle.removeObserver(observer)
            microphoneMonitor.stop()
        }
    }

    LaunchedEffect(settingsState.notificationsEnabled) {
        indoorViewModel.setNotificationsEnabled(settingsState.notificationsEnabled)
    }

    val mainState by mainViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(mainState.devModeEnabled) {
        if (!mainState.devModeEnabled) {
            indoorViewModel.setDemoEnabled(false)
        }
    }

    val forecastState by forecastViewModel.state.collectAsStateWithLifecycle()
    // Pull-to-refresh spinner: on from the pull until the refresh it starts finishes loading.
    var pullRefreshing by remember { mutableStateOf(false) }
    // Holds the spinner briefly after loading ends, so even an instant refresh is seen to happen.
    LaunchedEffect(mainState.isLoading, pullRefreshing) {
        if (pullRefreshing && !mainState.isLoading) {
            delay(600)
            pullRefreshing = false
        }
    }
    val requestCurrentLocation =
        rememberLocationPermissionRequester(
            onPermissionGranted = {
                mainViewModel.onUseCurrentLocation()
                if (mainViewModel.state.value.exposureStarted) {
                    monitoringController.start()
                }
            },
            onPermissionDenied = mainViewModel::onLocationPermissionDenied,
        )

    LaunchedEffect(mainViewModel) {
        if (mainViewModel.state.value.locationFix == null) {
            requestCurrentLocation()
        }
    }

    UvAppTheme(
        themeMode = settingsState.themeMode,
        accent = settingsState.accent,
    ) {
        IndoorSuggestionDialog(indoorViewModel, indoorState)

        if (showEnhanceSensingRationale) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    showEnhanceSensingRationale = false
                },
                title = {
                    Text("Enable Enhanced Sensing")
                },
                text = {
                    Text(
                        "Enabling Enhanced Sensing requests permissions to improve UV " +
                            "light estimation:\n" +
                            "• Microphone: Helps detect outdoor ambient noise\n" +
                            "• Camera: Reads brightness to calibrate the ambient light " +
                            "sensor. No photos will be saved.\n" +
                            "• Activity recognition: Detects walking, stillness and " +
                            "movement to refine sun exposure estimates\n\n" +
                            "All data stays local on your device. If permissions are " +
                            "denied, enhanced sensing will be disabled, and core UV " +
                            "features still work.",
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showEnhanceSensingRationale = false
                            requestMicrophone()
                        },
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showEnhanceSensingRationale = false
                        },
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(UvTheme.background),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                if (mainState.selectedTab == Tab.SETTINGS) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        state = settingsState,
                        indoorContent = {
                            IndoorLocationsPanel(
                                indoorViewModel,
                                indoorState,
                                requestSave,
                                enableSuggestions,
                                settings = true,
                            )
                        },
                        developerContent = {
                            IndoorDemoToggle(
                                indoorViewModel,
                                indoorState,
                            )
                        },
                        onEnhancedSensingToggle = toggleEnhancedSensing,
                    )
                } else {
                    // Pull down to refresh on every page except Settings. ForecastViewModel.refresh()
                    // delegates to mainViewModel.onRefresh(); Sun log also re-anchors its weeks to today.
                    val pullState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = pullRefreshing,
                        onRefresh = {
                            pullRefreshing = true
                            if (mainState.selectedTab == Tab.SUN_LOG) mainViewModel.observeSunLogWeeks()
                            forecastViewModel.refresh()
                        },
                        modifier = Modifier.fillMaxSize(),
                        state = pullState,
                    ) {
                        // The page follows the pull and stays shifted under the spinner while refreshing.
                        Box(
                            Modifier.graphicsLayer {
                                translationY = pullState.distanceFraction * PullToRefreshDefaults.PositionalThreshold.toPx()
                            },
                        ) {
                            when (mainState.selectedTab) {
                                Tab.HOME -> HomeScreen(
                                    viewModel = mainViewModel,
                                    state = mainState,
                                    onLocate = requestCurrentLocation,
                                    indoorContent = { IndoorLocationsPanel(indoorViewModel, indoorState, requestSave, enableSuggestions) },
                                )
                                Tab.FORECAST -> ForecastScreen(
                                    state = forecastState,
                                    onSearchClick = mainViewModel::onSearchClick,
                                    onLocate = requestCurrentLocation,
                                    onSelectDay = forecastViewModel::selectDay,
                                    onSelectTime = forecastViewModel::selectTime,
                                    onCurrentTime = forecastViewModel::selectCurrentTime,
                                )
                                else -> SunLogScreen(
                                    state = mainState,
                                    onPageSettled = mainViewModel::onSunLogPageSettled,
                                    onShowTime = mainViewModel::onSunLogShowTime,
                                )
                            }
                        }
                    }
                }

                // Automatic loads show the thin top bar; a pull shows its own spinner instead.
                TopLoadingBar(mainState.isLoading && !pullRefreshing, Modifier.align(Alignment.TopCenter))

                BottomNav(
                    selected = mainState.selectedTab,
                    onSelect = mainViewModel::onTabSelected,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                if (mainState.showSearchDialog) {
                    BackHandler {
                        mainViewModel.onSearchDismiss()
                    }

                    SearchDialogOverlay(
                        query = mainState.searchQuery,
                        results = mainState.searchResults,
                        status = mainState.searchStatus,
                        onQueryChange = mainViewModel::onQueryChange,
                        onSubmit = mainViewModel::onSearchSubmit,
                        onDismiss = mainViewModel::onSearchDismiss,
                        onSelectPlace = mainViewModel::onPlaceSelected,
                        onUseCurrentLocation = requestCurrentLocation,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxSize()
                            .padding(bottom = 64.dp),
                    )
                }
            }
        }
    }
}
