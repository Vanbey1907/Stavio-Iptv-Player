package com.example.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ChannelEntity
import com.example.network.StavioActor
import com.example.network.StavioEpisodeMeta
import com.example.network.StavioMetadataService
import com.example.network.StavioTrailer
import com.example.viewmodel.MainViewModel

private val DetailBgBlack = Color(0xFF050507)
private val DetailCardSurface = Color(0xFF18181C)
private val DetailAvatarSurface = Color(0xFF1C1C22)
private val DetailImdbYellow = Color(0xFFF5C518)
private val DetailTextZinc200 = Color(0xFFE4E4E7)
private val DetailTextZinc300 = Color(0xFFD4D4D8)
private val DetailTextZinc400 = Color(0xFFA1A1AA)
private val DetailBorderSubtle = Color(0x1AFFFFFF) // border-white/10

private data class ResolvedSeriesEpisode(
    val season: Int,
    val episodeNumber: Int,
    val channel: ChannelEntity?,
    val meta: StavioEpisodeMeta?,
    val fallbackTitle: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VodDetailScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val vodName by viewModel.selectedVodName.collectAsStateWithLifecycle()
    val channels by viewModel.selectedVodChannels.collectAsStateWithLifecycle()
    val metadata by viewModel.vodMetadata.collectAsStateWithLifecycle()
    val isMetaLoading by viewModel.isVodMetaLoading.collectAsStateWithLifecycle()
    val watchHistoryMap by viewModel.watchHistoryByType.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.goBack()
    }

    val firstChannel = channels.firstOrNull()
    if (firstChannel == null) {
        LaunchedEffect(Unit) {
            viewModel.goBack()
        }
        return
    }

    val safeVodName = remember(vodName, firstChannel.name) {
        vodName.trim().ifEmpty { firstChannel.name.trim().ifEmpty { "İsimsiz İçerik" } }
    }

    val isSeries = remember(channels, firstChannel) {
        try {
            channels.size > 1 || viewModel.determineMediaType(firstChannel) == "series"
        } catch (_: Throwable) {
            channels.size > 1
        }
    }

    val cleanedTitleInfo = remember(safeVodName) {
        StavioMetadataService.cleanMediaTitle(safeVodName)
    }

    val locManager = remember { com.example.util.LocalizationManager.getInstance(context) }
    val currentLang by locManager.currentLanguage.collectAsStateWithLifecycle()

    // İçerik adı veya seçili uygulama dili değiştiğinde bilgileri ve çeviriyi anında güncelle
    LaunchedEffect(safeVodName, isSeries, currentLang.code) {
        viewModel.fetchVodInfo(safeVodName, if (isSeries) "series" else "movie")
    }

    val coroutineScope = rememberCoroutineScope()
    var isMuted by rememberSaveable { mutableStateOf(false) }
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    var selectedTrailerForModal by remember { mutableStateOf<StavioTrailer?>(null) }
    var activeHeroTrailer by remember { mutableStateOf<StavioTrailer?>(null) }
    var isResolvingFallbackTrailer by remember { mutableStateOf(false) }

    // Safe lists for cast and trailers (Null / Empty / Duplicate protection)
    val safeCast = remember(metadata.cast) {
        metadata.cast.filter { it.name.isNotBlank() }
    }

    val safeTrailers = remember(metadata.trailers) {
        metadata.trailers.filter { it.ytId.isNotBlank() || it.directStreamUrl.isNotBlank() }
    }

    // Map IPTV channels by (season, episodeNumber) safely
    val iptvSeasonGroups = remember(channels, isSeries) {
        if (!isSeries) {
            emptyMap()
        } else {
            try {
                val seasonRegex = Regex("(?i)(?:S|Sezon\\s*)(\\d{1,2})")
                val epRegex = Regex("(?i)(?:E|B[öo]l[üu]m\\s*|Ep\\s*|Part\\s*)(\\d{1,3})")
                val groupedBySeason = channels.groupBy { ch ->
                    val sMatch = seasonRegex.find(ch.name)
                    sMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                }.toSortedMap()

                groupedBySeason.mapValues { (_, epChannels) ->
                    epChannels.mapIndexed { idx, ch ->
                        val epMatch = epRegex.find(ch.name)
                        val epNum = epMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: (idx + 1)
                        Pair(epNum, ch)
                    }.sortedBy { it.first }
                }
            } catch (_: Throwable) {
                mapOf(1 to channels.mapIndexed { idx, ch -> Pair(idx + 1, ch) })
            }
        }
    }

    // Combine IPTV seasons and Cinemeta seasons so all available seasons are navigable
    val allSeasons = remember(iptvSeasonGroups, metadata.episodes, isSeries) {
        if (!isSeries) {
            emptyList()
        } else {
            val sSet = sortedSetOf<Int>()
            sSet.addAll(iptvSeasonGroups.keys)
            if (sSet.isEmpty()) {
                sSet.addAll(metadata.episodes.keys.map { it.first }.filter { it > 0 })
            }
            if (sSet.isEmpty()) sSet.add(1)
            sSet.toList()
        }
    }

    var selectedSeason by rememberSaveable(safeVodName) {
        mutableIntStateOf(allSeasons.firstOrNull() ?: 1)
    }

    LaunchedEffect(allSeasons) {
        if (allSeasons.isNotEmpty() && selectedSeason !in allSeasons) {
            selectedSeason = allSeasons.first()
        }
    }

    // Build episode list for the selected season (merging IPTV streams + Cinemeta metadata)
    val currentSeasonEpisodes = remember(selectedSeason, iptvSeasonGroups, metadata.episodes, firstChannel) {
        val iptvEps = iptvSeasonGroups[selectedSeason].orEmpty()
        if (iptvEps.isNotEmpty()) {
            iptvEps.map { (epNum, ch) ->
                val meta = metadata.episodes[Pair(selectedSeason, epNum)]
                ResolvedSeriesEpisode(
                    season = selectedSeason,
                    episodeNumber = epNum,
                    channel = ch,
                    meta = meta,
                    fallbackTitle = ch.name.ifBlank { "${epNum}. Bölüm" }
                )
            }
        } else {
            val metaEpsForSeason = metadata.episodes.values
                .filter { it.season == selectedSeason }
                .sortedBy { it.episode }
            if (metaEpsForSeason.isNotEmpty()) {
                metaEpsForSeason.map { m ->
                    ResolvedSeriesEpisode(
                        season = selectedSeason,
                        episodeNumber = m.episode,
                        channel = firstChannel,
                        meta = m,
                        fallbackTitle = m.title.ifBlank { "${m.episode}. Bölüm" }
                    )
                }
            } else {
                listOf(
                    ResolvedSeriesEpisode(
                        season = selectedSeason,
                        episodeNumber = 1,
                        channel = firstChannel,
                        meta = null,
                        fallbackTitle = firstChannel.name.ifBlank { "1. Bölüm" }
                    )
                )
            }
        }
    }

    // Primary Play target (Movie channel or S1 B1 / first episode of selected season)
    val primaryPlayEpisode = remember(currentSeasonEpisodes, iptvSeasonGroups) {
        currentSeasonEpisodes.firstOrNull()
            ?: iptvSeasonGroups.values.firstOrNull()?.firstOrNull()?.let { (epNum, ch) ->
                ResolvedSeriesEpisode(1, epNum, ch, null, ch.name)
            }
    }

    val heroBackdropUrl = remember(metadata, channels) {
        metadata.background
            .ifBlank { metadata.poster }
            .ifBlank { channels.firstOrNull { it.logoUrl.isNotBlank() }?.logoUrl.orEmpty() }
    }

    val genresText = remember(metadata.genres, firstChannel.groupTitle, isSeries) {
        if (metadata.genres.isNotEmpty()) {
            metadata.genres.filter { it.isNotBlank() }.joinToString(" • ")
        } else {
            firstChannel.groupTitle.ifBlank { if (isSeries) "Dizi" else "Film" }
        }
    }

    val displayYear = remember(metadata.releaseInfo, cleanedTitleInfo.year) {
        metadata.releaseInfo.ifBlank { cleanedTitleInfo.year.orEmpty() }
    }

    val listState = rememberLazyListState()

    // Güvenli oynatma yardımcı fonksiyonu (Bölüm 4: Oynat Butonu Güvenliği)
    val safePlayChannel: (ChannelEntity?) -> Unit = { targetChannel ->
        val streamUrl = targetChannel?.url?.trim().orEmpty()
        if (targetChannel == null || streamUrl.isEmpty()) {
            Toast.makeText(
                context,
                "Bu içerik için geçerli bir yayın bağlantısı bulunamadı.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.selectChannelAndPlay(targetChannel)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DetailBgBlack)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 56.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. ÜST SİNEMATİK HERO ALANI VE ÖZEL FRAGMAN OYNATICI (Kırmızı İşaretli Alan)
            // Fotoğraftaki kırmızı işaretli üst hero alanında film veya dizinin üstüne basınca ilk fragmanı
            // hiçbir YouTube kontrolü/logosu görünmeden özel oynatıcıyla (Başlat/Durdur, -10sn/+10sn ve alt oynatma çubuğu) oynatır.
            item(key = "hero_header") {
                var logoLoadFailed by remember(metadata.logo) { mutableStateOf(false) }
                var heroLoadFailed by remember(heroBackdropUrl) { mutableStateOf(false) }

                val triggerFirstTrailerInHero: () -> Unit = {
                    val firstTrailer = safeTrailers.firstOrNull()
                    if (firstTrailer != null) {
                        isMuted = false
                        activeHeroTrailer = firstTrailer
                    } else if (!isResolvingFallbackTrailer) {
                        isResolvingFallbackTrailer = true
                        coroutineScope.launch {
                            val searchTitle = metadata.cleanTitle.ifBlank { cleanedTitleInfo.cleanTitle }
                            val resolvedTrailer = StavioMetadataService.resolveFallbackTrailer(
                                cleanTitle = searchTitle,
                                year = displayYear,
                                isSeries = isSeries
                            )
                            isResolvingFallbackTrailer = false
                            if (resolvedTrailer != null && (resolvedTrailer.ytId.isNotBlank() || resolvedTrailer.directStreamUrl.isNotBlank())) {
                                isMuted = false
                                activeHeroTrailer = resolvedTrailer
                            } else {
                                Toast.makeText(context, "Bu içerik için fragman bulunamadı.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clickable(enabled = activeHeroTrailer == null) {
                            triggerFirstTrailerInHero()
                        }
                ) {
                    if (activeHeroTrailer != null) {
                        StavioCustomChromelessTrailerPlayer(
                            trailer = activeHeroTrailer!!,
                            backdropUrl = heroBackdropUrl,
                            title = metadata.cleanTitle.ifBlank { cleanedTitleInfo.cleanTitle },
                            year = displayYear,
                            isSeries = isSeries,
                            initialMuted = isMuted,
                            onBackClick = { viewModel.goBack() },
                            onCloseTrailer = { activeHeroTrailer = null },
                            onMuteChanged = { isMuted = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Hero Background Image (Fallback UI if empty or error)
                        if (heroBackdropUrl.isNotBlank() && !heroLoadFailed) {
                            AsyncImage(
                                model = heroBackdropUrl,
                                contentDescription = safeVodName,
                                contentScale = ContentScale.Crop,
                                onError = { heroLoadFailed = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF181824), DetailBgBlack)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSeries) Icons.Rounded.VideoLibrary else Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.size(84.dp)
                                )
                            }
                        }

                        // Alt Karartma (Vignette): bg-gradient-to-t from-[#050507] via-[#050507]/60 to-transparent
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Black.copy(alpha = 0.20f),
                                            0.45f to Color.Transparent,
                                            0.72f to DetailBgBlack.copy(alpha = 0.65f),
                                            1.0f to DetailBgBlack
                                        )
                                    )
                                )
                        )

                        // Fragman yükleniyorsa zarif gösterge
                        if (isResolvingFallbackTrailer) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .padding(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Üst Butonlar: Sol üstte geri ok (←), sağ üstte fragman/ses başlatıcı
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { viewModel.goBack() },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Geri",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = { triggerFirstTrailerInHero() },
                                shape = RoundedCornerShape(999.dp),
                                color = Color.Black.copy(alpha = 0.50f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayCircleFilled,
                                        contentDescription = "Fragmanı Oynat",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Fragman",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Geçişin tam ortasında şeffaf logo görseli + altında türler (Action • Adventure)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (metadata.logo.isNotBlank() && !logoLoadFailed) {
                                AsyncImage(
                                    model = metadata.logo,
                                    contentDescription = metadata.cleanTitle.ifBlank { safeVodName },
                                    contentScale = ContentScale.Fit,
                                    onError = { logoLoadFailed = true },
                                    modifier = Modifier
                                        .heightIn(max = 80.dp)
                                        .fillMaxWidth(0.75f)
                                )
                            } else {
                                Text(
                                    text = metadata.cleanTitle.ifBlank { cleanedTitleInfo.cleanTitle },
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 30.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = genresText,
                                color = DetailTextZinc400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 2. AKSİYON BUTONLARI SATIRI (▶ Oynat / Kaldığımız Dakikadan Devam Et & •••)
            item(key = "action_buttons_row") {
                val watchedEntry = remember(watchHistoryMap, safeVodName, channels, isSeries) {
                    viewModel.getWatchedEntryForVod(safeVodName, channels, isSeries)
                }

                val resumeChannel = remember(watchedEntry, channels, primaryPlayEpisode, firstChannel, isSeries) {
                    if (watchedEntry != null) {
                        channels.firstOrNull { it.url == watchedEntry.channelUrl }
                            ?: if (isSeries) (primaryPlayEpisode?.channel ?: firstChannel) else firstChannel
                    } else {
                        if (isSeries) (primaryPlayEpisode?.channel ?: firstChannel) else firstChannel
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val playLabel = when {
                            watchedEntry != null && watchedEntry.positionMs >= 5000L -> {
                                "Devam Et • ${watchedEntry.formattedMinuteText}"
                            }
                            isSeries -> {
                                val s = primaryPlayEpisode?.season ?: 1
                                val e = primaryPlayEpisode?.episodeNumber ?: 1
                                "S$s B$e oynat"
                            }
                            else -> "Oynat"
                        }

                        Button(
                            onClick = {
                                safePlayChannel(resumeChannel)
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = playLabel,
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Sağda 48x48px yuvarlak ••• butonu
                        Surface(
                            onClick = { showMoreOptionsSheet = true },
                            shape = CircleShape,
                            color = DetailCardSurface,
                            border = BorderStroke(1.dp, DetailBorderSubtle),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.MoreHoriz,
                                    contentDescription = "Daha Fazla Seçenek",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Kaldığımız dakika ve zarif ilerleme çubuğu (sadece daha önce izlenmeye başlandıysa görünür)
                    if (watchedEntry != null && watchedEntry.positionMs >= 5000L) {
                        val totalDurMin = (watchedEntry.durationMs / 60000L).toInt()
                        val remMin = ((watchedEntry.durationMs - watchedEntry.positionMs).coerceAtLeast(0L) / 60000L).toInt()
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.History,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "İzlemeye Devam Et • ${watchedEntry.formattedMinuteText}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (totalDurMin > 0 && remMin > 0) {
                                        Text(
                                            text = "$remMin dk kaldı",
                                            color = DetailTextZinc400,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(7.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.White.copy(alpha = 0.18f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(watchedEntry.progressFraction.coerceIn(0.05f, 1f))
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. META VERİ, YÖNETMEN/YAZAR VE ÖZET ALANI
            item(key = "meta_director_summary") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    // Meta Satırı (Yayın Yılı • Süre • IMDb Puanı)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (displayYear.isNotBlank()) {
                            Text(
                                text = displayYear,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (metadata.runtime.isNotBlank()) {
                            Text(
                                text = metadata.runtime,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (metadata.imdbRating.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(DetailImdbYellow, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "IMDb",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Text(
                                    text = metadata.imdbRating,
                                    color = DetailImdbYellow,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (isMetaLoading) {
                            CircularProgressIndicator(
                                color = Color.White.copy(alpha = 0.6f),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Kadro Bilgisi (Yönetmen & Yazar)
                    val safeDirectors = remember(metadata.directors) { metadata.directors.filter { it.isNotBlank() } }
                    val safeWriters = remember(metadata.writers) { metadata.writers.filter { it.isNotBlank() } }

                    if (safeDirectors.isNotEmpty() || safeWriters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (safeDirectors.isNotEmpty()) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = DetailTextZinc400, fontWeight = FontWeight.Normal)) {
                                            append("Yönetmen: ")
                                        }
                                        withStyle(SpanStyle(color = DetailTextZinc200, fontWeight = FontWeight.Medium)) {
                                            append(safeDirectors.joinToString(", "))
                                        }
                                    },
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                            if (safeWriters.isNotEmpty()) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = DetailTextZinc400, fontWeight = FontWeight.Normal)) {
                                            append("Yazar: ")
                                        }
                                        withStyle(SpanStyle(color = DetailTextZinc200, fontWeight = FontWeight.Medium)) {
                                            append(safeWriters.take(4).joinToString(", "))
                                        }
                                    },
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    // Konu Özeti: Seçili uygulama diline (tr, en, es, fr, de, ar) anında otomatik çevrilir
                    val baseSummaryText = metadata.description.ifBlank {
                        "${metadata.cleanTitle.ifBlank { safeVodName }} — ${firstChannel.groupTitle.ifBlank { "Genel" }} kategorisinde yer alan ${if (isSeries) "dizi" else "film"} içeriği."
                    }
                    var translatedSummaryText by remember(baseSummaryText, currentLang.code) {
                        mutableStateOf(baseSummaryText)
                    }
                    LaunchedEffect(baseSummaryText, currentLang.code) {
                        if (baseSummaryText.isNotBlank()) {
                            val translated = withContext(Dispatchers.IO) {
                                StavioMetadataService.translateTextInstant(baseSummaryText, currentLang.code)
                            }
                            if (translated.isNotBlank()) {
                                translatedSummaryText = translated
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                    ) {
                        Text(
                            text = translatedSummaryText,
                            color = DetailTextZinc300,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (translatedSummaryText.length > 120) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isDescriptionExpanded) "Daha az göster ▴" else "Daha fazla göster ▾",
                                color = DetailTextZinc400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 4. "OYUNCULAR" BÖLÜMÜ (Yuvarlak İnisiyalli / Fotoğraflı 64x64px Avatarlar)
            if (safeCast.isNotEmpty()) {
                item(key = "cast_section") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        Text(
                            text = "Oyuncular",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(
                                items = safeCast,
                                key = { index, actor -> "actor_${index}_${actor.name}" }
                            ) { _, actor ->
                                StavioActorAvatarItem(actor = actor)
                            }
                        }
                    }
                }
            }

            // 5. "FRAGMANLAR" BÖLÜMÜ (Trailer ▾ + 16:9 YouTube Kartları)
            if (safeTrailers.isNotEmpty()) {
                item(key = "trailers_section") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Fragmanlar",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = DetailCardSurface,
                                border = BorderStroke(1.dp, DetailBorderSubtle)
                            ) {
                                Text(
                                    text = "Trailer ▾",
                                    color = DetailTextZinc300,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = safeTrailers,
                                key = { index, trailer -> "trailer_${index}_${trailer.ytId}" }
                            ) { _, trailer ->
                                StavioTrailerCard(
                                    trailer = trailer,
                                    onClick = {
                                        isMuted = false
                                        activeHeroTrailer = trailer
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 6. "SEZONLAR" VE BÖLÜM KARTLARI (SADECE DİZİLER İÇİN)
            if (isSeries && allSeasons.isNotEmpty()) {
                item(key = "seasons_episodes_section") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        Text(
                            text = "Sezonlar",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Sezon Seçici Haplar (1. sezon, 2. sezon...)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = allSeasons,
                                key = { index, seasonNum -> "season_pill_${index}_$seasonNum" }
                            ) { _, seasonNum ->
                                val isSelected = seasonNum == selectedSeason
                                Surface(
                                    onClick = { selectedSeason = seasonNum },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) DetailAvatarSurface else Color.Transparent
                                ) {
                                    Text(
                                        text = "$seasonNum. sezon",
                                        color = if (isSelected) Color.White else DetailTextZinc400,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(
                                            horizontal = if (isSelected) 16.dp else 12.dp,
                                            vertical = 8.dp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Yatay Sinematik Bölüm Kartları (w-72 aspect-[16/10] rounded-2xl)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = currentSeasonEpisodes,
                                key = { index, ep -> "ep_${index}_${ep.season}_${ep.episodeNumber}_${ep.channel?.id ?: 0}" }
                            ) { _, epItem ->
                                StavioEpisodeCard(
                                    episodeItem = epItem,
                                    fallbackBackdrop = heroBackdropUrl,
                                    onClick = {
                                        val target = epItem.channel ?: firstChannel
                                        safePlayChannel(target)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 7. EN ALT BİLGİ TABLOSU ("Film detayları" / "Dizi detayları")
            item(key = "bottom_details_table") {
                val statusVal = metadata.status.ifBlank { if (isSeries) "Devam Ediyor" else "" }
                val releaseVal = displayYear.ifBlank { "Bilinmiyor" }
                val runtimeVal = metadata.runtime.ifBlank { if (isSeries) "45dk" else "1s 50dk" }
                val countryVal = metadata.country.ifBlank { "Uluslararası" }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 24.dp)
                ) {
                    Text(
                        text = if (isSeries) "Dizi detayları" else "Film detayları",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (isSeries && statusVal.isNotEmpty()) {
                        StavioDetailTableRow(label = "Durum", value = statusVal)
                    }
                    StavioDetailTableRow(label = "Yayın bilgisi", value = releaseVal)
                    StavioDetailTableRow(label = "Süre", value = runtimeVal)
                    StavioDetailTableRow(label = "Menşe ülke", value = countryVal)
                }
            }
        }
    }

    // ••• Daha Fazla Seçenek Modalı
    if (showMoreOptionsSheet) {
        AlertDialog(
            onDismissRequest = { showMoreOptionsSheet = false },
            containerColor = DetailCardSurface,
            title = {
                Text(
                    text = metadata.cleanTitle.ifBlank { safeVodName },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            viewModel.toggleFavorite(firstChannel)
                            Toast.makeText(
                                context,
                                if (firstChannel.isFavorite) "Favorilerden çıkarıldı" else "Favorilere eklendi",
                                Toast.LENGTH_SHORT
                            ).show()
                            showMoreOptionsSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DetailAvatarSurface,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(
                            imageVector = if (firstChannel.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = if (firstChannel.isFavorite) Color(0xFFFF2E4D) else Color.White
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (firstChannel.isFavorite) "Favorilerden Çıkar" else "Favorilere Ekle",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            showMoreOptionsSheet = false
                            val streamUrl = firstChannel.url.trim()
                            if (streamUrl.isEmpty()) {
                                Toast.makeText(context, "Bu içerik için yayın bağlantısı bulunamadı", Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(streamUrl), "video/*")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Harici Oynatıcıda Aç"))
                                } catch (_: Throwable) {
                                    Toast.makeText(context, "Uygun oynatıcı bulunamadı", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DetailAvatarSurface,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Harici Oynatıcıda Aç", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val streamUrl = firstChannel.url.trim()
                            if (streamUrl.isEmpty()) {
                                Toast.makeText(context, "Kopyalanacak yayın bağlantısı bulunamadı", Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Stream URL", streamUrl))
                                    Toast.makeText(context, "Yayın bağlantısı kopyalandı", Toast.LENGTH_SHORT).show()
                                } catch (_: Throwable) {}
                            }
                            showMoreOptionsSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DetailAvatarSurface,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Akış Bağlantısını Kopyala", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoreOptionsSheet = false }) {
                    Text("Kapat", color = DetailTextZinc400)
                }
            }
        )
    }

    // UYGULAMA İÇİ ÖZEL FRAGMAN MODALI (Hiçbir YouTube arayüzü olmadan Stavio özel oynatıcı)
    selectedTrailerForModal?.let { activeTrailer ->
        StavioInAppTrailerModal(
            trailer = activeTrailer,
            backdropUrl = heroBackdropUrl,
            contentTitle = metadata.cleanTitle.ifBlank { safeVodName },
            onDismiss = { selectedTrailerForModal = null }
        )
    }
}

private class StavioTrailerJsBridge(
    private val mainHandler: Handler,
    private val onStateUpdate: (currentSec: Float, durationSec: Float, playerState: Int) -> Unit,
    private val onEmbedError: (errorCode: Int) -> Unit
) {
    @JavascriptInterface
    fun onPlayerUpdate(currentSec: Double, durationSec: Double, state: Int) {
        val safeCur = if (currentSec.isNaN() || currentSec < 0.0) 0f else currentSec.toFloat()
        val safeDur = if (durationSec.isNaN() || durationSec < 0.0) 0f else durationSec.toFloat()
        mainHandler.post {
            onStateUpdate(safeCur, safeDur, state)
        }
    }

    @JavascriptInterface
    fun onPlayerError(code: Int) {
        mainHandler.post {
            onEmbedError(code)
        }
    }
}

private fun formatTrailerTime(seconds: Float): String {
    val totalSec = seconds.toInt().coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
}

/**
 * STAVIO SIFIRDAN YENİLENMİŞ ÇİFT MOTORLU (EXOPLAYER + CHROMELESS WEBVIEW) ÖZEL FRAGMAN OYNATICISI:
 * - Video katmanının (ExoPlayer / WebView) tam üstünde yer alan özel Compose Dokunma Katmanı (Touch Catcher)
 *   sayesinde ekrana her dokunulduğunda oynatma tuşları %100 kararlılıkla açılır/kapanır; tuşların tamamen kaybolup
 *   geri gelmeme sorunu kesin olarak ortadan kaldırılmıştır.
 * - Video gerçekten oynamaya başlayana kadar (hasStartedPlaying == true) kontrol tuşları asla kendiliğinden gizlenmez.
 * - Oynatma başladıktan sonra kullanıcı 4 saniye dokunmazsa tuşlar zarifçe gizlenir, ekrana tek dokunuşla anında geri gelir.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun StavioCustomChromelessTrailerPlayer(
    trailer: StavioTrailer,
    backdropUrl: String,
    title: String,
    year: String? = null,
    isSeries: Boolean = false,
    initialMuted: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onCloseTrailer: () -> Unit,
    onMuteChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cleanId = remember(trailer.ytId) {
        val raw = trailer.ytId.trim()
        when {
            raw.contains("v=") -> raw.substringAfter("v=").substringBefore("&")
            raw.contains("youtu.be/") -> raw.substringAfter("youtu.be/").substringBefore("?")
            raw.contains("embed/") -> raw.substringAfter("embed/").substringBefore("?")
            else -> raw
        }
    }

    var resolvedDirectUrl by remember(trailer.ytId, trailer.directStreamUrl, title) {
        mutableStateOf(trailer.directStreamUrl.trim())
    }
    var isResolvingDirectStream by remember(trailer.ytId, trailer.directStreamUrl, title) {
        mutableStateOf(trailer.directStreamUrl.isBlank())
    }
    var directStreamFailed by remember(trailer.ytId, trailer.directStreamUrl, title) {
        mutableStateOf(false)
    }
    var webEmbedFailed by remember(cleanId) {
        mutableStateOf(false)
    }

    // Doğrudan MP4/HLS fragman akışını en fazla 2.2 saniye içinde çöz, gecikirse anında yedek motora geç
    LaunchedEffect(trailer, title, year, isSeries) {
        if (resolvedDirectUrl.isBlank()) {
            isResolvingDirectStream = true
            val stream = withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(2200L) {
                    StavioMetadataService.resolveDirectStreamForTrailer(
                        trailer = trailer,
                        cleanTitle = title,
                        year = year,
                        isSeries = isSeries
                    )
                }.orEmpty()
            }
            if (stream.isNotBlank()) {
                resolvedDirectUrl = stream
            }
            isResolvingDirectStream = false
        } else {
            isResolvingDirectStream = false
        }
    }

    // Eğer WebView gömülü oynatıcı hata verirse, anında iTunes doğrudan MP4 akışına geç
    LaunchedEffect(webEmbedFailed) {
        if (webEmbedFailed && resolvedDirectUrl.isBlank()) {
            val itunesFallback = withContext(Dispatchers.IO) {
                StavioMetadataService.fetchItunesDirectTrailer(title, year, isSeries)?.directStreamUrl.orEmpty()
            }
            if (itunesFallback.isNotBlank()) {
                directStreamFailed = false
                resolvedDirectUrl = itunesFallback
            }
        }
    }

    val useNativeExoPlayer = resolvedDirectUrl.isNotBlank() && !directStreamFailed

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var exoPlayerRef by remember { mutableStateOf<ExoPlayer?>(null) }

    var isPlaying by remember(cleanId, resolvedDirectUrl) { mutableStateOf(true) }
    var hasStartedPlaying by remember(cleanId, resolvedDirectUrl) { mutableStateOf(false) }
    var currentSec by remember(cleanId, resolvedDirectUrl) { mutableFloatStateOf(0f) }
    var durationSec by remember(cleanId, resolvedDirectUrl) { mutableFloatStateOf(0f) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragValue by remember { mutableFloatStateOf(0f) }
    var isMuted by remember(cleanId) { mutableStateOf(initialMuted) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val seekRelativeSeconds: (Float) -> Unit = { deltaSec ->
        if (useNativeExoPlayer) {
            val p = exoPlayerRef
            if (p != null) {
                val dur = p.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                val targetMs = (p.currentPosition + (deltaSec * 1000L).toLong()).coerceIn(0L, dur)
                p.seekTo(targetMs)
                p.play()
                currentSec = targetMs / 1000f
            }
        } else {
            webViewRef?.evaluateJavascript("stavioSeekRelative($deltaSec);", null)
        }
        isPlaying = true
        hasStartedPlaying = true
        controlsVisible = true
        interactionTick++
    }

    val togglePlayPause: () -> Unit = {
        if (useNativeExoPlayer) {
            val p = exoPlayerRef
            if (p != null) {
                if (p.isPlaying) {
                    p.pause()
                    isPlaying = false
                } else {
                    p.play()
                    isPlaying = true
                }
            }
        } else {
            webViewRef?.evaluateJavascript("stavioTogglePlay();", null)
            isPlaying = !isPlaying
        }
        controlsVisible = true
        interactionTick++
    }

    // Yerel ExoPlayer ilerleme takipçisi (200ms aralıkla gerçek zamanlı süre ve çubuk güncellemesi)
    LaunchedEffect(useNativeExoPlayer, exoPlayerRef, isDraggingSlider) {
        while (useNativeExoPlayer && exoPlayerRef != null) {
            val player = exoPlayerRef
            if (player != null) {
                val posMs = player.currentPosition.coerceAtLeast(0L)
                val durMs = player.duration
                if (!isDraggingSlider) {
                    currentSec = posMs / 1000f
                }
                if (durMs > 0L) {
                    durationSec = durMs / 1000f
                }
                if (player.isPlaying && posMs > 100L) {
                    hasStartedPlaying = true
                }
                isPlaying = player.isPlaying
            }
            delay(200L)
        }
    }

    // SADECE video gerçekten oynamaya başladıktan sonra (hasStartedPlaying == true) 4 saniye dokunulmazsa tuşları gizle;
    // duraklatıldığında veya yüklenirken tuşlar her zaman ekranda kalır!
    LaunchedEffect(controlsVisible, isPlaying, isDraggingSlider, hasStartedPlaying, isResolvingDirectStream, interactionTick) {
        if (!hasStartedPlaying || isResolvingDirectStream || !isPlaying || isDraggingSlider) {
            controlsVisible = true
        } else if (controlsVisible) {
            delay(4000L)
            if (hasStartedPlaying && isPlaying && !isDraggingSlider && !isResolvingDirectStream) {
                controlsVisible = false
            }
        }
    }

    val chromelessHtml = remember(cleanId, initialMuted) {
        val wantUnmute = if (initialMuted) 0 else 1
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; background: #050507; user-select: none; -webkit-user-select: none; }
                html, body {
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: #050507;
                    pointer-events: none;
                }
                .viewport {
                    position: relative;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: #050507;
                    pointer-events: none;
                }
                /* Videoyu tam merkezde tutarken YouTube üst/alt barlarını 58px dışarı kırpar */
                #player, iframe {
                    position: absolute;
                    top: -58px;
                    left: 0;
                    width: 100%;
                    height: calc(100% + 116px);
                    border: 0;
                    display: block;
                    pointer-events: none !important;
                }
            </style>
        </head>
        <body>
            <div class="viewport">
                <div id="player"></div>
            </div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
                var player;
                var timerId = null;
                var unmutedOnce = false;
                var wantUnmute = $wantUnmute;

                function notifyAndroid(stateOverride) {
                    if (!player || typeof player.getCurrentTime !== 'function') return;
                    try {
                        var cur = player.getCurrentTime() || 0;
                        var dur = player.getDuration() || 0;
                        var st = (typeof stateOverride === 'number') ? stateOverride : (player.getPlayerState ? player.getPlayerState() : -1);
                        if (window.StavioBridge && window.StavioBridge.onPlayerUpdate) {
                            window.StavioBridge.onPlayerUpdate(cur, dur, st);
                        }
                    } catch (e) {}
                }

                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        width: '100%',
                        height: '100%',
                        videoId: '$cleanId',
                        host: 'https://www.youtube-nocookie.com',
                        playerVars: {
                            'autoplay': 1,
                            'controls': 0,
                            'disablekb': 1,
                            'fs': 0,
                            'iv_load_policy': 3,
                            'modestbranding': 1,
                            'playsinline': 1,
                            'rel': 0,
                            'showinfo': 0,
                            'mute': 1,
                            'origin': 'https://www.youtube-nocookie.com'
                        },
                        events: {
                            'onReady': function(event) {
                                try {
                                    event.target.playVideo();
                                    if (wantUnmute === 1) {
                                        event.target.unMute();
                                        event.target.setVolume(100);
                                    }
                                } catch(e) {}
                                if (timerId) clearInterval(timerId);
                                timerId = setInterval(function() { notifyAndroid(); }, 250);
                            },
                            'onStateChange': function(event) {
                                if (event.data === 1 && wantUnmute === 1 && !unmutedOnce) {
                                    unmutedOnce = true;
                                    try {
                                        player.unMute();
                                        player.setVolume(100);
                                    } catch (e) {}
                                }
                                if (event.data === 0) {
                                    try {
                                        player.seekTo(0, true);
                                        player.pauseVideo();
                                    } catch (e) {}
                                }
                                notifyAndroid(event.data);
                            },
                            'onError': function(event) {
                                if (window.StavioBridge && window.StavioBridge.onPlayerError) {
                                    window.StavioBridge.onPlayerError(event.data || -1);
                                }
                            }
                        }
                    });
                }

                function stavioTogglePlay() {
                    if (!player) return;
                    try {
                        var st = player.getPlayerState();
                        if (st === 1) {
                            player.pauseVideo();
                            notifyAndroid(2);
                        } else {
                            player.playVideo();
                            notifyAndroid(1);
                        }
                    } catch(e) {}
                }

                function stavioSeekRelative(deltaSec) {
                    if (!player || typeof player.getCurrentTime !== 'function') return;
                    try {
                        var cur = player.getCurrentTime() || 0;
                        var dur = player.getDuration() || 0;
                        var target = Math.max(0, cur + deltaSec);
                        if (dur > 0) target = Math.min(dur - 0.5, target);
                        player.seekTo(target, true);
                        player.playVideo();
                        notifyAndroid(1);
                    } catch(e) {}
                }

                function stavioSeekTo(sec) {
                    if (!player) return;
                    try {
                        player.seekTo(Math.max(0, sec), true);
                        player.playVideo();
                        notifyAndroid(1);
                    } catch(e) {}
                }

                function stavioSetMute(mute) {
                    wantUnmute = mute ? 0 : 1;
                    if (!player) return;
                    try {
                        if (mute) player.mute();
                        else {
                            player.unMute();
                            player.setVolume(100);
                        }
                    } catch(e) {}
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    DisposableEffect(cleanId, resolvedDirectUrl) {
        onDispose {
            try {
                exoPlayerRef?.release()
                exoPlayerRef = null
            } catch (_: Throwable) {}
            try {
                webViewRef?.stopLoading()
                webViewRef?.loadUrl("about:blank")
                webViewRef?.onPause()
                webViewRef?.destroy()
                webViewRef = null
            } catch (_: Throwable) {}
        }
    }

    Box(
        modifier = modifier.background(DetailBgBlack)
    ) {
        if (useNativeExoPlayer) {
            // 1A. BİRİNCİL MOTOR: YEREL EXOPLAYER (Sıfır YouTube UI, Donanım Hızlandırmalı Doğrudan MP4/HLS Fragman)
            DisposableEffect(resolvedDirectUrl) {
                val player = ExoPlayer.Builder(context).build().apply {
                    val mediaItem = MediaItem.fromUri(Uri.parse(resolvedDirectUrl))
                    setMediaItem(mediaItem)
                    volume = if (isMuted) 0f else 1f
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                hasStartedPlaying = true
                                isPlaying = playWhenReady
                                if (duration > 0L) {
                                    durationSec = duration / 1000f
                                }
                            } else if (playbackState == Player.STATE_ENDED) {
                                seekTo(0L)
                                pause()
                                isPlaying = false
                                controlsVisible = true
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                            if (playing) {
                                hasStartedPlaying = true
                            } else {
                                controlsVisible = true
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            if (cleanId.isNotBlank()) {
                                directStreamFailed = true
                            }
                        }
                    })
                    prepare()
                }
                exoPlayerRef = player
                onDispose {
                    try {
                        player.release()
                    } catch (_: Throwable) {}
                    if (exoPlayerRef === player) {
                        exoPlayerRef = null
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        isClickable = false
                        isFocusable = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setBackgroundColor(android.graphics.Color.parseColor("#050507"))
                        player = exoPlayerRef
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayerRef) {
                        view.player = exoPlayerRef
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (!isResolvingDirectStream && cleanId.isNotBlank() && !webEmbedFailed) {
            // 1B. YEDEK MOTOR: CHROMELESS YOUTUBE-NOCOOKIE WEBVIEW MOTORU
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.parseColor("#050507"))
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        isClickable = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadsImagesAutomatically = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        }
                        addJavascriptInterface(
                            StavioTrailerJsBridge(
                                mainHandler = mainHandler,
                                onStateUpdate = { cur, dur, state ->
                                    if (!isDraggingSlider) {
                                        currentSec = cur
                                    }
                                    if (dur > 0f) {
                                        durationSec = dur
                                    }
                                    when (state) {
                                        1 -> {
                                            isPlaying = true
                                            hasStartedPlaying = true
                                        }
                                        2, 0 -> {
                                            isPlaying = false
                                            controlsVisible = true
                                        }
                                        else -> {
                                            if (cur > 0.1f) {
                                                isPlaying = true
                                                hasStartedPlaying = true
                                            }
                                        }
                                    }
                                },
                                onEmbedError = {
                                    webEmbedFailed = true
                                }
                            ),
                            "StavioBridge"
                        )
                        webChromeClient = object : WebChromeClient() {
                            override fun getDefaultVideoPoster(): Bitmap {
                                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return request?.isForMainFrame == true
                            }
                        }
                        loadDataWithBaseURL(
                            "https://www.youtube-nocookie.com",
                            chromelessHtml,
                            "text/html",
                            "UTF-8",
                            null
                        )
                        webViewRef = this
                    }
                },
                update = { view ->
                    webViewRef = view
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. İlk yükleme anında (video henüz ilk karesini çizmeden önce) hafif poster geçişi
        if ((!hasStartedPlaying || isResolvingDirectStream) && backdropUrl.isNotBlank()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (isResolvingDirectStream) 0.55f else 0.40f,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Alt Geçiş Vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = if (controlsVisible) 0.38f else 0.05f),
                            0.45f to Color.Transparent,
                            0.82f to Color.Black.copy(alpha = if (controlsVisible) 0.48f else 0.20f),
                            1.0f to DetailBgBlack
                        )
                    )
                )
        )

        // 2.5. ÖZEL COMPOSE DOKUNMA YAKALAYICI KATMAN (TOUCH CATCHER OVERLAY):
        // AndroidView (WebView / PlayerView) dokunmaları yutmasın diye tam üstünde yer alır.
        // Ekrana tek dokunuşta kontrolleri açar/kapatır, sol/sağ çift dokunuşta -10sn/+10sn sarar!
        var touchAreaWidthPx by remember { mutableFloatStateOf(1f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { touchAreaWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(useNativeExoPlayer) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            interactionTick++
                        },
                        onDoubleTap = { offset ->
                            if (offset.x < touchAreaWidthPx * 0.45f) {
                                seekRelativeSeconds(-10f)
                            } else if (offset.x > touchAreaWidthPx * 0.55f) {
                                seekRelativeSeconds(10f)
                            } else {
                                togglePlayPause()
                            }
                        }
                    )
                }
        )

        // 3. MİNİMALİST iPHONE / iOS 18 TARZI ÖZEL FRAGMAN KONTROLLERİ
        AnimatedVisibility(
            visible = controlsVisible || !hasStartedPlaying || !isPlaying || isResolvingDirectStream,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Üst Bar: Minimalist iOS Buzlu Cam Butonlar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (onBackClick != null) {
                            Surface(
                                onClick = onBackClick,
                                shape = CircleShape,
                                color = Color(0x8814141C),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.20f)),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Geri",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0x8814141C),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFFFF3B30), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = title,
                                    color = Color.White.copy(alpha = 0.96f),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 170.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Ses Aç / Kapat
                        Surface(
                            onClick = {
                                val nextMute = !isMuted
                                isMuted = nextMute
                                onMuteChanged?.invoke(nextMute)
                                if (useNativeExoPlayer) {
                                    exoPlayerRef?.volume = if (nextMute) 0f else 1f
                                } else {
                                    webViewRef?.evaluateJavascript("stavioSetMute($nextMute);", null)
                                }
                                controlsVisible = true
                                interactionTick++
                            },
                            shape = CircleShape,
                            color = Color(0x8814141C),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.20f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "Ses",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Fragmanı Kapat
                        Surface(
                            onClick = onCloseTrailer,
                            shape = CircleShape,
                            color = Color(0x8814141C),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.20f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Fragmanı Kapat",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Orta Kontrol Satırı: Minimalist iOS Buzlu Cam (-10sn | Oynat/Duraklat | +10sn)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 10 Saniye Geri
                    Surface(
                        onClick = { seekRelativeSeconds(-10f) },
                        shape = CircleShape,
                        color = Color(0x8014141C),
                        border = BorderStroke(0.9.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Replay10,
                                contentDescription = "10 Saniye Geri",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Başlat / Durdur (Minimalist iOS Koyu Buzlu Cam Daire)
                    Surface(
                        onClick = { togglePlayPause() },
                        shape = CircleShape,
                        color = Color(0x94181822),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if ((!hasStartedPlaying || isResolvingDirectStream) && isPlaying) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Durdur" else "Başlat",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // 10 Saniye İleri
                    Surface(
                        onClick = { seekRelativeSeconds(10f) },
                        shape = CircleShape,
                        color = Color(0x8014141C),
                        border = BorderStroke(0.9.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Forward10,
                                contentDescription = "10 Saniye İleri",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Alt iPhone / iOS 18 Minimalist Oynatma Çubuğu (İnce Kapsül Scrubber Bar)
                val effectiveDuration = durationSec.coerceAtLeast(1f)
                val displaySec = if (isDraggingSlider) sliderDragValue else currentSec.coerceIn(0f, effectiveDuration)
                val progressFraction = (displaySec / effectiveDuration).coerceIn(0f, 1f)
                val remainingSec = (effectiveDuration - displaySec).coerceAtLeast(0f)

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0x8C101018),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = formatTrailerTime(displaySec),
                            color = Color.White.copy(alpha = 0.94f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        var trackWidthPx by remember { mutableFloatStateOf(1f) }
                        val animatedTrackHeight by animateDpAsState(
                            targetValue = if (isDraggingSlider) 6.dp else 3.5.dp,
                            animationSpec = tween(150),
                            label = "ios_track_height"
                        )
                        val animatedThumbSize by animateDpAsState(
                            targetValue = if (isDraggingSlider) 11.dp else 7.dp,
                            animationSpec = tween(150),
                            label = "ios_thumb_size"
                        )

                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                                .pointerInput(effectiveDuration, useNativeExoPlayer) {
                                    detectTapGestures { offset ->
                                        val frac = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                                        val targetSec = frac * effectiveDuration
                                        currentSec = targetSec
                                        isPlaying = true
                                        hasStartedPlaying = true
                                        controlsVisible = true
                                        if (useNativeExoPlayer) {
                                            exoPlayerRef?.seekTo((targetSec * 1000f).toLong())
                                            exoPlayerRef?.play()
                                        } else {
                                            webViewRef?.evaluateJavascript("stavioSeekTo($targetSec);", null)
                                        }
                                        interactionTick++
                                    }
                                }
                                .pointerInput(effectiveDuration, useNativeExoPlayer) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { offset ->
                                            isDraggingSlider = true
                                            controlsVisible = true
                                            val frac = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                                            sliderDragValue = frac * effectiveDuration
                                            interactionTick++
                                        },
                                        onHorizontalDrag = { change, _ ->
                                            change.consume()
                                            val frac = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                                            sliderDragValue = frac * effectiveDuration
                                            interactionTick++
                                        },
                                        onDragEnd = {
                                            val targetSec = sliderDragValue
                                            currentSec = targetSec
                                            isDraggingSlider = false
                                            isPlaying = true
                                            hasStartedPlaying = true
                                            controlsVisible = true
                                            if (useNativeExoPlayer) {
                                                exoPlayerRef?.seekTo((targetSec * 1000f).toLong())
                                                exoPlayerRef?.play()
                                            } else {
                                                webViewRef?.evaluateJavascript("stavioSeekTo($targetSec);", null)
                                            }
                                            interactionTick++
                                        },
                                        onDragCancel = {
                                            isDraggingSlider = false
                                            interactionTick++
                                        }
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedTrackHeight)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.24f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progressFraction)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.White)
                                )
                            }

                            val density = LocalDensity.current
                            val thumbOffsetDp = with(density) {
                                ((trackWidthPx * progressFraction).toDp() - (animatedThumbSize / 2)).coerceAtLeast(0.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .offset(x = thumbOffsetDp)
                                    .size(animatedThumbSize)
                                    .background(Color.White, CircleShape)
                            )
                        }

                        Text(
                            text = if (durationSec > 0f) "-${formatTrailerTime(remainingSec)}" else "00:00",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StavioInAppTrailerModal(
    trailer: StavioTrailer,
    backdropUrl: String,
    contentTitle: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEB050507))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 14.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF514141C),
                border = BorderStroke(1.dp, Color(0x2EFFFFFF)),
                shadowElevation = 28.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .aspectRatio(16f / 10f)
                    .clickable(enabled = false) {}
            ) {
                StavioCustomChromelessTrailerPlayer(
                    trailer = trailer,
                    backdropUrl = backdropUrl,
                    title = trailer.title.takeIf { it.isNotBlank() && it != "Fragman" } ?: contentTitle,
                    initialMuted = false,
                    onBackClick = null,
                    onCloseTrailer = onDismiss,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 4. "Oyuncular" Bölümü Avatar Öğesi:
 * - 64x64px (w-16 h-16) koyu gri daire (#1C1C22)
 * - Fotoğraf varsa daire içinde gösterir, yoksa veya yüklenemezse ad-soyad baş harflerini (TH, ZE, EC, NA) ortada gösterir.
 * - Altında oyuncunun tam adı (text-[11px] text-zinc-300 text-center w-20 truncate mt-1.5)
 */
@Composable
private fun StavioActorAvatarItem(actor: StavioActor) {
    var photoFailed by remember(actor.photoUrl) { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(DetailAvatarSurface, CircleShape)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = actor.initials,
                color = DetailTextZinc400,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            if (!actor.photoUrl.isNullOrBlank() && !photoFailed) {
                AsyncImage(
                    model = actor.photoUrl,
                    contentDescription = actor.name,
                    contentScale = ContentScale.Crop,
                    onError = { photoFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = actor.name.ifBlank { "Oyuncu" },
            color = DetailTextZinc300,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 5. "Fragmanlar" Bölümü Kartı:
 * - w-60 (240.dp) aspect-video (16:9) rounded-xl (12.dp) overflow-hidden bg-zinc-900
 * - img.youtube.com/vi/{ytId}/hqdefault.jpg
 * - Sol altında küçük "Fragman" (text-xs text-zinc-300 mt-1.5) etiketi
 */
@Composable
private fun StavioTrailerCard(
    trailer: StavioTrailer,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF18181B),
            border = BorderStroke(1.dp, DetailBorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (trailer.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = trailer.thumbnailUrl,
                        contentDescription = trailer.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                )
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Fragmanı Oynat",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = trailer.title.ifBlank { "Fragman" },
            color = DetailTextZinc300,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 6. Yatay Sinematik Bölüm Kartı (w-72 / 288.dp, aspect-[16/10], rounded-2xl / 16.dp):
 * - Arka planda bölümün sahne fotoğrafı (thumbnail) + bg-gradient-to-t from-black/95 via-black/60 to-transparent
 * - Sol altta üst üste:
 *   1. Küçük koyu rozet: "S1 B1"
 *   2. Kalın beyaz bölüm başlığı: ör. "Winter Is Coming"
 *   3. 2 satırlık bölüm özeti
 *   4. En alt satır: Solda sarı IMDb rozeti + bölüm puanı (8.9), sağda bölüm yayın tarihi (18 Nisan 2011)
 */
@Composable
private fun StavioEpisodeCard(
    episodeItem: ResolvedSeriesEpisode,
    fallbackBackdrop: String,
    onClick: () -> Unit
) {
    val meta = episodeItem.meta
    val thumbUrl = remember(meta, episodeItem.channel, fallbackBackdrop) {
        meta?.thumbnail?.takeIf { it.isNotBlank() }
            ?: episodeItem.channel?.logoUrl?.takeIf { it.isNotBlank() }
            ?: fallbackBackdrop
    }

    val cleanEpTitle = remember(meta, episodeItem) {
        if (!meta?.title.isNullOrBlank()) {
            meta!!.title
        } else {
            val cleaned = StavioMetadataService.cleanMediaTitle(episodeItem.fallbackTitle).cleanTitle
            cleaned.ifBlank { "${episodeItem.episodeNumber}. Bölüm" }
        }
    }

    val epOverview = remember(meta) {
        meta?.overview?.takeIf { it.isNotBlank() } ?: ""
    }

    val epRating = remember(meta) {
        meta?.imdbRating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" } ?: ""
    }

    val epReleased = remember(meta) {
        meta?.releasedFormatted?.takeIf { it.isNotBlank() } ?: ""
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = DetailCardSurface,
        border = BorderStroke(1.dp, DetailBorderSubtle),
        modifier = Modifier
            .width(288.dp)
            .aspectRatio(16f / 10f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbUrl.isNotBlank()) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = cleanEpTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // bg-gradient-to-t from-black/95 via-black/60 to-transparent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.38f to Color.Black.copy(alpha = 0.60f),
                                1.0f to Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Sol Altta Üst Üste Bilgiler
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // 1. Küçük koyu rozet içinde sezon/bölüm kodu: S1 B1
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "S${episodeItem.season} B${episodeItem.episodeNumber}",
                        color = DetailTextZinc200,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Kalın beyaz bölüm başlığı
                Text(
                    text = cleanEpTitle,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 3. 2 satırlık bölüm özeti
                if (epOverview.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = epOverview,
                        color = DetailTextZinc300,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 4. En alt satır: Solda sarı IMDb rozeti + bölüm puanı, sağda yayın tarihi
                if (epRating.isNotEmpty() || epReleased.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (epRating.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(DetailImdbYellow, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "IMDb",
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Text(
                                    text = epRating,
                                    color = DetailImdbYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (epReleased.isNotEmpty()) {
                            Text(
                                text = epReleased,
                                color = DetailTextZinc400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 7. En Alt Bilgi Tablosu Satırı (border-b border-white/10 py-3 flex items-center justify-between text-xs)
 */
@Composable
private fun StavioDetailTableRow(
    label: String,
    value: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = DetailTextZinc400,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = value,
                color = DetailTextZinc200,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(0.65f)
            )
        }
        HorizontalDivider(
            color = DetailBorderSubtle,
            thickness = 1.dp
        )
    }
}

