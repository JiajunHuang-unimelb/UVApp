package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProximityDebouncerTest {
    @Test
    fun `covered state is applied immediately`() {
        val debouncer = ProximityDebouncer(clearDelayMillis = 1_000L)

        assertEquals(true, debouncer.update(isOccluded = true, nowElapsedMillis = 10_000L))
    }

    @Test
    fun `initial clear reading does not wait unnecessarily`() {
        val debouncer = ProximityDebouncer(clearDelayMillis = 1_000L)

        assertEquals(false, debouncer.update(isOccluded = false, nowElapsedMillis = 10_000L))
    }

    @Test
    fun `clear transition waits until delay boundary`() {
        val debouncer = ProximityDebouncer(clearDelayMillis = 1_000L)
        debouncer.update(isOccluded = true, nowElapsedMillis = 10_000L)

        assertEquals(true, debouncer.update(isOccluded = false, nowElapsedMillis = 11_000L))
        assertEquals(true, debouncer.currentValue(11_999L))
        assertEquals(false, debouncer.currentValue(12_000L))
    }

    @Test
    fun `covered reading cancels pending clear`() {
        val debouncer = ProximityDebouncer(clearDelayMillis = 1_000L)
        debouncer.update(isOccluded = true, nowElapsedMillis = 10_000L)
        debouncer.update(isOccluded = false, nowElapsedMillis = 11_000L)

        assertEquals(true, debouncer.update(isOccluded = true, nowElapsedMillis = 11_500L))
        assertEquals(true, debouncer.currentValue(20_000L))
    }

    @Test
    fun `repeated clear samples do not restart confirmation window`() {
        val debouncer = ProximityDebouncer(clearDelayMillis = 1_000L)
        debouncer.update(isOccluded = true, nowElapsedMillis = 10_000L)
        debouncer.update(isOccluded = false, nowElapsedMillis = 11_000L)

        assertEquals(true, debouncer.update(isOccluded = false, nowElapsedMillis = 11_500L))
        assertEquals(false, debouncer.currentValue(12_000L))
    }

    @Test
    fun `clock rollback restarts pending clear delay`() {
        val debouncer = ProximityDebouncer(clearDelayMillis = 1_000L)
        debouncer.update(isOccluded = true, nowElapsedMillis = 10_000L)
        debouncer.update(isOccluded = false, nowElapsedMillis = 11_000L)

        assertEquals(true, debouncer.currentValue(1_000L))
        assertEquals(true, debouncer.currentValue(1_999L))
        assertEquals(false, debouncer.currentValue(2_000L))
    }

    @Test
    fun `reset returns state to unavailable`() {
        val debouncer = ProximityDebouncer()
        debouncer.update(isOccluded = true, nowElapsedMillis = 1_000L)

        debouncer.reset()

        assertNull(debouncer.currentValue(2_000L))
    }

    @Test
    fun `invalid delay and time are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ProximityDebouncer(0L) }
        val debouncer = ProximityDebouncer()
        assertThrows(IllegalArgumentException::class.java) {
            debouncer.update(isOccluded = true, nowElapsedMillis = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) { debouncer.currentValue(-1L) }
    }
}
