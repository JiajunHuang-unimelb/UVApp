package com.example.uvapp

import com.example.uvapp.domain.model.ForecastDay
import com.example.uvapp.domain.model.HourlyUv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForecastDayTest {

    private fun day(hourly: List<HourlyUv>) = ForecastDay(
        weekday = "Wed",
        dayOfMonth = 13,
        maxUv = 8.5,
        sunriseMinutes = 403,
        sunsetMinutes = 1211,
        hourly = hourly,
    )

    @Test
    fun `uvAt interpolates between hourly points (14_30 between 14 and 15)`() {
        val d = day(listOf(HourlyUv(14.0, 8.2), HourlyUv(15.0, 7.7)))
        assertEquals(7.95, d.uvAt(14.5)!!, 0.001)
    }

    @Test
    fun `uvAt returns the exact hourly value for integral hours`() {
        val d = day(listOf(HourlyUv(12.0, 8.5), HourlyUv(13.0, 8.5)))
        assertEquals(8.5, d.uvAt(12.0)!!, 0.001)
        assertEquals(8.5, d.uvAt(13.0)!!, 0.001)
    }

    @Test
    fun `uvAt clamps to the nearest point when only one side has data`() {
        val d = day(listOf(HourlyUv(14.0, 8.2)))
        assertEquals(8.2, d.uvAt(3.0)!!, 0.001)
        assertEquals(8.2, d.uvAt(2.5)!!, 0.001)
    }

    @Test
    fun `uvAt returns null when there is no data at all`() {
        val d = day(emptyList())
        assertNull(d.uvAt(14.5))
    }

    @Test
    fun `uvAt clamps to the nearest point at the window edges`() {
        val d = day(listOf(HourlyUv(7.0, 4.0)))
        // 06:30 -> between 06 (null) and 07 -> falls back to 07's value.
        assertEquals(4.0, d.uvAt(6.5)!!, 0.001)
    }

    @Test
    fun `uvAt handles half-hour data points exactly`() {
        val d = day(listOf(HourlyUv(6.5, 3.2), HourlyUv(7.0, 4.0), HourlyUv(20.5, 1.0)))
        assertEquals(3.2, d.uvAt(6.5)!!, 0.001)
        assertEquals(1.0, d.uvAt(20.5)!!, 0.001)
        assertEquals(3.6, d.uvAt(6.75)!!, 0.001)
    }
}
