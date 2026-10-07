package com.example.uvapp.domain.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorSampleGateTest {
    @Test
    fun `first sample is accepted immediately`() {
        val gate = SensorSampleGate(500L) { 1_000L }

        assertTrue(gate.tryAcquire())
    }

    @Test
    fun `samples are limited until interval boundary`() {
        var now = 1_000L
        val gate = SensorSampleGate(500L) { now }

        assertTrue(gate.tryAcquire())
        now = 1_499L
        assertFalse(gate.tryAcquire())
        now = 1_500L
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun `clock rollback starts a new interval`() {
        var now = 5_000L
        val gate = SensorSampleGate(500L) { now }
        assertTrue(gate.tryAcquire())

        now = 4_000L
        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
    }

    @Test
    fun `invalid interval and clock are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SensorSampleGate(0L) }
        assertFalse(SensorSampleGate(500L) { -1L }.tryAcquire())
    }
}
