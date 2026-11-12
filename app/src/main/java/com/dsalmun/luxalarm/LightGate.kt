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

/** What the alarm should be sounding right now. */
enum class GateSound {
    /** The regular alarm sound (tornado in obnoxious mode). */
    ALARM,
    SILENT,
    /** Obnoxious mode only: the violation punishment, see [GateState.bruhCount]. */
    PUNISH,
}

data class GateState(
    val sound: GateSound = GateSound.ALARM,
    /** True once the light has been held long enough and is still on. */
    val canStop: Boolean = false,
    /** Time still to hold the light, or null while the light is not being held. */
    val holdRemainingMs: Long? = null,
    /** True while the light was held earlier but has now been off for too long. */
    val violated: Boolean = false,
    /** During [GateSound.PUNISH]: how many bruhs are due so far (1..3); 3 means scream next. */
    val bruhCount: Int = 0,
)

/**
 * Decides, from the light level over time, whether the alarm may be turned off and what it
 * should sound like. The light must stay above the threshold for [minHoldMs] in one go, so a
 * flashlight pointed at the sensor for a few seconds is not enough.
 *
 * A dip shorter than [graceMs] is forgiven (a hand passing over the sensor). A longer one is a
 * violation: the hold starts over, and the alarm comes back — in obnoxious mode as a bruh every
 * [graceMs], three times, then a scream until the light is back.
 */
class LightGate(
    private val requiredLux: Float,
    private val minHoldMs: Long,
    private val obnoxious: Boolean,
    private val graceMs: Long = 5_000,
) {
    private var lux = 0f
    private var everLit = false
    private var holdStart: Long? = null
    private var darkSince: Long? = null

    fun onLight(lux: Float, now: Long): GateState {
        this.lux = lux
        return update(now)
    }

    fun update(now: Long): GateState {
        val lit = lux >= requiredLux
        if (lit) {
            everLit = true
            darkSince = null
            if (holdStart == null) holdStart = now
        } else if (everLit) {
            val since = darkSince ?: now.also { darkSince = it }
            if (now - since >= graceMs) holdStart = null
        }

        val start = holdStart
        val held = start?.let { now - it } ?: 0L
        val canStop = lit && start != null && held >= minHoldMs
        val holdRemaining = start?.let { (minHoldMs - held).coerceAtLeast(0) }

        if (!everLit) return GateState(GateSound.ALARM)

        val dark = darkSince
        val violatedFor = if (dark != null && start == null) now - dark else null
        if (violatedFor == null) {
            return GateState(GateSound.SILENT, canStop, holdRemaining)
        }
        if (!obnoxious) {
            return GateState(GateSound.ALARM, violated = true)
        }
        val bruhs = (violatedFor / graceMs).toInt().coerceIn(1, 3)
        return GateState(GateSound.PUNISH, violated = true, bruhCount = bruhs)
    }
}
