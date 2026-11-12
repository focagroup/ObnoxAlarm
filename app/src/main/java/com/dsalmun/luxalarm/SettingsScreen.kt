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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun rememberLightSensorValue(): Float {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lightLevel by remember { mutableFloatStateOf(0f) }

    DisposableEffect(lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_LIGHT) {
                        lightLevel = event.values[0]
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    lightSensor?.let {
                        sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    sensorManager.unregisterListener(listener)
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            sensorManager.unregisterListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return lightLevel
}

/** Both dependencies are defaulted parameters so a test can supply its own. */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    settingsManager: SettingsManager = remember { AppContainer.settingsManager },
    currentLightLevel: Float = rememberLightSensorValue(),
) {
    val requiredLuxLevel by settingsManager.requiredLuxLevel.collectAsState()
    var sliderValue by remember(requiredLuxLevel) { mutableFloatStateOf(requiredLuxLevel) }
    val minLightMinutes by settingsManager.minLightMinutes.collectAsState()
    var minutesValue by remember(minLightMinutes) { mutableIntStateOf(minLightMinutes) }
    val obnoxiousMode by settingsManager.obnoxiousMode.collectAsState()

    SettingsScreenContent(
        requiredLuxLevel = sliderValue,
        currentLightLevel = currentLightLevel,
        onBackClick = onBackClick,
        onLuxLevelChange = { sliderValue = it },
        onLuxLevelChangeFinished = { settingsManager.setRequiredLuxLevel(sliderValue) },
        minLightMinutes = minutesValue,
        onMinLightMinutesChange = { minutesValue = it },
        onMinLightMinutesChangeFinished = { settingsManager.setMinLightMinutes(minutesValue) },
        obnoxiousMode = obnoxiousMode,
        onEnableObnoxiousMode = { settingsManager.enableObnoxiousMode() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    requiredLuxLevel: Float,
    currentLightLevel: Float,
    onBackClick: () -> Unit,
    onLuxLevelChange: (Float) -> Unit,
    onLuxLevelChangeFinished: () -> Unit,
    minLightMinutes: Int = SettingsManager.DEFAULT_MIN_LIGHT_MINUTES,
    onMinLightMinutesChange: (Int) -> Unit = {},
    onMinLightMinutesChangeFinished: () -> Unit = {},
    obnoxiousMode: Boolean = false,
    onEnableObnoxiousMode: () -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24px),
                            contentDescription = "Back",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            LuxLevelSetting(
                currentValue = requiredLuxLevel,
                currentLightLevel = currentLightLevel,
                onValueChange = onLuxLevelChange,
                onValueChangeFinished = onLuxLevelChangeFinished,
            )
            Spacer(modifier = Modifier.height(16.dp))
            MinLightDurationSetting(
                minutes = minLightMinutes,
                onValueChange = onMinLightMinutesChange,
                onValueChangeFinished = onMinLightMinutesChangeFinished,
            )
            Spacer(modifier = Modifier.height(16.dp))
            ObnoxiousModeSetting(enabled = obnoxiousMode, onEnable = onEnableObnoxiousMode)
        }
    }
}

@Composable
internal fun LuxLevelSetting(
    currentValue: Float,
    currentLightLevel: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val meetsThreshold = currentLightLevel >= currentValue

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Required Light Level",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The minimum light level (in lux) required to turn off the alarm.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (meetsThreshold) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Current Light Level",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${currentLightLevel.toInt()} lux",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (meetsThreshold) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${SettingsManager.MIN_LUX_LEVEL.toInt()}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Slider(
                    value = currentValue,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = SettingsManager.MIN_LUX_LEVEL..SettingsManager.MAX_LUX_LEVEL,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    text = "${SettingsManager.MAX_LUX_LEVEL.toInt()}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "${currentValue.toInt()} lux",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    if (meetsThreshold) "✓ Current light level meets threshold"
                    else "Current light is below threshold",
                fontSize = 12.sp,
                color =
                    if (meetsThreshold) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun MinLightDurationSetting(
    minutes: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Minimum Light Duration",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "The light has to stay on this long without a break before the alarm can " +
                        "be turned off. A flashlight won't cut it.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${SettingsManager.MIN_LIGHT_MINUTES}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { onValueChange(kotlin.math.round(it).toInt()) },
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange =
                        SettingsManager.MIN_LIGHT_MINUTES.toFloat()..SettingsManager
                                .MAX_LIGHT_MINUTES
                                .toFloat(),
                    steps =
                        SettingsManager.MAX_LIGHT_MINUTES - SettingsManager.MIN_LIGHT_MINUTES - 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    text = "${SettingsManager.MAX_LIGHT_MINUTES}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "$minutes min",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun ObnoxiousModeSetting(enabled: Boolean, onEnable: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "tornado",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        if (enabled) "a wise choice has been made"
                        else "Makes the alarm a lot more convincing.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Checkbox(
                checked = enabled,
                onCheckedChange = { if (it && !enabled) onEnable() },
                enabled = !enabled,
            )
        }
    }
}
