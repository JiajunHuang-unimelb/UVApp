package com.example.uvapp

import com.example.uvapp.ui.components.formatThousands
import com.example.uvapp.ui.components.formatUv
import com.example.uvapp.ui.components.minutesToHhMm
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the shared formatting helpers (deduped from per-file copies). */
class FormatTest {

    @Test
    fun formatThousands_groupsDigits() {
        assertEquals("0", formatThousands(0))
        assertEquals("999", formatThousands(999))
        assertEquals("1 000", formatThousands(1000))
        assertEquals("38 200", formatThousands(38200))
        assertEquals("1 000 000", formatThousands(1000000))
    }

    @Test
    fun formatUv_usesOneDecimalPlace() {
        assertEquals("8.4", formatUv(8.4))
        assertEquals("11.0", formatUv(11.0))
    }

    @Test
    fun minutesToHhMm_formatsClockTime() {
        assertEquals("00:00", minutesToHhMm(0))
        assertEquals("06:30", minutesToHhMm(390))
        assertEquals("09:05", minutesToHhMm(545))
        assertEquals("20:30", minutesToHhMm(1230))
    }
}
