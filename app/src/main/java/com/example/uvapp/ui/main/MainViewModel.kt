package com.example.uvapp.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uvapp.data.openmeteo.OpenMeteoCurrentUvRepository
import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.location.LocationUnavailableException
import com.example.uvapp.domain.repository.CurrentUvRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

data class MainUiState(
    val isLoading: Boolean = false,
    val uvIndex: Double? = null,
    val observedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val errorMessage: String? = null,
)

class MainViewModel(
    private val locationProvider: CurrentLocationProvider,
    private val repository: CurrentUvRepository = OpenMeteoCurrentUvRepository.create(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = MainUiState(isLoading = true)

            try {
                val location = locationProvider.getCurrentLocation()
                val reading =
                    repository.getCurrentUv(
                        latitude = location.latitude,
                        longitude = location.longitude,
                    )
                _uiState.value =
                    MainUiState(
                        uvIndex = reading.index,
                        observedAt = reading.observedAt,
                        latitude = location.latitude,
                        longitude = location.longitude,
                    )
            } catch (error: LocationUnavailableException) {
                _uiState.value = MainUiState(errorMessage = error.message)
            } catch (_: SecurityException) {
                _uiState.value = MainUiState(errorMessage = "Location permission is required")
            } catch (error: IOException) {
                _uiState.value = MainUiState(errorMessage = error.message ?: "Network unavailable")
            } catch (error: HttpException) {
                _uiState.value = MainUiState(errorMessage = "Open-Meteo returned HTTP ${error.code()}")
            } catch (error: SerializationException) {
                _uiState.value = MainUiState(errorMessage = error.message ?: "Invalid API response")
            }
        }
    }
}
