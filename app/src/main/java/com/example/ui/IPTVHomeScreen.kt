package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.ChannelEntity
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ScreenState
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun IPTVHomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val previousScreen by viewModel.previousScreen.collectAsStateWithLifecycle()
    val isPlayerActive by viewModel.isPlayerActive.collectAsStateWithLifecycle()
    val currentlyPlaying by viewModel.currentlyPlaying.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.loadingProgress.collectAsStateWithLifecycle()
    val loadingMessage by viewModel.loadingMessage.collectAsStateWithLifecycle()
    val loadingInfo by viewModel.loadingInfo.collectAsStateWithLifecycle()
    val hasSelectedInitialLanguage by viewModel.hasSelectedInitialLanguage.collectAsStateWithLifecycle()
    val theme = LocalAppTheme.current
    var showPlaylistDialog by remember { mutableStateOf(false) }

    // Hızlı Açılış Ekranı (Splash Screen):
    // Kullanıcının gönderdiği fotoğraftaki gibi Stavio PNG logosu + "Stavio" yazısı + altında yüklenme işareti (750ms hızlı açılış)
    var showLaunchSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (showLaunchSplash) {
            delay(750L)
            showLaunchSplash = false
        }
    }

    // System Back Handler
    BackHandler(enabled = !isLoading && !showLaunchSplash) {
        viewModel.goBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        val rawBaseScreen = if (currentScreen == ScreenState.PLAYER) previousScreen else currentScreen
        val baseScreen = if (rawBaseScreen == ScreenState.PLAYLISTS || rawBaseScreen == ScreenState.PLAYER) {
            ScreenState.DASHBOARD
        } else {
            rawBaseScreen
        }

        // DashboardScreen her zaman bellekte (composition içinde) hazır tutulur;
        // böylece bir film/dizi detayından veya kategoriden geri dönüldüğünde 0ms anında açılır (yeniden yükleme yapmaz).
        DashboardScreen(viewModel)

        AnimatedVisibility(
            visible = baseScreen == ScreenState.CHANNELS,
            enter = fadeIn(animationSpec = tween(85)),
            exit = fadeOut(animationSpec = tween(70))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.background)
                    .pointerInput(Unit) { detectTapGestures {} }
            ) {
                ChannelsScreen(viewModel)
            }
        }

        AnimatedVisibility(
            visible = baseScreen == ScreenState.VOD_DETAIL,
            enter = fadeIn(animationSpec = tween(85)),
            exit = fadeOut(animationSpec = tween(70))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.background)
                    .pointerInput(Unit) { detectTapGestures {} }
            ) {
                VodDetailScreen(viewModel)
            }
        }

        // Fullscreen Player Overlay
        if (currentScreen == ScreenState.PLAYER || isPlayerActive || currentlyPlaying != null) {
            PlayerScreen(viewModel)
        }

        // Mandatory Onboarding: Top 5 Languages + Turkish on First App Launch
        if (!showLaunchSplash && !hasSelectedInitialLanguage && currentScreen != ScreenState.PLAYER && !isPlayerActive && currentlyPlaying == null) {
            LanguageSelectionModal(
                isInitialOnboarding = true,
                onDismiss = { }
            )
        }

        errorMessage?.let { error ->
            Snackbar(
                containerColor = com.example.ui.theme.ErrorRed,
                contentColor = com.example.ui.theme.TextPrimary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                dismissAction = { viewModel.errorMessage.value = null }
            ) {
                Text(text = error, fontWeight = FontWeight.Bold)
            }
        }

        // Custom Playlist Import Dialog
        if (showPlaylistDialog) {
            PlaylistImportDialog(
                onDismiss = { showPlaylistDialog = false },
                onImportUrl = { url, name ->
                    viewModel.addPlaylistFromUrl(url, name)
                    showPlaylistDialog = false
                },
                onImportRaw = { text, name ->
                },
                onImportUri = { uri, ctx, name ->
                    viewModel.addPlaylistFromUri(uri, ctx, name)
                    showPlaylistDialog = false
                },
                onImportMag = { portal, mac, name ->
                    viewModel.addMagPlaylist(portal, mac, name)
                    showPlaylistDialog = false
                }
            )
        }

        // Global Loading Overlay
        if (isLoading) {
            Dialog(onDismissRequest = { /* Cannot dismiss */ }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.SurfaceDark),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = com.example.ui.theme.ColorAccentPrime,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = loadingMessage.ifEmpty { "Yükleniyor..." },
                            color = com.example.ui.theme.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        if (isLoading && loadingProgress > 0f) {
                            Text(
                                text = "%${(loadingProgress * 100).toInt()}",
                                color = com.example.ui.theme.ColorAccentPrime,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp
                            )
                            LinearProgressIndicator(
                                progress = { loadingProgress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = com.example.ui.theme.ColorAccentPrime,
                                trackColor = com.example.ui.theme.SurfaceDarkHover
                            )
                        }
                        if (!loadingInfo.isNullOrEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.SurfaceDarkHover),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = loadingInfo!!,
                                    color = com.example.ui.theme.TextSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // STAVIO HIZLI AÇILIŞ EKRANI (Splash Screen - Screenshot Referansı)
        AnimatedVisibility(
            visible = showLaunchSplash,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(240))
        ) {
            StavioLaunchSplashScreen()
        }
    }
}

@Composable
private fun StavioLaunchSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A2430),
                        Color(0xFF0E141B),
                        Color(0xFF05070A),
                        Color.Black
                    ),
                    radius = 950f
                )
            )
            .pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.stavio_official_logo),
                    contentDescription = "Stavio Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Stavio",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = StavioDisplayFontFamily,
                    letterSpacing = (-0.8).sp
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.88f),
                strokeWidth = 2.6.dp,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(viewModel: MainViewModel, onOpenDialog: () -> Unit) {
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    var selectedPlaylistForOptions by remember { androidx.compose.runtime.mutableStateOf<com.example.data.PlaylistEntity?>(null) }
    var showAboutDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    val context = LocalContext.current

    if (showAboutDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Stavio Media", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("STAVIO MEDIA\n\nModern ve gelişmiş bir IPTV uygulamasıdır.\n\nSürüm: 1.2", color = com.example.ui.theme.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Kapat", color = com.example.ui.theme.ColorAccentPrime)
                }
            },
            containerColor = com.example.ui.theme.SurfaceDark
        )
    }

    if (selectedPlaylistForOptions != null) {
        val playlist = selectedPlaylistForOptions!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedPlaylistForOptions = null },
            title = { Text(text = "Seçenekler: ${playlist.name}", color = com.example.ui.theme.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { 
                            viewModel.deletePlaylist(playlist)
                            selectedPlaylistForOptions = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.SurfaceDarkHover, contentColor = com.example.ui.theme.ErrorRed)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Listeyi Sil")
                    }
                    
                    if (playlist.urlOrPath.isNotEmpty()) {
                        Button(
                            onClick = { 
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Playlist Link", playlist.urlOrPath)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Bağlantı kopyalandı", android.widget.Toast.LENGTH_SHORT).show()
                                selectedPlaylistForOptions = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.ColorAccentPrime)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Bağlantıyı Kopyala")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPlaylistForOptions = null }) {
                    Text("Kapat", color = com.example.ui.theme.TextSecondary)
                }
            },
            containerColor = com.example.ui.theme.SurfaceDark
        )
    }

    Scaffold(
        containerColor = com.example.ui.theme.BackgroundDark,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.example.ui.theme.BackgroundDark,
                    titleContentColor = com.example.ui.theme.TextPrimary,
                    actionIconContentColor = com.example.ui.theme.TextPrimary
                ),
                title = {
                    Column {
                        Text(
                            text = "STAVIO IPTV",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.TextPrimary
                        )
                        Text(
                            text = "${playlists.size} Liste",
                            fontSize = 12.sp,
                            color = com.example.ui.theme.TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Hakkında")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = com.example.ui.theme.SurfaceDark.copy(alpha = 0.95f),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = { viewModel.selectTabAndNavigate(com.example.viewmodel.MainTab.LIVE) },
                    icon = { Icon(Icons.Default.Tv, contentDescription = "Canlı TV") },
                    label = { Text("Canlı TV") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.ui.theme.ColorAccentPrime,
                        selectedTextColor = com.example.ui.theme.ColorAccentPrime,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = com.example.ui.theme.TextSecondary,
                        unselectedTextColor = com.example.ui.theme.TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { viewModel.selectTabAndNavigate(com.example.viewmodel.MainTab.MOVIES) },
                    icon = { Icon(Icons.Default.Movie, contentDescription = "Filmler") },
                    label = { Text("Filmler") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.ui.theme.ColorAccentPrime,
                        selectedTextColor = com.example.ui.theme.ColorAccentPrime,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = com.example.ui.theme.TextSecondary,
                        unselectedTextColor = com.example.ui.theme.TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { viewModel.selectTabAndNavigate(com.example.viewmodel.MainTab.SERIES) },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Diziler") },
                    label = { Text("Diziler") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.ui.theme.ColorAccentPrime,
                        selectedTextColor = com.example.ui.theme.ColorAccentPrime,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = com.example.ui.theme.TextSecondary,
                        unselectedTextColor = com.example.ui.theme.TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { viewModel.selectTabAndNavigate(com.example.viewmodel.MainTab.PLAYLISTS) },
                    icon = { Icon(Icons.Default.List, contentDescription = "IPTV") },
                    label = { Text("IPTV") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.ui.theme.ColorAccentPrime,
                        selectedTextColor = com.example.ui.theme.ColorAccentPrime,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = com.example.ui.theme.TextSecondary,
                        unselectedTextColor = com.example.ui.theme.TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { viewModel.selectTabAndNavigate(com.example.viewmodel.MainTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                    label = { Text("Ayarlar") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.ui.theme.ColorAccentPrime,
                        selectedTextColor = com.example.ui.theme.ColorAccentPrime,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = com.example.ui.theme.TextSecondary,
                        unselectedTextColor = com.example.ui.theme.TextSecondary
                    )
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenDialog,
                containerColor = com.example.ui.theme.ColorAccentPrime,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = "Ekle") },
                text = { Text("Liste Ekle") }
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(playlists) { playlist ->
                val subtitleInfo = if (playlist.expirationDate != null) "Son Kul: ${playlist.expirationDate}" else "Sınırsız"
                
                PlaylistCard(
                    title = playlist.name,
                    subtitle = subtitleInfo,
                    icon = if (playlist.type == "MAG") Icons.Filled.Star else Icons.Default.Menu,
                    colorStart = Color(0xFF2C2C35),
                    colorEnd = Color(0xFF16161D),
                    onClick = { viewModel.selectPlaylistAndNavigate(playlist) },
                    onLongClick = { selectedPlaylistForOptions = playlist }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    colorStart: Color,
    colorEnd: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = com.example.ui.theme.SurfaceDark,
        border = BorderStroke(1.dp, com.example.ui.theme.SurfaceCardBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colorStart.copy(alpha = 0.18f),
                            com.example.ui.theme.SurfaceDarkHover.copy(alpha = 0.4f),
                            com.example.ui.theme.BackgroundDark.copy(alpha = 0.8f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                Brush.linearGradient(listOf(colorStart, colorEnd)),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                colorStart.copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "IPTV",
                            color = colorStart,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        color = com.example.ui.theme.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = com.example.ui.theme.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}



@Composable
fun CategoryCard(
    title: String,
    icon: ImageVector,
    imageUrl: String?,
    colorStart: Color,
    colorEnd: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.SurfaceDark),
        border = BorderStroke(1.dp, com.example.ui.theme.SurfaceDarkHover)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(colorStart.copy(alpha = 0.2f), Color.Transparent)))
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.2f
                )
            }
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(colorStart.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ){
                   Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorStart,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    color = com.example.ui.theme.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(viewModel: MainViewModel) {
    val theme = LocalAppTheme.current
    val channels by viewModel.filteredChannels.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val activeSeriesFilter by viewModel.activeSeriesFilter.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedChannelForPlayer by remember { androidx.compose.runtime.mutableStateOf<ChannelEntity?>(null) }
    var selectedChannelForOptions by remember { androidx.compose.runtime.mutableStateOf<ChannelEntity?>(null) }
    val context = LocalContext.current

    val channelListState = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) { androidx.compose.foundation.lazy.LazyListState() }
    val channelGridState = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver) { androidx.compose.foundation.lazy.grid.LazyGridState() }
    val seriesGridState = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver) { androidx.compose.foundation.lazy.grid.LazyGridState() }

    BackHandler(enabled = searchQuery.isNotEmpty()) {
        viewModel.setSearchQuery("")
    }

    if (selectedChannelForPlayer != null) {
        PlayerSelectionDialog(
            channel = selectedChannelForPlayer!!,
            onDismiss = { selectedChannelForPlayer = null },
            onInternalPlayer = { 
                viewModel.selectChannelAndPlay(it)
                selectedChannelForPlayer = null
            },
            onExternalPlayer = { channel, packageName ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(channel.url), "video/*")
                    if (packageName != null) setPackage(packageName)
                }
                try {
                    context.startActivity(android.content.Intent.createChooser(intent, "Oynatıcı Seçiniz"))
                } catch(e: Exception) {}
                selectedChannelForPlayer = null
            }
        )
    }

    if (selectedChannelForOptions != null) {
        ChannelOptionsDialog(
            channel = selectedChannelForOptions!!,
            onDismiss = { selectedChannelForOptions = null },
            onToggleFavorite = {
                viewModel.toggleFavorite(it)
                selectedChannelForOptions = null
            },
            onCopyLink = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("IPTV Link", it.url)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Bağlantı kopyalandı", android.widget.Toast.LENGTH_SHORT).show()
                selectedChannelForOptions = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Stavio iPhone / iOS 18 Tarzı Yüzen Buzlu Cam Üst Bar
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xD912121A),
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.14f)),
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        modifier = Modifier.size(40.dp),
                        onClick = { viewModel.goBack() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Geri Dön",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedCategory.ifEmpty { "STAVIO KANALLAR" },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "STAVIO • ${channels.size} İçerik",
                            color = theme.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Stavio Şeffaf Cam Arama Çubuğu
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(text = "Kanal veya içerik adında ara...", color = theme.textSecondary, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ara", tint = theme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.setSearchQuery("")
                                keyboardController?.hide()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Temizle", tint = Color.White)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.07f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = theme.primary
                    ),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                )
            }
        }

        // Content
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.primary, strokeWidth = 3.dp)
            }
        } else if (channels.isEmpty()) {
            EmptyStateView()
        } else {
            val preSelectedType = viewModel.selectedMediaType.collectAsStateWithLifecycle().value
            val mediaType = if (preSelectedType == "movie" || preSelectedType == "series") {
                preSelectedType
            } else {
                channels.firstOrNull()?.let { viewModel.determineMediaType(it) } ?: "live"
            }
            
            if (mediaType == "series") {
                // Group by series name in natural playlist order (no alphabetical sorting)
                val seriesGroups = channels.groupBy { viewModel.extractCategoryOrSeriesName(it, "series") }
                val sortedGroups = seriesGroups.toList()
                var seriesPageLimit by remember(channels, selectedCategory) { mutableIntStateOf(60) }
                val pagedSeries = remember(sortedGroups, seriesPageLimit) { sortedGroups.take(seriesPageLimit) }

                LazyVerticalGrid(
                    state = seriesGridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pagedSeries, key = { "series_grid_${it.first}_${it.second.firstOrNull()?.id ?: 0}" }) { (seriesName, seriesChannels) ->
                        val posterChannel = seriesChannels.firstOrNull { it.logoUrl.isNotEmpty() } ?: seriesChannels.firstOrNull() ?: return@items
                        Card(
                            colors = CardDefaults.cardColors(containerColor = theme.surface),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clickable {
                                    viewModel.handleMediaItemClick(
                                        channel = posterChannel,
                                        itemName = seriesName,
                                        channelsGroup = seriesChannels,
                                        explicitMediaType = "series"
                                    )
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (posterChannel.logoUrl.isNotEmpty()) {
                                    coil.compose.SubcomposeAsyncImage(
                                        model = posterChannel.logoUrl,
                                        contentDescription = seriesName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(theme.surfaceHover),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            seriesName.take(2).uppercase(),
                                            color = theme.textSecondary,
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = seriesName,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    if (sortedGroups.size > seriesPageLimit) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "load_more_series") {
                            LaunchedEffect(Unit) {
                                seriesPageLimit += 60
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = theme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            } else if (mediaType == "live") {
                var livePageLimit by remember(channels, selectedCategory, searchQuery) { mutableIntStateOf(60) }
                val pagedChannels = remember(channels, livePageLimit) { channels.take(livePageLimit) }

                LazyVerticalGrid(
                    state = channelGridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pagedChannels, key = { "live_grid_${it.id}" }) { channel ->
                        ChannelGridItem(
                            channel = channel,
                            mediaType = "live",
                            onClick = { viewModel.selectChannelAndPlay(channel) },
                            onFavoriteToggle = { selectedChannelForOptions = channel }
                        )
                    }
                    if (channels.size > livePageLimit) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "load_more_live") {
                            LaunchedEffect(Unit) {
                                livePageLimit += 60
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = theme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            } else {
                var vodPageLimit by remember(channels, selectedCategory, searchQuery) { mutableIntStateOf(60) }
                val pagedChannels = remember(channels, vodPageLimit) { channels.take(vodPageLimit) }

                LazyVerticalGrid(
                    state = channelGridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pagedChannels, key = { "vod_grid_${it.id}" }) { channel ->
                        ChannelGridItem(
                            channel = channel,
                            mediaType = viewModel.determineMediaType(channel),
                            onClick = { 
                                viewModel.handleMediaItemClick(
                                    channel = channel,
                                    itemName = channel.name,
                                    channelsGroup = listOf(channel),
                                    explicitMediaType = viewModel.determineMediaType(channel)
                                )
                            },
                            onFavoriteToggle = { selectedChannelForOptions = channel }
                        )
                    }
                    if (channels.size > vodPageLimit) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "load_more_vod") {
                            LaunchedEffect(Unit) {
                                vodPageLimit += 60
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = theme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelGridItem(
    channel: ChannelEntity,
    mediaType: String,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val theme = LocalAppTheme.current
    val ratio = if (mediaType == "live") 16f / 9.5f else 2f / 3f
    Card(
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onFavoriteToggle
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (channel.logoUrl.isNotEmpty()) {
                coil.compose.SubcomposeAsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = if (mediaType == "live") ContentScale.Fit else ContentScale.Crop,
                    modifier = if (mediaType == "live") Modifier.fillMaxSize().padding(12.dp) else Modifier.fillMaxSize(),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = theme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(theme.surfaceHover),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = channel.name.take(2).uppercase(),
                                color = theme.textSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (mediaType == "live") 24.sp else 34.sp
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(theme.surfaceHover),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        color = theme.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (mediaType == "live") 24.sp else 34.sp
                    )
                }
            }

            // Dark gradient overlay for bottom text
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.85f)
                            ),
                            startY = 0f
                        )
                    )
            )

            if (mediaType == "live") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(theme.liveBadge, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "● CANLI",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .fillMaxWidth(0.85f)
            )

            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Favoriye Ekle",
                    tint = if (channel.isFavorite) ColorGold else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PlayerScreen(viewModel: MainViewModel) {
    val currentlyPlaying by viewModel.currentlyPlaying.collectAsStateWithLifecycle()
    val videoScaleMode by viewModel.videoScaleMode.collectAsStateWithLifecycle()
    val isScreenLocked by viewModel.isScreenLocked.collectAsStateWithLifecycle()
    val allChannels by viewModel.allChannels.collectAsStateWithLifecycle()
    val selectedVodChannels by viewModel.selectedVodChannels.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.findActivity()
    val window = activity?.window

    DisposableEffect(Unit) {
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            viewModel.isVideoActuallyPlaying.value = false
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(isScreenLocked) {
        if (isScreenLocked) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        currentlyPlaying?.let { channel ->
            val siblingChannels = remember(channel.id, channel.groupTitle, allChannels, selectedVodChannels) {
                if (selectedVodChannels.size > 1 && selectedVodChannels.any { it.id == channel.id }) {
                    selectedVodChannels
                } else {
                    allChannels.filter { it.groupTitle == channel.groupTitle }.ifEmpty { allChannels.take(80) }.take(120)
                }
            }

            val initialResumePositionMs = remember(channel.url) {
                viewModel.getSavedPositionForChannel(channel)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                IptvVideoPlayer(
                    channelUrl = channel.url,
                    title = channel.name,
                    subtitle = channel.groupTitle,
                    channelLogoUrl = channel.logoUrl,
                    isFavorite = channel.isFavorite,
                    videoScaleMode = videoScaleMode,
                    relatedChannels = siblingChannels,
                    currentChannelId = channel.id,
                    initialPositionMs = initialResumePositionMs,
                    onProgressUpdate = { posMs, durMs ->
                        viewModel.updatePlaybackProgress(channel, posMs, durMs)
                    },
                    onSelectChannel = { nextCh ->
                        viewModel.selectChannelAndPlay(nextCh)
                    },
                    onToggleFavorite = {
                        viewModel.toggleFavorite(channel)
                    },
                    onEnterPip = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            try {
                                val params = android.app.PictureInPictureParams.Builder()
                                    .setAspectRatio(android.util.Rational(16, 9))
                                    .build()
                                activity?.enterPictureInPictureMode(params)
                            } catch (_: Throwable) {}
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onClose = { viewModel.goBack() },
                    onChangeScaleMode = {
                        val newMode = when (videoScaleMode) {
                            "Fit" -> "Fill"
                            "Fill" -> "Stretch"
                            else -> "Fit"
                        }
                        viewModel.changeVideoScaleMode(newMode)
                    },
                    onPlayerError = {
                        viewModel.isVideoActuallyPlaying.value = false
                    },
                    onVideoPlayingStateChanged = { isPlaying ->
                        viewModel.isVideoActuallyPlaying.value = isPlaying
                    }
                )
            }
        }
    }
}



@Composable
fun IPTVHeader(
    title: String,
    subtitle: String,
    onImportClick: (() -> Unit)?,
    onResetClick: (() -> Unit)?,
    onMenuClick: (() -> Unit)? = null,
    isRoot: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(com.example.ui.theme.SurfaceDark.copy(alpha = 0.5f), Color.Transparent)))
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onMenuClick?.invoke() },
                    modifier = Modifier.size(36.dp).padding(end = 12.dp)
                ) {
                    Icon(
                        imageVector = if (isRoot) Icons.Default.Menu else Icons.Filled.ArrowBack,
                        contentDescription = "Menü/Geri",
                        tint = com.example.ui.theme.TextPrimary
                    )
                }
                Text(title, color = com.example.ui.theme.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            }
            Text(subtitle, color = com.example.ui.theme.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 36.dp))
        }

        Row {
            if (onResetClick != null) {
                IconButton(
                    onClick = onResetClick,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = com.example.ui.theme.SurfaceDark, contentColor = com.example.ui.theme.ColorAccentPrime),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Sıfırla", modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            if (onImportClick != null) {
                Button(
                    onClick = onImportClick,
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.ColorAccentPrime, contentColor = com.example.ui.theme.TextPrimary),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ekle", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Liste Ekle", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChannelOptionsDialog(
    channel: ChannelEntity,
    onDismiss: () -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    onCopyLink: (ChannelEntity) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Seçenekler: ${channel.name}", color = com.example.ui.theme.TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { onToggleFavorite(channel) },
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.SurfaceDarkHover)
                ) {
                    Icon(if (channel.isFavorite) Icons.Filled.Star else Icons.Outlined.Star, contentDescription = null, tint = if (channel.isFavorite) com.example.ui.theme.ColorAccentVariant else Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Text(if (channel.isFavorite) "Favorilerden Çıkar" else "Favorilere Ekle", color = com.example.ui.theme.TextPrimary)
                }
                
                Button(
                    onClick = { onCopyLink(channel) },
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.ColorAccentPrime)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = com.example.ui.theme.TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Bağlantıyı Kopyala (m3u8)", color = com.example.ui.theme.TextPrimary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = com.example.ui.theme.TextSecondary)
            }
        },
        containerColor = com.example.ui.theme.SurfaceDark
    )
}

@Composable
fun PlayerSelectionDialog(
    channel: ChannelEntity,
    onDismiss: () -> Unit,
    onInternalPlayer: (ChannelEntity) -> Unit,
    onExternalPlayer: (ChannelEntity, String?) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Oynatıcı Seç: ${channel.name}", color = com.example.ui.theme.TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PlayerButton("Dahili Oyuncu (ExoPlayer)", Icons.Filled.PlayArrow) { onInternalPlayer(channel) }
                PlayerButton("MX Player (Pro/Free)", Icons.Filled.PlayArrow) { onExternalPlayer(channel, "com.mxtech.videoplayer.ad") }
                PlayerButton("VLC", Icons.Filled.PlayArrow) { onExternalPlayer(channel, "org.videolan.vlc") }
                PlayerButton("Wuffy Player", Icons.Filled.PlayArrow) { onExternalPlayer(channel, "co.wuffy.player") }
                PlayerButton("XPlayer", Icons.Filled.PlayArrow) { onExternalPlayer(channel, "video.player.videoplayer") }
                PlayerButton("Diğer (Seçiciyi Aç)", Icons.Filled.Share) { onExternalPlayer(channel, null) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = com.example.ui.theme.TextSecondary)
            }
        },
        containerColor = com.example.ui.theme.SurfaceDark
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChannelListItem(
    channel: ChannelEntity,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val theme = LocalAppTheme.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onFavoriteToggle
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = theme.surfaceHover,
                border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                modifier = Modifier.size(48.dp)
            ) {
                if (channel.logoUrl.isNotEmpty()) {
                    coil.compose.SubcomposeAsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        error = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    channel.name.take(2).uppercase(),
                                    color = theme.primary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            channel.name.take(2).uppercase(),
                            color = theme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (channel.isFavorite) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Favori",
                            tint = ColorGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // LIVE badge
                    Box(
                        modifier = Modifier
                            .background(
                                theme.liveBadge.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "CANLI",
                            color = theme.liveBadge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    if (channel.groupTitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = channel.groupTitle,
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Oynat",
                    tint = theme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PlayerButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = com.example.ui.theme.TextPrimary),
        border = BorderStroke(1.dp, com.example.ui.theme.ColorAccentPrime)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
fun EmptyStateView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = com.example.ui.theme.TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Kanal Bulunamadı", color = com.example.ui.theme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Bu kategoride yayın bulunmuyor. Arama filtresini temizlemeyi deneyin.", color = com.example.ui.theme.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

private fun formatMacAddressInput(raw: String): String {
    val cleanHex = raw.uppercase(java.util.Locale.ROOT).replace(Regex("[^0-9A-F]"), "").take(12)
    if (cleanHex.isEmpty()) return ""
    val sb = StringBuilder()
    for (i in cleanHex.indices) {
        if (i > 0 && i % 2 == 0) {
            sb.append(':')
        }
        sb.append(cleanHex[i])
    }
    return sb.toString()
}

@Composable
fun PlaylistImportDialog(
    onDismiss: () -> Unit,
    onImportUrl: (String, String) -> Unit,
    onImportRaw: (String, String) -> Unit,
    onImportUri: (Uri, Context, String) -> Unit,
    onImportMag: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var tabSelected by remember { mutableIntStateOf(0) } // 0: M3U URL, 1: Xtream, 2: MAC Portal, 3: Dosya
    var playlistName by remember { mutableStateOf("") }
    var urlText by remember { mutableStateOf("") }

    // Xtream Codes fields
    var xtreamServerUrl by remember { mutableStateOf("") }
    var xtreamUsername by remember { mutableStateOf("") }
    var xtreamPassword by remember { mutableStateOf("") }

    // MAC Portal (Stalker) fields
    var portalUrl by remember { mutableStateOf("") }
    var macAddress by remember { mutableStateOf("00:1A:79:") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            onImportUri(uri, context, playlistName.trim())
        }
    }

    val dialogSurface = Color(0xFF101014)
    val fieldSurface = Color(0xFF18181C)
    val borderSubtle = Color(0x26FFFFFF)
    val textZinc400 = Color(0xFFA1A1AA)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color.White.copy(alpha = 0.55f),
        unfocusedBorderColor = borderSubtle,
        focusedContainerColor = fieldSurface,
        unfocusedContainerColor = fieldSurface,
        cursorColor = Color.White
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = dialogSurface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, borderSubtle),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Yeni Liste Ekle",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sekme Seçici (M3U URL | Xtream | MAC Portal | Dosya)
                Surface(
                    color = fieldSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf("M3U URL", "Xtream", "MAC Portal", "Dosya")
                        tabs.forEachIndexed { index, title ->
                            val isSelected = tabSelected == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) Color.White else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        tabSelected = index
                                        validationError = null
                                    }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.Black else textZinc400,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Liste Adı (İsteğe Bağlı)
                Text(
                    text = "Liste Adı (İsteğe Bağlı)",
                    color = textZinc400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = { Text("Örn: Ev IPTV / Stalker Portal", color = textZinc400.copy(alpha = 0.6f), fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                when (tabSelected) {
                    0 -> {
                        Text(
                            text = "M3U / M3U8 Bağlantı Adresi",
                            color = textZinc400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = {
                                urlText = it
                                validationError = null
                            },
                            placeholder = { Text("http://ornek.com:8080/get.php?...", color = textZinc400.copy(alpha = 0.6f), fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            trailingIcon = {
                                if (urlText.isNotEmpty()) {
                                    IconButton(onClick = { urlText = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = textZinc400)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    1 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column {
                                Text(
                                    text = "Sunucu URL (Host:Port)",
                                    color = textZinc400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                OutlinedTextField(
                                    value = xtreamServerUrl,
                                    onValueChange = {
                                        xtreamServerUrl = it
                                        validationError = null
                                    },
                                    placeholder = { Text("http://sunucu.com:8080", color = textZinc400.copy(alpha = 0.6f), fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Column {
                                Text(
                                    text = "Kullanıcı Adı",
                                    color = textZinc400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                OutlinedTextField(
                                    value = xtreamUsername,
                                    onValueChange = {
                                        xtreamUsername = it
                                        validationError = null
                                    },
                                    placeholder = { Text("Kullanıcı adınız", color = textZinc400.copy(alpha = 0.6f), fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Column {
                                Text(
                                    text = "Şifre",
                                    color = textZinc400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                OutlinedTextField(
                                    value = xtreamPassword,
                                    onValueChange = {
                                        xtreamPassword = it
                                        validationError = null
                                    },
                                    placeholder = { Text("Şifreniz", color = textZinc400.copy(alpha = 0.6f), fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    2 -> {
                        // 6. MAC PORTAL (STALKER) GİRİŞ EKRANI DÜZELTMESİ
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Portal URL (Stalker / MAG)",
                                        color = textZinc400,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.White.copy(alpha = 0.10f),
                                            modifier = Modifier.clickable {
                                                if (!portalUrl.startsWith("http")) {
                                                    portalUrl = "http://$portalUrl"
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = "http://",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.White.copy(alpha = 0.10f),
                                            modifier = Modifier.clickable {
                                                if (!portalUrl.endsWith("/c")) {
                                                    portalUrl = portalUrl.trimEnd('/') + "/c"
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = "/c",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = portalUrl,
                                    onValueChange = {
                                        portalUrl = it
                                        validationError = null
                                    },
                                    placeholder = { Text("http://portal.ornek.com:8080/c", color = textZinc400.copy(alpha = 0.6f), fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = fieldColors,
                                    trailingIcon = {
                                        if (portalUrl.isNotEmpty()) {
                                            IconButton(onClick = { portalUrl = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = textZinc400)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "MAC Adresi (00:1A:79:XX:XX:XX)",
                                        color = textZinc400,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.White.copy(alpha = 0.10f),
                                        modifier = Modifier.clickable {
                                            macAddress = "00:1A:79:"
                                            validationError = null
                                        }
                                    ) {
                                        Text(
                                            text = "00:1A:79: Sıfırla",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = macAddress,
                                    onValueChange = { input ->
                                        validationError = null
                                        // Kullanıcı iki nokta siliyorsa izin ver, harf/rakam yazıyorsa otomatik XX:XX:XX:XX:XX:XX formatla
                                        macAddress = if (input.length < macAddress.length) {
                                            input.uppercase(java.util.Locale.ROOT)
                                        } else {
                                            formatMacAddressInput(input)
                                        }
                                    },
                                    placeholder = { Text("00:1A:79:A1:B2:C3", color = textZinc400.copy(alpha = 0.6f), fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = fieldColors,
                                    trailingIcon = {
                                        if (macAddress.isNotEmpty()) {
                                            IconButton(onClick = { macAddress = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = textZinc400)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    3 -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Cihazdan .m3u / .m3u8 Dosyası Seç", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (!validationError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = validationError!!,
                        color = Color(0xFFFF4D4D),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Vazgeç", color = textZinc400, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (tabSelected != 3) {
                        Button(
                            onClick = {
                                when (tabSelected) {
                                    0 -> {
                                        val cleanUrl = urlText.trim()
                                        if (cleanUrl.isEmpty()) {
                                            validationError = "Lütfen geçerli bir M3U URL girin."
                                        } else {
                                            val finalUrl = if (!cleanUrl.startsWith("http://", true) && !cleanUrl.startsWith("https://", true)) {
                                                "http://$cleanUrl"
                                            } else cleanUrl
                                            onImportUrl(finalUrl, playlistName.trim())
                                        }
                                    }
                                    1 -> {
                                        var srv = xtreamServerUrl.trim().trimEnd('/')
                                        val usr = xtreamUsername.trim()
                                        val pwd = xtreamPassword.trim()
                                        if (srv.isEmpty() || usr.isEmpty() || pwd.isEmpty()) {
                                            validationError = "Lütfen sunucu adresi, kullanıcı adı ve şifre alanlarını doldurun."
                                        } else {
                                            if (!srv.startsWith("http://", true) && !srv.startsWith("https://", true)) {
                                                srv = "http://$srv"
                                            }
                                            val xtreamM3uUrl = "$srv/get.php?username=$usr&password=$pwd&type=m3u_plus&output=ts"
                                            val effectiveName = playlistName.trim().ifEmpty { usr }
                                            onImportUrl(xtreamM3uUrl, effectiveName)
                                        }
                                    }
                                    2 -> {
                                        val cleanPortal = portalUrl.trim()
                                        val hexOnly = macAddress.uppercase(java.util.Locale.ROOT).replace(Regex("[^0-9A-F]"), "")
                                        if (cleanPortal.isEmpty()) {
                                            validationError = "Lütfen Portal URL adresini girin."
                                        } else if (hexOnly.length != 12) {
                                            validationError = "MAC adresi 12 karakter (Örn: 00:1A:79:A1:B2:C3) olmalıdır."
                                        } else {
                                            val formattedMac = hexOnly.chunked(2).joinToString(":")
                                            onImportMag(cleanPortal, formattedMac, playlistName.trim())
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("Sisteme Yükle", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

fun Context.findActivity(): Activity? = when(this) { 
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null 
}

