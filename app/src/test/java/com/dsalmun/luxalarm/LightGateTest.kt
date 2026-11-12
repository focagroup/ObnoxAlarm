/*
 * This file is part of Lux Alarm, authored by Daniel Salmun.
 *
 * Lux Alarm is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Lux Alarm is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Lux Alarm.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.dsalmun.luxalarm

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class LightGateTest {
    private companion object {
        const val REQUIRED = 50f
        const val BRIGHT = 300f
        const val DARK = 5f
        const val HOLD = 10 * 60_000L
    }

    private fun gate(obnoxious: Boolean = false) = LightGate(REQUIRED, HOLD, obnoxious)

    @Test
    fun beforeAnyLight_theAlarmRingsAndCannotStop() {
        val state = gate().onLight(DARK, 0)

        assertEquals(GateSound.ALARM, state.sound)
        assertFalse(state.canStop)
        assertNull(state.holdRemainingMs)
    }

    @Test
    fun lightOn_silencesTheAlarmButDoesNotAllowStoppingYet() {
        val g = gate()
        val state = g.onLight(BRIGHT, 0)

        assertEquals(GateSound.SILENT, state.sound)
        assertFalse(state.canStop, "A flashlight for a moment must not be enough")
        assertEquals(HOLD, state.holdRemainingMs)
        assertEquals(60_000L, g.update(HOLD - 60_000L).holdRemainingMs)
    }

    @Test
    fun afterTheFullHold_theAlarmCanStop() {
        val g = gate()
        g.onLight(BRIGHT, 0)

        assertFalse(g.update(HOLD - 1).canStop)
        assertTrue(g.update(HOLD).canStop)
    }

    @Test
    fun aShortDip_isForgivenAndTheHoldKeepsCounting() {
        val g = gate()
        g.onLight(BRIGHT, 0)
        g.onLight(DARK, 60_000)
        assertEquals(GateSound.SILENT, g.update(64_000).sound)

        val state = g.onLight(BRIGHT, 64_000)

        assertEquals(HOLD - 64_000, state.holdRemainingMs)
    }

    @Test
    fun aLongDip_isAViolationAndTheHoldStartsOver() {
        val g = gate()
        g.onLight(BRIGHT, 0)
        g.onLight(DARK, 60_000)

        val violated = g.update(65_000)
        assertEquals(GateSound.ALARM, violated.sound, "Normal mode rings the alarm again")
        assertTrue(violated.violated)

        val back = g.onLight(BRIGHT, 70_000)
        assertEquals(GateSound.SILENT, back.sound)
        assertEquals(HOLD, back.holdRemainingMs, "The hold has to be one unbroken stretch")
    }

    @Test
    fun obnoxious_beforeAnyLight_ringsTheAlarm() {
        assertEquals(GateSound.ALARM, gate(obnoxious = true).onLight(DARK, 0).sound)
    }

    @Test
    fun obnoxious_aViolationBruhsEveryFiveSecondsThreeTimes() {
        val g = gate(obnoxious = true)
        g.onLight(BRIGHT, 0)
        g.onLight(DARK, 60_000)

        assertEquals(GateSound.SILENT, g.update(64_999).sound)
        assertEquals(1, g.update(65_000).bruhCount)
        assertEquals(GateSound.PUNISH, g.update(65_000).sound)
        assertEquals(1, g.update(69_999).bruhCount)
        assertEquals(2, g.update(70_000).bruhCount)
        assertEquals(3, g.update(75_000).bruhCount)
        assertEquals(3, g.update(500_000).bruhCount, "After the third, it is scream time")
    }

    @Test
    fun obnoxious_lightBack_endsThePunishment() {
        val g = gate(obnoxious = true)
        g.onLight(BRIGHT, 0)
        g.onLight(DARK, 60_000)
        g.update(90_000)

        val state = g.onLight(BRIGHT, 91_000)

        assertEquals(GateSound.SILENT, state.sound)
        assertEquals(0, state.bruhCount)
    }

    @Test
    fun goingDarkAfterTheHold_takesAwayTheStopButton() {
        val g = gate()
        g.onLight(BRIGHT, 0)
        assertTrue(g.update(HOLD).canStop)

        assertFalse(g.onLight(DARK, HOLD + 1).canStop)
    }

    @Test
    fun formatRemaining_roundsUpToWholeSeconds() {
        assertEquals("10:00", formatRemaining(HOLD))
        assertEquals("0:01", formatRemaining(1))
        assertEquals("0:00", formatRemaining(0))
    }
}
