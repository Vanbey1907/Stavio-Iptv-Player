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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.ChannelEntity
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ScreenState

// UI Components will now utilize Material Theme colors and modern shapes


@Composable
fun IPTVHomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.loadingProgress.collectAsStateWithLifecycle()
    val loadingMessage by viewModel.loadingMessage.collectAsStateWithLifecycle()
    val loadingInfo by viewModel.loadingInfo.collectAsStateWithLifecycle()
    var showPlaylistDialog by remember { mutableStateOf(false) }

    // System Back Handler
    BackHandler(enabled = !isLoading) {
        viewModel.goBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.example.ui.theme.BackgroundDark)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(animationSpec = tween(400)) + 
                 androidx.compose.animation.scaleIn(initialScale = 0.95f, animationSpec = tween(400))) togetherWith 
                 androidx.compose.animation.fadeOut(animationSpec = tween(400))
            },
            label = "Screen Transition"
        ) { state ->
            when (state) {
                ScreenState.PLAYLISTS -> DashboardScreen(viewModel)
                ScreenState.DASHBOARD -> DashboardScreen(viewModel)
                ScreenState.CHANNELS -> ChannelsScreen(viewModel)
                ScreenState.PLAYER -> PlayerScreen(viewModel)
                ScreenState.VOD_DETAIL -> VodDetailScreen(viewModel)
            }
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
            title = { Text("Vanbey IPTV Player", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("VANBEY IPTV Player\n\nModern ve gelişmiş bir IPTV uygulamasıdır.\n\nSürüm: 1.0", color = com.example.ui.theme.TextSecondary) },
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
                            text = "VANBEY IPTV",
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
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.SurfaceDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, com.example.ui.theme.SurfaceDarkHover)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(
                    colors = listOf(
                        colorStart.copy(alpha = 0.15f),
                        com.example.ui.theme.BackgroundDark.copy(alpha = 0.4f)
                    )
                ))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(colorStart.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ){
                   Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorStart,
                        modifier = Modifier.size(24.dp)
                   )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    color = com.example.ui.theme.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
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
    val channels by viewModel.filteredChannels.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val activeSeriesFilter by viewModel.activeSeriesFilter.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedChannelForPlayer by remember { androidx.compose.runtime.mutableStateOf<ChannelEntity?>(null) }
    var selectedChannelForOptions by remember { androidx.compose.runtime.mutableStateOf<ChannelEntity?>(null) }
    val context = LocalContext.current

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
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.goBack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Geri Dön", tint = com.example.ui.theme.TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = selectedCategory.ifEmpty { "Kanallar" },
                color = com.example.ui.theme.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text(text = "Kanal adında ara...", color = com.example.ui.theme.TextSecondary, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ara", tint = com.example.ui.theme.TextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        viewModel.setSearchQuery("")
                        keyboardController?.hide()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Temizle", tint = com.example.ui.theme.TextPrimary)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = com.example.ui.theme.SurfaceDark,
                unfocusedContainerColor = com.example.ui.theme.SurfaceDark,
                focusedBorderColor = com.example.ui.theme.ColorAccentPrime.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = com.example.ui.theme.TextPrimary,
                unfocusedTextColor = com.example.ui.theme.TextPrimary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Content
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = com.example.ui.theme.ColorAccentPrime)
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
                // Group by series name
                val seriesGroups = channels.groupBy { viewModel.extractCategoryOrSeriesName(it, "series") }
                val sortedGroups = seriesGroups.toList().sortedBy { it.first }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedGroups, key = { it.first }) { (seriesName, seriesChannels) ->
                        val posterChannel = seriesChannels.firstOrNull { it.logoUrl.isNotEmpty() } ?: seriesChannels.first()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.SurfaceDark),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, com.example.ui.theme.SurfaceDarkHover.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clickable {
                                    viewModel.selectedVodName.value = seriesName
                                    viewModel.selectedVodChannels.value = seriesChannels
                                    viewModel.fetchVodInfo(seriesName, "series")
                                    viewModel.navigateTo(ScreenState.VOD_DETAIL)
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
                                        modifier = Modifier.fillMaxSize().background(com.example.ui.theme.SurfaceDarkHover),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            seriesName.take(2).uppercase(),
                                            color = com.example.ui.theme.TextSecondary,
                                            fontSize = 36.sp,
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
                }
            } else if (mediaType == "live") {
                val displayChannels = channels
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayChannels, key = { it.id }) { channel ->
                        ChannelListItem(
                            channel = channel,
                            onClick = { selectedChannelForPlayer = channel },
                            onFavoriteToggle = { selectedChannelForOptions = channel }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(channels, key = { it.id }) { channel ->
                        ChannelGridItem(
                            channel = channel,
                            mediaType = viewModel.determineMediaType(channel),
                            onClick = { 
                                viewModel.selectedVodName.value = channel.name
                                viewModel.selectedVodChannels.value = listOf(channel)
                                viewModel.fetchVodInfo(channel.name, viewModel.determineMediaType(channel))
                                viewModel.navigateTo(ScreenState.VOD_DETAIL)
                            },
                            onFavoriteToggle = { selectedChannelForOptions = channel }
                        )
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
    val ratio = if (mediaType == "live") 16f / 9f else 2f / 3f
    Card(
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, com.example.ui.theme.SurfaceDarkHover.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = com.example.ui.theme.ColorAccentPrime,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(com.example.ui.theme.SurfaceDarkHover, com.example.ui.theme.BackgroundDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = channel.name.take(2).uppercase(),
                                color = com.example.ui.theme.TextPrimary.copy(alpha = 0.2f),
                                fontWeight = FontWeight.Black,
                                fontSize = 42.sp
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(com.example.ui.theme.SurfaceDarkHover, com.example.ui.theme.BackgroundDark))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        color = com.example.ui.theme.TextPrimary.copy(alpha = 0.2f),
                        fontWeight = FontWeight.Black,
                        fontSize = 42.sp
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
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = 0f
                        )
                    )
            )

            // Play Icon subtle overlay
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Oynat",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
            )

            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .fillMaxWidth(0.8f)
            )

            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Favoriye Ekle",
                    tint = if (channel.isFavorite) com.example.ui.theme.ColorAccentVariant else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
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

    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.findActivity()
    val window = activity?.window

    DisposableEffect(Unit) {
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        // Start portrait automatically, but allow user rotation or programmatic
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
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
            Box(modifier = Modifier.fillMaxSize()) {
                IptvVideoPlayer(
                    channelUrl = channel.url,
                    title = "", 
                    subtitle = channel.name,
                    videoScaleMode = videoScaleMode,
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
                    onPlayerError = {}
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
    Card(
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, com.example.ui.theme.SurfaceDarkHover.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onFavoriteToggle
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (channel.logoUrl.isNotEmpty()) {
                coil.compose.SubcomposeAsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
                    error = {
                        Box(
                            modifier = Modifier.size(50.dp).background(com.example.ui.theme.SurfaceDarkHover, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(channel.name.take(2).uppercase(), color = com.example.ui.theme.TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier.size(50.dp).background(com.example.ui.theme.SurfaceDarkHover, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(channel.name.take(2).uppercase(), color = com.example.ui.theme.TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = com.example.ui.theme.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.groupTitle.isNotEmpty()) {
                    Text(
                        text = channel.groupTitle,
                        color = com.example.ui.theme.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            if (channel.isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Favori",
                    tint = Color(0xFFFBC02D),
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

@Composable
fun PlaylistImportDialog(
    onDismiss: () -> Unit,
    onImportUrl: (String, String) -> Unit,
    onImportRaw: (String, String) -> Unit,
    onImportUri: (Uri, Context, String) -> Unit,
    onImportMag: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var tabSelected by remember { mutableStateOf(0) }
    var playlistName by remember { mutableStateOf("") }
    var urlText by remember { mutableStateOf("") }
    var portalUrl by remember { mutableStateOf("") }
    var macAddress by remember { mutableStateOf("00:1A:79:00:00:00") }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            onImportUri(uri, context, playlistName)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.SurfaceDark),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    text = "Yeni Liste Ekle", 
                    color = com.example.ui.theme.TextPrimary, 
                    fontSize = 22.sp, 
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Liste Adı (İsteğe Bağlı)", color = com.example.ui.theme.TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Menu, contentDescription = null, tint = com.example.ui.theme.TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = com.example.ui.theme.TextPrimary, unfocusedTextColor = com.example.ui.theme.TextPrimary,
                        focusedBorderColor = com.example.ui.theme.ColorAccentPrime, unfocusedBorderColor = com.example.ui.theme.SurfaceDarkHover,
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                TabRow(
                    selectedTabIndex = tabSelected,
                    containerColor = Color.Transparent,
                    contentColor = com.example.ui.theme.ColorAccentPrime,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            color = com.example.ui.theme.ColorAccentPrime,
                            height = 3.dp,
                            modifier = Modifier.tabIndicatorOffset(tabPositions[tabSelected])
                        )
                    },
                    divider = { }
                ) {
                    Tab(selected = tabSelected == 0, onClick = { tabSelected = 0 }, text = { Text("M3U URL", color = if (tabSelected == 0) com.example.ui.theme.TextPrimary else com.example.ui.theme.TextSecondary, fontWeight = if (tabSelected == 0) FontWeight.Bold else FontWeight.Normal) })
                    Tab(selected = tabSelected == 1, onClick = { tabSelected = 1 }, text = { Text("Dosya", color = if (tabSelected == 1) com.example.ui.theme.TextPrimary else com.example.ui.theme.TextSecondary, fontWeight = if (tabSelected == 1) FontWeight.Bold else FontWeight.Normal) })
                    Tab(selected = tabSelected == 2, onClick = { tabSelected = 2 }, text = { Text("MAG", color = if (tabSelected == 2) com.example.ui.theme.TextPrimary else com.example.ui.theme.TextSecondary, fontWeight = if (tabSelected == 2) FontWeight.Bold else FontWeight.Normal) })
                }

                Spacer(modifier = Modifier.height(20.dp))

                when (tabSelected) {
                    0 -> {
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            placeholder = { Text("http://example.com/playlist.m3u", color = com.example.ui.theme.TextSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = com.example.ui.theme.TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = com.example.ui.theme.TextPrimary, unfocusedTextColor = com.example.ui.theme.TextPrimary,
                                focusedBorderColor = com.example.ui.theme.ColorAccentPrime, unfocusedBorderColor = com.example.ui.theme.SurfaceDarkHover,
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    1 -> {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.ColorAccentPrime, contentColor = com.example.ui.theme.TextPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Cihazdan .m3u Dosyası Seç", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    2 -> {
                        Column {
                            OutlinedTextField(
                                value = portalUrl,
                                onValueChange = { portalUrl = it },
                                placeholder = { Text("Portal URL (http://...)", color = com.example.ui.theme.TextSecondary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = com.example.ui.theme.TextSecondary) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = com.example.ui.theme.TextPrimary, unfocusedTextColor = com.example.ui.theme.TextPrimary,
                                    focusedBorderColor = com.example.ui.theme.ColorAccentPrime, unfocusedBorderColor = com.example.ui.theme.SurfaceDarkHover,
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = macAddress,
                                onValueChange = { macAddress = it },
                                placeholder = { Text("MAC Address", color = com.example.ui.theme.TextSecondary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = com.example.ui.theme.TextSecondary) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = com.example.ui.theme.TextPrimary, unfocusedTextColor = com.example.ui.theme.TextPrimary,
                                    focusedBorderColor = com.example.ui.theme.ColorAccentPrime, unfocusedBorderColor = com.example.ui.theme.SurfaceDarkHover,
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { 
                        Text("Vazgeç", color = com.example.ui.theme.TextSecondary, fontWeight = FontWeight.Bold) 
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    if (tabSelected != 1) { // Dosya sekmesinde direkt açılıyor
                        Button(
                            onClick = {
                                if (tabSelected == 0 && urlText.isNotEmpty()) onImportUrl(urlText, playlistName)
                                else if (tabSelected == 2 && portalUrl.isNotEmpty()) onImportMag(portalUrl, macAddress, playlistName)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.ColorAccentPrime, contentColor = com.example.ui.theme.TextPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sisteme Yükle", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VodDetailScreen(viewModel: MainViewModel) {
    val vodName by viewModel.selectedVodName.collectAsStateWithLifecycle()
    val channels by viewModel.selectedVodChannels.collectAsStateWithLifecycle()
    
    val vodDescription by viewModel.vodDescription.collectAsStateWithLifecycle()
    val vodRating by viewModel.vodRating.collectAsStateWithLifecycle()
    val topCast by viewModel.topCast.collectAsStateWithLifecycle()
    val tmdbPosterUrl by viewModel.vodPoster.collectAsStateWithLifecycle()
    
    if (channels.isEmpty()) {
        viewModel.goBack()
        return
    }
    
    val firstChannel = channels.first()
    val posterUrl = if (tmdbPosterUrl.isNotEmpty()) tmdbPosterUrl else (channels.firstOrNull { it.logoUrl.isNotEmpty() }?.logoUrl ?: "")
    val isSeries = channels.size > 1 || viewModel.determineMediaType(firstChannel) == "series"
    
    var selectedSeason by remember { androidx.compose.runtime.mutableStateOf(1) }
    var expandedSeasonDropdown by remember { androidx.compose.runtime.mutableStateOf(false) }
    
    val seasonGroups = remember(channels) {
        if (!isSeries) emptyMap()
        else {
            channels.groupBy { 
                val match = Regex("(?i)(?:S|Sezon)\\s*(\\d+)").find(it.name)
                match?.groupValues?.get(1)?.toIntOrNull() ?: 1
            }.toSortedMap()
        }
    }
    
    val seasons = seasonGroups.keys.toList()
    val currentEpisodes = seasonGroups[selectedSeason] ?: emptyList()
    
    Box(modifier = Modifier.fillMaxSize().background(com.example.ui.theme.BackgroundDark)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Hero Image Header
            item {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    if (posterUrl.isNotEmpty()) {
                        coil.compose.SubcomposeAsyncImage(
                            model = posterUrl,
                            contentDescription = vodName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(com.example.ui.theme.SurfaceDarkHover),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = vodName.take(2).uppercase(),
                                color = com.example.ui.theme.TextSecondary,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(
                                colors = listOf(Color.Transparent, com.example.ui.theme.BackgroundDark),
                                startY = 300f
                            ))
                    )
                    
                    // Back button
                    IconButton(
                        onClick = { viewModel.goBack() },
                        modifier = Modifier
                            .padding(top = 32.dp, start = 16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri Dön", tint = Color.White)
                    }
                }
            }
            
            // Details
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = vodName,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    
                    if (vodRating > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = "Puan", tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(text = "%.1f".format(vodRating), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    if (vodDescription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = vodDescription,
                            color = com.example.ui.theme.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }

                    if (topCast.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Oyuncular", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(topCast) { actor ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                                    if (actor.profile_path != null) {
                                        val picUrl = if (actor.profile_path.startsWith("http")) actor.profile_path else "https://image.tmdb.org/t/p/w185${actor.profile_path}"
                                        coil.compose.SubcomposeAsyncImage(
                                            model = picUrl,
                                            contentDescription = actor.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(64.dp).background(com.example.ui.theme.SurfaceDark, CircleShape).clip(CircleShape)
                                        )
                                    } else {
                                        Box(modifier = Modifier.size(64.dp).background(com.example.ui.theme.SurfaceDark, CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = com.example.ui.theme.TextSecondary)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(text = actor.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                    Text(text = actor.character, color = com.example.ui.theme.TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    
                    if (!isSeries) {
                        Button(
                            onClick = { viewModel.selectChannelAndPlay(firstChannel) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.ColorAccentPrime),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Oynat", tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Hemen İzle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
            
            if (isSeries && seasons.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { expandedSeasonDropdown = true },
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, com.example.ui.theme.SurfaceDarkHover)
                            ) {
                                Text("Sezon $selectedSeason")
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            
                            androidx.compose.material3.DropdownMenu(
                                expanded = expandedSeasonDropdown,
                                onDismissRequest = { expandedSeasonDropdown = false },
                                modifier = Modifier.background(com.example.ui.theme.SurfaceDark)
                            ) {
                                seasons.forEach { season ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Sezon $season", color = Color.White) },
                                        onClick = {
                                            selectedSeason = season
                                            expandedSeasonDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${currentEpisodes.size} Bölüm",
                            color = com.example.ui.theme.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
                
                items(currentEpisodes, key = { it.id }) { episode ->
                    val epMatch = Regex("(?i)(?:E|Bölüm|Bolum|Part)\\s*(\\d+)").find(episode.name)
                    val epNumber = epMatch?.groupValues?.get(1) ?: ""
                    val epTitle = if (epNumber.isNotEmpty()) "$epNumber. Bölüm" else episode.name
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.SurfaceDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { viewModel.selectChannelAndPlay(episode) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp).fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier.size(100.dp, 60.dp).background(com.example.ui.theme.SurfaceDarkHover, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Oynat", tint = Color.White)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = epTitle,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = episode.name,
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
        }
    }
}

fun Context.findActivity(): Activity? = when(this) { 
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null 
}
