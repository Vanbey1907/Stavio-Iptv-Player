package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ChannelEntity
import com.example.data.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class CategoryUiModel(
    val name: String,
    val imageUrl: String?
)

enum class ScreenState {
    PLAYLISTS, DASHBOARD, CHANNELS, PLAYER, VOD_DETAIL
}

enum class MainTab {
    LIVE, MOVIES, SERIES, PLAYLISTS, SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val channelDao = database.channelDao()
    private val playlistDao = database.playlistDao()

    private val prefs = application.getSharedPreferences("stavio_prefs", android.content.Context.MODE_PRIVATE)

    val currentScreen = MutableStateFlow(ScreenState.DASHBOARD)
    val activeSeriesFilter = MutableStateFlow<String?>(null)
    val selectedVodName = MutableStateFlow<String>("")
    val selectedVodChannels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    
    val topCast = MutableStateFlow<List<com.example.network.TmdbCast>>(emptyList())
    val vodDescription = MutableStateFlow<String>("")
    val vodRating = MutableStateFlow<Double>(0.0)
    val vodPoster = MutableStateFlow<String>("")
    
    fun fetchVodInfo(name: String, mediaType: String) {
        topCast.value = emptyList()
        vodDescription.value = ""
        vodRating.value = 0.0
        vodPoster.value = ""
        
        val cleanName = name.replace(Regex("(?i)(s\\d+e\\d+|\\bsezon\\b|\\bbölüm\\b).*"), "").trim()
        
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // TVMaze implementation for free information
                val api = com.example.network.TvMazeApi.create()
                val response = api.searchShows(cleanName)
                val bestMatch = response.maxByOrNull { it.score }?.show
                
                if (bestMatch != null) {
                    val summaryPlain = bestMatch.summary?.replace(Regex("<.*?>"), "") ?: ""
                    vodDescription.value = summaryPlain
                    vodRating.value = bestMatch.rating?.average ?: 0.0
                    vodPoster.value = bestMatch.image?.original ?: bestMatch.image?.medium ?: ""
                    
                    try {
                        val cast = api.getShowCast(bestMatch.id)
                        topCast.value = cast.take(5).map {
                            com.example.network.TmdbCast(
                                id = it.person.id,
                                name = it.person.name,
                                character = it.character.name,
                                profile_path = it.person.image?.medium
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    private val backStack = java.util.Stack<ScreenState>()

    fun navigateTo(screen: ScreenState) {
        if (currentScreen.value != screen) {
            backStack.push(currentScreen.value)
            currentScreen.value = screen
        }
    }

    val currentTab = MutableStateFlow(MainTab.PLAYLISTS)

    val isLoading = MutableStateFlow(false)
    val loadingProgress = MutableStateFlow(0f)
    val loadingMessage = MutableStateFlow("")
    val loadingInfo = MutableStateFlow<String?>(null) // To show IPTV info
    val errorMessage = MutableStateFlow<String?>(null)

    val allPlaylists = playlistDao.getAllPlaylists().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val selectedPlaylist = MutableStateFlow<com.example.data.PlaylistEntity?>(null)

    val selectedMediaType = MutableStateFlow("playlists")
    val selectedCategory = MutableStateFlow("Hepsi")
    val searchQuery = MutableStateFlow("")

    val currentlyPlaying = MutableStateFlow<ChannelEntity?>(null)

    // Fit, Fill, Stretch
    val videoScaleMode = MutableStateFlow("Fit")
    val isScreenLocked = MutableStateFlow(false)

    fun determineMediaType(channel: ChannelEntity): String {
        val u = channel.url.lowercase()
        val c = channel.groupTitle.lowercase()
        val n = channel.name.lowercase()
        val isVideoUrl = u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi") || u.endsWith(".mov") || u.endsWith(".flv") || u.endsWith(".wmv") || u.contains("movie") || u.contains("series")
        
        val hasEpisodePattern = Regex("(?i)(s\\d+e\\d+|\\bsezon\\b|\\bbölüm\\b|\\bepisode\\b|\\bep\\b|\\bpart\\b)").containsMatchIn(n.replace(" ", ""))
        val isSeriesCategory = c.contains("dizi") || c.contains("series") || c.contains("tv show") || c.contains("belgesel")
        val isMovieCategory = c.contains("film") || c.contains("movie") || c.contains("vod") || c.contains("sinema") || c.contains("boxset")

        return if (u.contains("/series/") || isSeriesCategory || hasEpisodePattern) {
            "series"
        } else if (u.contains("/movie/") || isMovieCategory || isVideoUrl) {
            "movie"
        } else if (c.contains("radyo") || c.contains("radio")) {
            "radio"
        } else {
            "live"
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allChannels: StateFlow<List<ChannelEntity>> = selectedPlaylist.flatMapLatest { playlist ->
        if (playlist == null) flowOf(emptyList()) 
        else channelDao.getChannelsByPlaylist(playlist.id.toLong())
            .onStart { isLoading.value = true }
            .onEach { isLoading.value = false }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<CategoryUiModel>> = combine(allChannels, selectedMediaType) { list, mediaType ->
        val map = linkedMapOf<String, String?>()
        list.filter { mediaType == "ALL" || determineMediaType(it) == mediaType }.forEach {
            if (it.groupTitle.isNotEmpty() && !map.containsKey(it.groupTitle)) {
                map[it.groupTitle] = it.logoUrl.ifEmpty { null }
            } else if (it.groupTitle.isNotEmpty() && map[it.groupTitle] == null && it.logoUrl.isNotEmpty()) {
                map[it.groupTitle] = it.logoUrl
            }
        }
        map.map { CategoryUiModel(it.key, it.value) }.sortedBy { it.name }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun extractCategoryOrSeriesName(channel: ChannelEntity, mediaType: String): String {
        if (mediaType == "series") {
            val n = channel.name
            val match = Regex("(?i)(.*?)\\s*(?:\\-?\\s*S\\d+\\s*E\\d+|Sezon|Bölüm|-?\\s*\\d+\\s*Bölüm|Part|Episode|Ep\\.?\\s*\\d+)").find(n)
            if (match != null) {
                val seriesName = match.groupValues[1].trim()
                val cleaned = seriesName.replace(Regex("[-|_|:|#]+$"), "").trim()
                if (cleaned.isNotEmpty()) {
                    return cleaned
                }
            }
            val dashSplit = n.split(Regex("\\s*-\\s*"))
            if (dashSplit.size > 1) {
                val potentialName = dashSplit[0].trim()
                if (potentialName.length > 2) return potentialName
            }
            return n.trim() // Ensure no group fallback so episodes show up
        }
        return channel.groupTitle.ifEmpty { "Diğer" }
    }

    val filteredChannels: StateFlow<List<ChannelEntity>> = combine(
        allChannels,
        selectedCategory,
        searchQuery,
        selectedMediaType
    ) { channels, category, query, mediaType ->
        var list = channels.filter { mediaType == "ALL" || determineMediaType(it) == mediaType }
        
        if (category == "★ Favoriler") {
            list = list.filter { it.isFavorite }
        } else if (category != "Hepsi") {
            list = list.filter { extractCategoryOrSeriesName(it, mediaType) == category }
        }

        if (query.isNotEmpty()) {
            list = list.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.groupTitle.contains(query, ignoreCase = true)
            }
        }
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        val lastPid = prefs.getLong("last_playlist_id", -1L)
        if (lastPid != -1L) {
            viewModelScope.launch {
                val p = playlistDao.getPlaylistById(lastPid)
                if (p != null) {
                    selectedPlaylist.value = p
                    currentTab.value = MainTab.LIVE
                    selectedMediaType.value = "live"
                }
            }
        } else {
            currentTab.value = MainTab.PLAYLISTS
            selectedMediaType.value = "playlists"
        }
    }

    fun loadRawM3u(rawText: String, playlistId: Long) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val parsed = withContext(Dispatchers.IO) {
                    com.example.data.M3uParser.parse(rawText, playlistId)
                }
                if (parsed.isNotEmpty()) {
                    parsed.chunked(999).forEach { chunk ->
                        channelDao.insertAll(chunk)
                    }
                    currentScreen.value = ScreenState.DASHBOARD
                } else {
                    errorMessage.value = "Geçerli bir IPTV kanalı bulunamadı."
                }
            } catch (e: Exception) {
                errorMessage.value = "Ayrıştırma hatası: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun addPlaylistFromUri(uri: Uri, context: Context, customName: String) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val fileName = if (customName.isNotEmpty()) customName else getFileName(context, uri) ?: "Dosya_${System.currentTimeMillis()}"
                val pid = playlistDao.insert(com.example.data.PlaylistEntity(name = fileName, type = "M3U_FILE", urlOrPath = uri.toString()))
                val parsed = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        com.example.data.M3uParser.parse(stream, pid)
                    } ?: emptyList()
                }
                
                if (parsed.isNotEmpty()) {
                    parsed.chunked(999).forEach { chunk ->
                        channelDao.insertAll(chunk)
                    }
                    currentScreen.value = ScreenState.DASHBOARD
                } else {
                    errorMessage.value = "Dosya boş veya okunamıyor."
                }
            } catch (e: Exception) {
                errorMessage.value = "Dosya okunamadı: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun addPlaylistFromUrl(urlString: String, customName: String) {
        viewModelScope.launch {
            isLoading.value = true
            loadingProgress.value = 0f
            loadingMessage.value = "IPTV Bilgileri alınıyor..."
            loadingInfo.value = null
            errorMessage.value = null
            try {
                // Determine if it's an Xtream URL and extract info
                var expirationDate: String? = null
                var playlistName = if (customName.isNotEmpty()) customName else urlString.substringAfterLast("/").substringBefore("?").ifEmpty { "URL Playlist" }
                
                try {
                    val uri = Uri.parse(urlString)
                    val username = uri.getQueryParameter("username")
                    val password = uri.getQueryParameter("password")
                    if (username != null && password != null) {
                        val host = "${uri.scheme}://${uri.authority}"
                        val playerApiUrl = "$host/player_api.php?username=$username&password=$password"
                        loadingMessage.value = "Kullanıcı bilgileri doğrulanıyor..."
                        val apiResponse = withContext(Dispatchers.IO) {
                            val conn = URL(playerApiUrl).openConnection() as HttpURLConnection
                            conn.connectTimeout = 10000
                            conn.readTimeout = 10000
                            if (conn.responseCode == 200) {
                                conn.inputStream.use { stream -> InputStreamReader(stream, "UTF-8").readText() }
                            } else null
                        }
                        if (apiResponse != null) {
                            try {
                                val json = org.json.JSONObject(apiResponse)
                                val userInfo = json.optJSONObject("user_info")
                                if (userInfo != null) {
                                    val exp = userInfo.optString("exp_date", "")
                                    loadingInfo.value = "Son Kullanma Tarihi:\n" + if (exp.isNotEmpty() && exp != "null") {
                                        val expTime = exp.toLongOrNull() ?: 0L
                                        expirationDate = if (expTime > 0) java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(expTime * 1000L)) else exp
                                        expirationDate
                                    } else "Sınırsız"
                                    val maxConn = userInfo.optString("max_connections", "1")
                                    val status = userInfo.optString("status", "")
                                    loadingInfo.value = "${loadingInfo.value}\nDurum: $status | Max Bağlantı: $maxConn"
                                    if (customName.isEmpty()) {
                                        playlistName = userInfo.optString("username", playlistName)
                                    }
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                loadingMessage.value = "Kanallar ayrıştırılıyor ve kaydediliyor..."
                val pid = playlistDao.insert(com.example.data.PlaylistEntity(name = playlistName, type = "M3U_URL", urlOrPath = urlString, expirationDate = expirationDate))
                
                val parsed = withContext(Dispatchers.IO) {
                    val url = URL(urlString)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    conn.useCaches = false
                    
                    conn.inputStream.use { stream ->
                        com.example.data.M3uParser.parse(stream, pid)
                    }
                }
                
                if (parsed.isNotEmpty()) {
                    parsed.chunked(999).forEach { chunk ->
                        channelDao.insertAll(chunk)
                    }
                    currentScreen.value = ScreenState.DASHBOARD
                } else {
                    errorMessage.value = "İçerik boş veya kanal bulunamadı."
                }
            } catch (e: Exception) {
                errorMessage.value = "Yükleme Hatası: ${e.message}"
            } finally {
                isLoading.value = false
                loadingProgress.value = 0f
            }
        }
    }

    fun addMagPlaylist(portalUrl: String, macAddress: String, customName: String) {
        viewModelScope.launch {
            isLoading.value = true
            loadingProgress.value = 0f
            loadingMessage.value = "Portal kontrol ediliyor..."
            loadingInfo.value = null
            errorMessage.value = null
            try {
                // Verify portal
                val result = withContext(Dispatchers.IO) {
                    try {
                        val finalUrl = if (portalUrl.endsWith("/")) portalUrl.dropLast(1) else portalUrl
                        val checkUrl = "$finalUrl/c/"
                        val conn = URL(checkUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 10000
                        conn.readTimeout = 10000
                        // Just hitting /c/ of portal
                        conn.responseCode
                    } catch (e: Exception) {
                        -1
                    }
                }
                
                if (result == -1) {
                    errorMessage.value = "Portal adresine erişilemedi. Lütfen adresi kontrol edin."
                    return@launch
                }
                
                loadingMessage.value = "Portala bağlanıldı. Kaydediliyor..."
                val name = if (customName.isNotEmpty()) customName else "MAG Portal"
                playlistDao.insert(com.example.data.PlaylistEntity(
                    name = name, 
                    type = "MAG", 
                    urlOrPath = portalUrl, 
                    macAddress = macAddress
                ))
            } catch (e: Exception) {
                errorMessage.value = "Yükleme Hatası: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun deletePlaylist(playlist: com.example.data.PlaylistEntity) {
        viewModelScope.launch {
            channelDao.deleteChannelsByPlaylist(playlist.id.toLong())
            playlistDao.delete(playlist)
        }
    }

    fun playExternalVideo(uri: Uri, context: Context) {
        val fileName = getFileName(context, uri) ?: "External Video"
        val tempChannel = ChannelEntity(
            playlistId = -1,
            name = fileName,
            url = uri.toString(),
            groupTitle = "External",
            logoUrl = ""
        )
        currentlyPlaying.value = tempChannel
        navigateTo(ScreenState.PLAYER)
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return result?.substringBeforeLast(".") ?: result
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            val updatedState = !channel.isFavorite
            channelDao.updateFavorite(channel.id, updatedState)
            
            if (currentlyPlaying.value?.id == channel.id) {
                currentlyPlaying.value = currentlyPlaying.value?.copy(isFavorite = updatedState)
            }
        }
    }

    fun selectPlaylistAndNavigate(playlist: com.example.data.PlaylistEntity) {
        selectedPlaylist.value = playlist
        prefs.edit().putLong("last_playlist_id", playlist.id.toLong()).apply()
        selectTabAndNavigate(MainTab.LIVE)
        currentScreen.value = ScreenState.DASHBOARD
    }
    
    fun selectTabAndNavigate(tab: MainTab) {
        currentTab.value = tab
        activeSeriesFilter.value = null
        selectedMediaType.value = when(tab) {
            MainTab.LIVE -> "live"
            MainTab.MOVIES -> "movie"
            MainTab.SERIES -> "series"
            MainTab.PLAYLISTS -> "playlists"
            MainTab.SETTINGS -> "settings"
        }
        if (currentScreen.value != ScreenState.DASHBOARD) {
            backStack.clear()
        }
        currentScreen.value = ScreenState.DASHBOARD
    }

    fun selectCategoryAndNavigate(category: String) {
        selectedCategory.value = category
        searchQuery.value = ""
        activeSeriesFilter.value = null
        navigateTo(ScreenState.CHANNELS)
    }

    fun selectChannelAndPlay(channel: ChannelEntity) {
        currentlyPlaying.value = channel
        navigateTo(ScreenState.PLAYER)
    }

    fun changeVideoScaleMode(mode: String) {
        videoScaleMode.value = mode
    }

    fun toggleScreenLock() {
        isScreenLocked.value = !isScreenLocked.value
    }

    val closeAppEvent = MutableSharedFlow<Unit>()

    fun goBack() {
        if (currentScreen.value == ScreenState.PLAYER) {
            val wasExternal = currentlyPlaying.value?.playlistId == -1L
            currentlyPlaying.value = null
            isScreenLocked.value = false
            if (wasExternal) {
                viewModelScope.launch { closeAppEvent.emit(Unit) }
                return
            }
        }
        
        if (backStack.isNotEmpty()) {
            currentScreen.value = backStack.pop()
        } else {
            if (currentScreen.value == ScreenState.CHANNELS) {
                currentScreen.value = ScreenState.DASHBOARD
            } else if (currentScreen.value == ScreenState.DASHBOARD && currentTab.value != MainTab.LIVE) {
                selectTabAndNavigate(MainTab.LIVE)
            } else {
                viewModelScope.launch { closeAppEvent.emit(Unit) }
            }
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            isLoading.value = true
            channelDao.clearAll()
            playlistDao.clearAll()
            selectTabAndNavigate(MainTab.PLAYLISTS)
            selectedCategory.value = "Hepsi"
            currentlyPlaying.value = null
            isLoading.value = false
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }
}
