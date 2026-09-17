package com.example.uvapp.domain.pocket

import kotlinx.coroutines.flow.Flow

/** Android-free boundary implemented by the sensor integration layer. */
interface PocketSensorSource {
    val samples: Flow<PocketSensorSample>
}
