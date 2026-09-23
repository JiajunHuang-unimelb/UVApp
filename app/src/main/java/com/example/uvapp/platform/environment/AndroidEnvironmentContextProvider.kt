package com.example.uvapp.platform.environment

import com.example.uvapp.domain.environment.EnvironmentContextProvider
import com.example.uvapp.domain.environment.EnvironmentSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-scoped bridge from the monitoring service to exposure logic. */
object AndroidEnvironmentContextProvider : EnvironmentContextProvider {
    private val mutable =
        MutableStateFlow(
            EnvironmentSample(
                lux = UNAVAILABLE_LUX,
                nearIndoorLocation = false,
            ),
        )

    override val samples: StateFlow<EnvironmentSample> = mutable.asStateFlow()

    fun updateLux(lux: Int) {
        mutable.update { it.copy(lux = lux.coerceIn(0, MAX_LUX)) }
    }

    fun updateIndoorProximity(isNear: Boolean) {
        mutable.update { it.copy(nearIndoorLocation = isNear) }
    }

    /** Unknown evidence is treated as outdoor/high-light so stale indoor pauses can clear. */
    fun markUnavailable() {
        mutable.value =
            EnvironmentSample(
                lux = UNAVAILABLE_LUX,
                nearIndoorLocation = false,
            )
    }

    private const val MAX_LUX = 100_000
    private const val UNAVAILABLE_LUX = MAX_LUX
}
