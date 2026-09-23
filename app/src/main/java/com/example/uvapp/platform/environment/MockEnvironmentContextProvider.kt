package com.example.uvapp.platform.environment

import com.example.uvapp.domain.environment.EnvironmentContextProvider
import com.example.uvapp.domain.environment.EnvironmentSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Deterministic source for unit tests and developer-controlled environmental input. */
class MockEnvironmentContextProvider(
    initialLux: Int = 38_200,
    initiallyNearIndoorLocation: Boolean = false,
) : EnvironmentContextProvider {
    private val _samples =
        MutableStateFlow(
            EnvironmentSample(
                lux = initialLux,
                nearIndoorLocation = initiallyNearIndoorLocation,
            ),
        )

    override val samples: StateFlow<EnvironmentSample> = _samples.asStateFlow()

    fun setLux(lux: Int) {
        _samples.update { it.copy(lux = lux.coerceIn(0, MAX_LUX)) }
    }

    fun setNearIndoorLocation(isNear: Boolean) {
        _samples.update { it.copy(nearIndoorLocation = isNear) }
    }

    private companion object {
        const val MAX_LUX = 100_000
    }
}
