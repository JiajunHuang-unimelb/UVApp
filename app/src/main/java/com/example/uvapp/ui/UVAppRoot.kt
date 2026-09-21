package com.example.uvapp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.uvapp.data.preferences.DataStoreIndoorLocationRepository
import com.example.uvapp.platform.alerts.IndoorSuggestionNotifier
import com.example.uvapp.viewmodel.IndoorLocationsViewModel
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uvapp.data.nominatim.PlaceRepositoryFactory
import com.example.uvapp.data.preferences.DataStoreUserPreferencesRepository
import com.example.uvapp.data.repository.UvRepositoryFactory
import com.example.uvapp.platform.alerts.AndroidExposureAlertGateway
import com.example.uvapp.platform.environment.MockEnvironmentContextProvider
import com.example.uvapp.platform.location.FusedCurrentLocationProvider
import com.example.uvapp.ui.components.BottomNav
import com.example.uvapp.ui.components.RefreshButton
import com.example.uvapp.ui.components.SearchDialogOverlay
import com.example.uvapp.ui.components.TopLoadingBar
import com.example.uvapp.ui.location.rememberLocationPermissionRequester
import com.example.uvapp.ui.screens.ForecastScreen
import com.example.uvapp.ui.screens.HomeScreen
import com.example.uvapp.ui.screens.SettingsScreen
import com.example.uvapp.ui.theme.UvAppTheme
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.ForecastViewModel
import com.example.uvapp.viewmodel.MainViewModel
import com.example.uvapp.viewmodel.SettingsViewModel
import com.example.uvapp.viewmodel.Tab

/**
 * App shell: theme wiring, shared chrome (loading bar, refresh, bottom nav),
 * tab content and the search-dialog overlay. ViewModels are activity-scoped;
 * Home and Forecast share the same source of truth via MainViewModel.
 */
@Composable
fun UVAppRoot() {
    val applicationContext = LocalContext.current.applicationContext
    val locationProvider =
        remember(applicationContext) { FusedCurrentLocationProvider(applicationContext) }
    val forecastRepository =
        remember(applicationContext) { UvRepositoryFactory.create(applicationContext) }
    val placeRepository =
        remember(applicationContext) { PlaceRepositoryFactory.create(applicationContext) }
    val preferencesRepository =
        remember(applicationContext) { DataStoreUserPreferencesRepository(applicationContext) }
    val environmentContextProvider = remember { MockEnvironmentContextProvider() }
    val alertGateway =
        remember(applicationContext) { AndroidExposureAlertGateway(applicationContext) }
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
            alertGateway = alertGateway,
        )
    }
    val forecastViewModel: ForecastViewModel = viewModel {
        ForecastViewModel(settingsViewModel, mainViewModel)
    }

    val indoorRepository = remember { DataStoreIndoorLocationRepository(applicationContext) }
    val notifier = remember { IndoorSuggestionNotifier(applicationContext) }
    val indoorViewModel: IndoorLocationsViewModel = viewModel {
        IndoorLocationsViewModel(indoorRepository, FusedCurrentLocationProvider(applicationContext, freshOnly = true), mainViewModel, notifier::show)
    }
    val indoorState by indoorViewModel.state.collectAsStateWithLifecycle()
    val requestSave = rememberLocationPermissionRequester(onPermissionGranted = indoorViewModel::requestSave, onPermissionDenied = { indoorViewModel.permissionDenied() })
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { indoorViewModel.enableSuggestions(true) }
    val enableSuggestions: () -> Unit = {
        if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        else indoorViewModel.enableSuggestions(true)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, indoorViewModel) {
        fun updateVisibility() = indoorViewModel.setVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, _ -> updateVisibility() }
        lifecycle.addObserver(observer)
        updateVisibility()
        onDispose { lifecycle.removeObserver(observer); indoorViewModel.setVisible(false) }
    }
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(settingsState.notificationsEnabled) { indoorViewModel.setNotificationsEnabled(settingsState.notificationsEnabled) }
    val mainState by mainViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(mainState.devModeEnabled) { if (!mainState.devModeEnabled) indoorViewModel.setDemoEnabled(false) }
    val forecastState by forecastViewModel.state.collectAsStateWithLifecycle()
    val requestCurrentLocation =
        rememberLocationPermissionRequester(
            onPermissionGranted = mainViewModel::onUseCurrentLocation,
            onPermissionDenied = mainViewModel::onLocationPermissionDenied,
        )

    LaunchedEffect(mainViewModel) {
        if (mainViewModel.state.value.locationFix == null) requestCurrentLocation()
    }

    UvAppTheme(themeMode = settingsState.themeMode, accent = settingsState.accent) {
        IndoorSuggestionDialog(indoorViewModel, indoorState)
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
                when (mainState.selectedTab) {
                    Tab.HOME -> HomeScreen(
                        viewModel = mainViewModel,
                        state = mainState,
                        onLocate = requestCurrentLocation,
                        indoorContent = { IndoorLocationsPanel(indoorViewModel, indoorState, requestSave, enableSuggestions, developerMode = mainState.devModeEnabled) },
                    )
                    Tab.FORECAST -> ForecastScreen(
                        state = forecastState,
                        onSearchClick = mainViewModel::onSearchClick,
                        onLocate = requestCurrentLocation,
                        onSelectDay = forecastViewModel::selectDay,
                        onSelectTime = forecastViewModel::selectTime,
                        onCurrentTime = forecastViewModel::selectCurrentTime,
                    )
                    Tab.SETTINGS -> SettingsScreen(settingsViewModel, settingsState, indoorContent = { IndoorLocationsPanel(indoorViewModel, indoorState, requestSave, enableSuggestions, settings = true) })
                }

                TopLoadingBar(mainState.isLoading, Modifier.align(Alignment.TopCenter))
                RefreshButton(
                    isLoading = mainState.isLoading,
                    // ForecastViewModel.refresh() delegates to mainViewModel.onRefresh() —
                    // calling both here would fire the same refresh twice.
                    onClick = forecastViewModel::refresh,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 16.dp),
                )

                BottomNav(
                    selected = mainState.selectedTab,
                    onSelect = mainViewModel::onTabSelected,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                if (mainState.showSearchDialog) {
                    BackHandler { mainViewModel.onSearchDismiss() }
                    SearchDialogOverlay(
                        query = mainState.searchQuery,
                        onQueryChange = mainViewModel::onQueryChange,
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
