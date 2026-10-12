package com.example.uvapp.domain.environment

import com.example.uvapp.domain.model.IndoorLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndoorProximityRuleTest {
    private val home = IndoorLocation("home", "Home", -37.8539155, 144.7316696, 0L)
    private val atHome = IndoorProximityRule.HeldFix(-37.853999, 144.731684, accuracyMeters = 9.7f, elapsedMillis = 10_000L)

    private fun isNear(
        fix: IndoorProximityRule.HeldFix?,
        now: Long,
        stationary: Boolean,
    ) = IndoorProximityRule.isNear(
        fix = fix,
        savedLocations = listOf(home),
        nowElapsedMillis = now,
        userStationary = stationary,
        maxAccuracyMeters = 50f,
        maxAgeMillis = 75_000L,
    )

    @Test
    fun `coarse fix is ignored so the held fix keeps the user near while stationary`() {
        // The 206 m fix seen on the device is rejected before it can replace the held one.
        assertFalse(LocationFixValidator.shouldReplace(accuracyMeters = 206.8f, maxAccuracyMeters = 50f))

        assertTrue(isNear(atHome, now = 20_000L, stationary = true))
    }

    @Test
    fun `held fix does not expire while the user has not walked`() {
        assertTrue(isNear(atHome, now = 10_000L + 10 * 60_000L, stationary = true))
    }

    @Test
    fun `held fix expires after 75 s once the user walks`() {
        assertTrue(isNear(atHome, now = 85_000L, stationary = false))
        assertFalse(isNear(atHome, now = 85_001L, stationary = false))
    }

    @Test
    fun `missing step data keeps the 75 s expiry`() {
        // The service passes stationary = false when there is no step reading.
        val stepReading: StepActivityReading? = null
        val stationary = stepReading?.activity == StepActivity.STATIONARY

        assertFalse(isNear(atHome, now = 85_001L, stationary = stationary))
    }

    @Test
    fun `precise fix outside the radius changes the answer while stationary`() {
        val awayFromHome = IndoorProximityRule.HeldFix(-37.8560, 144.7330, accuracyMeters = 8f, elapsedMillis = 200_000L)

        assertTrue(LocationFixValidator.shouldReplace(awayFromHome.accuracyMeters, maxAccuracyMeters = 50f))
        assertFalse(isNear(awayFromHome, now = 201_000L, stationary = true))
    }

    @Test
    fun `no precise fix yet is not near`() {
        assertFalse(isNear(fix = null, now = 0L, stationary = true))
    }
}
