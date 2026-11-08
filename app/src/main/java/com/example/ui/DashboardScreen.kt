package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.ChannelEntity
import com.example.ui.theme.*
import com.example.util.LocalizationManager
import com.example.viewmodel.MainTab
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.PrecomputedTabCatalog
import com.example.viewmodel.ScreenState
import com.example.viewmodel.WatchedMediaEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val locManager = remember { LocalizationManager.getInstance(context) }
    val currentLang by locManager.currentLanguage.collectAsStateWithLifecycle()
    val theme = LocalAppTheme.current

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isStreamExtractorOpen by viewModel.isStreamExtractorOpen.collectAsStateWithLifecycle()
    val allChannels by viewModel.allChannels.collectAsStateWithLifecycle()
    val catalogByType by viewModel.catalogByType.collectAsStateWithLifecycle()
    val isCatalogComputing by viewModel.isCatalogComputing.collectAsStateWithLifecycle()
    val isInitialStartupLoading by viewModel.isInitialStartupLoading.collectAsStateWithLifecycle()
    val isGlobalLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var showImportDialog by remember { mutableStateOf(false) }

    val profileInitial = remember(currentTab) {
        val saved = viewModel.prefs.getString("stavio_active_profile", "Kullanıcı (K)") ?: "K"
        saved.firstOrNull()?.uppercaseChar()?.toString() ?: "K"
    }

    // Üst bar tamamen kaldırıldı; alt bar ise yalnızca Linkten Kaynak Bulucu sayfasındayken gizlenir, geri çıkılınca hemen geri gelir
    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {},
        bottomBar = {
            val _lang = currentLang
            if (!(currentTab == MainTab.SETTINGS && isStreamExtractorOpen)) {
                StavioReferenceFloatingBottomBar(
                    currentTab = currentTab,
                    profileInitial = profileInitial,
                    onSelectTab = {
                        viewModel.isStreamExtractorOpen.value = false
                        viewModel.selectTabAndNavigate(it)
                    }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == MainTab.PLAYLISTS) {
                ExtendedFloatingActionButton(
                    onClick = { showImportDialog = true },
                    containerColor = Color(0xFF3B82F6),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    elevation = FloatingActionButtonDefaults.elevation(8.dp),
                    icon = { Icon(Icons.Rounded.Add, contentDescription = "Ekle") },
                    text = { Text("Yeni Liste Ekle", fontWeight = FontWeight.Bold, fontFamily = StavioFontFamily) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val bottomInsetPadding = innerPadding.calculateBottomPadding()

            // Menüler arası akıcı ve canlı geçiş animasyonu
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.985f,
                            animationSpec = tween(180, easing = FastOutSlowInEasing)
                        )) togetherWith
                        fadeOut(animationSpec = tween(120, easing = FastOutLinearInEasing))
                },
                label = "StavioSmoothTabSwitch"
            ) { tab ->
                when (tab) {
                    MainTab.LIVE, MainTab.MOVIES, MainTab.SERIES -> {
                        val mediaType = when (tab) {
                            MainTab.MOVIES -> "movie"
                            MainTab.SERIES -> "series"
                            else -> "live"
                        }
                        val catalog = catalogByType[mediaType] ?: PrecomputedTabCatalog()
                        val showLoadingState = isCatalogComputing || isInitialStartupLoading || isGlobalLoading ||
                            (allChannels.isNotEmpty() && catalogByType.isEmpty())
                        FilteredTabContent(
                            viewModel = viewModel,
                            tab = tab,
                            mediaType = mediaType,
                            groupedChannels = catalog.groupedChannels,
                            totalMediaTypeCount = catalog.totalCount,
                            isLoading = showLoadingState,
                            bottomOverlayPadding = bottomInsetPadding
                        )
                    }
                    MainTab.SEARCH -> {
                        StavioDedicatedSearchScreen(
                            allChannels = allChannels,
                            viewModel = viewModel,
                            bottomOverlayPadding = bottomInsetPadding
                        )
                    }
                    MainTab.PLAYLISTS -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                        ) {
                            IPTVPlaylistsTabContent(viewModel)
                        }
                    }
                    MainTab.SETTINGS -> {
                        SettingsScreen(viewModel)
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        PlaylistImportDialog(
            onDismiss = { showImportDialog = false },
            onImportUrl = { url, name ->
                viewModel.addPlaylistFromUrl(url, name)
                showImportDialog = false
            },
            onImportRaw = { _, _ -> },
            onImportUri = { uri, ctx, name ->
                viewModel.addPlaylistFromUri(uri, ctx, name)
                showImportDialog = false
            },
            onImportMag = { portal, mac, name ->
                viewModel.addMagPlaylist(portal, mac, name)
                showImportDialog = false
            }
        )
    }
}

/**
 * Alt bar için çok hafif yukarı doğru bombeli (konveks) kapsül şekli.
 * Sol ve sağ uçlar tam yuvarlak kapsül, üst kenar ise ortada çok hafif (3.2.dp) yukarı doğru kavislenir.
 */
private class StavioSubtleDomedCapsuleShape(
    private val archDp: androidx.compose.ui.unit.Dp = 3.2.dp
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val archPx = with(density) { archDp.toPx() }
        val r = (size.height - archPx) / 2f
        val topY = archPx
        val bottomY = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(r, topY)
            // Üst kenar: çok hafif yukarı doğru bombeli (quadratic bezier)
            quadraticTo(
                size.width * 0.5f,
                -archPx * 0.65f,
                size.width - r,
                topY
            )
            // Sağ yarım daire uç
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = size.width - 2f * r,
                    top = topY,
                    right = size.width,
                    bottom = bottomY
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            // Alt kenar: çok hafif paralel kavis
            quadraticTo(
                size.width * 0.5f,
                bottomY - archPx * 0.45f,
                r,
                bottomY
            )
            // Sol yarım daire uç
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = 0f,
                    top = topY,
                    right = 2f * r,
                    bottom = bottomY
                ),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/**
 * STAVIO YÜZEN KAPSÜL ALT BAR (Canlı • Film • Dizi • Ara • Ayarlar)
 * - IPTV sekmesi alt bardan kaldırıldı (Ayarlar'da en üste eklendi).
 * - Seçili menünün yanında adı yazar (sadece seçili olanda), seçili olmayanlarda yalnızca ikon görünür.
 * - Alt menü yukarı doğru çok hafif bombelidir.
 */
@Composable
fun StavioReferenceFloatingBottomBar(
    currentTab: MainTab,
    profileInitial: String,
    onSelectTab: (MainTab) -> Unit
) {
    val navItems = remember {
        listOf(
            Triple(MainTab.LIVE, Icons.Rounded.Tv, "Canlı"),
            Triple(MainTab.MOVIES, Icons.Rounded.Movie, "Film"),
            Triple(MainTab.SERIES, Icons.Rounded.VideoLibrary, "Dizi"),
            Triple(MainTab.SEARCH, Icons.Rounded.Search, "Ara"),
            Triple(MainTab.SETTINGS, Icons.Rounded.Person, "Ayarlar")
        )
    }

    val domedShape = remember { StavioSubtleDomedCapsuleShape(archDp = 3.2.dp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = domedShape,
            color = Color(0xDB15161B),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x55FFFFFF),
                        Color(0x22FFFFFF),
                        Color(0x18FFFFFF),
                        Color(0x409CA3AF)
                    )
                )
            ),
            shadowElevation = 24.dp,
            modifier = Modifier
                .widthIn(min = 300.dp, max = 380.dp)
                .fillMaxWidth(0.92f)
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalCount = navItems.size
                navItems.forEachIndexed { index, (tab, icon, label) ->
                    val isSelected = currentTab == tab || (tab == MainTab.SETTINGS && currentTab == MainTab.PLAYLISTS)
                    // Ortadaki elemanlara çok hafif yukarı bombe (dome) ofseti
                    val normalizedCenterDist = kotlin.math.abs(index - (totalCount - 1) / 2f) / ((totalCount - 1) / 2f)
                    val subtleDomeOffsetY = (-1.8f * (1f - normalizedCenterDist * normalizedCenterDist)).dp

                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF3E4047) else Color.Transparent,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "navPillColor"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color(0xFFD0D4DC),
                        animationSpec = tween(durationMillis = 180),
                        label = "navIconTint"
                    )

                    Row(
                        modifier = Modifier
                            .offset(y = subtleDomeOffsetY)
                            .height(46.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(pillColor, RoundedCornerShape(999.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onSelectTab(tab)
                            }
                            .padding(
                                horizontal = if (isSelected) 16.dp else 12.dp,
                                vertical = 6.dp
                            )
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (tab == MainTab.SETTINGS) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFF101C2E), CircleShape)
                                    .border(
                                        width = 2.dp,
                                        color = if (isSelected) Color(0xFF4C9AFF) else Color(0xFF1D63DC),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profileInitial,
                                    color = if (isSelected) Color.White else Color(0xFF4C9AFF),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = StavioFontFamily
                                )
                            }
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = iconTint,
                                modifier = Modifier.size(23.dp)
                            )
                        }

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = StavioFontFamily,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1. Fotoğraftaki "İzlemeye devam" Kartı (Alacakaranlık Efsanesi / Squid Game stili):
 * - Sadece "İzlemeye devam" kısmı yataydır (230.dp x 136.dp, RoundedCornerShape(16.dp)).
 * - Sağ üstte koyu yuvarlak kapsül içinde kalan süre ("1s 47d kaldı" / "Canlı").
 * - Sol altta içerik adı ve hemen altında ince beyaz ilerleme çubuğu.
 */
@Composable
private fun StavioContinueWatchingLandscapeCard(
    entry: WatchedMediaEntry,
    fillGridWidth: Boolean = false,
    onClick: () -> Unit
) {
    var imgError by remember(entry.logoUrl) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF14151A),
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.10f)),
        modifier = (if (fillGridWidth) Modifier.fillMaxWidth() else Modifier.width(230.dp))
            .height(136.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (entry.logoUrl.isNotBlank() && !imgError) {
                AsyncImage(
                    model = entry.logoUrl,
                    contentDescription = entry.itemName,
                    contentScale = ContentScale.Crop,
                    onError = { imgError = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1E2430), Color(0xFF0D1017))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircleOutline,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            // Sinematik alt karartma
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.05f),
                                0.48f to Color.Black.copy(alpha = 0.22f),
                                1.0f to Color.Black.copy(alpha = 0.86f)
                            )
                        )
                    )
            )

            // Sağ üst kalan süre kapsülü ("1s 47d kaldı")
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xBA18191E),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            ) {
                Text(
                    text = entry.remainingTimeBadgeText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = StavioFontFamily,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            // Sol alt başlık + beyaz ilerleme çubuğu
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = entry.itemName,
                    color = Color.White,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = StavioFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // İnce beyaz ilerleme çubuğu
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.24f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(entry.progressFraction.coerceIn(0.08f, 1f))
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

/**
 * STAVIO ÖZEL ARAMA EKRANI (Alt bardaki Ara sekmesi için hızlı ve akıcı arama deneyimi)
 */
@Composable
fun StavioDedicatedSearchScreen(
    allChannels: List<ChannelEntity>,
    viewModel: MainViewModel,
    bottomOverlayPadding: androidx.compose.ui.unit.Dp
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("all") }

    val filteredResults = remember(allChannels, query, selectedFilter) {
        val base = if (query.isBlank()) {
            allChannels.take(80)
        } else {
            allChannels.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.groupTitle.contains(query, ignoreCase = true)
            }.take(150)
        }
        if (selectedFilter == "all") {
            base
        } else {
            base.filter { viewModel.determineMediaType(it) == selectedFilter }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(top = 14.dp)
    ) {
        Text(
            text = "Ara",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = StavioFontFamily,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Koyu Minimalist Arama Çubuğu
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF141418),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Ara",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = "Film, dizi veya canlı kanal ara...",
                            color = Color(0xFF8E8E93),
                            fontSize = 15.sp,
                            fontFamily = StavioFontFamily
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Temizle",
                            tint = Color(0xFF8E8E93)
                        )
                    }
                }
            }
        }

        // Hızlı Tür Filtre Hapları (Tümü, Filmler, Diziler, Canlı)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            val filters = listOf(
                "all" to "Tümü",
                "movie" to "Filmler",
                "series" to "Diziler",
                "live" to "Canlı"
            )
            items(filters, key = { it.first }) { (key, label) ->
                val isSel = selectedFilter == key
                Surface(
                    onClick = { selectedFilter = key },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSel) Color(0xFF42454D) else Color(0xFF17191F),
                    border = BorderStroke(1.dp, if (isSel) Color(0xFF707582) else Color(0xFF2D3039))
                ) {
                    Text(
                        text = label,
                        color = if (isSel) Color.White else Color(0xFFA7ACB8),
                        fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                        fontFamily = StavioFontFamily,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }
        }

        Box(modifier = Modifier.padding(bottom = bottomOverlayPadding)) {
            SearchResultsLayout(channels = filteredResults, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultsLayout(channels: List<ChannelEntity>, viewModel: MainViewModel) {
    if (channels.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sonuç bulunamadı",
                color = Color(0xFF8E8E93),
                fontSize = 16.sp,
                fontFamily = StavioFontFamily
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(channels, key = { idx, ch -> "search_${idx}_${ch.id}" }) { _, channel ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF121214),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.handleMediaItemClick(channel = channel) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E1E22),
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (channel.logoUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = channel.logoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Tv,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel.name,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                fontFamily = StavioFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = channel.groupTitle.ifEmpty { "Genel" },
                                color = Color(0xFF8E8E93),
                                fontSize = 12.5.sp,
                                fontFamily = StavioFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * FilteredTabContent (Canlı, Filmler ve Diziler için Fotoğraf 1 ve Fotoğraf 2 ile Birebir Tasarım):
 * - En üstte menü adı ("Canlı", "Filmler", "Diziler") ve hemen altında 2. fotoğraftaki işaretli alanın
 *   aynısı olan yatay kategori filtre hapları ("Tümü (214)", "İzlemeye devam (2)", "Öne Çıkanlar (24)", ...).
 * - Spotlight ve Genres gibi kısımlar kesinlikle yoktur.
 * - "İzlemeye devam" yatay kartlardan oluşur (Fotoğraf 1).
 * - Diğer tüm IPTV kategorileri Fotoğraf 1'deki gibi dikey poster kartlardan oluşur ve isimleri posterin hemen altında yazar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilteredTabContent(
    viewModel: MainViewModel,
    tab: MainTab,
    mediaType: String,
    groupedChannels: List<Pair<String, List<Pair<String, List<ChannelEntity>>>>>,
    totalMediaTypeCount: Int,
    isLoading: Boolean,
    topOverlayPadding: androidx.compose.ui.unit.Dp = 0.dp,
    bottomOverlayPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val quickCategories by viewModel.quickCategoryByTab.collectAsStateWithLifecycle()
    val selectedQuickCategory = quickCategories[mediaType]
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()

    // Kategori seçiliyken geri tuşuna basıldığında "Tümü" görünümüne dön
    BackHandler(enabled = selectedQuickCategory != null) {
        viewModel.setQuickCategory(mediaType, null)
    }

    val listState = rememberSaveable(mediaType, saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }

    val tabTitle = when (tab) {
        MainTab.MOVIES -> "Filmler"
        MainTab.SERIES -> "Diziler"
        else -> "Canlı"
    }

    // İçerikler yüklenirken "Bu kategoride içerik bulunamadı" yerine şık yükleme ekranı göster
    if (isLoading && groupedChannels.isEmpty()) {
        StavioCategoryLoadingView(
            tabTitle = tabTitle,
            topOverlayPadding = topOverlayPadding,
            bottomOverlayPadding = bottomOverlayPadding
        )
        return
    }

    if (groupedChannels.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = when (tab) {
                        MainTab.MOVIES -> Icons.Rounded.Movie
                        MainTab.SERIES -> Icons.Rounded.VideoLibrary
                        else -> Icons.Rounded.Tv
                    },
                    contentDescription = null,
                    tint = Color(0xFF8E8E93).copy(alpha = 0.5f),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Bu kategoride içerik bulunamadı",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = StavioFontFamily
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ayarlar menüsünün en üstündeki IPTV bölümünden oynatma listenizi yönetebilirsiniz.",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                    fontFamily = StavioFontFamily,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val watchHistoryMap by viewModel.watchHistoryByType.collectAsStateWithLifecycle()
    val activePlaylistId = selectedPlaylist?.id?.toLong()

    // SADECE aktif oynatma listesine (activePlaylistId) ait izleme geçmişini filtrele
    val watchedEntriesForTab = remember(watchHistoryMap, mediaType, activePlaylistId) {
        if (activePlaylistId == null) {
            emptyList()
        } else {
            watchHistoryMap[mediaType]
                .orEmpty()
                .filter { it.playlistId == activePlaylistId }
        }
    }

    // Yalnızca mevcut oynatma listesinde bulunan içerikleri "İzlemeye devam" satırına ekle
    val watchedItemsForTab = remember(watchedEntriesForTab, groupedChannels, mediaType) {
        val allPairsByName = groupedChannels.asSequence()
            .flatMap { it.second.asSequence() }
            .associateBy { it.first.lowercase(java.util.Locale.ROOT).trim() }

        val allChannelsByUrl = groupedChannels.asSequence()
            .flatMap { it.second.asSequence() }
            .flatMap { it.second.asSequence() }
            .associateBy { it.url }

        watchedEntriesForTab.mapNotNull { entry ->
            val matchByName = allPairsByName[entry.itemName.lowercase(java.util.Locale.ROOT).trim()]
            if (matchByName != null && matchByName.second.isNotEmpty()) {
                Triple(entry, entry.itemName, matchByName.second)
            } else {
                val matchByUrl = allChannelsByUrl[entry.channelUrl]
                if (matchByUrl != null) {
                    Triple(entry, entry.itemName, listOf(matchByUrl))
                } else {
                    null
                }
            }
        }
    }

    val isLive = mediaType == "live"
    val continueWatchingFilterKey = "__CONTINUE_WATCHING__"

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            top = topOverlayPadding + 12.dp,
            bottom = bottomOverlayPadding + 44.dp
        )
    ) {
        // EN ÜSTTE: Menü Başlığı ("Canlı", "Filmler", "Diziler") + Hemen Altında İşaretli Alandaki Kategori Hapları (Fotoğraf 2)
        item(key = "top_tab_header_and_category_pills_$mediaType") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp)
            ) {
                Text(
                    text = tabTitle,
                    color = Color.White,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = StavioFontFamily,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. "Tümü (X)" Hapı
                    item(key = "pill_all_$mediaType") {
                        val isAllSelected = selectedQuickCategory == null
                        StavioCategoryFilterPill(
                            label = "Tümü ($totalMediaTypeCount)",
                            isSelected = isAllSelected,
                            onClick = { viewModel.setQuickCategory(mediaType, null) }
                        )
                    }

                    // 2. "İzlemeye devam (X)" Hapı (varsa)
                    if (watchedItemsForTab.isNotEmpty()) {
                        item(key = "pill_cw_$mediaType") {
                            val isCwSelected = selectedQuickCategory == continueWatchingFilterKey
                            StavioCategoryFilterPill(
                                label = "İzlemeye devam (${watchedItemsForTab.size})",
                                isSelected = isCwSelected,
                                onClick = {
                                    viewModel.setQuickCategory(
                                        mediaType,
                                        if (isCwSelected) null else continueWatchingFilterKey
                                    )
                                }
                            )
                        }
                    }

                    // 3. IPTV'nin Kendi Kategorileri ("Öne Çıkanlar (24)", vb.)
                    items(
                        groupedChannels,
                        key = { "pill_${mediaType}_${it.first}" }
                    ) { (catName, itemsList) ->
                        val isCatSelected = selectedQuickCategory == catName
                        StavioCategoryFilterPill(
                            label = "$catName (${itemsList.size})",
                            isSelected = isCatSelected,
                            onClick = {
                                viewModel.setQuickCategory(
                                    mediaType,
                                    if (isCatSelected) null else catName
                                )
                            }
                        )
                    }
                }
            }
        }

        if (selectedQuickCategory == continueWatchingFilterKey) {
            // "İzlemeye devam" filtresi seçiliyse: Yatay kartlar listesi
            item(key = "cw_filtered_header") {
                StavioSectionHeader(
                    title = "İzlemeye devam",
                    count = watchedItemsForTab.size,
                    onViewAllClick = { viewModel.setQuickCategory(mediaType, null) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val cwRows = watchedItemsForTab.chunked(2)
            itemsIndexed(
                cwRows,
                key = { idx, row -> "cw_grid_row_${mediaType}_${idx}_${row.firstOrNull()?.first?.key}" }
            ) { _, rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { (watchedEntry, itemName, channelsData) ->
                        val primaryChannel = channelsData.firstOrNull()
                        if (primaryChannel != null) {
                            val displayEntry = if (watchedEntry.logoUrl.isBlank() && primaryChannel.logoUrl.isNotBlank()) {
                                watchedEntry.copy(logoUrl = primaryChannel.logoUrl)
                            } else {
                                watchedEntry
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                StavioContinueWatchingLandscapeCard(
                                    entry = displayEntry,
                                    fillGridWidth = true,
                                    onClick = {
                                        viewModel.handleMediaItemClick(
                                            channel = primaryChannel,
                                            itemName = itemName,
                                            channelsGroup = channelsData,
                                            explicitMediaType = mediaType
                                        )
                                    }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    repeat(2 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else if (selectedQuickCategory != null) {
            // Belirli bir IPTV kategorisi seçiliyse: Dikey poster kartlarından oluşan 3 sütunlu Grid
            val selectedItems = groupedChannels.firstOrNull { it.first == selectedQuickCategory }?.second.orEmpty()
            val cols = 3
            val chunkedRows = selectedItems.chunked(cols)

            item(key = "selected_category_header_$selectedQuickCategory") {
                StavioSectionHeader(
                    title = selectedQuickCategory,
                    count = selectedItems.size,
                    onViewAllClick = { viewModel.setQuickCategory(mediaType, null) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            itemsIndexed(
                chunkedRows,
                key = { rowIdx, rowItems -> "grid_row_${selectedQuickCategory}_${rowIdx}_${rowItems.firstOrNull()?.second?.firstOrNull()?.id ?: rowIdx}" }
            ) { _, rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { (itemName, channelsData) ->
                        val channel = channelsData.firstOrNull()
                        if (channel != null) {
                            Box(modifier = Modifier.weight(1f)) {
                                StavioContentCard(
                                    channel = channel,
                                    itemName = itemName,
                                    channelsData = channelsData,
                                    isLive = isLive,
                                    mediaType = mediaType,
                                    viewModel = viewModel,
                                    fillGridWidth = true
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    repeat(cols - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            // VARSAYILAN "TÜMÜ" GÖRÜNÜMÜ (Fotoğraf 1 ile birebir):
            // 1. Üstte "İzlemeye devam" (Yatay kartlar + sağda "Tümü (X) >")
            if (watchedItemsForTab.isNotEmpty()) {
                item(key = "top_continue_watching_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 26.dp)
                    ) {
                        StavioSectionHeader(
                            title = "İzlemeye devam",
                            count = watchedItemsForTab.size,
                            onViewAllClick = {
                                viewModel.setQuickCategory(mediaType, continueWatchingFilterKey)
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                watchedItemsForTab.take(20),
                                key = { idx, triple -> "cw_${mediaType}_${idx}_${triple.first.key}" }
                            ) { _, (watchedEntry, itemName, channelsData) ->
                                val primaryChannel = channelsData.firstOrNull() ?: return@itemsIndexed
                                val displayEntry = if (watchedEntry.logoUrl.isBlank() && primaryChannel.logoUrl.isNotBlank()) {
                                    watchedEntry.copy(logoUrl = primaryChannel.logoUrl)
                                } else {
                                    watchedEntry
                                }
                                StavioContinueWatchingLandscapeCard(
                                    entry = displayEntry,
                                    onClick = {
                                        viewModel.handleMediaItemClick(
                                            channel = primaryChannel,
                                            itemName = itemName,
                                            channelsGroup = channelsData,
                                            explicitMediaType = mediaType
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. Altında IPTV'nin Kendi Kategorileri (Fotoğraf 1'deki dikey poster kartları ve altlarında isimleri)
            itemsIndexed(
                groupedChannels,
                key = { idx, pair -> "iptv_shelf_${mediaType}_${idx}_${pair.first}" }
            ) { _, (categoryTitle, itemsList) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 26.dp)
                ) {
                    StavioSectionHeader(
                        title = categoryTitle,
                        count = itemsList.size,
                        onViewAllClick = {
                            viewModel.setQuickCategory(mediaType, categoryTitle)
                        }
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        itemsIndexed(
                            itemsList.take(30),
                            key = { idx, item -> "${categoryTitle}_${idx}_${item.first}_${item.second.firstOrNull()?.id ?: idx}" }
                        ) { _, (itemName, channelsData) ->
                            val channel = channelsData.firstOrNull() ?: return@itemsIndexed

                            StavioContentCard(
                                channel = channel,
                                itemName = itemName,
                                channelsData = channelsData,
                                isLive = isLive,
                                mediaType = mediaType,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. Fotoğraftaki işaretli alanda yer alan yuvarlak kategori filtre hapı:
 * - Seçili: #42454D dolgu, #707582 ince çerçeve, beyaz yazı ("Tümü (214)")
 * - Seçili değil: #17191F koyu dolgu, #2D3039 ince çerçeve, açık gri yazı ("İzlemeye devam (2)", "Öne Çıkanlar (24)")
 */
@Composable
private fun StavioCategoryFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (isSelected) Color(0xFF42454D) else Color(0xFF17191F),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color(0xFF707582) else Color(0xFF2D3039)
        )
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFFA7ACB8),
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            fontFamily = StavioFontFamily,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun StavioCategoryLoadingView(
    tabTitle: String,
    topOverlayPadding: androidx.compose.ui.unit.Dp,
    bottomOverlayPadding: androidx.compose.ui.unit.Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stavioSkeletonPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topOverlayPadding + 12.dp, bottom = bottomOverlayPadding + 24.dp)
        ) {
            // En üstte sekme adı ("Canlı", "Filmler", "Diziler")
            Text(
                text = tabTitle,
                color = Color.White,
                fontSize = 31.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = StavioFontFamily,
                letterSpacing = (-0.4).sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Kategori hapları iskeleti
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                listOf(108.dp, 152.dp, 136.dp).forEach { w ->
                    Box(
                        modifier = Modifier
                            .width(w)
                            .height(38.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF1A1C22).copy(alpha = pulseAlpha))
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dikey poster satırları iskeleti
            repeat(2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 26.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .width(150.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1C1C22).copy(alpha = pulseAlpha))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        repeat(3) {
                            Column(modifier = Modifier.width(142.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(142.dp)
                                        .height(210.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF16161B).copy(alpha = pulseAlpha))
                                )
                                Spacer(modifier = Modifier.height(7.dp))
                                Box(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1C1C22).copy(alpha = pulseAlpha))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Ortada Zarif Stavio Yükleme Kapsülü
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xE614151A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            shadowElevation = 20.dp,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.4.dp,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "$tabTitle hazırlanıyor...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = StavioFontFamily
                    )
                    Text(
                        text = "Kategoriler ve içerikler yükleniyor",
                        color = Color(0xFFA1A1AA),
                        fontSize = 11.5.sp,
                        fontFamily = StavioFontFamily
                    )
                }
            }
        }
    }
}

/**
 * 1. Fotoğraftaki Kategori Satır Başlığı:
 * - Solda: "İzlemeye devam", "Öne Çıkanlar", "Unfamiliar" (23.sp, Beyaz)
 * - Sağda: "Tümü (24) >" (14.sp, Açık Gri #9CA3AF)
 */
@Composable
private fun StavioSectionHeader(
    title: String,
    count: Int,
    onViewAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewAllClick)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = StavioFontFamily,
            letterSpacing = (-0.3).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Tümü ($count)",
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = StavioFontFamily
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Tümünü Gör",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 1. Fotoğraftaki Dikey Poster Kartı ve Altındaki İsim Tasarımı ("Gönül Dağı", "O Kız - 1.", "Yargı"):
 * - Canlı, Film ve Diziler sekmelerinin tamamında kategori içerikleri bu dikey kartlarla gösterilir.
 * - Poster boyutu: 142.dp genişlik x 210.dp yükseklik, RoundedCornerShape(14.dp), ince çerçeve.
 * - İçeriğin adı posterin içinde değil, fotoğraf 1'deki gibi tam altında (sola hizalı, tek satır beyaz/açık gri) yazar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StavioContentCard(
    channel: ChannelEntity,
    itemName: String,
    channelsData: List<ChannelEntity>,
    isLive: Boolean,
    mediaType: String,
    viewModel: MainViewModel,
    fillGridWidth: Boolean = false
) {
    val cardWidth = 142.dp
    val posterHeight = if (fillGridWidth) 172.dp else 210.dp
    var imgError by remember(channel.logoUrl) { mutableStateOf(false) }

    Column(
        modifier = (if (fillGridWidth) Modifier.fillMaxWidth() else Modifier.width(cardWidth))
            .combinedClickable(
                onClick = {
                    viewModel.handleMediaItemClick(
                        channel = channel,
                        itemName = itemName,
                        channelsGroup = channelsData,
                        explicitMediaType = if (isLive) "live" else mediaType
                    )
                },
                onLongClick = {
                    viewModel.toggleFavorite(channel)
                }
            )
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF14151A),
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.10f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(posterHeight)
                .clip(RoundedCornerShape(14.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (channel.logoUrl.isNotEmpty() && !imgError) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = itemName,
                        contentScale = ContentScale.Crop,
                        onError = { imgError = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF1F2430), Color(0xFF0E1017)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isLive) Icons.Rounded.Tv else Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = itemName,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = StavioFontFamily,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (channel.isFavorite) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.60f), CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = "Favori",
                            tint = ColorGold,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = itemName,
            color = Color(0xFFE5E7EB),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = StavioFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

/**
 * IPTVPlaylistsTabContent: Ayarlar'ın en üstünden erişilen IPTV oynatma listeleri yönetim ekranı
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IPTVPlaylistsTabContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    var selectedPlaylistForOptions by remember { mutableStateOf<com.example.data.PlaylistEntity?>(null) }
    var showAddPlaylistModal by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.selectTabAndNavigate(MainTab.SETTINGS)
    }

    if (showAddPlaylistModal) {
        PlaylistImportDialog(
            onDismiss = { showAddPlaylistModal = false },
            onImportUrl = { url, name ->
                viewModel.addPlaylistFromUrl(url, name)
                showAddPlaylistModal = false
            },
            onImportRaw = { _, _ -> },
            onImportUri = { uri, ctx, name ->
                viewModel.addPlaylistFromUri(uri, ctx, name)
                showAddPlaylistModal = false
            },
            onImportMag = { portal, mac, name ->
                viewModel.addMagPlaylist(portal, mac, name)
                showAddPlaylistModal = false
            }
        )
    }

    if (selectedPlaylistForOptions != null) {
        val playlist = selectedPlaylistForOptions!!
        AlertDialog(
            onDismissRequest = { selectedPlaylistForOptions = null },
            containerColor = Color(0xFF121214),
            title = {
                Text(
                    text = "Seçenekler: ${playlist.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = StavioFontFamily
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            viewModel.deletePlaylist(playlist)
                            selectedPlaylistForOptions = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x33E53935),
                            contentColor = Color(0xFFFF4D4D)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Listeyi Sil", fontWeight = FontWeight.Bold, fontFamily = StavioFontFamily)
                    }

                    if (playlist.urlOrPath.isNotEmpty()) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Playlist Link", playlist.urlOrPath)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Bağlantı panoya kopyalandı", android.widget.Toast.LENGTH_SHORT).show()
                                selectedPlaylistForOptions = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3B82F6),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Bağlantıyı Kopyala", fontWeight = FontWeight.Bold, fontFamily = StavioFontFamily)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPlaylistForOptions = null }) {
                    Text("Kapat", color = Color(0xFF8E8E93), fontFamily = StavioFontFamily)
                }
            }
        )
    }

    if (playlists.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = Color(0xFF8E8E93).copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Henüz Oynatma Listesi Eklenmedi",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = StavioFontFamily
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "M3U, Xtream Codes veya MAC Portal (Stalker) listenizi eklemek için aşağıdaki butona dokunun.",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                    fontFamily = StavioFontFamily,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { showAddPlaylistModal = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Yeni Liste Ekle", fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = StavioFontFamily)
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 110.dp, top = 16.dp, start = 18.dp, end = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "playlists_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.selectTabAndNavigate(MainTab.SETTINGS) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBackIosNew,
                            contentDescription = "Ayarlar'a Dön",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "IPTV & Kaynaklar",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = StavioFontFamily
                    )
                }
            }
            item(key = "add_playlist_top_button") {
                Button(
                    onClick = { showAddPlaylistModal = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(bottom = 4.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Yeni Liste Ekle (M3U / Xtream / MAC Portal)", fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = StavioFontFamily)
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                val isMag = playlist.type.contains("MAG", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF121214),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { viewModel.selectPlaylistAndNavigate(playlist) },
                            onLongClick = { selectedPlaylistForOptions = playlist }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF232327), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isMag) Icons.Rounded.Star else Icons.Rounded.Dns,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = playlist.name,
                                    color = Color.White,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = StavioFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${playlist.type} • ${if (playlist.expirationDate != null) "Son Kul: ${playlist.expirationDate}" else "Sınırsız Erişim"}",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.5.sp,
                                    fontFamily = StavioFontFamily
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedPlaylistForOptions = playlist },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "Seçenekler",
                                tint = Color(0xFF8E8E93)
                            )
                        }
                    }
                }
            }
        }
    }
}
