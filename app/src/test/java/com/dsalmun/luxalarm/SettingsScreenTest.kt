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

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dsalmun.luxalarm.ui.theme.LuxAlarmTheme
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Against a real [SettingsManager], so the slider-to-storage round trip is covered, not mocked. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("lux_alarm_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences(SettingsManager.LOCK_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        settingsManager = SettingsManager(context)
    }

    @Test
    fun theInitialSliderPositionComesFromStorage() {
        settingsManager.setRequiredLuxLevel(250f)

        setContent()

        composeRule.onNodeWithText("250 lux").assertIsDisplayed()
    }

    @Test
    fun releasingTheSlider_persistsTheThreshold() {
        setContent()

        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(300f) }

        assertEquals(300f, settingsManager.getRequiredLuxLevel())
        assertEquals(300f, settingsManager.requiredLuxLevel.value)
        assertEquals(
            300f,
            SettingsManager(context).getRequiredLuxLevel(),
            "A freshly built manager must see the stored value",
        )
    }

    @Test
    fun theDisplayedThresholdFollowsTheSlider() {
        setContent()

        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(300f) }

        composeRule.onNodeWithText("300 lux").assertIsDisplayed()
    }

    @Test
    fun releasingTheDurationSlider_persistsTheMinutes() {
        setContent()

        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[1]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(25f) }

        composeRule.onNodeWithText("25 min").assertIsDisplayed()
        assertEquals(25, SettingsManager(context).getMinLightMinutes())
    }

    @Test
    fun theDurationDefaultsToTenMinutes() {
        setContent()

        composeRule.onNodeWithText("10 min").assertIsDisplayed()
    }

    @Test
    fun checkingTornado_locksItWithoutAPopup() {
        setContent()
        composeRule.onNodeWithText("tornado").assertIsDisplayed()
        composeRule.onNodeWithText("Makes the alarm a lot more convincing.").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsOff()

        composeRule.onNode(isToggleable()).performClick()

        assertTrue(SettingsManager(context).isObnoxiousMode(), "It has to survive a restart")
        composeRule.onNode(isToggleable()).assertIsOn().assertIsNotEnabled()
        composeRule.onNodeWithText("a wise choice has been made").assertIsDisplayed()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun obnoxiousMode_isOffByDefault() {
        assertFalse(settingsManager.isObnoxiousMode())
    }

    private fun setContent(currentLightLevel: Float = 0f) {
        composeRule.setContent {
            // dynamicColor defaults to true and would read the host's system palette.
            LuxAlarmTheme(dynamicColor = false) {
                SettingsScreen(
                    onBackClick = {},
                    settingsManager = settingsManager,
                    currentLightLevel = currentLightLevel,
                )
            }
        }
    }
}
