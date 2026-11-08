package com.example.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ChannelEntity
import com.example.viewmodel.MainTab
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ScreenState
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.ColorAccentPrime
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.SurfaceDarkHover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val allChannels by viewModel.allChannels.collectAsStateWithLifecycle()
    
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                ),
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Kanal, film veya dizi ara...", color = TextSecondary) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = ColorAccentPrime,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showPlaylistDialog = true }
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, tint = ColorAccentPrime)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Sağlayıcı Seç", fontSize = 12.sp, color = TextSecondary)
                                Text(
                                    selectedPlaylist?.name ?: "Varsayılan Liste",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextPrimary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else {
                            isSearchActive = true
                        }
                    }) {
                        Icon(if (isSearchActive) Icons.Default.Close else Icons.Default.Search, contentDescription = "Ara")
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentTab == MainTab.PLAYLISTS) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { showImportDialog = true },
                    containerColor = ColorAccentPrime,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = "Ekle") },
                    text = { Text("Liste Ekle") }
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark.copy(alpha = 0.95f),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == MainTab.LIVE,
                    onClick = { viewModel.selectTabAndNavigate(MainTab.LIVE) },
                    icon = { Icon(Icons.Default.Tv, contentDescription = "Canlı TV") },
                    label = { Text("Canlı TV") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ColorAccentPrime,
                        selectedTextColor = ColorAccentPrime,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.MOVIES,
                    onClick = { viewModel.selectTabAndNavigate(MainTab.MOVIES) },
                    icon = { Icon(Icons.Default.Movie, contentDescription = "Filmler") },
                    label = { Text("Filmler") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ColorAccentPrime,
                        selectedTextColor = ColorAccentPrime,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.SERIES,
                    onClick = { viewModel.selectTabAndNavigate(MainTab.SERIES) },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Diziler") },
                    label = { Text("Diziler") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ColorAccentPrime,
                        selectedTextColor = ColorAccentPrime,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.PLAYLISTS,
                    onClick = { viewModel.selectTabAndNavigate(MainTab.PLAYLISTS) },
                    icon = { Icon(Icons.Default.List, contentDescription = "IPTV") },
                    label = { Text("IPTV") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ColorAccentPrime,
                        selectedTextColor = ColorAccentPrime,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.SETTINGS,
                    onClick = { viewModel.selectTabAndNavigate(MainTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                    label = { Text("Ayarlar") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ColorAccentPrime,
                        selectedTextColor = ColorAccentPrime,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isSearchActive && searchQuery.isNotEmpty()) {
                val searchResults = remember(allChannels, searchQuery) {
                    allChannels.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }
                SearchResultsLayout(searchResults, viewModel)
            } else {
                when (currentTab) {
                    MainTab.LIVE, MainTab.MOVIES, MainTab.SERIES -> FilteredTabContent(viewModel, currentTab)
                    MainTab.PLAYLISTS -> IPTVPlaylistsTabContent(viewModel)
                    MainTab.SETTINGS -> SettingsScreen(viewModel)
                    else -> Box {}
                }
            }
        }
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Sağlayıcı Değiştir", color = TextPrimary) },
            text = {
                val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
                LazyColumn {
                    items(playlists) { p ->
                        Text(
                            text = p.name,
                            color = TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectPlaylistAndNavigate(p)
                                    showPlaylistDialog = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                showPlaylistDialog = false
                                viewModel.selectTabAndNavigate(MainTab.PLAYLISTS)
                                showImportDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorAccentPrime)
                        ) {
                            Text("Yeni Ekle")
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = SurfaceDark
        )
    }

    if (showImportDialog) {
        PlaylistImportDialog(
            onDismiss = { showImportDialog = false },
            onImportUrl = { url, name ->
                viewModel.addPlaylistFromUrl(url, name)
                showImportDialog = false
            },
            onImportRaw = { text, name -> },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultsLayout(channels: List<ChannelEntity>, viewModel: MainViewModel) {
    if (channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sonuç bulunamadı", color = TextSecondary)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(channels, key = { it.id }) { channel ->
                val mediaType = viewModel.determineMediaType(channel)
                val ratio = if (mediaType == "live") 16f / 9f else 2f / 3f
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio)
                        .combinedClickable(
                            onClick = { 
                                val cType = viewModel.determineMediaType(channel)
                                if (cType == "live") {
                                    viewModel.selectChannelAndPlay(channel)
                                } else {
                                    viewModel.selectedVodName.value = channel.name
                                    viewModel.selectedVodChannels.value = listOf(channel)
                                    viewModel.fetchVodInfo(channel.name, cType)
                                    viewModel.navigateTo(com.example.viewmodel.ScreenState.VOD_DETAIL)
                                }
                            }
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (channel.logoUrl.isNotEmpty()) {
                            AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = channel.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(SurfaceDarkHover),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    channel.name.take(2).uppercase(),
                                    color = TextPrimary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                        )
                        Text(
                            text = channel.name,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FilteredTabContent(viewModel: MainViewModel, tab: MainTab) {
    val allChannels by viewModel.allChannels.collectAsStateWithLifecycle()
    val mediaType = when(tab) {
        MainTab.MOVIES -> "movie"
        MainTab.SERIES -> "series"
        else -> "live"
    }
    
    // Pair of Category Title to List of (Item Title to List of Channel Entities representing episodes/item)
    var groupedChannels by androidx.compose.runtime.remember { mutableStateOf<List<Pair<String, List<Pair<String, List<com.example.data.ChannelEntity>>>>>>(emptyList()) }
    var isLoading by androidx.compose.runtime.remember { mutableStateOf(true) }

    androidx.compose.runtime.LaunchedEffect(allChannels, mediaType) {
        isLoading = true
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val filtered = allChannels.filter { viewModel.determineMediaType(it) == mediaType }
            val grouped = filtered.groupBy { it.groupTitle.ifEmpty { "Diğer" } }
            
            val processedGroups = grouped.mapValues { (_, channels) ->
                if (mediaType == "series") {
                    val seriesMap = channels.groupBy { viewModel.extractCategoryOrSeriesName(it, "series") }
                    seriesMap.map { (seriesName, eps) ->
                        Pair(seriesName, eps)
                    }
                } else {
                    channels.map { Pair(it.name, listOf(it)) }
                }
            }
            groupedChannels = processedGroups.toList().sortedBy { it.first }
        }
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (groupedChannels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Bu kategoride içerik bulunamadı.", color = TextSecondary)
        }
        return
    }

    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(groupedChannels, key = { it.first }) { (categoryTitle, itemsList) ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            viewModel.selectedMediaType.value = mediaType
                            viewModel.selectedCategory.value = categoryTitle
                            viewModel.navigateTo(ScreenState.CHANNELS)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = categoryTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, 
                        contentDescription = "Tümünü Gör", 
                        tint = TextSecondary, 
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(itemsList.take(25), key = { it.second.first().id }) { (itemName, channelsData) ->
                        val channel = channelsData.first()
                        val isLive = mediaType == "live"
                        
                        Column(
                            modifier = Modifier
                                .width(if (isLive) 160.dp else 120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { 
                                        if (isLive) {
                                            viewModel.selectChannelAndPlay(channel)
                                        } else {
                                            viewModel.selectedVodName.value = itemName
                                            viewModel.selectedVodChannels.value = channelsData
                                            viewModel.fetchVodInfo(itemName, mediaType)
                                            viewModel.navigateTo(com.example.viewmodel.ScreenState.VOD_DETAIL)
                                        }
                                    }
                                )
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(if (isLive) 16f / 9f else 2f / 3.2f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (channel.logoUrl.isNotEmpty()) {
                                        coil.compose.AsyncImage(
                                            model = channel.logoUrl,
                                            contentDescription = channel.name,
                                            contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop,
                                            modifier = if (isLive) Modifier.fillMaxSize().padding(16.dp) else Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(SurfaceDarkHover),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                channel.name.take(2).uppercase(),
                                                color = TextSecondary,
                                                fontSize = if (isLive) 24.sp else 36.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    if (isLive) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(SurfaceDark.copy(alpha = 0.95f))
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = channel.name,
                                                color = Color.White,
                                                maxLines = 1,
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
                            
                            if (!isLive) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = channel.name.trim(),
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
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
fun IPTVPlaylistsTabContent(viewModel: MainViewModel) {
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    var selectedPlaylistForOptions by remember { mutableStateOf<com.example.data.PlaylistEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (selectedPlaylistForOptions != null) {
        val playlist = selectedPlaylistForOptions!!
        AlertDialog(
            onDismissRequest = { selectedPlaylistForOptions = null },
            title = { Text(text = "Seçenekler: ${playlist.name}", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { 
                            viewModel.deletePlaylist(playlist)
                            selectedPlaylistForOptions = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkHover, contentColor = com.example.ui.theme.ErrorRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Listeyi Sil")
                    }
                    
                    if (playlist.urlOrPath.isNotEmpty()) {
                        Button(
                            onClick = { 
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Playlist Link", playlist.urlOrPath)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Bağlantı kopyalandı", android.widget.Toast.LENGTH_SHORT).show()
                                selectedPlaylistForOptions = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorAccentPrime),
                            modifier = Modifier.fillMaxWidth()
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
                    Text("Kapat", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Kayıtlı IPTV listesi bulunamadı.\nAyarlar -> Oynatma Listelerini Yönet menüsünden yeni bir liste ekleyebilirsiniz.",
                color = TextSecondary,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                modifier = Modifier.padding(24.dp)
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
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

