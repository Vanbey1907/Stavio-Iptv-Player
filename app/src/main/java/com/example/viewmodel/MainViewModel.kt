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
    LIVE, MOVIES, SERIES, SEARCH, PLAYLISTS, SETTINGS
}

data class PrecomputedTabCatalog(
    val groupedChannels: List<Pair<String, List<Pair<String, List<ChannelEntity>>>>> = emptyList(),
    val totalCount: Int = 0
)

data class WatchedMediaEntry(
    val key: String,
    val mediaType: String,
    val itemName: String,
    val channelId: Int,
    val playlistId: Long,
    val channelName: String,
    val channelUrl: String,
    val logoUrl: String,
    val groupTitle: String,
    val timestamp: Long,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0
) {
    val progressFraction: Float
        get() = if (durationMs > 0L && positionMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0.04f, 0.98f)
        } else if (positionMs > 0L) {
            0.22f
        } else {
            0f
        }

    val formattedMinuteText: String
        get() {
            val totalMinutes = (positionMs / 60000L).toInt()
            val totalSeconds = ((positionMs / 1000L) % 60L).toInt()
            val epPrefix = if (mediaType == "series" && seasonNumber > 0 && episodeNumber > 0) {
                "S${seasonNumber} B${episodeNumber} • "
            } else {
                ""
            }
            return when {
                mediaType == "live" -> "Son İzlenen"
                totalMinutes >= 60 -> {
                    val hrs = totalMinutes / 60
                    val mins = totalMinutes % 60
                    if (mins > 0) "${epPrefix}${hrs}s ${mins}dk" else "${epPrefix}${hrs}s"
                }
                totalMinutes >= 1 -> "${epPrefix}${totalMinutes}. dk"
                positionMs >= 5000L -> "${epPrefix}0:${String.format(java.util.Locale.US, "%02d", totalSeconds)}"
                epPrefix.isNotEmpty() -> epPrefix.removeSuffix(" • ")
                else -> "Devam Et"
            }
        }

    val remainingTimeBadgeText: String
        get() {
            if (mediaType == "live") return "Canlı"
            if (durationMs > positionMs && positionMs > 0L) {
                val remMin = ((durationMs - positionMs) / 60000L).toInt().coerceAtLeast(1)
                val hrs = remMin / 60
                val mins = remMin % 60
                return if (hrs > 0) "${hrs}s ${mins}d kaldı" else "${mins}d kaldı"
            }
            val posMin = (positionMs / 60000L).toInt()
            if (posMin > 0) return "${posMin}d izlendi"
            return formattedMinuteText
        }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val channelDao = database.channelDao()
    private val playlistDao = database.playlistDao()

    val prefs = application.getSharedPreferences("stavio_prefs", android.content.Context.MODE_PRIVATE)
    private val watchHistoryPrefKey = "stavio_watch_history_v3"

    val watchHistoryByType = MutableStateFlow<Map<String, List<WatchedMediaEntry>>>(emptyMap())

    val currentScreen = MutableStateFlow(ScreenState.DASHBOARD)
    val activeSeriesFilter = MutableStateFlow<String?>(null)
    val selectedVodName = MutableStateFlow<String>("")
    val selectedVodChannels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    
    val topCast = MutableStateFlow<List<com.example.network.TmdbCast>>(emptyList())
    val vodDescription = MutableStateFlow<String>("")
    val vodRating = MutableStateFlow<Double>(0.0)
    val vodPoster = MutableStateFlow<String>("")
    val vodMetadata = MutableStateFlow(com.example.network.StavioVodMetadata())
    val isVodMetaLoading = MutableStateFlow(false)
    private var fetchVodJob: kotlinx.coroutines.Job? = null

    private fun parseSeasonAndEpisode(rawName: String): Pair<Int, Int> {
        return try {
            val sMatch = Regex("(?i)(?:S|Sezon\\s*)(\\d{1,2})").find(rawName)
            val eMatch = Regex("(?i)(?:E|B[öo]l[üu]m\\s*|Ep\\s*|Part\\s*)(\\d{1,3})").find(rawName)
            val s = sMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val e = eMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            Pair(s, e)
        } catch (_: Throwable) {
            Pair(0, 0)
        }
    }

    private fun loadWatchHistoryFromPrefs() {
        try {
            val rawJson = prefs.getString(watchHistoryPrefKey, null) ?: return
            val arr = org.json.JSONArray(rawJson)
            val entries = mutableListOf<WatchedMediaEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val mediaType = obj.optString("mediaType", "").trim()
                val itemName = obj.optString("itemName", "").trim()
                val channelUrl = obj.optString("channelUrl", "").trim()
                if (mediaType.isEmpty() || itemName.isEmpty() || channelUrl.isEmpty()) continue
                entries.add(
                    WatchedMediaEntry(
                        key = obj.optString("key", "${mediaType}_${itemName.lowercase(java.util.Locale.ROOT)}"),
                        mediaType = mediaType,
                        itemName = itemName,
                        channelId = obj.optInt("channelId", 0),
                        playlistId = obj.optLong("playlistId", 0L),
                        channelName = obj.optString("channelName", itemName),
                        channelUrl = channelUrl,
                        logoUrl = obj.optString("logoUrl", ""),
                        groupTitle = obj.optString("groupTitle", "Genel"),
                        timestamp = obj.optLong("timestamp", 0L),
                        positionMs = obj.optLong("positionMs", 0L),
                        durationMs = obj.optLong("durationMs", 0L),
                        seasonNumber = obj.optInt("seasonNumber", 0),
                        episodeNumber = obj.optInt("episodeNumber", 0)
                    )
                )
            }
            watchHistoryByType.value = entries
                .groupBy { it.mediaType }
                .mapValues { (_, list) -> list.sortedByDescending { it.timestamp }.take(30) }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun saveWatchHistoryToPrefs(map: Map<String, List<WatchedMediaEntry>>) {
        try {
            val arr = org.json.JSONArray()
            map.values.flatten().sortedByDescending { it.timestamp }.forEach { entry ->
                val obj = org.json.JSONObject().apply {
                    put("key", entry.key)
                    put("mediaType", entry.mediaType)
                    put("itemName", entry.itemName)
                    put("channelId", entry.channelId)
                    put("playlistId", entry.playlistId)
                    put("channelName", entry.channelName)
                    put("channelUrl", entry.channelUrl)
                    put("logoUrl", entry.logoUrl)
                    put("groupTitle", entry.groupTitle)
                    put("timestamp", entry.timestamp)
                    put("positionMs", entry.positionMs)
                    put("durationMs", entry.durationMs)
                    put("seasonNumber", entry.seasonNumber)
                    put("episodeNumber", entry.episodeNumber)
                }
                arr.put(obj)
            }
            prefs.edit().putString(watchHistoryPrefKey, arr.toString()).apply()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun recordWatchedMedia(
        channel: ChannelEntity,
        itemName: String? = null,
        explicitMediaType: String? = null,
        positionMs: Long? = null,
        durationMs: Long? = null
    ) {
        try {
            if (channel.playlistId == -1L || channel.url.isBlank()) return
            val rawType = explicitMediaType?.takeIf { it.isNotBlank() }?.lowercase(java.util.Locale.ROOT)
                ?: determineMediaType(channel)
            val effectiveType = when {
                rawType == "series" && !isLiveChannel(channel) -> "series"
                rawType == "movie" && !isLiveChannel(channel) -> "movie"
                else -> "live"
            }
            val cleanDisplayTitle = when (effectiveType) {
                "series" -> itemName?.takeIf { it.isNotBlank() } ?: extractCategoryOrSeriesName(channel, "series")
                else -> itemName?.takeIf { it.isNotBlank() } ?: channel.name
            }.trim().ifEmpty { channel.name.trim() }

            if (cleanDisplayTitle.isEmpty()) return

            val entryKey = "${effectiveType}_${cleanDisplayTitle.lowercase(java.util.Locale.ROOT)}"
            val currentMap = watchHistoryByType.value.toMutableMap()
            val currentListForType = currentMap[effectiveType].orEmpty()
            val previousEntry = currentListForType.firstOrNull {
                it.key == entryKey || (effectiveType != "series" && it.channelUrl == channel.url)
            }

            val (parsedS, parsedE) = if (effectiveType == "series") {
                parseSeasonAndEpisode(channel.name)
            } else {
                Pair(0, 0)
            }

            val resolvedPos = when {
                positionMs != null && positionMs > 0L -> positionMs
                previousEntry != null && previousEntry.channelUrl == channel.url -> previousEntry.positionMs
                else -> 0L
            }
            val resolvedDur = when {
                durationMs != null && durationMs > 0L -> durationMs
                previousEntry != null && previousEntry.channelUrl == channel.url -> previousEntry.durationMs
                else -> 0L
            }

            val newEntry = WatchedMediaEntry(
                key = entryKey,
                mediaType = effectiveType,
                itemName = cleanDisplayTitle,
                channelId = channel.id,
                playlistId = channel.playlistId,
                channelName = channel.name,
                channelUrl = channel.url,
                logoUrl = channel.logoUrl.ifBlank { previousEntry?.logoUrl.orEmpty() },
                groupTitle = channel.groupTitle.ifBlank { previousEntry?.groupTitle ?: "Genel" },
                timestamp = System.currentTimeMillis(),
                positionMs = resolvedPos,
                durationMs = resolvedDur,
                seasonNumber = if (parsedS > 0) parsedS else (previousEntry?.seasonNumber ?: 0),
                episodeNumber = if (parsedE > 0) parsedE else (previousEntry?.episodeNumber ?: 0)
            )

            val existingList = currentListForType
                .filterNot { it.key == entryKey || (effectiveType != "series" && it.channelUrl == channel.url) }
            val updatedList = (listOf(newEntry) + existingList).take(30)
            currentMap[effectiveType] = updatedList
            watchHistoryByType.value = currentMap
            saveWatchHistoryToPrefs(currentMap)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun clearWatchHistory() {
        watchHistoryByType.value = emptyMap()
        prefs.edit().remove(watchHistoryPrefKey).apply()
    }

    fun updatePlaybackProgress(
        channel: ChannelEntity?,
        positionMs: Long,
        durationMs: Long
    ) {
        if (channel == null || channel.playlistId == -1L || channel.url.isBlank()) return
        if (positionMs < 2000L && durationMs <= 0L) return
        recordWatchedMedia(
            channel = channel,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L)
        )
    }

    fun getSavedPositionForChannel(channel: ChannelEntity?): Long {
        if (channel == null || channel.url.isBlank()) return 0L
        val allEntries = watchHistoryByType.value.values.flatten()
        val match = allEntries.firstOrNull { it.channelUrl == channel.url } ?: return 0L
        if (match.durationMs > 0L && match.positionMs > match.durationMs - 15_000L) {
            return 0L
        }
        return match.positionMs.coerceAtLeast(0L)
    }

    fun getWatchedEntryForVod(
        vodName: String,
        channels: List<ChannelEntity>,
        isSeries: Boolean
    ): WatchedMediaEntry? {
        val activePlaylistId = selectedPlaylist.value?.id?.toLong()
            ?: channels.firstOrNull()?.playlistId?.takeIf { it > 0L }
            ?: return null
        val mediaType = if (isSeries) "series" else "movie"
        val entries = watchHistoryByType.value[mediaType].orEmpty()
            .filter { it.playlistId == activePlaylistId }
        if (entries.isEmpty()) return null
        val normalizedVod = vodName.trim().lowercase(java.util.Locale.ROOT)
        val channelUrls = channels.map { it.url }.toSet()
        return entries.firstOrNull {
            it.itemName.trim().lowercase(java.util.Locale.ROOT) == normalizedVod ||
                it.channelUrl in channelUrls
        }
    }
    
    fun handleMediaItemClick(
        channel: ChannelEntity?,
        itemName: String? = null,
        channelsGroup: List<ChannelEntity>? = null,
        explicitMediaType: String? = null
    ) {
        try {
            val validList = channelsGroup?.filterNotNull().orEmpty().ifEmpty {
                if (channel != null) listOf(channel) else emptyList()
            }
            if (validList.isEmpty()) return

            val primary = channel ?: validList.firstOrNull() ?: return
            val resolvedType = explicitMediaType?.takeIf { it.isNotBlank() } ?: determineMediaType(primary)

            // ÖNEMLİ: Film veya dizinin üzerine tıklanıp detay sayfası açıldığında "İzlemeye Devam Et" kısmına EKLENMEZ!
            // Sadece izlemeye başlandığında (selectChannelAndPlay / Player) eklenir.
            if (resolvedType.equals("live", ignoreCase = true) || resolvedType.equals("radio", ignoreCase = true)) {
                selectChannelAndPlay(primary)
            } else {
                openVodDetail(
                    rawName = itemName?.takeIf { it.isNotBlank() } ?: primary.name,
                    channels = validList,
                    mediaType = resolvedType
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun openVodDetail(
        rawName: String?,
        channels: List<ChannelEntity>?,
        mediaType: String? = null
    ) {
        try {
            val rawList = channels?.filterNotNull().orEmpty()
            if (rawList.isEmpty()) return

            val safeTitle = rawName?.trim()?.takeIf { it.isNotEmpty() }
                ?: rawList.firstOrNull()?.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: "İsimsiz İçerik"

            val sanitizedChannels = rawList.map { ch ->
                ch.copy(
                    name = ch.name.trim().ifEmpty { safeTitle },
                    logoUrl = ch.logoUrl.trim(),
                    url = ch.url.trim(),
                    groupTitle = ch.groupTitle.trim().ifEmpty { "Genel" }
                )
            }

            val primaryChannel = sanitizedChannels.first()
            val resolvedMediaType = mediaType?.takeIf { it.isNotBlank() }
                ?: if (sanitizedChannels.size > 1) "series" else determineMediaType(primaryChannel)

            selectedVodName.value = safeTitle
            selectedVodChannels.value = sanitizedChannels
            fetchVodInfo(safeTitle, resolvedMediaType)
            navigateTo(ScreenState.VOD_DETAIL)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun fetchVodInfo(name: String?, mediaType: String?) {
        try {
            fetchVodJob?.cancel()
            val currentChannels = selectedVodChannels.value
            val firstCh = currentChannels.firstOrNull()
            val safeName = name?.trim()?.takeIf { it.isNotEmpty() }
                ?: firstCh?.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: "İsimsiz İçerik"

            val cleaned = com.example.network.StavioMetadataService.cleanMediaTitle(safeName)
            val isSeries = mediaType.equals("series", ignoreCase = true) || currentChannels.size > 1
            val activeLangCode = locManager.currentLanguage.value.code

            val fallbackLogo = currentChannels.firstOrNull { it.logoUrl.isNotBlank() }?.logoUrl.orEmpty()
            val fallbackGroup = firstCh?.groupTitle?.takeIf { it.isNotBlank() }
                ?: if (isSeries) "Dizi" else "Film"
            val fallbackDescription = "${cleaned.cleanTitle} — $fallbackGroup kategorisinde yer alan ${if (isSeries) "dizi" else "film"} içeriği."

            // 2. Hafıza Önbelleği (mediaCache) Kontrolü: Aynı içeriğe ve dile tekrar tıklandığında 0.01 sn içinde anında yükle!
            val cachedMeta = com.example.network.StavioMetadataService.getCachedMetadata(
                rawTitle = safeName,
                isSeries = isSeries,
                fallbackLogo = fallbackLogo,
                fallbackGroup = fallbackGroup,
                languageCode = activeLangCode
            )
            if (cachedMeta != null) {
                val hydrated = cachedMeta.copy(
                    cleanTitle = cachedMeta.cleanTitle.ifBlank { cleaned.cleanTitle },
                    background = cachedMeta.background.ifBlank { fallbackLogo },
                    poster = cachedMeta.poster.ifBlank { fallbackLogo },
                    genres = cachedMeta.genres.ifEmpty { listOf(fallbackGroup) },
                    releaseInfo = cachedMeta.releaseInfo.ifBlank { cleaned.year.orEmpty() },
                    description = cachedMeta.description.ifBlank { fallbackDescription }
                )
                vodMetadata.value = hydrated
                vodDescription.value = hydrated.description
                vodRating.value = hydrated.imdbRating.toDoubleOrNull() ?: 0.0
                vodPoster.value = hydrated.background.ifEmpty { hydrated.poster }
                topCast.value = hydrated.cast.mapIndexed { idx, actor ->
                    com.example.network.TmdbCast(
                        id = idx,
                        name = actor.name,
                        character = actor.character,
                        profile_path = actor.photoUrl
                    )
                }
                isVodMetaLoading.value = false
                return
            }

            // Loading aşamasında ve hata durumunda detay sayfasının anında hatasız çizilmesi için yerel fallback UI verisi
            val initialFallbackMeta = com.example.network.StavioVodMetadata(
                cleanTitle = cleaned.cleanTitle,
                background = fallbackLogo,
                poster = fallbackLogo,
                genres = listOf(fallbackGroup),
                releaseInfo = cleaned.year.orEmpty(),
                description = fallbackDescription,
                isLoaded = false
            )

            topCast.value = emptyList()
            vodDescription.value = fallbackDescription
            vodRating.value = 0.0
            vodPoster.value = fallbackLogo
            vodMetadata.value = initialFallbackMeta
            isVodMetaLoading.value = true

            val customTmdbKey = try { prefs.getString("tmdb_api_key", null) } catch (_: Throwable) { null }

            fetchVodJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val meta = com.example.network.StavioMetadataService.fetchMetadata(
                        rawTitle = safeName,
                        isSeries = isSeries,
                        tmdbApiKey = customTmdbKey,
                        fallbackLogo = fallbackLogo,
                        fallbackGroup = fallbackGroup,
                        languageCode = activeLangCode
                    )
                    val safeMeta = meta.copy(
                        cleanTitle = meta.cleanTitle.ifBlank { cleaned.cleanTitle },
                        background = meta.background.ifBlank { fallbackLogo },
                        poster = meta.poster.ifBlank { fallbackLogo },
                        genres = meta.genres.ifEmpty { listOf(fallbackGroup) },
                        releaseInfo = meta.releaseInfo.ifBlank { cleaned.year.orEmpty() },
                        description = meta.description.ifBlank { fallbackDescription }
                    )
                    vodMetadata.value = safeMeta
                    vodDescription.value = safeMeta.description
                    vodRating.value = safeMeta.imdbRating.toDoubleOrNull() ?: 0.0
                    vodPoster.value = safeMeta.background.ifEmpty { safeMeta.poster }
                    topCast.value = safeMeta.cast.mapIndexed { idx, actor ->
                        com.example.network.TmdbCast(
                            id = idx,
                            name = actor.name,
                            character = actor.character,
                            profile_path = actor.photoUrl
                        )
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    // Hata durumunda yerel IPTV verileriyle fallback UI korunur
                    vodMetadata.value = initialFallbackMeta
                } finally {
                    isVodMetaLoading.value = false
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            isVodMetaLoading.value = false
        }
    }
    val previousScreen = MutableStateFlow(ScreenState.DASHBOARD)
    val isPlayerActive = MutableStateFlow(false)
    val isVideoActuallyPlaying = MutableStateFlow(false)
    val tabBackStack = java.util.Stack<MainTab>()
    private val backStack = java.util.Stack<ScreenState>()

    val quickCategoryByTab = MutableStateFlow<Map<String, String?>>(emptyMap())

    fun setQuickCategory(mediaType: String, cat: String?) {
        quickCategoryByTab.value = quickCategoryByTab.value.toMutableMap().apply { put(mediaType, cat) }
    }

    fun getQuickCategory(mediaType: String): String? = quickCategoryByTab.value[mediaType]

    fun navigateTo(screen: ScreenState) {
        if (currentScreen.value != screen) {
            if (currentScreen.value != ScreenState.PLAYER) {
                previousScreen.value = currentScreen.value
            }
            backStack.push(currentScreen.value)
            currentScreen.value = screen
            if (screen == ScreenState.PLAYER) {
                isPlayerActive.value = true
            } else {
                isPlayerActive.value = false
                isVideoActuallyPlaying.value = false
            }
        }
    }

    val currentTab = MutableStateFlow(MainTab.PLAYLISTS)
    val isStreamExtractorOpen = MutableStateFlow(false)

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

    // Fit, Fill, Stretch (persisted across sessions)
    val videoScaleMode = MutableStateFlow(prefs.getString("stavio_video_scale_mode", "Fit") ?: "Fit")
    val isScreenLocked = MutableStateFlow(false)

    val locManager = com.example.util.LocalizationManager.getInstance(application)
    val themeManager = com.example.ui.theme.ThemeManager.getInstance(application)
    val devManager = com.example.util.DeveloperOptionsManager.getInstance(application)

    val currentLanguage = locManager.currentLanguage
    val hasSelectedInitialLanguage = locManager.hasSelectedInitialLanguage
    val currentTheme = themeManager.currentTheme

    // Performance Optimization: Cache media type determinations to eliminate regex stuttering
    private val mediaTypeCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val seriesNameCache = java.util.concurrent.ConcurrentHashMap<Int, String>()

    fun determineMediaType(channel: ChannelEntity): String {
        return mediaTypeCache.computeIfAbsent(channel.id) {
            computeMediaType(channel)
        }
    }

    private val episodeRegex = Regex("(?i)(?:\\bs\\d{1,2}\\s*e\\d{1,3}\\b|\\b\\d{1,2}x\\d{1,3}\\b|\\b\\d{1,2}\\s*\\.\\s*sezon\\b|\\bsezon\\s*\\d{1,2}\\b|\\bb[öo]l[üu]m\\s*\\d{1,3}\\b|\\b\\d{1,3}\\s*\\.\\s*b[öo]l[üu]m\\b|\\bepisode\\s*\\d{1,3}\\b)")
    private val xtreamRawLiveUrlRegex = Regex("(?i)^https?://[^/]+(?::\\d+)?/(?:live/)?[^/]+/[^/]+/\\d+(?:\\.(?:ts|m3u8))?(?:\\?.*)?$")
    private val vodExtensionRegex = Regex("(?i)\\.(?:mp4|mkv|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|divx)(?:\\?.*)?$")

    private fun has247LiveMarker(c: String, n: String): Boolean {
        return c.contains("7/24") || n.contains("7/24") ||
            c.contains("24/7") || n.contains("24/7") ||
            c.contains("7-24") || n.contains("7-24") ||
            c.contains("24-7") || n.contains("24-7")
    }

    private fun isExplicitSeriesChannel(u: String, c: String, n: String): Boolean {
        if (has247LiveMarker(c, n)) return false
        if (u.contains("/series/")) return true
        if (episodeRegex.containsMatchIn(n) || episodeRegex.containsMatchIn(c)) return true
        if (c.contains("dizi") || c.contains("series") || c.contains("sezon") ||
            c.contains("tv show") || c.contains("mini dizi") || c.contains("anime")
        ) {
            return true
        }
        return false
    }

    private fun isExplicitMovieChannel(u: String, c: String, n: String): Boolean {
        if (has247LiveMarker(c, n)) return false
        if (u.contains("/movie/") || u.contains("/vod/")) return true
        if (vodExtensionRegex.containsMatchIn(u) && !u.contains("/series/") && !episodeRegex.containsMatchIn(n)) {
            return true
        }
        if (c.contains("film") || c.contains("movie") || c.contains("vod") ||
            c.contains("sinema") || c.contains("cinema") || c.contains("boxset") ||
            c.contains("vizyon") || c.contains("bluray") || c.contains("blu-ray") ||
            c.contains("4k film") || c.contains("yerli film") || c.contains("yabancı film") ||
            c.contains("yabanci film") || c.contains("aksiyon") || c.contains("komedi") ||
            c.contains("korku") || c.contains("dram") || c.contains("animasyon") ||
            c.contains("bilim kurgu") || c.contains("gerilim") || c.contains("macera") ||
            c.contains("romantik") || c.contains("fantastik")
        ) {
            return true
        }
        return false
    }

    /**
     * KARARLI MEDYA TÜRÜ SINIFLANDIRICI:
     * - Gerçek Film (/movie/, .mp4/.mkv/.avi, Film grupları) ve Dizi (/series/, S01E01, Dizi grupları) içerikleri
     *   sunucu adresinde "live" geçse veya tvg-id bulunsa bile ASLA yanlışlıkla Canlı TV sayılmaz.
     * - 7/24 kanalları, .m3u8/.ts/rtmp canlı yayınları ve ham Xtream canlı port yayınları Canlı TV olarak ayrılır.
     */
    fun isLiveChannel(channel: ChannelEntity): Boolean {
        val u = channel.url.lowercase(java.util.Locale.ROOT).trim()
        val c = channel.groupTitle.lowercase(java.util.Locale("tr", "TR")).trim()
        val n = channel.name.lowercase(java.util.Locale("tr", "TR")).trim()

        if (has247LiveMarker(c, n)) return true
        if (isExplicitSeriesChannel(u, c, n) || isExplicitMovieChannel(u, c, n)) return false

        if (u.contains(".m3u8") ||
            u.contains("rtmp://") ||
            u.contains("rtsp://") ||
            u.contains("udp://") ||
            u.contains("mms://") ||
            u.contains("/live/") ||
            u.endsWith(".ts") ||
            u.contains(".ts?") ||
            u.contains("output=ts") ||
            u.contains("output=m3u8") ||
            u.contains("extension=ts") ||
            xtreamRawLiveUrlRegex.matches(u)
        ) {
            return true
        }

        return true
    }

    private fun computeMediaType(channel: ChannelEntity): String {
        val u = channel.url.lowercase(java.util.Locale.ROOT).trim()
        val c = channel.groupTitle.lowercase(java.util.Locale("tr", "TR")).trim()
        val n = channel.name.lowercase(java.util.Locale("tr", "TR")).trim()

        if (has247LiveMarker(c, n)) {
            return "live"
        }
        if (isExplicitSeriesChannel(u, c, n)) {
            return "series"
        }
        if (isExplicitMovieChannel(u, c, n)) {
            return "movie"
        }
        return "live"
    }

    val isInitialStartupLoading = MutableStateFlow(true)
    val isCatalogComputing = MutableStateFlow(true)

    val allChannels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val catalogByType = MutableStateFlow<Map<String, PrecomputedTabCatalog>>(emptyMap())
    private var catalogLoadJob: kotlinx.coroutines.Job? = null

    private fun buildCatalogMap(channels: List<ChannelEntity>): Map<String, PrecomputedTabCatalog> {
        if (channels.isEmpty()) return emptyMap()
        val byMediaType = channels.groupBy { determineMediaType(it) }
        return listOf("live", "movie", "series").associateWith { mediaType ->
            val listForType = byMediaType[mediaType].orEmpty()
            val grouped = listForType.groupBy { it.groupTitle.ifEmpty { "Genel" } }
            val processedGroups = grouped.mapValues { (_, groupChannels) ->
                if (mediaType == "series") {
                    val seriesMap = groupChannels.groupBy { extractCategoryOrSeriesName(it, "series") }
                    seriesMap.map { (seriesName, eps) -> Pair(seriesName, eps) }
                } else {
                    groupChannels.map { Pair(it.name, listOf(it)) }
                }
            }.toList()
            PrecomputedTabCatalog(
                groupedChannels = processedGroups,
                totalCount = listForType.size
            )
        }
    }

    private fun loadPlaylistCatalog(playlist: com.example.data.PlaylistEntity?) {
        catalogLoadJob?.cancel()
        if (playlist == null) {
            allChannels.value = emptyList()
            catalogByType.value = emptyMap()
            isCatalogComputing.value = false
            isInitialStartupLoading.value = false
            return
        }
        isCatalogComputing.value = true
        catalogLoadJob = viewModelScope.launch {
            try {
                val loadedChannels = withContext(Dispatchers.IO) {
                    channelDao.getChannelsListByPlaylist(playlist.id.toLong())
                }
                val computedCatalog = withContext(Dispatchers.Default) {
                    buildCatalogMap(loadedChannels)
                }
                allChannels.value = loadedChannels
                catalogByType.value = computedCatalog
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                isCatalogComputing.value = false
                isInitialStartupLoading.value = false
            }
        }
    }

    val categories: StateFlow<List<CategoryUiModel>> = combine(allChannels, selectedMediaType) { list, mediaType ->
        val map = linkedMapOf<String, String?>()
        list.filter {
            val type = determineMediaType(it)
            if (mediaType == "movie" || mediaType == "series") {
                type == mediaType && !isLiveChannel(it)
            } else {
                mediaType == "ALL" || type == mediaType
            }
        }.forEach {
            if (it.groupTitle.isNotEmpty() && !map.containsKey(it.groupTitle)) {
                map[it.groupTitle] = it.logoUrl.ifEmpty { null }
            } else if (it.groupTitle.isNotEmpty() && map[it.groupTitle] == null && it.logoUrl.isNotEmpty()) {
                map[it.groupTitle] = it.logoUrl
            }
        }
        map.map { CategoryUiModel(it.key, it.value) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun extractCategoryOrSeriesName(channel: ChannelEntity, mediaType: String): String {
        if (mediaType == "series") {
            return seriesNameCache.computeIfAbsent(channel.id) {
                val n = channel.name
                val match = Regex("(?i)(.*?)\\s*(?:\\-?\\s*S\\d+\\s*E\\d+|Sezon|Bölüm|-?\\s*\\d+\\s*Bölüm|Part|Episode|Ep\\.?\\s*\\d+)").find(n)
                if (match != null) {
                    val seriesName = match.groupValues[1].trim()
                    val cleaned = seriesName.replace(Regex("[-|_|:|#]+$"), "").trim()
                    if (cleaned.isNotEmpty()) {
                        return@computeIfAbsent cleaned
                    }
                }
                val dashSplit = n.split(Regex("\\s*-\\s*"))
                if (dashSplit.size > 1) {
                    val potentialName = dashSplit[0].trim()
                    if (potentialName.length > 2) return@computeIfAbsent potentialName
                }
                n.trim()
            }
        }
        return channel.groupTitle.ifEmpty { "Diğer" }
    }

    val filteredChannels: StateFlow<List<ChannelEntity>> = combine(
        allChannels,
        selectedCategory,
        searchQuery,
        selectedMediaType
    ) { channels, category, query, mediaType ->
        var list = channels.filter {
            val type = determineMediaType(it)
            if (mediaType == "movie" || mediaType == "series") {
                type == mediaType && !isLiveChannel(it)
            } else {
                mediaType == "ALL" || type == mediaType
            }
        }
        
        if (category == "★ Favoriler") {
            list = list.filter { it.isFavorite }
        } else if (category.isNotEmpty() && category != "Hepsi") {
            list = list.filter {
                it.groupTitle.ifEmpty { "Genel" } == category ||
                extractCategoryOrSeriesName(it, mediaType) == category
            }
        }

        if (query.isNotEmpty()) {
            val q = query.trim()
            list = list.filter {
                it.name.contains(q, ignoreCase = true) ||
                it.groupTitle.contains(q, ignoreCase = true)
            }
        }
        list
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadWatchHistoryFromPrefs()
        val lastPid = prefs.getLong("last_playlist_id", -1L)
        viewModelScope.launch {
            if (lastPid != -1L) {
                val p = playlistDao.getPlaylistById(lastPid)
                if (p != null) {
                    selectedPlaylist.value = p
                    if (currentlyPlaying.value == null && !isPlayerActive.value && currentScreen.value != ScreenState.PLAYER) {
                        currentTab.value = MainTab.LIVE
                        selectedMediaType.value = "live"
                    }
                    loadPlaylistCatalog(p)
                    return@launch
                }
            }
            val all = playlistDao.getAllPlaylistsList()
            val first = all.firstOrNull()
            if (first != null) {
                if (currentlyPlaying.value == null && !isPlayerActive.value && currentScreen.value != ScreenState.PLAYER) {
                    selectPlaylistAndNavigate(first)
                } else {
                    selectedPlaylist.value = first
                    loadPlaylistCatalog(first)
                }
            } else {
                isInitialStartupLoading.value = false
                isCatalogComputing.value = false
                if (currentlyPlaying.value == null && !isPlayerActive.value && currentScreen.value != ScreenState.PLAYER) {
                    currentTab.value = MainTab.PLAYLISTS
                    selectedMediaType.value = "playlists"
                }
            }
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
                    val created = playlistDao.getPlaylistById(playlistId)
                    if (created != null) {
                        selectPlaylistAndNavigate(created)
                    } else {
                        currentScreen.value = ScreenState.DASHBOARD
                    }
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
                    val created = playlistDao.getPlaylistById(pid)
                    if (created != null) {
                        selectPlaylistAndNavigate(created)
                    } else {
                        currentScreen.value = ScreenState.DASHBOARD
                    }
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
                    val created = playlistDao.getPlaylistById(pid)
                    if (created != null) {
                        selectPlaylistAndNavigate(created)
                    } else {
                        currentScreen.value = ScreenState.DASHBOARD
                    }
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

    private fun normalizePortalUrl(rawUrl: String): String {
        var trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed = "http://$trimmed"
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.dropLast(1)
        }
        return trimmed
    }

    private fun normalizeMacAddress(rawMac: String): String {
        val cleanedHex = rawMac.trim().uppercase(java.util.Locale.ROOT).replace(Regex("[^0-9A-F]"), "")
        return if (cleanedHex.length == 12) {
            cleanedHex.chunked(2).joinToString(":")
        } else {
            rawMac.trim().uppercase(java.util.Locale.ROOT).replace("-", ":")
        }
    }

    private fun buildCandidatePortalEndpoints(rawPortalUrl: String): List<String> {
        val normalized = normalizePortalUrl(rawPortalUrl)
        if (normalized.isEmpty()) return emptyList()
        if (normalized.endsWith(".php", ignoreCase = true)) {
            return listOf(normalized)
        }
        val baseWithoutC = if (normalized.endsWith("/c", ignoreCase = true)) {
            normalized.substringBeforeLast("/c").trimEnd('/')
        } else {
            normalized
        }
        val candidates = linkedSetOf<String>()
        candidates.add("$baseWithoutC/portal.php")
        candidates.add("$baseWithoutC/server/load.php")
        if (!baseWithoutC.contains("/stalker_portal", ignoreCase = true)) {
            candidates.add("$baseWithoutC/stalker_portal/server/load.php")
            candidates.add("$baseWithoutC/stalker_portal/portal.php")
        }
        candidates.add("$normalized/portal.php")
        return candidates.toList()
    }

    private fun performMagHandshake(rawPortalUrl: String, rawMac: String): Triple<String, String, String>? {
        val macAddress = normalizeMacAddress(rawMac)
        val encodedMac = java.net.URLEncoder.encode(macAddress, "UTF-8")
        val cookieMac = macAddress.replace(":", "%3A")
        val endpoints = buildCandidatePortalEndpoints(rawPortalUrl)

        for (endpoint in endpoints) {
            try {
                val handshakeUrl = "$endpoint?type=stb&action=handshake&token=&JsHttpRequest=1-xml&mac=$encodedMac"
                val conn = URL(handshakeUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 12000
                conn.readTimeout = 12000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
                conn.setRequestProperty("X-User-Agent", "Model: MAG250; Link: WiFi")
                conn.setRequestProperty("Cookie", "mac=$cookieMac; stb_lang=en; timezone=Europe%2FIstanbul")

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.use { it.bufferedReader().readText() }.trim()
                    if (responseText.startsWith("{")) {
                        val json = org.json.JSONObject(responseText)
                        val jsObj = json.optJSONObject("js")
                        val tok = jsObj?.optString("token", "")?.ifEmpty { json.optString("token", "") }
                            ?: json.optString("token", "")
                        val rand = jsObj?.optString("random", "")?.ifEmpty { json.optString("random", "") }
                            ?: json.optString("random", "")
                        if (tok.isNotEmpty()) {
                            return Triple(endpoint, tok, rand)
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private suspend fun resolveMagStreamUrl(playlist: com.example.data.PlaylistEntity, cmd: String): String? {
        val portalUrl = playlist.urlOrPath
        val macAddress = normalizeMacAddress(playlist.macAddress ?: return null)
        val cookieMac = macAddress.replace(":", "%3A")

        try {
            // Doğrudan HTTP bağlantısı içeren cmd ise ve ffmpeg öneki varsa temizle
            val trimmedCmd = cmd.trim()
            if (trimmedCmd.startsWith("http://", ignoreCase = true) || trimmedCmd.startsWith("https://", ignoreCase = true)) {
                if (!trimmedCmd.contains("/ch/") && !trimmedCmd.contains("localhost")) {
                    return trimmedCmd
                }
            }

            // 1. Handshake to get working endpoint and token
            val handshakeResult = performMagHandshake(portalUrl, macAddress) ?: return null
            val portalPhpUrl = handshakeResult.first
            val token = handshakeResult.second

            // Optional: call get_profile to register the session on the server
            try {
                val profileUrl = "$portalPhpUrl?type=stb&action=get_profile&hd=1&ver=ImageDescription&JsHttpRequest=1-xml&token=$token"
                val connProfile = URL(profileUrl).openConnection() as HttpURLConnection
                connProfile.connectTimeout = 10000
                connProfile.readTimeout = 10000
                connProfile.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
                connProfile.setRequestProperty("X-User-Agent", "Model: MAG250; Link: WiFi")
                connProfile.setRequestProperty("Cookie", "mac=$cookieMac; stb_lang=en; timezone=Europe%2FIstanbul")
                connProfile.setRequestProperty("Authorization", "Bearer $token")
                connProfile.responseCode
            } catch (e: Exception) { e.printStackTrace() }

            // 2. Call create_link
            val encodedCmd = java.net.URLEncoder.encode(cmd, "UTF-8")
            val createLinkUrl = "$portalPhpUrl?type=itv&action=create_link&cmd=$encodedCmd&series=0&forced_storage=0&disable_ad=0&download=0&force_ch_link_check=0&JsHttpRequest=1-xml&token=$token"

            val connLink = URL(createLinkUrl).openConnection() as HttpURLConnection
            connLink.connectTimeout = 15000
            connLink.readTimeout = 15000
            connLink.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
            connLink.setRequestProperty("X-User-Agent", "Model: MAG250; Link: WiFi")
            connLink.setRequestProperty("Cookie", "mac=$cookieMac; stb_lang=en; timezone=Europe%2FIstanbul")
            connLink.setRequestProperty("Authorization", "Bearer $token")

            if (connLink.responseCode == 200) {
                val linkResp = connLink.inputStream.use { it.bufferedReader().readText() }
                val linkJson = org.json.JSONObject(linkResp)

                val jsVal = linkJson.opt("js")
                var finalStreamUrl: String? = null

                if (jsVal is String && jsVal.isNotEmpty()) {
                    finalStreamUrl = jsVal
                } else if (jsVal is org.json.JSONObject) {
                    finalStreamUrl = jsVal.optString("cmd").ifEmpty { jsVal.optString("url") }
                }

                if (finalStreamUrl.isNullOrEmpty()) {
                    val resVal = linkJson.opt("result")
                    if (resVal is String && resVal.isNotEmpty()) {
                        finalStreamUrl = resVal
                    } else if (resVal is org.json.JSONObject) {
                        finalStreamUrl = resVal.optString("cmd").ifEmpty { resVal.optString("url") }
                    }
                }

                if (!finalStreamUrl.isNullOrBlank()) {
                    var cleanStream = finalStreamUrl.trim()
                    if (cleanStream.contains(" ")) {
                        cleanStream = cleanStream.substringAfterLast(" ").trim()
                    }
                    if (cleanStream.startsWith("http")) {
                        return cleanStream
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Fallback: cmd içinde doğrudan http adresi varsa onu döndür
        val fallbackHttp = cmd.trim().let { if (it.contains(" ")) it.substringAfterLast(" ").trim() else it }
        return if (fallbackHttp.startsWith("http")) fallbackHttp else null
    }

    fun addMagPlaylist(portalUrl: String, macAddress: String, customName: String) {
        viewModelScope.launch {
            val cleanPortal = normalizePortalUrl(portalUrl)
            val cleanMac = normalizeMacAddress(macAddress)
            if (cleanPortal.isEmpty() || cleanMac.length < 12) {
                errorMessage.value = "Lütfen geçerli bir Portal URL ve MAC adresi (00:1A:79:...) girin."
                return@launch
            }

            isLoading.value = true
            loadingProgress.value = 0f
            loadingMessage.value = "MAC Portal kontrol ediliyor..."
            loadingInfo.value = null
            errorMessage.value = null
            try {
                // 1. Handshake to verify and get token across candidate Stalker/MAG endpoints
                val handshakeTriple = withContext(Dispatchers.IO) {
                    performMagHandshake(cleanPortal, cleanMac)
                }

                if (handshakeTriple == null || handshakeTriple.second.isEmpty()) {
                    errorMessage.value = "Portal bağlantısı başarısız. Lütfen Portal URL ve MAC adresini kontrol edin."
                    return@launch
                }

                val portalPhpUrl = handshakeTriple.first
                val token = handshakeTriple.second
                val cookieMac = cleanMac.replace(":", "%3A")

                loadingMessage.value = "Portala bağlanıldı. Profil doğrulanıyor..."

                // Call get_profile to complete handshake
                withContext(Dispatchers.IO) {
                    try {
                        val profileUrl = "$portalPhpUrl?type=stb&action=get_profile&hd=1&ver=ImageDescription&JsHttpRequest=1-xml&token=$token"
                        val connProfile = URL(profileUrl).openConnection() as HttpURLConnection
                        connProfile.connectTimeout = 10000
                        connProfile.readTimeout = 10000
                        connProfile.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
                        connProfile.setRequestProperty("X-User-Agent", "Model: MAG250; Link: WiFi")
                        connProfile.setRequestProperty("Cookie", "mac=$cookieMac; stb_lang=en; timezone=Europe%2FIstanbul")
                        connProfile.setRequestProperty("Authorization", "Bearer $token")
                        connProfile.responseCode
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                loadingMessage.value = "Kategoriler indiriliyor..."
                
                // Fetch genres/categories
                val genreMap = withContext(Dispatchers.IO) {
                    val gMap = mutableMapOf<String, String>()
                    try {
                        val genresUrl = "$portalPhpUrl?type=itv&action=get_genres&token=$token"
                        val connGenres = URL(genresUrl).openConnection() as HttpURLConnection
                        connGenres.connectTimeout = 10000
                        connGenres.readTimeout = 10000
                        connGenres.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 qt/4.7.0 Safari/533.3")
                        connGenres.setRequestProperty("X-User-Agent", "Model: MAG250; Link: WiFi")
                        connGenres.setRequestProperty("Cookie", "mac=${macAddress.replace(":", "%3A")}")
                        connGenres.setRequestProperty("Authorization", "Bearer $token")
                        if (connGenres.responseCode == 200) {
                            val responseText = connGenres.inputStream.use { it.bufferedReader().readText() }
                            val json = org.json.JSONObject(responseText)
                            val jsObj = json.opt("js")
                            val genresArray = when (jsObj) {
                                is org.json.JSONArray -> jsObj
                                is org.json.JSONObject -> jsObj.optJSONArray("data") ?: org.json.JSONArray()
                                else -> org.json.JSONArray()
                            }
                            for (i in 0 until genresArray.length()) {
                                val g = genresArray.getJSONObject(i)
                                val id = g.optString("id")
                                val title = g.optString("title")
                                if (id.isNotEmpty() && title.isNotEmpty()) {
                                    gMap[id] = title
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    gMap
                }
                
                loadingMessage.value = "Kanallar indiriliyor..."
                
                // Fetch channels
                val parsedChannels = withContext(Dispatchers.IO) {
                    val channelsList = mutableListOf<com.example.data.ChannelEntity>()
                    try {
                        val channelsUrl = "$portalPhpUrl?type=itv&action=get_all_channels&token=$token"
                        val connChannels = URL(channelsUrl).openConnection() as HttpURLConnection
                        connChannels.connectTimeout = 20000
                        connChannels.readTimeout = 30000
                        connChannels.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 qt/4.7.0 Safari/533.3")
                        connChannels.setRequestProperty("X-User-Agent", "Model: MAG250; Link: WiFi")
                        connChannels.setRequestProperty("Cookie", "mac=${macAddress.replace(":", "%3A")}")
                        connChannels.setRequestProperty("Authorization", "Bearer $token")
                        
                        if (connChannels.responseCode == 200) {
                            val responseText = connChannels.inputStream.use { it.bufferedReader().readText() }
                            val json = org.json.JSONObject(responseText)
                            val jsObj = json.opt("js")
                            
                            val dataArray = when (jsObj) {
                                is org.json.JSONArray -> jsObj
                                is org.json.JSONObject -> {
                                    jsObj.optJSONArray("data") ?: org.json.JSONArray()
                                }
                                else -> org.json.JSONArray()
                            }
                            
                            val finalArray = if (dataArray.length() == 0) {
                                json.optJSONArray("data") ?: org.json.JSONArray()
                            } else {
                                dataArray
                            }
                            
                            for (i in 0 until finalArray.length()) {
                                val ch = finalArray.getJSONObject(i)
                                val name = ch.optString("name")
                                val cmd = ch.optString("cmd")
                                val logo = ch.optString("logo")
                                val genreId = ch.optString("tv_genre_id")
                                var genreName = ch.optString("genres_name", "")
                                
                                if (genreName.isEmpty() && genreId.isNotEmpty()) {
                                    genreName = genreMap[genreId] ?: ""
                                }
                                if (genreName.isEmpty()) {
                                    genreName = "Kanallar"
                                }
                                
                                val finalLogo = if (logo.isNotEmpty() && !logo.startsWith("http")) {
                                    val portalBase = portalUrl.substringBefore("/portal.php").substringBefore("/c")
                                    val cleanPortalBase = if (portalBase.endsWith("/")) portalBase.dropLast(1) else portalBase
                                    "$cleanPortalBase/$logo"
                                } else {
                                    logo
                                }
                                
                                channelsList.add(com.example.data.ChannelEntity(
                                    playlistId = 0,
                                    url = cmd,
                                    name = name,
                                    groupTitle = genreName,
                                    logoUrl = finalLogo
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    channelsList
                }
                
                if (parsedChannels.isEmpty()) {
                    errorMessage.value = "Portalda oynatılacak kanal bulunamadı."
                    return@launch
                }
                
                loadingMessage.value = "Kanallar kaydediliyor..."
                val name = if (customName.isNotEmpty()) customName else "MAG Portal"
                val pid = playlistDao.insert(com.example.data.PlaylistEntity(
                    name = name, 
                    type = "MAG", 
                    urlOrPath = cleanPortal, 
                    macAddress = cleanMac
                ))
                
                val finalChannels = parsedChannels.map { it.copy(playlistId = pid) }
                finalChannels.chunked(999).forEach { chunk ->
                    channelDao.insertAll(chunk)
                }
                
                val created = playlistDao.getPlaylistById(pid)
                if (created != null) {
                    selectPlaylistAndNavigate(created)
                } else {
                    currentScreen.value = ScreenState.DASHBOARD
                }
            } catch (e: Exception) {
                errorMessage.value = "Yükleme Hatası: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun deletePlaylist(playlist: com.example.data.PlaylistEntity) {
        viewModelScope.launch {
            val pid = playlist.id.toLong()
            channelDao.deleteChannelsByPlaylist(pid)
            playlistDao.delete(playlist)
            mediaTypeCache.clear()
            seriesNameCache.clear()
            // Silinen oynatma listesine ait geçmiş izleme kayıtlarını da temizle
            val cleanedHistory = watchHistoryByType.value.mapValues { (_, list) ->
                list.filterNot { it.playlistId == pid }
            }
            watchHistoryByType.value = cleanedHistory
            saveWatchHistoryToPrefs(cleanedHistory)
            if (selectedPlaylist.value?.id == playlist.id) {
                selectedPlaylist.value = null
                loadPlaylistCatalog(null)
                currentTab.value = MainTab.PLAYLISTS
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            channelDao.clearAll()
            playlistDao.clearAll()
            prefs.edit().remove("last_playlist_id").apply()
            selectedPlaylist.value = null
            currentlyPlaying.value = null
            allChannels.value = emptyList()
            catalogByType.value = emptyMap()
            mediaTypeCache.clear()
            seriesNameCache.clear()
            clearWatchHistory()
            withContext(Dispatchers.Main) {
                currentTab.value = MainTab.PLAYLISTS
                currentScreen.value = ScreenState.DASHBOARD
            }
        }
    }

    fun playExternalVideo(uri: Uri, context: Context) {
        val fileName = getFileName(context, uri)?.takeIf { it.isNotBlank() } ?: "Harici Video"
        val tempChannel = ChannelEntity(
            playlistId = -1,
            name = fileName,
            url = uri.toString(),
            groupTitle = "Harici Video Oynatıcı",
            logoUrl = ""
        )
        currentlyPlaying.value = tempChannel
        isPlayerActive.value = true
        navigateTo(ScreenState.PLAYER)
    }

    fun playExtractedStream(
        title: String,
        streamUrlWithHeaders: String,
        groupName: String = "Kaynak Bulucu",
        streamType: String = "movie"
    ) {
        val activePid = selectedPlaylist.value?.id?.toLong() ?: -1L
        val tempChannel = ChannelEntity(
            playlistId = activePid,
            name = title.ifBlank { "Yakalanan Akış" },
            url = streamUrlWithHeaders,
            groupTitle = groupName,
            logoUrl = ""
        )
        currentlyPlaying.value = tempChannel
        isPlayerActive.value = true
        navigateTo(ScreenState.PLAYER)
    }

    fun saveExtractedStreamToPlaylist(
        title: String,
        streamUrlWithHeaders: String,
        category: String = "Kaydedilen Kaynaklar",
        streamType: String = "movie",
        onComplete: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var targetPlaylist = selectedPlaylist.value
                if (targetPlaylist == null) {
                    val existing = allPlaylists.value.firstOrNull { it.name.equals("Kaydedilen Kaynaklar", ignoreCase = true) }
                        ?: allPlaylists.value.firstOrNull()
                    if (existing != null) {
                        targetPlaylist = existing
                    } else {
                        val newPid = playlistDao.insert(
                            com.example.data.PlaylistEntity(
                                name = "Kaydedilen Kaynaklar",
                                urlOrPath = "local://saved_streams",
                                type = "M3U"
                            )
                        )
                        val created = playlistDao.getPlaylistById(newPid) ?: com.example.data.PlaylistEntity(
                            id = newPid.toInt(),
                            name = "Kaydedilen Kaynaklar",
                            urlOrPath = "local://saved_streams",
                            type = "M3U"
                        )
                        targetPlaylist = created
                        withContext(Dispatchers.Main) {
                            selectedPlaylist.value = created
                        }
                    }
                }

                val pid = targetPlaylist.id.toLong()
                val effectiveGroup = when (streamType) {
                    "series" -> "Kaydedilen Diziler"
                    "live" -> "Kaydedilen Canlı TV"
                    else -> "Kaydedilen Filmler"
                }
                val newChannel = ChannelEntity(
                    playlistId = pid,
                    name = title.ifBlank { "Kaydedilen Akış" },
                    url = streamUrlWithHeaders,
                    groupTitle = effectiveGroup,
                    logoUrl = ""
                )
                channelDao.insertAll(listOf(newChannel))
                withContext(Dispatchers.Main) {
                    onComplete(targetPlaylist.name)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete("")
                }
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) result = cursor.getString(index)
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to URI path if contentResolver query is restricted
        }
        if (result == null) {
            result = uri.lastPathSegment ?: uri.path?.let { path ->
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
            val updatedList = allChannels.value.map {
                if (it.id == channel.id) it.copy(isFavorite = updatedState) else it
            }
            allChannels.value = updatedList
            catalogByType.value = withContext(Dispatchers.Default) {
                buildCatalogMap(updatedList)
            }
            if (currentlyPlaying.value?.id == channel.id) {
                currentlyPlaying.value = currentlyPlaying.value?.copy(isFavorite = updatedState)
            }
        }
    }

    fun selectPlaylistAndNavigate(playlist: com.example.data.PlaylistEntity) {
        mediaTypeCache.clear()
        seriesNameCache.clear()
        selectedCategory.value = "Hepsi"
        searchQuery.value = ""
        activeSeriesFilter.value = null
        quickCategoryByTab.value = emptyMap()
        selectedPlaylist.value = playlist
        prefs.edit().putLong("last_playlist_id", playlist.id.toLong()).apply()
        loadPlaylistCatalog(playlist)
        selectTabAndNavigate(MainTab.LIVE)
        currentScreen.value = ScreenState.DASHBOARD
    }
    
    fun selectTabAndNavigate(tab: MainTab) {
        if (currentTab.value != tab) {
            tabBackStack.push(currentTab.value)
        }
        currentTab.value = tab
        activeSeriesFilter.value = null
        selectedMediaType.value = when(tab) {
            MainTab.LIVE -> "live"
            MainTab.MOVIES -> "movie"
            MainTab.SERIES -> "series"
            MainTab.SEARCH -> "search"
            MainTab.PLAYLISTS -> "playlists"
            MainTab.SETTINGS -> "settings"
        }
        currentScreen.value = ScreenState.DASHBOARD
    }

    fun selectCategoryAndNavigate(category: String) {
        selectedCategory.value = category
        searchQuery.value = ""
        activeSeriesFilter.value = null
        navigateTo(ScreenState.CHANNELS)
    }

    fun selectChannelAndPlay(channel: ChannelEntity?) {
        if (channel == null || channel.url.isBlank()) {
            errorMessage.value = "Bu içerik için geçerli bir yayın bağlantısı bulunamadı."
            return
        }
        recordWatchedMedia(channel = channel)
        viewModelScope.launch {
            try {
                isLoading.value = true
                loadingProgress.value = 0f
                loadingMessage.value = "Kanal bağlantısı alınıyor..."
                loadingInfo.value = null
                errorMessage.value = null

                val playlist = withContext(Dispatchers.IO) {
                    try { playlistDao.getPlaylistById(channel.playlistId) } catch (_: Throwable) { null }
                }

                if (playlist != null && playlist.type == "MAG") {
                    val resolvedUrl = withContext(Dispatchers.IO) {
                        resolveMagStreamUrl(playlist, channel.url)
                    }
                    isLoading.value = false
                    if (!resolvedUrl.isNullOrBlank()) {
                        val playableChannel = channel.copy(url = resolvedUrl)
                        currentlyPlaying.value = playableChannel
                        navigateTo(ScreenState.PLAYER)
                    } else {
                        errorMessage.value = "Kanal bağlantısı alınamadı."
                    }
                } else {
                    isLoading.value = false
                    currentlyPlaying.value = channel
                    navigateTo(ScreenState.PLAYER)
                }
            } catch (e: Throwable) {
                isLoading.value = false
                errorMessage.value = "Oynatıcı başlatılamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}"
            }
        }
    }

    fun changeVideoScaleMode(mode: String) {
        videoScaleMode.value = mode
        prefs.edit().putString("stavio_video_scale_mode", mode).apply()
    }

    fun toggleScreenLock() {
        isScreenLocked.value = !isScreenLocked.value
    }

    val closeAppEvent = MutableSharedFlow<Unit>()

    fun closePlayer() {
        val wasExternal = currentlyPlaying.value?.playlistId == -1L
        currentlyPlaying.value = null
        isScreenLocked.value = false
        isPlayerActive.value = false
        isVideoActuallyPlaying.value = false
        if (wasExternal) {
            viewModelScope.launch { closeAppEvent.emit(Unit) }
            return
        }
        if (backStack.isNotEmpty()) {
            val prev = backStack.pop()
            currentScreen.value = if (prev == ScreenState.PLAYER) previousScreen.value else prev
        } else {
            currentScreen.value = previousScreen.value
        }
    }

    fun goBack() {
        if (isPlayerActive.value || currentScreen.value == ScreenState.PLAYER) {
            closePlayer()
            return
        }
        
        if (backStack.isNotEmpty()) {
            currentScreen.value = backStack.pop()
            return
        }
        
        if (currentScreen.value == ScreenState.CHANNELS || currentScreen.value == ScreenState.VOD_DETAIL) {
            currentScreen.value = ScreenState.DASHBOARD
            return
        }
        
        if (currentScreen.value == ScreenState.DASHBOARD) {
            if (tabBackStack.isNotEmpty()) {
                val prevTab = tabBackStack.pop()
                currentTab.value = prevTab
                selectedMediaType.value = when (prevTab) {
                    MainTab.LIVE -> "live"
                    MainTab.MOVIES -> "movie"
                    MainTab.SERIES -> "series"
                    MainTab.SEARCH -> "search"
                    MainTab.PLAYLISTS -> "playlists"
                    MainTab.SETTINGS -> "settings"
                }
                return
            } else if (currentTab.value != MainTab.LIVE) {
                currentTab.value = MainTab.LIVE
                selectedMediaType.value = "live"
                return
            } else {
                viewModelScope.launch { closeAppEvent.emit(Unit) }
                return
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
