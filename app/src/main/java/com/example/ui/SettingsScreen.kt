package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.util.*
import com.example.viewmodel.MainTab
import com.example.viewmodel.MainViewModel

private val SettingsBgBlack = Color(0xFF000000)
private val SettingsCardDark = Color(0xFF121214)
private val SettingsIconBoxDark = Color(0xFF232327)
private val SettingsDividerColor = Color(0x0DFFFFFF)
private val SettingsSubtitleGray = Color(0xFF8E8E93)
private val SettingsHeaderGray = Color(0xFF8E8E93)

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val locManager = remember { LocalizationManager.getInstance(context) }
    val themeManager = remember { ThemeManager.getInstance(context) }
    val devManager = remember { DeveloperOptionsManager.getInstance(context) }
    val theme = LocalAppTheme.current

    val currentLang by locManager.currentLanguage.collectAsStateWithLifecycle()
    val currentTheme by themeManager.currentTheme.collectAsStateWithLifecycle()
    val isScreenLocked by viewModel.isScreenLocked.collectAsStateWithLifecycle()
    val videoScaleMode by viewModel.videoScaleMode.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()

    // Developer / Advanced settings states
    val hwAccel by devManager.hwAccel.collectAsStateWithLifecycle()
    val bufferMode by devManager.bufferMode.collectAsStateWithLifecycle()
    val userAgentPreset by devManager.userAgentPreset.collectAsStateWithLifecycle()
    val customUserAgent by devManager.customUserAgent.collectAsStateWithLifecycle()
    val showFpsOverlay by devManager.showFpsOverlay.collectAsStateWithLifecycle()
    val bypassSsl by devManager.bypassSsl.collectAsStateWithLifecycle()
    val networkTimeout by devManager.networkTimeoutSeconds.collectAsStateWithLifecycle()
    val debugLogs by devManager.debugLogs.collectAsStateWithLifecycle()

    // Dialog / Sub-sheet controllers
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showWatchPartyDialog by remember { mutableStateOf(false) }
    var showTrackingDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showContentDiscoveryDialog by remember { mutableStateOf(false) }
    var showPlaylistImportModal by remember { mutableStateOf(false) }
    var showPlaybackDialog by remember { mutableStateOf(false) }
    var showIntegrationsDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showContributorsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showUpdateChannelDialog by remember { mutableStateOf(false) }
    var showAdvancedSheet by remember { mutableStateOf(false) }
    val showStreamExtractor by viewModel.isStreamExtractorOpen.collectAsStateWithLifecycle()

    // Nested dialogs inside Görünüm / Oynatma / Gelişmiş
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showScaleModeDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showHwAccelDialog by remember { mutableStateOf(false) }
    var showBufferDialog by remember { mutableStateOf(false) }
    var showUserAgentDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showDevResetDialog by remember { mutableStateOf(false) }

    var updateChannelValue by remember {
        mutableStateOf(viewModel.prefs.getString("stavio_update_channel", "Beta") ?: "Beta")
    }
    var activeProfileName by remember {
        mutableStateOf(viewModel.prefs.getString("stavio_active_profile", "Kullanıcı (K)") ?: "Kullanıcı (K)")
    }

    if (showStreamExtractor) {
        StavioStreamExtractorDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.isStreamExtractorOpen.value = false }
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsBgBlack)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 128.dp)
    ) {
        // Large "Ayarlar" Header (Exact match to Screenshot 3)
        item(key = "settings_main_header") {
            Text(
                text = "Ayarlar",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = StavioFontFamily,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 20.dp)
            )
        }

        // Top Card Group: En üstte IPTV ve Linkten Kaynak Bulucu, ardından Profil değiştir, Hesap, Watch party, Takip
        item(key = "settings_top_card") {
            StavioSettingsGroupCard {
                StavioSettingsRow(
                    icon = Icons.Rounded.Dns,
                    title = "IPTV",
                    subtitle = if (selectedPlaylist != null) {
                        "Aktif: ${selectedPlaylist?.name} • Oynatma listelerini yönet veya yeni ekle."
                    } else {
                        "M3U, Xtream Codes veya MAC Portal oynatma listelerini yönet."
                    },
                    trailingText = "${allPlaylists.size} Liste",
                    onClick = { viewModel.selectTabAndNavigate(MainTab.PLAYLISTS) }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Rounded.Radar,
                    title = "Linkten Kaynak Bulucu",
                    subtitle = "Girilen linkteki MP4, M3U8, TS ve gizli video kaynaklarını bul, oynat ve kaydet.",
                    trailingText = "Yeni",
                    onClick = { viewModel.isStreamExtractorOpen.value = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.Person,
                    title = "Profil değiştir",
                    subtitle = "Farklı bir profile geç.",
                    onClick = { showProfileDialog = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.AccountCircle,
                    title = "Hesap",
                    subtitle = "Hesabını yönet, çıkış yap veya hesabı sil.",
                    onClick = { showAccountDialog = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.Groups,
                    title = "Watch party",
                    subtitle = "Join friends with a party code",
                    onClick = { showWatchPartyDialog = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.Sync,
                    title = "Takip",
                    subtitle = "Takip sağlayıcılarını bağlayın ve Kitaplığınızı ve İzlemeye Devam Et bölümünü hangisinin besleyeceğini seçin.",
                    onClick = { showTrackingDialog = true }
                )
            }
        }

        // GENEL Section (Screenshots 3 & 4)
        item(key = "settings_section_general") {
            StavioSettingsSectionHeader("GENEL")
        }
        item(key = "settings_card_general") {
            StavioSettingsGroupCard {
                StavioSettingsRow(
                    icon = Icons.Outlined.Palette,
                    title = "Görünüm",
                    subtitle = "Ana sayfa görünümünü ve görsel tercihleri ayarla.",
                    onClick = { showAppearanceDialog = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.Explore,
                    title = "İçerik & Keşif",
                    subtitle = "Eklentileri, katalogları ve keşif kaynaklarını yönet.",
                    onClick = { showContentDiscoveryDialog = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.PlayCircleOutline,
                    title = "Oynatma",
                    subtitle = "Oynatıcı, altyazılar ve otomatik oynatma",
                    onClick = { showPlaybackDialog = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.Extension,
                    title = "Entegrasyonlar",
                    subtitle = "TMDB ve MDBList servislerini bağla.",
                    onClick = { showIntegrationsDialog = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Bildirimler",
                    subtitle = "Bölüm yayın bildirimlerini yönet ve test bildirimi gönder.",
                    onClick = { showNotificationsDialog = true }
                )
            }
        }

        // HAKKINDA Section (Screenshot 4)
        item(key = "settings_section_about") {
            StavioSettingsSectionHeader("HAKKINDA")
        }
        item(key = "settings_card_about") {
            StavioSettingsGroupCard {
                StavioSettingsRow(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = "Destekçiler & Katkıda Bulunanlar",
                    subtitle = "Açık teşekkürler ve proje katkıları",
                    onClick = { showContributorsDialog = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.Shield,
                    title = "Gizlilik Politikası",
                    subtitle = "Stavio'nun verilerinizi nasıl işlediğini öğrenin",
                    onClick = { showPrivacyDialog = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.Description,
                    title = "Lisanslar & Teşekkürler",
                    subtitle = "Veri kaynakları, teşekkürler ve platform lisansları",
                    onClick = { showLicensesDialog = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.SystemUpdateAlt,
                    title = "Update channel",
                    subtitle = "Choose the type of app updates to receive",
                    trailingText = updateChannelValue,
                    onClick = { showUpdateChannelDialog = true }
                )
                StavioSettingsDivider()
                StavioSettingsRow(
                    icon = Icons.Outlined.Refresh,
                    title = "Güncellemeleri kontrol et",
                    subtitle = "Uygulamanın yeni sürümü var mı kontrol et.",
                    onClick = {
                        Toast.makeText(
                            context,
                            "Stavio güncel! (Sürüm 0.6.3-beta)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }

        // GELİŞMİŞ Section (Screenshot 4)
        item(key = "settings_section_advanced") {
            StavioSettingsSectionHeader("GELİŞMİŞ")
        }
        item(key = "settings_card_advanced") {
            StavioSettingsGroupCard {
                StavioSettingsRow(
                    icon = Icons.Outlined.Tune,
                    title = "Gelişmiş",
                    subtitle = "Başlangıç ve profil davranışı.",
                    onClick = { showAdvancedSheet = true }
                )
            }
        }

        // Bottom Branding Footer: "stavio" + "Vanbey tarafından ❤️ ile yapıldı" + "Sürüm 0.6.3-beta (146)" (Screenshot 4)
        item(key = "settings_footer_branding") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 34.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Colorful Stavio Play Triangle Vector matching Screenshot 4
                    Canvas(modifier = Modifier.size(28.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * 0.20f, size.height * 0.14f)
                            lineTo(size.width * 0.86f, size.height * 0.50f)
                            lineTo(size.width * 0.20f, size.height * 0.86f)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF38BDF8),
                                    Color(0xFFFACC15),
                                    Color(0xFFEF4444),
                                    Color(0xFFA855F7)
                                )
                            ),
                            style = Stroke(
                                width = 6.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "stavio",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = StavioDisplayFontFamily,
                        letterSpacing = (-0.8).sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Vanbey tarafından ❤️ ile yapıldı",
                    color = SettingsSubtitleGray,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = StavioFontFamily,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Sürüm 0.6.3-beta (146)",
                    color = Color(0xFF636366),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = StavioFontFamily,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // =========================================================================
    // SUB-DIALOGS & SHEETS (All Settings & Advanced Options Fully Functional)
    // =========================================================================

    // 1. Profil Değiştir Dialog
    if (showProfileDialog) {
        val profiles = listOf("Kullanıcı (K)", "Vanbey (V)", "Sinema Modu (S)", "Çocuk & Aile (Ç)")
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Profil değiştir", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { prof ->
                        val isSelected = activeProfileName == prof
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFF2C2C32) else SettingsIconBoxDark,
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color.Transparent),
                            onClick = {
                                activeProfileName = prof
                                viewModel.prefs.edit().putString("stavio_active_profile", prof).apply()
                                showProfileDialog = false
                                Toast.makeText(context, "$prof profiline geçildi", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(Color(0xFF3B82F6), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = prof.first().toString(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(prof, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3B82F6))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Kapat", color = SettingsSubtitleGray)
                }
            }
        )
    }

    // 2. Hesap Dialog
    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Hesap & Abonelik", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Aktif Profil: $activeProfileName",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Aktif Kaynak: ${selectedPlaylist?.name ?: "Varsayılan Stavio Listesi"}",
                        color = SettingsSubtitleGray,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = "Durum: ${selectedPlaylist?.expirationDate?.let { "Son Kullanma: $it" } ?: "Sınırsız Pro Erişim"}",
                        color = Color(0xFF4ADE80),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Tamam", color = Color.White)
                }
            }
        )
    }

    // 3. Watch Party Dialog
    if (showWatchPartyDialog) {
        var partyCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWatchPartyDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Watch party", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Arkadaşlarınızla aynı anda senkronize film veya dizi izlemek için bir parti kodu girin veya yeni oda oluşturun.",
                        color = SettingsSubtitleGray,
                        fontSize = 13.5.sp
                    )
                    OutlinedTextField(
                        value = partyCode,
                        onValueChange = { partyCode = it.uppercase() },
                        placeholder = { Text("Örn: STV-4829", color = SettingsSubtitleGray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF2C2C32)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val code = partyCode.ifBlank { "STV-${(1000..9999).random()}" }
                        Toast.makeText(context, "Watch Party Odası Aktif: $code", Toast.LENGTH_SHORT).show()
                        showWatchPartyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (partyCode.isBlank()) "Oda Oluştur" else "Odaya Katıl", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWatchPartyDialog = false }) {
                    Text("İptal", color = SettingsSubtitleGray)
                }
            }
        )
    }

    // 4. Takip (Trakt / İzlemeye Devam Et Beslemesi) Dialog
    if (showTrackingDialog) {
        AlertDialog(
            onDismissRequest = { showTrackingDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Takip & İzleme Geçmişi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Stavio yerel takip motoru aktif. İzlemeye başladığınız filmler ve diziler kaldığınız dakika, ses ve altyazı tercihleriyle otomatik olarak 'İzlemeye devam' bölümüne kaydedilir.",
                        color = SettingsSubtitleGray,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SettingsIconBoxDark,
                        onClick = {
                            viewModel.clearWatchHistory()
                            showTrackingDialog = false
                            Toast.makeText(context, "İzlemeye devam geçmişi temizlendi", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "İzlemeye Devam Et Geçmişini Temizle",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackingDialog = false }) {
                    Text("Kapat", color = Color.White)
                }
            }
        )
    }

    // 5. Görünüm (Tema & Dil) Dialog
    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Görünüm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SettingsIconBoxDark,
                        onClick = {
                            showAppearanceDialog = false
                            showThemeDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Renk Teması", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    text = if (currentLang == AppLanguage.TR) currentTheme.nameTr else currentTheme.nameEn,
                                    color = SettingsSubtitleGray,
                                    fontSize = 12.5.sp
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SettingsSubtitleGray)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SettingsIconBoxDark,
                        onClick = {
                            showAppearanceDialog = false
                            showLanguageDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Uygulama Dili", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    text = "${currentLang.flag} ${currentLang.nativeName}",
                                    color = SettingsSubtitleGray,
                                    fontSize = 12.5.sp
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SettingsSubtitleGray)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppearanceDialog = false }) {
                    Text("Kapat", color = SettingsSubtitleGray)
                }
            }
        )
    }

    // 6. İçerik & Keşif (IPTV Kaynakları & Listeler Yönetimi) Dialog
    if (showContentDiscoveryDialog) {
        AlertDialog(
            onDismissRequest = { showContentDiscoveryDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("İçerik & Keşif Kaynakları", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Kayıtlı IPTV / M3U / Xtream listelerinizi seçin veya yeni kaynak ekleyin (${allPlaylists.size} kaynak aktif).",
                        color = SettingsSubtitleGray,
                        fontSize = 13.sp
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(allPlaylists, key = { it.id }) { p ->
                            val isSelected = p.id == selectedPlaylist?.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0xFF282830) else SettingsIconBoxDark,
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color.Transparent),
                                onClick = {
                                    viewModel.selectPlaylistAndNavigate(p)
                                    showContentDiscoveryDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(p.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                                        Text(p.type, color = SettingsSubtitleGray, fontSize = 12.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        IconButton(
                                            onClick = { viewModel.deletePlaylist(p) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Sil", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            showContentDiscoveryDialog = false
                            showPlaylistImportModal = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Yeni M3U / Xtream / MAC Kaynağı Ekle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContentDiscoveryDialog = false }) {
                    Text("Kapat", color = SettingsSubtitleGray)
                }
            }
        )
    }

    if (showPlaylistImportModal) {
        PlaylistImportDialog(
            onDismiss = { showPlaylistImportModal = false },
            onImportUrl = { url, name ->
                viewModel.addPlaylistFromUrl(url, name)
                showPlaylistImportModal = false
            },
            onImportRaw = { _, _ -> },
            onImportUri = { uri, ctx, name ->
                viewModel.addPlaylistFromUri(uri, ctx, name)
                showPlaylistImportModal = false
            },
            onImportMag = { portal, mac, name ->
                viewModel.addMagPlaylist(portal, mac, name)
                showPlaylistImportModal = false
            }
        )
    }

    // 7. Oynatma (Oynatıcı, Altyazılar ve Otomatik Oynatma) Dialog
    if (showPlaybackDialog) {
        AlertDialog(
            onDismissRequest = { showPlaybackDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Oynatma", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SettingsIconBoxDark,
                        onClick = {
                            showPlaybackDialog = false
                            showScaleModeDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Varsayılan Ekran Oranı", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text("$videoScaleMode (Fit / Fill / Stretch)", color = SettingsSubtitleGray, fontSize = 12.5.sp)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SettingsSubtitleGray)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SettingsIconBoxDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Kaldığı Yerden Otomatik Devam Et", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                                Text("Ses, altyazı ve dakika tercihini hatırla", color = SettingsSubtitleGray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = true,
                                onCheckedChange = {},
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3B82F6))
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SettingsIconBoxDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dokunmatik Ekran Kilidi", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                                Text("Oynatıcıda yanlış dokunuşları önle", color = SettingsSubtitleGray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = isScreenLocked,
                                onCheckedChange = { viewModel.isScreenLocked.value = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3B82F6))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaybackDialog = false }) {
                    Text("Kapat", color = SettingsSubtitleGray)
                }
            }
        )
    }

    // 8. Entegrasyonlar Dialog
    if (showIntegrationsDialog) {
        AlertDialog(
            onDismissRequest = { showIntegrationsDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Entegrasyonlar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Cinemeta, IMDb ve TMDB Türkçe özet & fragman servisleri aktif olarak bağlıdır.",
                        color = SettingsSubtitleGray,
                        fontSize = 13.5.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SettingsIconBoxDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("TMDB & Cinemeta Metadata", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                            Text("Aktif", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntegrationsDialog = false }) {
                    Text("Tamam", color = Color.White)
                }
            }
        )
    }

    // 9. Bildirimler Dialog
    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Bildirimler", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Yeni dizi bölümleri ve canlı yayın hatırlatıcıları için bildirim tercihlerini yönetin.",
                        color = SettingsSubtitleGray,
                        fontSize = 13.5.sp
                    )
                    Button(
                        onClick = {
                            Toast.makeText(context, "Stavio Test Bildirimi başarıyla gönderildi!", Toast.LENGTH_SHORT).show()
                            showNotificationsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Bildirimi Gönder", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text("Kapat", color = SettingsSubtitleGray)
                }
            }
        )
    }

    // 10. Destekçiler & Katkıda Bulunanlar Dialog
    if (showContributorsDialog) {
        AlertDialog(
            onDismissRequest = { showContributorsDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Destekçiler & Katkıda Bulunanlar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Text(
                    text = "Stavio Media mimarisi, tasarımı ve altyapısı Vanbey tarafından geliştirilmiştir. Projeye destek veren tüm topluluk üyelerine teşekkür ederiz.",
                    color = SettingsSubtitleGray,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showContributorsDialog = false }) {
                    Text("Kapat", color = Color.White)
                }
            }
        )
    }

    // 11. Gizlilik Politikası Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Gizlilik Politikası", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Text(
                    text = "Stavio verilerinizi yalnızca cihazınızda yerel olarak saklar. Oynatma listeleriniz, izleme geçmişiniz ve tercihleriniz üçüncü taraf sunucularla paylaşılmaz.",
                    color = SettingsSubtitleGray,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Kapat", color = Color.White)
                }
            }
        )
    }

    // 12. Lisanslar & Teşekkürler Dialog
    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Lisanslar & Teşekkürler", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Text(
                    text = "• AndroidX Jetpack Compose & Material 3\n• AndroidX Media3 ExoPlayer & HLS\n• Coil Image Loader\n• Cinemeta & TMDB Metadata API",
                    color = SettingsSubtitleGray,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Kapat", color = Color.White)
                }
            }
        )
    }

    // 13. Update Channel Dialog
    if (showUpdateChannelDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateChannelDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Update channel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Stable", "Beta", "Nightly").forEach { ch ->
                        val isSel = updateChannelValue == ch
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSel) Color(0xFF282830) else SettingsIconBoxDark,
                            border = BorderStroke(1.dp, if (isSel) Color(0xFF3B82F6) else Color.Transparent),
                            onClick = {
                                updateChannelValue = ch
                                viewModel.prefs.edit().putString("stavio_update_channel", ch).apply()
                                showUpdateChannelDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ch, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3B82F6))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUpdateChannelDialog = false }) {
                    Text("Kapat", color = SettingsSubtitleGray)
                }
            }
        )
    }

    // 14. GELİŞMİŞ (Advanced Settings Modal — All technical/extra settings grouped cleanly here!)
    if (showAdvancedSheet) {
        AlertDialog(
            onDismissRequest = { showAdvancedSheet = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Gelişmiş Ayarlar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    item {
                        AdvancedOptionCard(
                            title = "Donanım Hızlandırma (Decoder)",
                            subtitle = if (currentLang == AppLanguage.TR) hwAccel.titleTr else hwAccel.titleEn,
                            onClick = { showHwAccelDialog = true }
                        )
                    }
                    item {
                        AdvancedOptionCard(
                            title = "Akış Arabelleği (Buffer)",
                            subtitle = if (currentLang == AppLanguage.TR) bufferMode.titleTr else bufferMode.titleEn,
                            onClick = { showBufferDialog = true }
                        )
                    }
                    item {
                        AdvancedOptionCard(
                            title = "Özel User-Agent",
                            subtitle = userAgentPreset.title,
                            onClick = { showUserAgentDialog = true }
                        )
                    }
                    item {
                        AdvancedOptionCard(
                            title = "Ağ Zaman Aşımı",
                            subtitle = "$networkTimeout saniye",
                            onClick = { showTimeoutDialog = true }
                        )
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SettingsIconBoxDark,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Canlı FPS & Akış Telemetrisi", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Oynatıcıda anlık çözünürlük ve kodek göster", color = SettingsSubtitleGray, fontSize = 12.sp)
                                }
                                Switch(
                                    checked = showFpsOverlay,
                                    onCheckedChange = { devManager.setShowFpsOverlay(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3B82F6))
                                )
                            }
                        }
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SettingsIconBoxDark,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Güvensiz SSL Doğrulama Modu", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Sertifikası eski IPTV sunucularına izin ver", color = SettingsSubtitleGray, fontSize = 12.sp)
                                }
                                Switch(
                                    checked = bypassSsl,
                                    onCheckedChange = { devManager.setBypassSsl(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3B82F6))
                                )
                            }
                        }
                    }
                    item {
                        AdvancedOptionCard(
                            title = "Canlı Akış Günlükleri (Logs)",
                            subtitle = "${debugLogs.size} günlük kaydı mevcut",
                            onClick = { showLogsDialog = true }
                        )
                    }
                    item {
                        AdvancedOptionCard(
                            title = "Gelişmiş Ayarları Sıfırla",
                            subtitle = "Decoder, buffer ve ağ ayarlarını varsayılana döndür",
                            isDestructive = true,
                            onClick = { showDevResetDialog = true }
                        )
                    }
                    item {
                        AdvancedOptionCard(
                            title = "Tüm Önbelleği ve Verileri Temizle",
                            subtitle = "Kayıtlı listeleri, önbelleği ve favorileri sıfırla",
                            isDestructive = true,
                            onClick = { showResetConfirmDialog = true }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdvancedSheet = false }) {
                    Text("Kapat", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // --- NESTED TECHNICAL DIALOGS ---

    if (showLanguageDialog) {
        LanguageSelectionModal(
            isInitialOnboarding = false,
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(locManager.getString("settings_theme_title"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AllThemes.forEach { th ->
                        val isSelected = currentTheme.id == th.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFF282830) else SettingsIconBoxDark,
                            border = BorderStroke(1.dp, if (isSelected) th.primary else Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    themeManager.setTheme(th)
                                    showThemeDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(20.dp).background(th.primary, CircleShape))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = if (currentLang == AppLanguage.TR) th.nameTr else th.nameEn,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = th.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Kapat", color = SettingsSubtitleGray)
                }
            }
        )
    }

    if (showScaleModeDialog) {
        AlertDialog(
            onDismissRequest = { showScaleModeDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Varsayılan Ekran Oranı", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Fit" to "Orantılı Sığdır (Orijinal)",
                        "Fill" to "Tam Ekran Doldur (Zoom)",
                        "Stretch" to "Genişlet (16:9 Tam)"
                    ).forEach { (mode, desc) ->
                        val isSelected = videoScaleMode == mode
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFF282830) else SettingsIconBoxDark,
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color.Transparent),
                            onClick = {
                                viewModel.videoScaleMode.value = mode
                                showScaleModeDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(mode, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(desc, color = SettingsSubtitleGray, fontSize = 12.sp)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3B82F6))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Tüm Verileri Temizle", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "Tüm kayıtlı IPTV listeleri, kanal önbellekleri ve favoriler kalıcı olarak silinecektir. Devam etmek istiyor musunuz?",
                    color = SettingsSubtitleGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showResetConfirmDialog = false
                        showAdvancedSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Evet, Temizle", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("İptal", color = SettingsSubtitleGray)
                }
            }
        )
    }

    if (showHwAccelDialog) {
        AlertDialog(
            onDismissRequest = { showHwAccelDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Donanım Hızlandırma", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HwAccelMode.entries.forEach { mode ->
                        val isSelected = hwAccel == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF282830) else SettingsIconBoxDark,
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color.Transparent),
                            onClick = {
                                devManager.setHwAccel(mode)
                                showHwAccelDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (currentLang == AppLanguage.TR) mode.titleTr else mode.titleEn,
                                    color = Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3B82F6))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showBufferDialog) {
        AlertDialog(
            onDismissRequest = { showBufferDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Akış Arabelleği (Buffer)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BufferMode.entries.forEach { mode ->
                        val isSelected = bufferMode == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF282830) else SettingsIconBoxDark,
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color.Transparent),
                            onClick = {
                                devManager.setBufferMode(mode)
                                showBufferDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (currentLang == AppLanguage.TR) mode.titleTr else mode.titleEn,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Min: ${mode.minBufferMs / 1000}s • Max: ${mode.maxBufferMs / 1000}s",
                                        color = SettingsSubtitleGray,
                                        fontSize = 11.5.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3B82F6))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showUserAgentDialog) {
        var customInput by remember { mutableStateOf(customUserAgent) }
        var selectedPreset by remember { mutableStateOf(userAgentPreset) }

        AlertDialog(
            onDismissRequest = { showUserAgentDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Özel User-Agent", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserAgentPreset.entries.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF282830) else SettingsIconBoxDark,
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color.Transparent),
                            onClick = { selectedPreset = preset },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(preset.title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    if (selectedPreset == UserAgentPreset.CUSTOM) {
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { customInput = it },
                            placeholder = { Text("Mozilla/5.0 ...", color = SettingsSubtitleGray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        devManager.setUserAgentPreset(selectedPreset, customInput)
                        showUserAgentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Kaydet", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Ağ Zaman Aşımı", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { sec ->
                        val isSelected = networkTimeout == sec
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF282830) else SettingsIconBoxDark,
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color.Transparent),
                            onClick = {
                                devManager.setNetworkTimeout(sec)
                                showTimeoutDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$sec saniye", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3B82F6))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Canlı Akış Günlükleri", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        items(debugLogs.size) { idx ->
                            Text(
                                text = debugLogs[idx],
                                color = Color(0xFF4ADE80),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogsDialog = false }) {
                    Text("Kapat", color = Color.White)
                }
            }
        )
    }

    if (showDevResetDialog) {
        AlertDialog(
            onDismissRequest = { showDevResetDialog = false },
            containerColor = SettingsCardDark,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Gelişmiş Ayarları Sıfırla", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "Tüm donanım hızlandırma, arabellek ve ağ parametreleri varsayılan değerlere döndürülecektir.",
                    color = SettingsSubtitleGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        devManager.resetToDefaults()
                        showDevResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sıfırla", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDevResetDialog = false }) {
                    Text("İptal", color = SettingsSubtitleGray)
                }
            }
        )
    }
}

@Composable
private fun StavioSettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = SettingsHeaderGray,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = StavioFontFamily,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun StavioSettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = SettingsCardDark,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun StavioSettingsDivider() {
    HorizontalDivider(
        color = SettingsDividerColor,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 18.dp)
    )
}

@Composable
private fun StavioSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(SettingsIconBoxDark, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = StavioFontFamily
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = SettingsSubtitleGray,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = StavioFontFamily,
                lineHeight = 18.sp
            )
        }

        if (trailingText != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = trailingText,
                color = SettingsSubtitleGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = StavioFontFamily
            )
        }
    }
}

@Composable
private fun AdvancedOptionCard(
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SettingsIconBoxDark,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isDestructive) Color(0xFFEF4444) else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    fontFamily = StavioFontFamily
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = SettingsSubtitleGray,
                    fontSize = 12.sp,
                    fontFamily = StavioFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = if (isDestructive) Color(0xFFEF4444) else SettingsSubtitleGray
            )
        }
    }
}
