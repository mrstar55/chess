package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioSettingsState
import com.example.data.EducationalPlaybackSpeed
import com.example.ui.theme.AppIcons
import kotlin.math.roundToInt

@Composable
fun AudioSettingsDialog(
    settings: AudioSettingsState,
    onMasterToggled: (Boolean) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onMoveSoundToggled: (Boolean) -> Unit,
    onCaptureSoundToggled: (Boolean) -> Unit,
    onSpecialMoveSoundToggled: (Boolean) -> Unit,
    onCheckMateSoundToggled: (Boolean) -> Unit,
    onExerciseFeedbackSoundToggled: (Boolean) -> Unit,
    onPlayTestSound: () -> Unit,
    showMoveTrail: Boolean = true,
    onShowMoveTrailToggled: (Boolean) -> Unit = {},
    playbackSpeed: EducationalPlaybackSpeed = EducationalPlaybackSpeed.NORMAL,
    onPlaybackSpeedSelected: (EducationalPlaybackSpeed) -> Unit = {},
    onDismiss: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("audio_settings_dialog"),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (settings.soundEnabled) AppIcons.VolumeUp else AppIcons.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "تنظیمات صدای شطرنج و بازپخش آموزشی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Master Toggle: فعال بودن صداها
                    AudioSettingToggleRow(
                        label = "فعال بودن صداها",
                        checked = settings.soundEnabled,
                        enabled = true,
                        onCheckedChange = onMasterToggled,
                        switchTag = "audio_master_switch"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // 2. Volume Slider: 0% .. 100%
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "میزان بلندی صدا",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${settings.volumePercent}٪",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("audio_volume_label")
                            )
                        }
                        Slider(
                            value = settings.volumePercent.toFloat(),
                            onValueChange = { onVolumeChanged(it.roundToInt()) },
                            valueRange = 0f..100f,
                            enabled = settings.soundEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("audio_volume_slider")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // 3. Category Switches
                    AudioSettingToggleRow(
                        label = "صدای حرکت مهره‌ها",
                        checked = settings.moveSoundEnabled,
                        enabled = settings.soundEnabled,
                        onCheckedChange = onMoveSoundToggled,
                        switchTag = "audio_cat_move_switch"
                    )

                    AudioSettingToggleRow(
                        label = "صدای گرفتن مهره",
                        checked = settings.captureSoundEnabled,
                        enabled = settings.soundEnabled,
                        onCheckedChange = onCaptureSoundToggled,
                        switchTag = "audio_cat_capture_switch"
                    )

                    AudioSettingToggleRow(
                        label = "صدای حرکت‌های ویژه",
                        subtitle = "قلعه‌رفتن، آن‌پاسان و ارتقای پیاده",
                        checked = settings.specialMoveSoundEnabled,
                        enabled = settings.soundEnabled,
                        onCheckedChange = onSpecialMoveSoundToggled,
                        switchTag = "audio_cat_special_switch"
                    )

                    AudioSettingToggleRow(
                        label = "صدای کیش و مات",
                        checked = settings.checkMateSoundEnabled,
                        enabled = settings.soundEnabled,
                        onCheckedChange = onCheckMateSoundToggled,
                        switchTag = "audio_cat_checkmate_switch"
                    )

                    AudioSettingToggleRow(
                        label = "صدای بازخورد تمرین",
                        checked = settings.exerciseFeedbackSoundEnabled,
                        enabled = settings.soundEnabled,
                        onCheckedChange = onExerciseFeedbackSoundToggled,
                        switchTag = "audio_cat_exercise_switch"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // 4. Educational Move Trail Toggle: نمایش مسیر حرکت مهره‌ها
                    AudioSettingToggleRow(
                        label = "نمایش مسیر حرکت مهره‌ها",
                        subtitle = "نمایش شبح مهره در خانه مبدأ و فلش مسیر حرکت در بازپخش آموزشی",
                        checked = showMoveTrail,
                        enabled = true,
                        onCheckedChange = onShowMoveTrailToggled,
                        switchTag = "setting_show_move_trail_switch"
                    )

                    // 5. Educational Playback Speed Selector: سرعت بازپخش آموزشی
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "سرعت بازپخش آموزشی",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EducationalPlaybackSpeed.values().forEach { speed ->
                                val isSelected = playbackSpeed == speed
                                FilledTonalButton(
                                    onClick = { onPlaybackSpeedSelected(speed) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("speed_option_${speed.name}")
                                ) {
                                    Text(
                                        text = speed.labelFa,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 6. Test Sound Button
                    FilledTonalButton(
                        onClick = onPlayTestSound,
                        enabled = settings.soundEnabled && settings.volumePercent > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audio_test_sound_btn")
                    ) {
                        Icon(
                            imageVector = AppIcons.VolumeUp,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "پخش صدای آزمایشی",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("audio_dialog_close_btn")
                ) {
                    Text("بستن")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("تأیید")
                }
            }
        )
    }
}

@Composable
private fun AudioSettingToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTag: String,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(switchTag)
        )
    }
}
