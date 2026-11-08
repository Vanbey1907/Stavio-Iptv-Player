package com.example.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.network.ExtractedStreamSource
import com.example.network.StavioStreamExtractor
import com.example.ui.theme.StavioFontFamily
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StavioStreamExtractorDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var inputUrl by rememberSaveable { mutableStateOf("") }
    var activeSectionTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Kaynak Bulucu, 1 = Kaydedilenler
    var selectedFormatFilter by rememberSaveable { mutableStateOf("ALL") }

    var isScanning by remember { mutableStateOf(false) }
    var scanStatusText by remember { mutableStateOf("Site adresini veya yayın linkini yapıştırıp 'Kaynakları Bul'a dokunun.") }
    var webSnifferUrl by remember { mutableStateOf<String?>(null) }
    var isFullBrowserOpen by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    val discoveredStreams = remember { mutableStateListOf<ExtractedStreamSource>() }
    var savedStreams by remember { mutableStateOf(StavioStreamExtractor.loadSavedStreams(context)) }
    val savedUrlSet = remember(savedStreams) {
        savedStreams.map { it.streamUrl.lowercase(Locale.ROOT) }.toSet()
    }

    var streamToSaveModal by remember { mutableStateOf<ExtractedStreamSource?>(null) }

    // Geri tuşu yönetimi: Tam ekran tarayıcı açıksa önce tarayıcıdan listeye dön, değilse Ayarlar'a dön
    BackHandler {
        if (isFullBrowserOpen) {
            isFullBrowserOpen = false
        } else {
            webSnifferUrl = null
            onDismiss()
        }
    }

    val exportM3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri != null) {
            val targetList = if (activeSectionTab == 1 && savedStreams.isNotEmpty()) {
                savedStreams
            } else {
                discoveredStreams.toList()
            }
            runCatching {
                val content = StavioStreamExtractor.buildM3uPlaylistContent(targetList)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "${targetList.size} kaynak M3U dosyası olarak kaydedildi", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Dosya kaydedilemedi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addDiscoveredStreamThreadSafe(source: ExtractedStreamSource) {
        coroutineScope.launch {
            val cleanKey = source.streamUrl.substringBefore("|").lowercase(Locale.ROOT)
            if (discoveredStreams.none { it.streamUrl.substringBefore("|").lowercase(Locale.ROOT) == cleanKey }) {
                discoveredStreams.add(0, source)
                scanStatusText = "${discoveredStreams.size} adet çalışan medya kaynağı yakalandı"
            }
        }
    }

    fun startDeepStreamExtraction(rawUrl: String, clearPrevious: Boolean = true) {
        val normalized = StavioStreamExtractor.normalizeInputUrl(rawUrl)
        if (normalized.isBlank()) {
            Toast.makeText(context, "Lütfen geçerli bir site veya yayın linki girin", Toast.LENGTH_SHORT).show()
            return
        }
        focusManager.clearFocus()
        if (clearPrevious) {
            discoveredStreams.clear()
        }
        isScanning = true
        scanStatusText = "Site ve gizli oynatıcı kaynakları taranıyor..."
        webSnifferUrl = normalized

        scanJob?.cancel()
        scanJob = coroutineScope.launch {
            StavioStreamExtractor.analyzeUrlDeep(
                rawInputUrl = normalized,
                onProgress = { msg -> scanStatusText = msg },
                onSourceFound = { src -> addDiscoveredStreamThreadSafe(src) }
            )
            if (discoveredStreams.isEmpty()) {
                scanStatusText = "Dinamik oynatıcı ve XHR ağ istekleri dinleniyor..."
                delay(4000)
            } else {
                delay(1500)
            }
            isScanning = false
            // Arka plan taraması bittiğinde sessiz WebView'ı durdur (kullanıcı etkileşimli tarayıcıda değilse)
            if (!isFullBrowserOpen) {
                webSnifferUrl = null
            }
            scanStatusText = if (discoveredStreams.isNotEmpty()) {
                "${discoveredStreams.size} adet çalışan medya kaynağı bulundu"
            } else {
                "Doğrudan akış bulunamadı. 'Site Gezgini' butonuna dokunup sitede videoyu açarak yakalayabilirsiniz."
            }
        }
    }

    val filteredDiscoveredStreams = remember(discoveredStreams.size, selectedFormatFilter) {
        val snapshot = discoveredStreams.toList()
        when (selectedFormatFilter) {
            "ALL" -> snapshot
            "HIDDEN" -> snapshot.filter { it.isHiddenSource }
            "M3U8" -> snapshot.filter { it.format == "M3U8" || it.format == "M3U" }
            "MP4" -> snapshot.filter { it.format == "MP4" }
            "TS" -> snapshot.filter { it.format == "TS" }
            else -> snapshot.filter { it.format !in setOf("M3U8", "M3U", "MP4", "TS") }
        }
    }

    // =========================================================================
    // TAM EKRAN ETKİLEŞİMLİ SİTE GEZGİNİ (Kaydırma ve Gezinme Sorunsuz)
    // =========================================================================
    if (isFullBrowserOpen) {
        val initialBrowserUrl = remember {
            val norm = StavioStreamExtractor.normalizeInputUrl(inputUrl)
            norm.ifBlank { "https://www.google.com" }
        }
        StavioFullInteractiveSiteBrowser(
            initialUrl = initialBrowserUrl,
            capturedCount = discoveredStreams.size,
            onStreamCaptured = { captured ->
                addDiscoveredStreamThreadSafe(captured)
            },
            onPageVisited = { visitedUrl ->
                inputUrl = visitedUrl
                coroutineScope.launch {
                    StavioStreamExtractor.analyzeUrlDeep(
                        rawInputUrl = visitedUrl,
                        onSourceFound = { src -> addDiscoveredStreamThreadSafe(src) }
                    )
                }
            },
            onCloseBrowser = {
                isFullBrowserOpen = false
                webSnifferUrl = null
            }
        )
        return
    }

    // Arka Plan Sessiz Yakalayıcı WebView (LazyColumn dışında, 0.dp boyutunda, %100 sessiz)
    if (webSnifferUrl != null && !isFullBrowserOpen) {
        Box(modifier = Modifier.size(0.dp)) {
            StavioSilentHeadlessWebSniffer(
                targetUrl = webSnifferUrl!!,
                onStreamCaptured = { captured ->
                    addDiscoveredStreamThreadSafe(captured)
                }
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF07080B)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Üst Başlık Barı
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        onClick = {
                            webSnifferUrl = null
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = Color(0xFF171920),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBackIosNew,
                                contentDescription = "Geri",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Kaynak Bulucu & Yakalayıcı",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = StavioFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Direkt Site • MP4 • M3U8 • TS • Gizli Kaynaklar",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.5.sp,
                            fontFamily = StavioFontFamily,
                            maxLines = 1
                        )
                    }
                }

                val exportCount = if (activeSectionTab == 1) savedStreams.size else discoveredStreams.size
                if (exportCount > 0) {
                    Surface(
                        onClick = {
                            exportM3uLauncher.launch("stavio_kaynaklar_${System.currentTimeMillis() % 10000}.m3u")
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileDownload,
                                contentDescription = "M3U Kaydet",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "M3U İndir",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = StavioFontFamily
                            )
                        }
                    }
                }
            }

            // Mod Geçiş Sekmeleri: "Kaynak Tarayıcı" / "Kaydedilenler (X)"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF121318))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(
                    "Kaynak Tarayıcı (${discoveredStreams.size})" to Icons.Rounded.Radar,
                    "Kaydedilenler (${savedStreams.size})" to Icons.Rounded.BookmarkAdded
                )
                tabs.forEachIndexed { idx, (label, icon) ->
                    val isSelected = activeSectionTab == idx
                    Surface(
                        onClick = { activeSectionTab = idx },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF2B2E38) else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color(0xFF8E93A0),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF8E93A0),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontFamily = StavioFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (activeSectionTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Link Giriş ve Tarama Kartı
                    item(key = "url_scanner_input_card") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF121318),
                            border = BorderStroke(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(Color(0x553B82F6), Color(0x1FFFFFFF), Color(0x4410B981))
                                )
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                OutlinedTextField(
                                    value = inputUrl,
                                    onValueChange = { inputUrl = it },
                                    placeholder = {
                                        Text(
                                            text = "Site adresi (ör. site.com) veya yayın linki yapıştırın",
                                            color = Color(0xFF6B7280),
                                            fontSize = 13.5.sp,
                                            fontFamily = StavioFontFamily
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Link,
                                            contentDescription = null,
                                            tint = Color(0xFF60A5FA)
                                        )
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (inputUrl.isNotEmpty()) {
                                                IconButton(onClick = { inputUrl = "" }) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "Temizle",
                                                        tint = Color(0xFF9CA3AF),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Surface(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                                    if (!clipText.isNullOrBlank()) {
                                                        inputUrl = clipText
                                                        startDeepStreamExtraction(clipText)
                                                    } else {
                                                        Toast.makeText(context, "Panoda kopyalanmış link bulunamadı", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF1F2430),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.ContentPaste,
                                                        contentDescription = "Yapıştır",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Yapıştır",
                                                        color = Color.White,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        fontFamily = StavioFontFamily
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = { startDeepStreamExtraction(inputUrl) }
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF0B0C10),
                                        unfocusedContainerColor = Color(0xFF0B0C10),
                                        focusedBorderColor = Color(0xFF3B82F6),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = Color(0xFF3B82F6)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Ana Aksiyon Satırı: "Kaynakları Bul" + "Site Gezgini (Tam Ekran Gezinilebilir Tarayıcı)"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { startDeepStreamExtraction(inputUrl) },
                                        enabled = inputUrl.isNotBlank() && !isScanning,
                                        shape = RoundedCornerShape(13.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2563EB),
                                            contentColor = Color.White,
                                            disabledContainerColor = Color(0xFF1E293B),
                                            disabledContentColor = Color(0xFF64748B)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        if (isScanning) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.2.dp,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Taranıyor...",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = StavioFontFamily
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.TravelExplore,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Kaynakları Bul",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = StavioFontFamily
                                            )
                                        }
                                    }

                                    // Tam Ekran Gezinilebilir Site Gezgini Butonu
                                    Surface(
                                        onClick = {
                                            focusManager.clearFocus()
                                            webSnifferUrl = null
                                            isFullBrowserOpen = true
                                        },
                                        shape = RoundedCornerShape(13.dp),
                                        color = Color(0xFF1B1E26),
                                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Public,
                                                contentDescription = "Site Gezgini",
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Site Gezgini",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                fontFamily = StavioFontFamily
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                when {
                                                    isScanning -> Color(0xFFF59E0B)
                                                    discoveredStreams.isNotEmpty() -> Color(0xFF10B981)
                                                    else -> Color(0xFF6B7280)
                                                },
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = scanStatusText,
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 12.sp,
                                        fontFamily = StavioFontFamily,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Format Filtre Hapları
                    if (discoveredStreams.isNotEmpty()) {
                        item(key = "format_filter_pills") {
                            val filters = listOf(
                                "ALL" to "Tümü (${discoveredStreams.size})",
                                "HIDDEN" to "Gizli (${discoveredStreams.count { it.isHiddenSource }})",
                                "M3U8" to "M3U8 (${discoveredStreams.count { it.format == "M3U8" || it.format == "M3U" }})",
                                "MP4" to "MP4 (${discoveredStreams.count { it.format == "MP4" }})",
                                "TS" to "TS (${discoveredStreams.count { it.format == "TS" }})",
                                "OTHER" to "Diğer (${discoveredStreams.count { it.format !in setOf("M3U8", "M3U", "MP4", "TS") }})"
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filters, key = { it.first }) { (key, label) ->
                                    val isSel = selectedFormatFilter == key
                                    Surface(
                                        onClick = { selectedFormatFilter = key },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (isSel) Color(0xFF3E4047) else Color(0xFF14151B),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSel) Color(0xFF707582) else Color(0xFF282B34)
                                        )
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSel) Color.White else Color(0xFFA7ACB8),
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                                            fontFamily = StavioFontFamily,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }

                        items(
                            filteredDiscoveredStreams,
                            key = { "found_${it.id}_${it.streamUrl.hashCode()}" }
                        ) { source ->
                            val isSaved = savedUrlSet.contains(source.streamUrl.lowercase(Locale.ROOT))
                            StavioExtractedStreamCard(
                                source = source,
                                isSaved = isSaved,
                                onPlay = {
                                    // Oynatıcıya geçmeden önce arka plan tarayıcısını tamamen sustur/kapat
                                    webSnifferUrl = null
                                    viewModel.playExtractedStream(
                                        title = source.title,
                                        streamUrlWithHeaders = source.toPlayableUrlWithHeaders(),
                                        groupName = "Kaynak Bulucu (${source.format})",
                                        streamType = if (source.format == "TS") "live" else "movie"
                                    )
                                },
                                onSaveClick = {
                                    streamToSaveModal = source
                                },
                                onCopyClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Stream URL", source.streamUrl))
                                    Toast.makeText(context, "Kaynak bağlantısı kopyalandı", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    } else if (!isScanning) {
                        item(key = "empty_extractor_guide") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFF111217),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Color(0xFF1E293B), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Radar,
                                            contentDescription = null,
                                            tint = Color(0xFF60A5FA),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Direkt Site veya Yayın Linkinden Kaynak Bulun",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = StavioFontFamily,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "İster doğrudan bir film/dizi/canlı TV sitesinin adresini, ister embed veya yayın linkini yapıştırın. Dilerseniz 'Site Gezgini' ile site içinde serbestçe gezinip istediğiniz videoya dokunarak gizli M3U8, MP4 ve TS kaynaklarını yakalayabilirsiniz.",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 12.5.sp,
                                        fontFamily = StavioFontFamily,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // =========================================================================
                // SEKME 2: KAYDEDİLEN KAYNAKLAR
                // =========================================================================
                if (savedStreams.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.BookmarkBorder,
                                contentDescription = null,
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Henüz Kaydedilmiş Kaynak Yok",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = StavioFontFamily
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Kaynak Tarayıcı ile bulduğunuz MP4, M3U8 veya TS yayınlarını 'Kaydet' butonuna dokunarak buraya veya IPTV listenize kaydedebilirsiniz.",
                                color = Color(0xFF9CA3AF),
                                fontSize = 13.sp,
                                fontFamily = StavioFontFamily,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 36.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "saved_streams_top_actions") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Kaydedilen Medya Kaynakları (${savedStreams.size})",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 13.sp,
                                    fontFamily = StavioFontFamily
                                )
                                TextButton(
                                    onClick = {
                                        savedStreams = StavioStreamExtractor.clearAllSavedStreams(context)
                                        Toast.makeText(context, "Kayıtlı kaynaklar temizlendi", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = "Tümünü Temizle",
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.5.sp,
                                        fontFamily = StavioFontFamily
                                    )
                                }
                            }
                        }

                        items(
                            savedStreams,
                            key = { "saved_${it.id}_${it.streamUrl.hashCode()}" }
                        ) { source ->
                            StavioExtractedStreamCard(
                                source = source,
                                isSaved = true,
                                showDeleteButton = true,
                                onPlay = {
                                    webSnifferUrl = null
                                    viewModel.playExtractedStream(
                                        title = source.title,
                                        streamUrlWithHeaders = source.toPlayableUrlWithHeaders(),
                                        groupName = "Kaydedilen Kaynaklar",
                                        streamType = if (source.format == "TS") "live" else "movie"
                                    )
                                },
                                onSaveClick = {
                                    streamToSaveModal = source
                                },
                                onCopyClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Stream URL", source.streamUrl))
                                    Toast.makeText(context, "Kaynak bağlantısı kopyalandı", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteClick = {
                                    savedStreams = StavioStreamExtractor.removeSavedStream(context, source.streamUrl)
                                    Toast.makeText(context, "Kaynak kaldırıldı", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Kaydetme Seçenekleri Dialogu
    if (streamToSaveModal != null) {
        val target = streamToSaveModal!!
        var customTitle by remember(target) { mutableStateOf(target.title) }
        var selectedTargetType by remember {
            mutableStateOf(if (target.format == "TS") "live" else "movie")
        }

        AlertDialog(
            onDismissRequest = { streamToSaveModal = null },
            containerColor = Color(0xFF14151B),
            title = {
                Text(
                    text = "Kaynağı Kaydet",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = StavioFontFamily
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        label = { Text("Kaynak Adı", color = Color(0xFF9CA3AF), fontFamily = StavioFontFamily) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "IPTV Kategori Türü:",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.5.sp,
                        fontFamily = StavioFontFamily
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "movie" to "Filmler",
                            "series" to "Diziler",
                            "live" to "Canlı TV"
                        ).forEach { (typeKey, typeLabel) ->
                            val isSel = selectedTargetType == typeKey
                            Surface(
                                onClick = { selectedTargetType = typeKey },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) Color(0xFF2563EB) else Color(0xFF1F222B),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = typeLabel,
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                    fontFamily = StavioFontFamily,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val finalTitle = customTitle.ifBlank { target.title }
                            savedStreams = StavioStreamExtractor.saveStreamToStorage(
                                context = context,
                                source = target,
                                customTitle = finalTitle
                            )
                            viewModel.saveExtractedStreamToPlaylist(
                                title = finalTitle,
                                streamUrlWithHeaders = target.toPlayableUrlWithHeaders(),
                                category = "Kaydedilen Kaynaklar",
                                streamType = selectedTargetType
                            ) { playlistName ->
                                val msg = if (playlistName.isNotBlank()) {
                                    "Kaynak kaydedildi ve '$playlistName' listesine eklendi"
                                } else {
                                    "Kaynak 'Kaydedilenler' listesine eklendi"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            streamToSaveModal = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Rounded.BookmarkAdded, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Uygulamaya ve IPTV Listesine Kaydet", fontWeight = FontWeight.SemiBold, fontFamily = StavioFontFamily)
                    }

                    OutlinedButton(
                        onClick = {
                            val finalTitle = customTitle.ifBlank { target.title }
                            savedStreams = StavioStreamExtractor.saveStreamToStorage(context, target, finalTitle)
                            val ok = StavioStreamExtractor.enqueueDirectDownload(
                                context,
                                target.copy(title = finalTitle)
                            )
                            if (ok) {
                                Toast.makeText(context, "İndirme başlatıldı (İndirilenler klasörü)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Kaynak kaydedildi", Toast.LENGTH_SHORT).show()
                            }
                            streamToSaveModal = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cihaza İndir (${target.format})",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = StavioFontFamily
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { streamToSaveModal = null }) {
                    Text("Kapat", color = Color(0xFF9CA3AF), fontFamily = StavioFontFamily)
                }
            }
        )
    }
}

/**
 * TAM EKRAN ETKİLEŞİMLİ SİTE GEZGİNİ & YAKALAYICI:
 * - LazyColumn dışındadır; kaydırma, tıklama, menü açma ve site içinde gezinme %100 akıcıdır.
 * - Arka planda ses çıkmasını tamamen engeller (tüm videoları sessize alır).
 * - Kullanıcı sitede hangi film/dizi/kanal sayfasına girerse o sayfadaki M3U8, MP4, TS kaynaklarını anında yakalar.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun StavioFullInteractiveSiteBrowser(
    initialUrl: String,
    capturedCount: Int,
    onStreamCaptured: (ExtractedStreamSource) -> Unit,
    onPageVisited: (String) -> Unit,
    onCloseBrowser: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var addressBarText by remember(initialUrl) { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isPageLoading by remember { mutableStateOf(true) }

    val currentOnCaptured by rememberUpdatedState(onStreamCaptured)
    val currentOnPageVisited by rememberUpdatedState(onPageVisited)

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onCloseBrowser()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF07080B)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Üst Gezinme ve Adres Çubuğu
                Surface(
                    color = Color(0xFF121318),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Kapat / Sonuçlara Dön
                            IconButton(
                                onClick = onCloseBrowser,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Kapat",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // WebView Geri
                            IconButton(
                                onClick = { webViewRef?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBack,
                                    contentDescription = "Önceki Sayfa",
                                    tint = if (canGoBack) Color.White else Color(0xFF4B5563),
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // WebView İleri
                            IconButton(
                                onClick = { webViewRef?.goForward() },
                                enabled = canGoForward,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowForward,
                                    contentDescription = "Sonraki Sayfa",
                                    tint = if (canGoForward) Color.White else Color(0xFF4B5563),
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Adres Çubuğu
                            OutlinedTextField(
                                value = addressBarText,
                                onValueChange = { addressBarText = it },
                                singleLine = true,
                                placeholder = {
                                    Text("Site adresi girin...", fontSize = 12.5.sp, color = Color(0xFF6B7280))
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        focusManager.clearFocus()
                                        val target = StavioStreamExtractor.normalizeInputUrl(addressBarText)
                                        if (target.isNotBlank()) {
                                            webViewRef?.loadUrl(target)
                                        }
                                    }
                                ),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF090A0E),
                                    unfocusedContainerColor = Color(0xFF090A0E),
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.5.sp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            )

                            // Yenile
                            IconButton(
                                onClick = { webViewRef?.reload() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Yenile",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        if (isPageLoading) {
                            LinearProgressIndicator(
                                color = Color(0xFF3B82F6),
                                trackColor = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .height(2.dp)
                            )
                        }
                    }
                }

                // Tam Ekran Kaydırılabilir WebView
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            isVerticalScrollBarEnabled = true
                            isHorizontalScrollBarEnabled = false
                            isFocusable = true
                            isFocusableInTouchMode = true

                            // Dokunma ve kaydırma olaylarının üst bileşenler tarafından engellenmesini önle
                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                }
                                false
                            }

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = ExtractedStreamSource.DEFAULT_BROWSER_UA
                                loadsImagesAutomatically = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                setSupportMultipleWindows(false) // Reklam pop-up pencerelerini engelle
                            }

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onUrlSniffed(url: String, pageRef: String, pageTitle: String) {
                                        if (url.isBlank()) return
                                        val candidate = StavioStreamExtractor.inspectInterceptedNetworkRequest(
                                            requestUrl = url,
                                            mimeType = null,
                                            requestHeaders = mapOf("Referer" to pageRef),
                                            pageUrl = pageRef.ifBlank { initialUrl },
                                            pageTitle = pageTitle
                                        )
                                        if (candidate != null) {
                                            currentOnCaptured(candidate)
                                        }
                                    }
                                },
                                "StavioSnifferBridge"
                            )

                            webChromeClient = object : WebChromeClient() {}

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val reqUrl = request?.url?.toString().orEmpty()
                                    if (reqUrl.isBlank()) return true
                                    // Harici uygulama / reklam protokollerini engelle
                                    if (!reqUrl.startsWith("http://", ignoreCase = true) && !reqUrl.startsWith("https://", ignoreCase = true)) {
                                        return true
                                    }
                                    // Eğer tıklanan link doğrudan bir video akışıysa yakala
                                    if (StavioStreamExtractor.looksLikeDirectStreamUrl(reqUrl)) {
                                        val cand = StavioStreamExtractor.inspectInterceptedNetworkRequest(
                                            requestUrl = reqUrl,
                                            mimeType = null,
                                            requestHeaders = mapOf("Referer" to (view?.url ?: initialUrl)),
                                            pageUrl = view?.url ?: initialUrl,
                                            pageTitle = view?.title.orEmpty()
                                        )
                                        if (cand != null) {
                                            currentOnCaptured(cand)
                                        }
                                    }
                                    return false
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString().orEmpty()
                                    if (reqUrl.isNotBlank()) {
                                        val headers = request?.requestHeaders.orEmpty()
                                        val candidate = StavioStreamExtractor.inspectInterceptedNetworkRequest(
                                            requestUrl = reqUrl,
                                            mimeType = headers["Accept"],
                                            requestHeaders = headers,
                                            pageUrl = initialUrl,
                                            pageTitle = ""
                                        )
                                        if (candidate != null) {
                                            currentOnCaptured(candidate)
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isPageLoading = true
                                    if (!url.isNullOrBlank()) {
                                        addressBarText = url
                                    }
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    injectSilentStreamSnifferJs(view, autoClickPlay = false)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isPageLoading = false
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    injectSilentStreamSnifferJs(view, autoClickPlay = false)
                                    if (!url.isNullOrBlank() && url.startsWith("http")) {
                                        currentOnPageVisited(url)
                                    }
                                }
                            }

                            webViewRef = this
                            loadUrl(initialUrl)
                        }
                    },
                    onRelease = { webView ->
                        runCatching {
                            webView.loadUrl("about:blank")
                            webView.onPause()
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }
                )
            }

            // Alt Yüzen Durum & Sonuçlara Dönüş Kapsülü
            Surface(
                onClick = onCloseBrowser,
                shape = RoundedCornerShape(999.dp),
                color = if (capturedCount > 0) Color(0xFF10B981) else Color(0xE6181A22),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = if (capturedCount > 0) Icons.Rounded.CheckCircle else Icons.Rounded.Radar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (capturedCount > 0) {
                            "$capturedCount Kaynak Yakalandı • Listeyi Gör & Oynat"
                        } else {
                            "Sitede videoyu açın • Kaynak otomatik yakalanır"
                        },
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = StavioFontFamily
                    )
                }
            }
        }
    }
}

/**
 * Arka Plan Sessiz Yakalayıcı WebView:
 * - LazyColumn dışındadır (0.dp boyutunda).
 * - Tüm sesleri (%100 mute + volume=0 + pause + AudioContext suspend) engeller; arama sırasında asla ses çıkmaz.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun StavioSilentHeadlessWebSniffer(
    targetUrl: String,
    onStreamCaptured: (ExtractedStreamSource) -> Unit
) {
    val currentOnCaptured by rememberUpdatedState(onStreamCaptured)

    AndroidView(
        modifier = Modifier.size(0.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = ExtractedStreamSource.DEFAULT_BROWSER_UA
                    loadsImagesAutomatically = false
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onUrlSniffed(url: String, pageRef: String, pageTitle: String) {
                            if (url.isBlank()) return
                            val candidate = StavioStreamExtractor.inspectInterceptedNetworkRequest(
                                requestUrl = url,
                                mimeType = null,
                                requestHeaders = mapOf("Referer" to pageRef),
                                pageUrl = pageRef.ifBlank { targetUrl },
                                pageTitle = pageTitle
                            )
                            if (candidate != null) {
                                currentOnCaptured(candidate)
                            }
                        }
                    },
                    "StavioSnifferBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString().orEmpty()
                        if (reqUrl.isNotBlank()) {
                            val headers = request?.requestHeaders.orEmpty()
                            val candidate = StavioStreamExtractor.inspectInterceptedNetworkRequest(
                                requestUrl = reqUrl,
                                mimeType = headers["Accept"],
                                requestHeaders = headers,
                                pageUrl = targetUrl,
                                pageTitle = ""
                            )
                            if (candidate != null) {
                                currentOnCaptured(candidate)
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        injectSilentStreamSnifferJs(view, autoClickPlay = true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        injectSilentStreamSnifferJs(view, autoClickPlay = true)
                    }
                }

                loadUrl(targetUrl)
            }
        },
        update = { webView ->
            if (webView.url != targetUrl && webView.originalUrl != targetUrl) {
                webView.loadUrl(targetUrl)
            }
        },
        onRelease = { webView ->
            runCatching {
                webView.loadUrl("about:blank")
                webView.onPause()
                webView.stopLoading()
                webView.destroy()
            }
        }
    )
}

/**
 * Sayfa içerisindeki XHR/Fetch/Video kaynaklarını yakalayan ve
 * tüm ses çıkışlarını (%100 sessiz) kilitleyen JavaScript kancası.
 */
private fun injectSilentStreamSnifferJs(webView: WebView?, autoClickPlay: Boolean) {
    val js = """
        (function() {
            function silenceAllMedia() {
                try {
                    document.querySelectorAll('video, audio').forEach(function(el) {
                        el.muted = true;
                        el.defaultMuted = true;
                        el.volume = 0;
                        if (${if (autoClickPlay) "true" else "false"} && !el.paused) {
                            el.pause();
                        }
                    });
                } catch(e) {}
            }
            silenceAllMedia();

            if (window.__stavioSilentSnifferInstalled) return;
            window.__stavioSilentSnifferInstalled = true;

            // HTMLMediaElement sesini tamamen kilitle (Asla dışarı ses çıkmasın)
            try {
                Object.defineProperty(HTMLMediaElement.prototype, 'muted', {
                    get: function() { return true; },
                    set: function() {}
                });
                Object.defineProperty(HTMLMediaElement.prototype, 'volume', {
                    get: function() { return 0; },
                    set: function() {}
                });
                if (window.AudioContext) {
                    const origResume = AudioContext.prototype.resume;
                    AudioContext.prototype.resume = function() {
                        try { this.suspend(); } catch(e) {}
                        return Promise.resolve();
                    };
                }
            } catch(e) {}

            function report(u) {
                try {
                    if (!u || typeof u !== 'string') return;
                    if (u.startsWith('blob:') || u.startsWith('data:')) return;
                    if (u.startsWith('//')) u = window.location.protocol + u;
                    window.StavioSnifferBridge.onUrlSniffed(u, window.location.href, document.title || '');
                } catch(e) {}
            }

            const origPlay = HTMLMediaElement.prototype.play;
            HTMLMediaElement.prototype.play = function() {
                try {
                    this.muted = true;
                    this.volume = 0;
                    if (this.src) report(this.src);
                    if (this.currentSrc) report(this.currentSrc);
                    this.pause();
                } catch(e) {}
                return Promise.resolve();
            };

            const origFetch = window.fetch;
            if (origFetch) {
                window.fetch = function() {
                    try {
                        const arg = arguments[0];
                        const url = (typeof arg === 'string') ? arg : (arg && arg.url ? arg.url : '');
                        report(url);
                    } catch(e) {}
                    return origFetch.apply(this, arguments);
                };
            }

            const origOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                try { report(url); } catch(e) {}
                return origOpen.apply(this, arguments);
            };

            function scanDomMedia() {
                try {
                    silenceAllMedia();
                    document.querySelectorAll('video, source, iframe, embed').forEach(function(el) {
                        if (el.src) report(el.src);
                        if (el.currentSrc) report(el.currentSrc);
                        ['data-src', 'data-lazy-src', 'data-litespeed-src', 'data-file', 'data-hls', 'data-embed', 'data-frame'].forEach(function(attr) {
                            const val = el.getAttribute(attr);
                            if (val) report(val);
                        });
                    });
                    if (${if (autoClickPlay) "true" else "false"}) {
                        document.querySelectorAll('.jw-icon-display, .vjs-big-play-button, .plyr__control--overlaid, .art-state, .dplayer-play-icon').forEach(function(btn) {
                            if (!btn.__stavioClicked) {
                                btn.__stavioClicked = true;
                                btn.click();
                            }
                        });
                    }
                } catch(e) {}
            }
            scanDomMedia();
            setInterval(scanDomMedia, 900);
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}

@Composable
private fun StavioExtractedStreamCard(
    source: ExtractedStreamSource,
    isSaved: Boolean,
    showDeleteButton: Boolean = false,
    onPlay: () -> Unit,
    onSaveClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    val badgeColor = when (source.format) {
        "M3U8", "M3U" -> Color(0xFF8B5CF6)
        "MP4" -> Color(0xFF10B981)
        "TS" -> Color(0xFFF59E0B)
        "MPD" -> Color(0xFF06B6D4)
        "MKV", "WEBM" -> Color(0xFFEC4899)
        else -> Color(0xFF3B82F6)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF121318),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.18f),
                        border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = source.format,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = StavioFontFamily,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (source.quality.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E222B)
                        ) {
                            Text(
                                text = source.quality,
                                color = Color(0xFFD1D5DB),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = StavioFontFamily,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (source.isHiddenSource) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "GİZLİ KAYNAK",
                                color = Color(0xFFF87171),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = StavioFontFamily,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text(
                    text = source.discoveryMethod,
                    color = Color(0xFF6B7280),
                    fontSize = 10.5.sp,
                    fontFamily = StavioFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = source.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = StavioFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = source.streamUrl,
                color = Color(0xFF9CA3AF),
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Oynat",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = StavioFontFamily
                    )
                }

                Surface(
                    onClick = onSaveClick,
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSaved) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFF1D2028),
                    border = BorderStroke(
                        1.dp,
                        if (isSaved) Color(0xFF10B981).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                            contentDescription = "Kaydet",
                            tint = if (isSaved) Color(0xFF34D399) else Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isSaved) "Kaydedildi" else "Kaydet",
                            color = if (isSaved) Color(0xFF34D399) else Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = StavioFontFamily
                        )
                    }
                }

                Surface(
                    onClick = onCopyClick,
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1D2028),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Bağlantıyı Kopyala",
                            tint = Color(0xFFD1D5DB),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showDeleteButton && onDeleteClick != null) {
                    Surface(
                        onClick = onDeleteClick,
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2A161B),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Sil",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
