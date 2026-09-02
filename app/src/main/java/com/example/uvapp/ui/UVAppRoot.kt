package com.example.uvapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uvapp.data.UvRepositoryProvider
import com.example.uvapp.ui.components.BottomNav
import com.example.uvapp.ui.components.RefreshButton
import com.example.uvapp.ui.components.SearchDialogOverlay
import com.example.uvapp.ui.components.TopLoadingBar
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
    val settingsViewModel: SettingsViewModel = viewModel()
    val mainViewModel: MainViewModel = viewModel {
        MainViewModel(UvRepositoryProvider.instance, settingsViewModel)
    }
    val forecastViewModel: ForecastViewModel = viewModel {
        ForecastViewModel(UvRepositoryProvider.instance, settingsViewModel, mainViewModel)
    }

    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val mainState by mainViewModel.state.collectAsStateWithLifecycle()
    val forecastState by forecastViewModel.state.collectAsStateWithLifecycle()

    UvAppTheme(themeMode = settingsState.themeMode, accent = settingsState.accent) {
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
                    Tab.HOME -> HomeScreen(mainViewModel, mainState)
                    Tab.FORECAST -> ForecastScreen(
                        state = forecastState,
                        onSearchClick = mainViewModel::onSearchClick,
                        onLocate = mainViewModel::onLocate,
                        onSelectDay = forecastViewModel::selectDay,
                        onSelectTime = forecastViewModel::selectTime,
                    )
                    Tab.SETTINGS -> SettingsScreen(settingsViewModel, settingsState)
                }

                TopLoadingBar(mainState.isLoading, Modifier.align(Alignment.TopCenter))
                RefreshButton(
                    isLoading = mainState.isLoading,
                    onClick = {
                        mainViewModel.onRefresh()
                        forecastViewModel.refresh()
                    },
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
                        onUseCurrentLocation = mainViewModel::onUseCurrentLocation,
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
