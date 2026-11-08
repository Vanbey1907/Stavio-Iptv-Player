package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ScreenState
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.ColorAccentPrime

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val isScreenLocked by viewModel.isScreenLocked.collectAsStateWithLifecycle()
    val videoScaleMode by viewModel.videoScaleMode.collectAsStateWithLifecycle()

    var showScaleModeDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Text(
                text = "Ayarlar",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
            )
        }

        item { SettingsSectionTitle("Oynatıcı") }
        item {
            SettingsCard {
                SettingsSwitchItem(
                    icon = Icons.Default.ScreenLockPortrait,
                    title = "Ekranı Uyanık Tut",
                    subtitle = "Oynatma sırasında cihaz ekranının kapanmasını engeller",
                    checked = isScreenLocked,
                    onCheckedChange = { viewModel.isScreenLocked.value = it }
                )
                Divider(color = TextSecondary.copy(alpha = 0.2f))
                SettingsItem(
                    icon = Icons.Default.AspectRatio,
                    title = "Video Boyutu (Ölçekleme)",
                    subtitle = "Mevcut: $videoScaleMode",
                    onClick = { showScaleModeDialog = true }
                )
            }
        }

        item { SettingsSectionTitle("Sağlayıcı ve Listeler") }
        item {
            SettingsCard {
                SettingsItem(
                    icon = Icons.Default.List,
                    title = "Oynatma Listelerini Yönet",
                    subtitle = "Mevcut M3U listelerini görüntüle, ekle veya sil",
                    onClick = { viewModel.currentScreen.value = ScreenState.PLAYLISTS }
                )
                Divider(color = TextSecondary.copy(alpha = 0.2f))
                SettingsItem(
                    icon = Icons.Default.Refresh,
                    title = "Listeyi Yenile",
                    subtitle = "Şu anki aktif listeyi tekrar eşitle",
                    onClick = { /* Reload current playlist */ }
                )
                Divider(color = TextSecondary.copy(alpha = 0.2f))
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Önbelleği Temizle",
                    subtitle = "Tüm veritabanını ve içerikleri sıfırla",
                    onClick = { viewModel.resetDatabase() },
                    isDestructive = true
                )
            }
        }

        item { SettingsSectionTitle("Uygulama") }
        item {
            SettingsCard {
                SettingsItem(
                    icon = Icons.Default.ColorLens,
                    title = "Tema",
                    subtitle = "Stavio Karanlık Tema (Varsayılan)",
                    onClick = { }
                )
                Divider(color = TextSecondary.copy(alpha = 0.2f))
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "Dil",
                    subtitle = "Sistem Varsayılanı (Türkçe)",
                    onClick = { }
                )
                Divider(color = TextSecondary.copy(alpha = 0.2f))
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Stavio Hakkında",
                    subtitle = "Sürüm 1.0.0 (IPTV & VOD Player)",
                    onClick = { }
                )
            }
        }
    }

    if (showScaleModeDialog) {
        AlertDialog(
            onDismissRequest = { showScaleModeDialog = false },
            title = { Text("Video Ölçekleme Modu", color = TextPrimary) },
            text = {
                Column {
                    listOf("Fit", "Fill", "Zoom").forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.videoScaleMode.value = mode
                                    showScaleModeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = mode,
                                color = if (videoScaleMode == mode) ColorAccentPrime else TextPrimary,
                                fontWeight = if (videoScaleMode == mode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = SurfaceDark
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = ColorAccentPrime,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) Color(0xFFE53935) else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                color = if (isDestructive) Color(0xFFE53935) else TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ColorAccentPrime,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceDark
            )
        )
    }
}

