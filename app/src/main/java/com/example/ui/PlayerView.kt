package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import kotlin.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import com.example.ui.theme.StavioFontFamily
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import com.example.data.ChannelEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// Navigation / Modal Tabs for Stavio Video Player
enum class StavioModalType {
    NONE,
    AUDIO,
    SUBTITLES,
    CHANNELS
}

enum class SubtitleTab {
    TRACKS,
    STYLE
}

@OptIn(UnstableApi::class)
@Composable
fun IptvVideoPlayer(
    channelUrl: String,
    title: String,
    subtitle: String,
    videoScaleMode: String,
    onClose: () -> Unit,
    onChangeScaleMode: () -> Unit,
    modifier: Modifier = Modifier,
    channelLogoUrl: String = "",
    isFavorite: Boolean = false,
    relatedChannels: List<ChannelEntity> = emptyList(),
    currentChannelId: Int = -1,
    initialPositionMs: Long = 0L,
    onProgressUpdate: (Long, Long) -> Unit = { _, _ -> },
    onSelectChannel: ((ChannelEntity) -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
    onPlayerError: (String) -> Unit,
    onVideoPlayingStateChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val playerPrefs = remember { context.getSharedPreferences("stavio_player_prefs", Context.MODE_PRIVATE) }
    val activity = context.findActivity()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    // Enforce Landscape Orientation during playback
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }

    var isBuffering by remember { mutableStateOf(true) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var showUi by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var localFavorite by remember(channelUrl, isFavorite) { mutableStateOf(isFavorite) }
    var showStreamStats by remember { mutableStateOf(false) }
    var scaleToastText by remember { mutableStateOf<String?>(null) }

    // Live Clock for Pro OSD Header
    var currentClockText by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            currentClockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(15000L)
        }
    }

    LaunchedEffect(scaleToastText) {
        if (scaleToastText != null) {
            delay(1300L)
            scaleToastText = null
        }
    }

    // Stavio Cinema Modals (Audio / Subtitles / Quick Channel Switcher)
    var activeModal by remember { mutableStateOf(StavioModalType.NONE) }
    var subtitleTab by remember { mutableStateOf(SubtitleTab.TRACKS) }

    // Subtitle Customization State (persisted in SharedPreferences so user choices are remembered across sessions)
    var subtitleFontSize by remember {
        mutableIntStateOf(playerPrefs.getInt("stavio_sub_font_size", 24))
    }
    var subtitleColor by remember {
        val savedColor = playerPrefs.getInt("stavio_sub_color_argb", android.graphics.Color.WHITE)
        mutableStateOf(Color(savedColor))
    }
    var subtitleBgType by remember {
        mutableStateOf(playerPrefs.getString("stavio_sub_bg_type", "none") ?: "none")
    }
    var subtitleEdgeType by remember {
        mutableStateOf(playerPrefs.getString("stavio_sub_edge_type", "shadow") ?: "shadow")
    }
    var subtitleIsBold by remember {
        mutableStateOf(playerPrefs.getBoolean("stavio_sub_is_bold", true))
    }
    var subtitleDelayMs by remember {
        mutableIntStateOf(playerPrefs.getInt("stavio_sub_delay_ms", 0))
    }
    var subtitleOffsetX by remember {
        mutableFloatStateOf(playerPrefs.getFloat("stavio_sub_offset_x", 0f))
    }
    var subtitleOffsetY by remember {
        mutableFloatStateOf(playerPrefs.getFloat("stavio_sub_offset_y", 0f))
    }

    // Subtitle Cues from ExoPlayer
    var currentCues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var isLive by remember { mutableStateOf(false) }
    var playbackSpeed by remember {
        mutableFloatStateOf(playerPrefs.getFloat("stavio_playback_speed", 1.0f))
    }
    var showRemainingTime by remember { mutableStateOf(false) }

    // YouTube-style Double Tap Seek States
    var rewindAnimationVisible by remember { mutableStateOf(false) }
    var rewindCountSeconds by remember { mutableIntStateOf(10) }
    var lastRewindTapTime by remember { mutableLongStateOf(0L) }
    var forwardAnimationVisible by remember { mutableStateOf(false) }
    var forwardCountSeconds by remember { mutableIntStateOf(10) }
    var lastForwardTapTime by remember { mutableLongStateOf(0L) }

    // Gesture feedback: Brightness & Volume HUD
    var brightnessHudVisible by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(0.7f) }
    var volumeHudVisible by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.7f) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val devManager = remember { com.example.util.DeveloperOptionsManager.getInstance(context) }
    val showFpsOverlay by devManager.showFpsOverlay.collectAsState()
    val hwAccel = devManager.hwAccel.value
    val bufferMode = devManager.bufferMode.value
    val trackSelector = remember { DefaultTrackSelector(context) }
    var streamReloadKey by remember { mutableIntStateOf(0) }

    val player = remember(channelUrl, streamReloadKey) {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            when (hwAccel) {
                com.example.util.HwAccelMode.HARDWARE_ONLY -> {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                    setEnableDecoderFallback(false)
                }
                com.example.util.HwAccelMode.SOFTWARE_FALLBACK -> {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                    setEnableDecoderFallback(true)
                }
                com.example.util.HwAccelMode.AUTO -> {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    setEnableDecoderFallback(true)
                }
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferMode.minBufferMs,
                bufferMode.maxBufferMs,
                bufferMode.playbackBufferMs,
                bufferMode.playbackBufferMs
            )
            .build()

        // Esnek SSL yapılandırması (Özel CDN ve IPTV sunucularındaki sertifika hatalarını önler)
        runCatching {
            val trustAll = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(c: Array<out java.security.cert.X509Certificate>?, a: String?) {}
                    override fun checkServerTrusted(c: Array<out java.security.cert.X509Certificate>?, a: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                }
            )
            val sslCtx = javax.net.ssl.SSLContext.getInstance("TLS").apply {
                init(null, trustAll, java.security.SecureRandom())
            }
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslCtx.socketFactory)
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        }

        val cleanStreamUrl = channelUrl.substringBefore("|").trim()
        val pipeHeaderPart = channelUrl.substringAfter("|", "").trim()
        val customRequestHeaders = mutableMapOf<String, String>()
        var customUserAgent: String? = null
        var explicitStreamFormat: String? = null

        if (pipeHeaderPart.isNotEmpty()) {
            pipeHeaderPart.split("&").forEach { pair ->
                val key = pair.substringBefore("=", "").trim()
                val rawValue = pair.substringAfter("=", "").trim()
                val value = runCatching { java.net.URLDecoder.decode(rawValue, "UTF-8") }.getOrDefault(rawValue)
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    when {
                        key.equals("User-Agent", ignoreCase = true) -> customUserAgent = value
                        key.equals("StreamFormat", ignoreCase = true) -> explicitStreamFormat = value.uppercase()
                        else -> customRequestHeaders[key] = value
                    }
                }
            }
        }

        // Eğer Cookie başlığı yoksa WebView CookieManager'dan mevcut çerezleri otomatik ekle
        if (!customRequestHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) {
            val webCookie = runCatching {
                android.webkit.CookieManager.getInstance().getCookie(cleanStreamUrl)
                    ?: customRequestHeaders["Referer"]?.let { android.webkit.CookieManager.getInstance().getCookie(it) }
            }.getOrNull()
            if (!webCookie.isNullOrBlank()) {
                customRequestHeaders["Cookie"] = webCookie
            }
        }
        customRequestHeaders.putIfAbsent("Accept", "*/*")

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(customUserAgent ?: devManager.getEffectiveUserAgent())
            .setConnectTimeoutMs(devManager.networkTimeoutSeconds.value * 1000)
            .setReadTimeoutMs(devManager.networkTimeoutSeconds.value * 1000)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                if (customRequestHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(customRequestHeaders)
                }
            }

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            httpDataSourceFactory
        )

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)

        devManager.log("Stavio Player başlatılıyor: $cleanStreamUrl")

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setTrackSelector(trackSelector)
            .build().apply {
                val lowerUrl = cleanStreamUrl.lowercase()
                val pathOnly = lowerUrl.substringBefore("?")
                val isLocalUri = lowerUrl.startsWith("content://") || lowerUrl.startsWith("file://")
                val mediaItem = MediaItem.Builder()
                    .setUri(cleanStreamUrl)
                    .apply {
                        when {
                            explicitStreamFormat == "M3U8" || pathOnly.endsWith(".m3u8") || lowerUrl.contains(".m3u8?") || lowerUrl.contains("m3u_plus") || lowerUrl.contains("output=m3u8") -> {
                                setMimeType(MimeTypes.APPLICATION_M3U8)
                            }
                            explicitStreamFormat == "MPD" || pathOnly.endsWith(".mpd") || lowerUrl.contains(".mpd?") || lowerUrl.contains("output=mpd") -> {
                                setMimeType(MimeTypes.APPLICATION_MPD)
                            }
                            explicitStreamFormat == "MP4" || pathOnly.endsWith(".mp4") -> {
                                setMimeType(MimeTypes.VIDEO_MP4)
                            }
                            !isLocalUri && (explicitStreamFormat == "TS" || pathOnly.endsWith(".ts") || lowerUrl.contains("output=ts")) -> {
                                setMimeType(MimeTypes.VIDEO_MP2T)
                            }
                        }
                    }
                    .build()
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPos by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPos by remember { mutableLongStateOf(0L) }
    var hasRestoredInitialPosition by remember(channelUrl) { mutableStateOf(false) }
    var hasAppliedSavedTrackPrefs by remember(channelUrl) { mutableStateOf(false) }

    LaunchedEffect(player, isDraggingSlider) {
        var tickCounter = 0
        while (true) {
            isLive = player.isCurrentWindowLive
            if (!isDraggingSlider) {
                currentPos = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
                bufferedPos = player.bufferedPosition.coerceAtLeast(0L)
            }
            tickCounter++
            // Her 3 saniyede bir kaldığımız dakikayı ve ilerlemeyi kaydet
            if (tickCounter % 30 == 0 && !isLive && currentPos >= 3000L) {
                onProgressUpdate(currentPos, duration)
            }
            delay(100L)
        }
    }

    val animatedPos by animateFloatAsState(
        targetValue = if (isDraggingSlider) dragPos.toFloat() else currentPos.toFloat(),
        animationSpec = tween(durationMillis = 100),
        label = "stavio_progress"
    )

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    streamError = null
                    if (playbackSpeed != 1.0f) {
                        player.setPlaybackSpeed(playbackSpeed)
                    }
                    if (!hasRestoredInitialPosition && initialPositionMs > 3000L && !player.isCurrentWindowLive) {
                        hasRestoredInitialPosition = true
                        val dur = player.duration
                        val targetPos = if (dur > 0L) {
                            initialPositionMs.coerceAtMost((dur - 5000L).coerceAtLeast(0L))
                        } else {
                            initialPositionMs
                        }
                        if (targetPos > 2000L) {
                            player.seekTo(targetPos)
                            currentPos = targetPos
                            val resumeMin = (targetPos / 60000L).toInt()
                            scaleToastText = if (resumeMin >= 1) {
                                "Kaldığınız yerden devam ediliyor • ${resumeMin}. dk"
                            } else {
                                "Kaldığınız yerden devam ediliyor"
                            }
                        }
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (!hasAppliedSavedTrackPrefs && tracks.groups.isNotEmpty()) {
                    hasAppliedSavedTrackPrefs = true
                    try {
                        val savedAudioLang = playerPrefs.getString("stavio_pref_audio_lang", null)
                        val savedAudioLabel = playerPrefs.getString("stavio_pref_audio_label", null)
                        val savedSubEnabled = playerPrefs.getBoolean("stavio_pref_sub_enabled", true)
                        val savedSubLang = playerPrefs.getString("stavio_pref_sub_lang", null)
                        val savedSubLabel = playerPrefs.getString("stavio_pref_sub_label", null)

                        var builder = player.trackSelectionParameters.buildUpon()
                        var changed = false

                        // Kayıtlı ses dilini geri yükle
                        if (!savedAudioLang.isNullOrBlank() || !savedAudioLabel.isNullOrBlank()) {
                            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                            outerAudio@ for (group in audioGroups) {
                                for (i in 0 until group.length) {
                                    val fmt = group.getTrackFormat(i)
                                    val langMatch = !savedAudioLang.isNullOrBlank() && fmt.language.equals(savedAudioLang, ignoreCase = true)
                                    val labelMatch = !savedAudioLabel.isNullOrBlank() && (fmt.label.equals(savedAudioLabel, ignoreCase = true) || fmt.language?.uppercase().equals(savedAudioLabel, ignoreCase = true))
                                    if (langMatch || labelMatch) {
                                        builder = builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                        changed = true
                                        break@outerAudio
                                    }
                                }
                            }
                        }

                        // Kayıtlı altyazı durumunu ve dilini geri yükle
                        if (!savedSubEnabled) {
                            builder = builder
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            changed = true
                        } else if (!savedSubLang.isNullOrBlank() || !savedSubLabel.isNullOrBlank()) {
                            val subGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                            outerSub@ for (group in subGroups) {
                                for (i in 0 until group.length) {
                                    val fmt = group.getTrackFormat(i)
                                    val langMatch = !savedSubLang.isNullOrBlank() && fmt.language.equals(savedSubLang, ignoreCase = true)
                                    val labelMatch = !savedSubLabel.isNullOrBlank() && (fmt.label.equals(savedSubLabel, ignoreCase = true) || fmt.language?.uppercase().equals(savedSubLabel, ignoreCase = true))
                                    if (langMatch || labelMatch) {
                                        builder = builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                        changed = true
                                        break@outerSub
                                    }
                                }
                            }
                        }

                        if (changed) {
                            player.trackSelectionParameters = builder.build()
                        }
                    } catch (_: Throwable) {}
                }
            }

            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
                onVideoPlayingStateChanged(isPlayingState)
                if (!isPlayingState && !player.isCurrentWindowLive && player.currentPosition >= 2000L) {
                    onProgressUpdate(player.currentPosition, player.duration.coerceAtLeast(0L))
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                currentCues = cueGroup.cues
            }

            var fallbackAttempt by mutableIntStateOf(0)

            override fun onPlayerError(error: PlaybackException) {
                val cleanUri = channelUrl.substringBefore("|").trim()
                if (fallbackAttempt == 0) {
                    fallbackAttempt = 1
                    val currentMime = player.currentMediaItem?.localConfiguration?.mimeType
                    val fallbackMime = if (currentMime == MimeTypes.APPLICATION_M3U8) {
                        null // Progressive (MP4 / MKV / TS) otomatik algılamayı dene
                    } else {
                        MimeTypes.APPLICATION_M3U8 // Gizli HLS (.txt / uzantısız) olarak dene
                    }
                    runCatching {
                        val retryItem = MediaItem.Builder()
                            .setUri(cleanUri)
                            .apply {
                                if (fallbackMime != null) setMimeType(fallbackMime)
                            }
                            .build()
                        player.setMediaItem(retryItem)
                        player.prepare()
                        player.playWhenReady = true
                        return
                    }
                } else if (fallbackAttempt == 1) {
                    fallbackAttempt = 2
                    runCatching {
                        val retryTsItem = MediaItem.Builder()
                            .setUri(cleanUri)
                            .setMimeType(MimeTypes.VIDEO_MP2T)
                            .build()
                        player.setMediaItem(retryTsItem)
                        player.prepare()
                        player.playWhenReady = true
                        return
                    }
                }

                val userFriendlyMessage = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Bağlantı Hatası: Yayına ulaşılamadı. Yeniden bağlanmayı deneyin."
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        "Kod Çözücü Hatası: Format desteklenmiyor."
                    else -> "Yayın Yüklenemedi veya Çevrimdışı."
                }
                streamError = userFriendlyMessage
                onVideoPlayingStateChanged(false)
                onPlayerError(userFriendlyMessage)
            }
        }
        player.addListener(listener)
        onDispose {
            try {
                if (!player.isCurrentWindowLive && player.currentPosition >= 2000L) {
                    onProgressUpdate(player.currentPosition, player.duration.coerceAtLeast(0L))
                }
            } catch (_: Throwable) {}
            onVideoPlayingStateChanged(false)
            player.removeListener(listener)
            player.stop()
            player.release()
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showUi, isPlaying, isDraggingSlider, activeModal) {
        if (showUi && isPlaying && !isDraggingSlider && activeModal == StavioModalType.NONE && !isLocked) {
            delay(4800)
            showUi = false
        }
    }

    // Hide seek animations after delay
    LaunchedEffect(rewindAnimationVisible) {
        if (rewindAnimationVisible) {
            delay(750)
            rewindAnimationVisible = false
        }
    }
    LaunchedEffect(forwardAnimationVisible) {
        if (forwardAnimationVisible) {
            delay(750)
            forwardAnimationVisible = false
        }
    }

    // Hide brightness and volume HUD after delay
    LaunchedEffect(brightnessHudVisible) {
        if (brightnessHudVisible) {
            delay(1200)
            brightnessHudVisible = false
        }
    }
    LaunchedEffect(volumeHudVisible) {
        if (volumeHudVisible) {
            delay(1200)
            volumeHudVisible = false
        }
    }

    // Intercept Back button
    BackHandler(enabled = true) {
        if (activeModal != StavioModalType.NONE) {
            activeModal = StavioModalType.NONE
        } else if (isLocked) {
            isLocked = false
        } else {
            onClose()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            // Vertical Drag for Brightness (Left half) and Volume (Right half)
            .pointerInput(isLocked, activeModal) {
                if (!isLocked && activeModal == StavioModalType.NONE) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat()
                            if (offset.x < width * 0.5f) {
                                brightnessHudVisible = true
                            } else {
                                volumeHudVisible = true
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            val width = size.width.toFloat()
                            val height = size.height.toFloat().coerceAtLeast(1f)
                            val deltaFraction = -dragAmount / height * 1.5f

                            if (change.position.x < width * 0.5f) {
                                // Adjust brightness
                                val currentBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
                                val safeCurrent = if (currentBrightness < 0f) 0.5f else currentBrightness
                                val newBrightness = (safeCurrent + deltaFraction).coerceIn(0.02f, 1.0f)
                                brightnessLevel = newBrightness
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = newBrightness
                                    win.attributes = lp
                                }
                                brightnessHudVisible = true
                            } else {
                                // Adjust volume
                                audioManager?.let { am ->
                                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val currentVolFraction = curVol.toFloat() / maxVol.coerceAtLeast(1)
                                    val newFraction = (currentVolFraction + deltaFraction).coerceIn(0f, 1f)
                                    val newIndex = (newFraction * maxVol).roundToInt().coerceIn(0, maxVol)
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, newIndex, 0)
                                    volumeLevel = newFraction
                                    volumeHudVisible = true
                                }
                            }
                        }
                    )
                }
            }
            // Tap & Double Tap gestures
            .pointerInput(isLocked, activeModal) {
                detectTapGestures(
                    onTap = {
                        if (activeModal != StavioModalType.NONE) {
                            activeModal = StavioModalType.NONE
                        } else {
                            showUi = !showUi
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked && activeModal == StavioModalType.NONE) {
                            val width = size.width.toFloat()
                            val now = System.currentTimeMillis()
                            if (offset.x < width * 0.40f) {
                                // YouTube style double tap LEFT -> Rewind 10s
                                if (now - lastRewindTapTime < 700) {
                                    rewindCountSeconds += 10
                                } else {
                                    rewindCountSeconds = 10
                                }
                                lastRewindTapTime = now
                                player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                                rewindAnimationVisible = true
                                forwardAnimationVisible = false
                            } else if (offset.x > width * 0.60f) {
                                // YouTube style double tap RIGHT -> Fast Forward 10s
                                if (now - lastForwardTapTime < 700) {
                                    forwardCountSeconds += 10
                                } else {
                                    forwardCountSeconds = 10
                                }
                                lastForwardTapTime = now
                                val target = (player.currentPosition + 10000)
                                val maxDur = if (player.duration > 0) player.duration else Long.MAX_VALUE
                                player.seekTo(target.coerceAtMost(maxDur))
                                forwardAnimationVisible = true
                                rewindAnimationVisible = false
                            } else {
                                // Double tap center -> Toggle Play/Pause
                                if (isPlaying) player.pause() else player.play()
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Core Video Surface via AndroidView & ExoPlayer
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    this.useController = false
                    // Hide native ExoPlayer subtitleView and disable embedded styles/boxes
                    this.subtitleView?.setApplyEmbeddedStyles(false)
                    this.subtitleView?.setApplyEmbeddedFontSizes(false)
                    this.subtitleView?.visibility = View.GONE
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = when (videoScaleMode) {
                    "Fill" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    "Stretch" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                playerView.subtitleView?.setApplyEmbeddedStyles(false)
                playerView.subtitleView?.setApplyEmbeddedFontSizes(false)
                playerView.subtitleView?.visibility = View.GONE
            },
            modifier = Modifier.fillMaxSize()
        )

        // YouTube-Style Double Tap Rewind Overlay (Left Arc)
        AnimatedVisibility(
            visible = rewindAnimationVisible,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.38f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topEnd = 160.dp, bottomEnd = 160.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.FastRewind,
                        contentDescription = "Geri Sar",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "-$rewindCountSeconds sn",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // YouTube-Style Double Tap Fast Forward Overlay (Right Arc)
        AnimatedVisibility(
            visible = forwardAnimationVisible,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.38f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 160.dp, bottomStart = 160.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "İleri Sar",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "+$forwardCountSeconds sn",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Vertical Gesture Feedback: Brightness HUD (Left edge)
        AnimatedVisibility(
            visible = brightnessHudVisible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            StavioVerticalGestureBar(
                icon = Icons.Filled.Brightness6,
                level = brightnessLevel,
                label = "Parlaklık"
            )
        }

        // Vertical Gesture Feedback: Volume HUD (Right edge)
        AnimatedVisibility(
            visible = volumeHudVisible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            StavioVerticalGestureBar(
                icon = if (volumeLevel == 0f) Icons.AutoMirrored.Filled.VolumeMute else if (volumeLevel < 0.5f) Icons.AutoMirrored.Filled.VolumeDown else Icons.AutoMirrored.Filled.VolumeUp,
                level = volumeLevel,
                label = "Ses"
            )
        }

        // Movable, Aspect-Ratio Immune Compose Subtitle Overlay (No background lines/boxes)
        val showPreviewSubtitle = activeModal == StavioModalType.SUBTITLES && subtitleTab == SubtitleTab.STYLE
        val activeSubtitleText = remember(currentCues, showPreviewSubtitle) {
            if (currentCues.isNotEmpty()) {
                currentCues.mapNotNull { cue ->
                    cue.text?.toString()
                        ?.replace(Regex("\\{\\\\[^\\}]*\\}"), "")
                        ?.replace(Regex("<[^>]*>"), "")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                }.joinToString("\n")
            } else if (showPreviewSubtitle) {
                "Örnek Altyazı: 'Stavio Media — Konumu değiştirmek için ekranda sürükleyin.'"
            } else {
                ""
            }
        }

        if (activeSubtitleText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(subtitleOffsetX.roundToInt(), subtitleOffsetY.roundToInt()) }
                        .padding(bottom = if (showUi) 110.dp else 28.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    playerPrefs.edit()
                                        .putFloat("stavio_sub_offset_x", subtitleOffsetX)
                                        .putFloat("stavio_sub_offset_y", subtitleOffsetY)
                                        .apply()
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                subtitleOffsetX += dragAmount.x
                                subtitleOffsetY += dragAmount.y
                            }
                        }
                        .then(
                            when (subtitleBgType) {
                                "pill" -> Modifier
                                    .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                "box" -> Modifier
                                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                "bordered" -> Modifier
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                else -> Modifier
                                    .background(Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (showPreviewSubtitle) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.OpenWith,
                                    contentDescription = "Taşı",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Ekranda Taşıyın",
                                    color = Color(0xFF818CF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = activeSubtitleText,
                            color = subtitleColor,
                            fontSize = subtitleFontSize.sp,
                            fontWeight = if (subtitleIsBold) FontWeight.Bold else FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            lineHeight = (subtitleFontSize * 1.35f).sp,
                            style = TextStyle(
                                background = Color.Transparent,
                                shadow = when (subtitleEdgeType) {
                                    "shadow" -> Shadow(
                                        color = Color.Black,
                                        offset = Offset(0f, 2f),
                                        blurRadius = 8f
                                    )
                                    "outline" -> Shadow(
                                        color = Color.Black,
                                        offset = Offset(0f, 3f),
                                        blurRadius = 10f
                                    )
                                    else -> Shadow(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        offset = Offset(0f, 1.5f),
                                        blurRadius = 4f
                                    )
                                }
                            )
                        )
                    }
                }
            }
        }

        // Center Buffering Spinner (Pro Cinema Glass Pill)
        if (isBuffering && streamError == null) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xCC090912),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF6366F1),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "STAVIO PRO ENGINE",
                            color = Color(0xFF818CF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Yayın optimize ediliyor...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Aspect Ratio Change Toast HUD
        AnimatedVisibility(
            visible = scaleToastText != null,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xD90A0A14),
                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AspectRatio,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = scaleToastText.orEmpty(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Stream Error Display
        streamError?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
                    modifier = Modifier.padding(24.dp).widthIn(max = 420.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(26.dp)
                    ) {
                        Text(
                            text = "STAVIO STREAM GUARD",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ModernButton(
                                text = "Yeniden Bağlan",
                                icon = Icons.Filled.Refresh,
                                onClick = {
                                    streamReloadKey++
                                    streamError = null
                                }
                            )
                            ModernButton(
                                text = "Çıkış",
                                icon = Icons.Filled.Close,
                                isSecondary = true,
                                onClick = onClose
                            )
                        }
                    }
                }
            }
        }

        // Developer / Pro Telemetry HUD
        if (showFpsOverlay || showStreamStats) {
            val vWidth = player.videoSize.width
            val vHeight = player.videoSize.height
            val resLabel = when {
                vHeight >= 2160 || vWidth >= 3840 -> "4K UHD (${vWidth}x${vHeight})"
                vHeight >= 1080 || vWidth >= 1920 -> "1080p FHD (${vWidth}x${vHeight})"
                vHeight >= 720 || vWidth >= 1280 -> "720p HD (${vWidth}x${vHeight})"
                vWidth > 0 && vHeight > 0 -> "${vWidth}x${vHeight}"
                else -> "Otomatik Akış"
            }
            val videoCodec = player.videoFormat?.sampleMimeType?.substringAfter("/")?.uppercase() ?: "H.264/HLS"
            val audioCodec = player.audioFormat?.sampleMimeType?.substringAfter("/")?.uppercase() ?: "AAC"
            val bufferSec = ((bufferedPos - currentPos).coerceAtLeast(0L) / 1000f)

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xD9090D16),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.55f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 86.dp, start = 24.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STAVIO PRO TELEMETRY",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Çözünürlük: $resLabel",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Kodek: $videoCodec • Ses: $audioCodec",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format(Locale.US, "Önbellek: %.1f sn • Hız: %.2fx", bufferSec, playbackSpeed),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Locked State UI Overlay (Stavio locked pill)
        if (isLocked) {
            AnimatedVisibility(
                visible = showUi,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xD90A0A12),
                    border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable { isLocked = false }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = "Kilidi Aç",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Ekran Kilitli — Kilidi Açmak İçin Dokunun",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else if (activeModal == StavioModalType.NONE) {
            val currentIdx = remember(relatedChannels, currentChannelId, channelUrl) {
                relatedChannels.indexOfFirst { it.id == currentChannelId || it.url == channelUrl }
            }
            val nextChannel = if (currentIdx >= 0 && currentIdx < relatedChannels.size - 1) relatedChannels.getOrNull(currentIdx + 1) else null

            val cleanDisplayTitle = remember(title, subtitle) {
                val raw = title.ifEmpty { subtitle }.ifEmpty { "Stavio" }
                raw.replace(Regex("\\(\\d{4}\\)"), "")
                    .replace(Regex("\\[\\d{4}\\]"), "")
                    .trim()
                    .ifEmpty { raw }
            }

            val displayYearOrSubtitle = remember(title, subtitle, isLive) {
                val yearMatch = Regex("\\b(19\\d{2}|20\\d{2})\\b").find("$title $subtitle")
                when {
                    yearMatch != null -> yearMatch.value
                    subtitle.isNotBlank() && !subtitle.equals(title, ignoreCase = true) -> subtitle
                    isLive -> "Canlı Yayın"
                    else -> "2024"
                }
            }

            // Minimalist Reference Player Overlay (Exact Match to Screenshot 2)
            AnimatedVisibility(
                visible = showUi,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Subtle Top Gradient Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.68f),
                                        Color.Black.copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    // Subtle Bottom Gradient Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.48f),
                                        Color.Black.copy(alpha = 0.86f)
                                    )
                                )
                            )
                    )

                    // =========================================================
                    // 1. TOP-RIGHT 4 CLEAN WHITE ICONS (Swap, WatchParty/Group, Lock, Back)
                    // =========================================================
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 24.dp, end = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(26.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Swap / Aspect Ratio / Stream Switch Icon
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Ekran Oranı / Akış Değiştir",
                            tint = Color.White,
                            modifier = Modifier
                                .size(26.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onChangeScaleMode()
                                    val nextLabel = when (videoScaleMode) {
                                        "Fit" -> "Doldur (Fill)"
                                        "Fill" -> "16:9 Tam Ekran"
                                        else -> "Orijinal Sığdır (Fit)"
                                    }
                                    scaleToastText = nextLabel
                                }
                        )

                        // 2. Watch Party / Group / Channels Icon
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = "Watch Party / Kanal & Bölüm Listesi",
                            tint = Color.White,
                            modifier = Modifier
                                .size(26.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (relatedChannels.size > 1 && onSelectChannel != null) {
                                        activeModal = StavioModalType.CHANNELS
                                    } else {
                                        scaleToastText = "Watch Party hazır"
                                    }
                                }
                        )

                        // 3. Lock Icon
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Ekranı Kilitle",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    isLocked = true
                                }
                        )

                        // 4. Back Arrow Icon (Far Top-Right, exact match to Screenshot 2)
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White,
                            modifier = Modifier
                                .size(26.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onClose()
                                }
                        )
                    }

                    // =========================================================
                    // 2. CENTER CONTROLS (3 Clean White Icons: Replay 10, Pause/Play, Forward 10)
                    // =========================================================
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(76.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = "10 Saniye Geri",
                            tint = Color.White,
                            modifier = Modifier
                                .size(46.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                                }
                        )

                        // Pause / Play
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                            tint = Color.White,
                            modifier = Modifier
                                .size(58.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (isPlaying) player.pause() else player.play()
                                }
                        )

                        // Forward 10s
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = "10 Saniye İleri",
                            tint = Color.White,
                            modifier = Modifier
                                .size(46.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    val target = (player.currentPosition + 10000)
                                    val maxDur = if (player.duration > 0) player.duration else Long.MAX_VALUE
                                    player.seekTo(target.coerceAtMost(maxDur))
                                }
                        )
                    }

                    // =========================================================
                    // 3. BOTTOM AREA (Title + Year -> Full-Width White Progress Bar -> 6 Left Icons & Right Timestamp)
                    // =========================================================
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 20.dp)
                    ) {
                        // Left-aligned Title & Year above progress bar
                        Text(
                            text = cleanDisplayTitle,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = StavioFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displayYearOrSubtitle,
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = StavioFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Full-Width Sleek White Progress Bar
                        StavioTimelineScrubber(
                            durationMs = duration,
                            currentPosMs = animatedPos.toLong(),
                            bufferedPosMs = bufferedPos,
                            isLive = isLive,
                            isInteracting = isDraggingSlider,
                            onScrubChange = { pos ->
                                isDraggingSlider = true
                                dragPos = pos
                            },
                            onScrubFinished = { pos ->
                                player.seekTo(pos)
                                isDraggingSlider = false
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom Icon Row (6 Clean White Icons on Left + Timestamp on Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. CC (Subtitles)
                                Icon(
                                    imageVector = Icons.Rounded.ClosedCaption,
                                    contentDescription = "Altyazılar",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            activeModal = StavioModalType.SUBTITLES
                                            subtitleTab = SubtitleTab.TRACKS
                                        }
                                )

                                // 2. Speaker / Audio Track
                                Icon(
                                    imageVector = Icons.Rounded.Speaker,
                                    contentDescription = "Ses Dili",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            activeModal = StavioModalType.AUDIO
                                        }
                                )

                                // 3. Cloud (Stream Telemetry / Source Info)
                                Icon(
                                    imageVector = Icons.Outlined.Cloud,
                                    contentDescription = "Kaynak ve Akış Bilgisi",
                                    tint = if (showStreamStats) Color(0xFF38BDF8) else Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            showStreamStats = !showStreamStats
                                        }
                                )

                                // 4. Speedometer (Playback Speed)
                                Icon(
                                    imageVector = Icons.Outlined.Speed,
                                    contentDescription = "Oynatma Hızı",
                                    tint = if (playbackSpeed != 1.0f) Color(0xFF38BDF8) else Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            playbackSpeed = when (playbackSpeed) {
                                                0.75f -> 1.0f
                                                1.0f -> 1.25f
                                                1.25f -> 1.5f
                                                1.5f -> 2.0f
                                                2.0f -> 0.75f
                                                else -> 1.0f
                                            }
                                            player.setPlaybackSpeed(playbackSpeed)
                                            playerPrefs.edit().putFloat("stavio_playback_speed", playbackSpeed).apply()
                                            scaleToastText = "Oynatma Hızı: ${playbackSpeed}x"
                                        }
                                )

                                // 5. Picture-in-Picture / Screen Frame
                                Icon(
                                    imageVector = Icons.Outlined.PictureInPictureAlt,
                                    contentDescription = "Küçük Pencere (PiP)",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (onEnterPip != null) {
                                                onEnterPip()
                                            } else {
                                                onChangeScaleMode()
                                                scaleToastText = "Ekran Oranı Değiştirildi"
                                            }
                                        }
                                )

                                // 6. Next Episode / Channel Chevron (>)
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "Sonraki Bölüm / Kanal",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (nextChannel != null && onSelectChannel != null) {
                                                onSelectChannel(nextChannel)
                                            } else if (relatedChannels.size > 1 && onSelectChannel != null) {
                                                activeModal = StavioModalType.CHANNELS
                                            } else {
                                                val target = (player.currentPosition + 30000)
                                                val maxDur = if (player.duration > 0) player.duration else Long.MAX_VALUE
                                                player.seekTo(target.coerceAtMost(maxDur))
                                            }
                                        }
                                )
                            }

                            // Right: Clean White Timestamp (06:41 / 1:58:06)
                            Text(
                                text = if (isLive && duration <= 0) {
                                    "CANLI"
                                } else {
                                    formatStavioRuntime(currentPos, duration, showRemainingTime)
                                },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = StavioFontFamily,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (duration > 0) {
                                        showRemainingTime = !showRemainingTime
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Stavio Cinema Modals: Audio Track Modal, Subtitle Modal & Quick Channel Switcher Modal
        if (activeModal == StavioModalType.AUDIO) {
            StavioAudioTrackModal(
                player = player,
                onTrackSelected = { langCode, labelText ->
                    playerPrefs.edit()
                        .putString("stavio_pref_audio_lang", langCode.orEmpty())
                        .putString("stavio_pref_audio_label", labelText)
                        .apply()
                },
                onDismiss = { activeModal = StavioModalType.NONE }
            )
        } else if (activeModal == StavioModalType.SUBTITLES) {
            StavioSubtitleModal(
                player = player,
                selectedTab = subtitleTab,
                onTabSelect = { subtitleTab = it },
                subtitleFontSize = subtitleFontSize,
                onFontSizeChange = {
                    subtitleFontSize = it
                    playerPrefs.edit().putInt("stavio_sub_font_size", it).apply()
                },
                subtitleColor = subtitleColor,
                onColorChange = {
                    subtitleColor = it
                    val argb = android.graphics.Color.argb(
                        (it.alpha * 255).toInt(),
                        (it.red * 255).toInt(),
                        (it.green * 255).toInt(),
                        (it.blue * 255).toInt()
                    )
                    playerPrefs.edit().putInt("stavio_sub_color_argb", argb).apply()
                },
                subtitleBgType = subtitleBgType,
                onBgTypeChange = {
                    subtitleBgType = it
                    playerPrefs.edit().putString("stavio_sub_bg_type", it).apply()
                },
                subtitleEdgeType = subtitleEdgeType,
                onEdgeTypeChange = {
                    subtitleEdgeType = it
                    playerPrefs.edit().putString("stavio_sub_edge_type", it).apply()
                },
                subtitleIsBold = subtitleIsBold,
                onBoldToggle = {
                    val nextBold = !subtitleIsBold
                    subtitleIsBold = nextBold
                    playerPrefs.edit().putBoolean("stavio_sub_is_bold", nextBold).apply()
                },
                subtitleDelayMs = subtitleDelayMs,
                onDelayChange = {
                    subtitleDelayMs = it
                    playerPrefs.edit().putInt("stavio_sub_delay_ms", it).apply()
                },
                onResetPosition = {
                    subtitleOffsetX = 0f
                    subtitleOffsetY = 0f
                    playerPrefs.edit()
                        .putFloat("stavio_sub_offset_x", 0f)
                        .putFloat("stavio_sub_offset_y", 0f)
                        .apply()
                },
                onSubtitlePrefChanged = { enabled, langCode, labelText ->
                    playerPrefs.edit()
                        .putBoolean("stavio_pref_sub_enabled", enabled)
                        .putString("stavio_pref_sub_lang", langCode.orEmpty())
                        .putString("stavio_pref_sub_label", labelText.orEmpty())
                        .apply()
                },
                onDismiss = { activeModal = StavioModalType.NONE }
            )
        } else if (activeModal == StavioModalType.CHANNELS && onSelectChannel != null) {
            StavioChannelQuickSwitchModal(
                channels = relatedChannels,
                currentChannelUrl = channelUrl,
                groupTitle = subtitle,
                onSelect = { ch ->
                    activeModal = StavioModalType.NONE
                    onSelectChannel(ch)
                },
                onDismiss = { activeModal = StavioModalType.NONE }
            )
        }
    }
}

// -------------------------------------------------------------
// STAVIO CINEMA OVERLAYS & MODALS
// -------------------------------------------------------------

@Composable
fun StavioOverlayScaffold(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val horizontalGradient = Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.40f))
                    )
                    onDrawBehind {
                        drawRect(horizontalGradient)
                    }
                }
                .padding(start = 36.dp, end = 36.dp, top = 20.dp, bottom = 24.dp),
            content = content
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun StavioAudioTrackModal(
    player: ExoPlayer,
    onTrackSelected: (langCode: String?, labelText: String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    var tracksInfo by remember { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                tracksInfo = tracks
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val audioGroups = tracksInfo.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

    StavioOverlayScaffold(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.95f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.55f)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ses Parçaları",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Kapat",
                            tint = Color.White.copy(alpha = 0.70f)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (audioGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ayrı ses parçası bulunamadı.",
                            color = Color.White.copy(alpha = 0.60f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        audioGroups.forEach { group ->
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val isSelected = group.isTrackSelected(i)
                                val lang = format.language?.uppercase() ?: format.label ?: "Ses Parçası ${i + 1}"
                                val channels = if (format.channelCount > 2) "${format.channelCount}.1" else if (format.channelCount == 2) "Stereo" else "Mono"
                                val codec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "AUDIO"

                                StavioTrackRow(
                                    title = lang,
                                    subtitle = "$channels • $codec",
                                    isSelected = isSelected,
                                    onClick = {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                        onTrackSelected(format.language, lang)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun StavioSubtitleModal(
    player: ExoPlayer,
    selectedTab: SubtitleTab,
    onTabSelect: (SubtitleTab) -> Unit,
    subtitleFontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    subtitleColor: Color,
    onColorChange: (Color) -> Unit,
    subtitleBgType: String,
    onBgTypeChange: (String) -> Unit,
    subtitleEdgeType: String,
    onEdgeTypeChange: (String) -> Unit,
    subtitleIsBold: Boolean,
    onBoldToggle: () -> Unit,
    subtitleDelayMs: Int,
    onDelayChange: (Int) -> Unit,
    onResetPosition: () -> Unit,
    onSubtitlePrefChanged: (enabled: Boolean, langCode: String?, labelText: String?) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit
) {
    var tracksInfo by remember { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                tracksInfo = tracks
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val subtitleGroups = tracksInfo.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val isAnySelected = subtitleGroups.any { group ->
        (0 until group.length).any { group.isTrackSelected(it) }
    }

    StavioOverlayScaffold(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.95f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.58f)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Altyazılar",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Kapat",
                            tint = Color.White.copy(alpha = 0.70f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Segmented Tabs: [ Parçalar ] [ Altyazı Stili ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == SubtitleTab.TRACKS) Color(0xFF6366F1) else Color.Transparent)
                            .clickable { onTabSelect(SubtitleTab.TRACKS) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Parçalar",
                            color = if (selectedTab == SubtitleTab.TRACKS) Color.White else Color.White.copy(alpha = 0.70f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == SubtitleTab.STYLE) Color(0xFF6366F1) else Color.Transparent)
                            .clickable { onTabSelect(SubtitleTab.STYLE) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Altyazı Stili",
                            color = if (selectedTab == SubtitleTab.STYLE) Color.White else Color.White.copy(alpha = 0.70f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Tab Content
                if (selectedTab == SubtitleTab.TRACKS) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Option: Turn off subtitles
                        StavioTrackRow(
                            title = "Altyazı Yok",
                            subtitle = "Altyazıları devre dışı bırak",
                            isSelected = !isAnySelected,
                            onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                    .build()
                                onSubtitlePrefChanged(false, null, null)
                            }
                        )

                        if (subtitleGroups.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Bu yayında gömülü altyazı akışı bulunamadı.",
                                    color = Color.White.copy(alpha = 0.60f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            subtitleGroups.forEach { group ->
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val isSelected = group.isTrackSelected(i)
                                    val lang = format.language?.uppercase() ?: format.label ?: "Altyazı ${i + 1}"
                                    val mime = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "SUB"

                                    StavioTrackRow(
                                        title = lang,
                                        subtitle = "Format: $mime",
                                        isSelected = isSelected,
                                        onClick = {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                .build()
                                            onSubtitlePrefChanged(true, format.language, lang)
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Subtitle Style & Customization (Stavio SubtitleStylePanel)
                    StavioSubtitleStylePanel(
                        subtitleFontSize = subtitleFontSize,
                        onFontSizeChange = onFontSizeChange,
                        subtitleColor = subtitleColor,
                        onColorChange = onColorChange,
                        subtitleBgType = subtitleBgType,
                        onBgTypeChange = onBgTypeChange,
                        subtitleEdgeType = subtitleEdgeType,
                        onEdgeTypeChange = onEdgeTypeChange,
                        subtitleIsBold = subtitleIsBold,
                        onBoldToggle = onBoldToggle,
                        subtitleDelayMs = subtitleDelayMs,
                        onDelayChange = onDelayChange,
                        onResetPosition = onResetPosition
                    )
                }
            }
        }
    }
}

@Composable
fun StavioTrackRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.10f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.60f),
                    fontSize = 12.sp
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Seçili",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun StavioSubtitleStylePanel(
    subtitleFontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    subtitleColor: Color,
    onColorChange: (Color) -> Unit,
    subtitleBgType: String,
    onBgTypeChange: (String) -> Unit,
    subtitleEdgeType: String,
    onEdgeTypeChange: (String) -> Unit,
    subtitleIsBold: Boolean,
    onBoldToggle: () -> Unit,
    subtitleDelayMs: Int,
    onDelayChange: (Int) -> Unit,
    onResetPosition: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Altyazı Gecikmesi (Subtitle Delay Stepper)
        Column {
            Text(
                text = "Altyazı Gecikmesi",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Stepper
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { onDelayChange(subtitleDelayMs - 100) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Azalt", tint = Color.White)
                    }
                    Text(
                        text = if (subtitleDelayMs == 0) "0 ms" else if (subtitleDelayMs > 0) "+$subtitleDelayMs ms" else "$subtitleDelayMs ms",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { onDelayChange(subtitleDelayMs + 100) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Artır", tint = Color.White)
                    }
                }
                // Sıfırla
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.clickable { onDelayChange(0) }
                ) {
                    Text(
                        text = "Sıfırla",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Yazı Boyutu (Font Size Stepper)
        Column {
            Text(
                text = "Yazı Boyutu ($subtitleFontSize sp)",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { onFontSizeChange((subtitleFontSize - 2).coerceAtLeast(14)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Küçült", tint = Color.White)
                }
                Text(
                    text = "$subtitleFontSize sp",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { onFontSizeChange((subtitleFontSize + 2).coerceAtMost(44)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Büyüt", tint = Color.White)
                }
            }
        }

        // Kalın Metin (Bold Toggle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onBoldToggle)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Kalın Metin (Bold)",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (subtitleIsBold) Color(0xFF6366F1) else Color.White.copy(alpha = 0.20f)
            ) {
                Text(
                    text = if (subtitleIsBold) "AÇIK" else "KAPALI",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Yazı Rengi Swatches
        Column {
            Text(
                text = "Yazı Rengi",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Color.White to "Beyaz",
                    Color(0xFFFACC15) to "Sarı",
                    Color(0xFF38BDF8) to "Mavi",
                    Color(0xFF4ADE80) to "Yeşil"
                ).forEach { (color, label) ->
                    val isSelected = subtitleColor == color
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(2.dp, if (isSelected) Color(0xFF6366F1) else Color.Transparent),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onColorChange(color) }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Arka Plan Stili
        Column {
            Text(
                text = "Arka Plan Stili",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "none" to "Şeffaf",
                    "pill" to "Kapsül",
                    "box" to "Kutu",
                    "bordered" to "Çerçeve"
                ).forEach { (type, label) ->
                    val isSelected = subtitleBgType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.08f))
                            .clickable { onBgTypeChange(type) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Konum Sıfırlama
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E1B4B).copy(alpha = 0.6f),
            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Konum Kaydırma",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF6366F1),
                    modifier = Modifier.clickable(onClick = onResetPosition)
                ) {
                    Text(
                        text = "Konumu Sıfırla",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STAVIO TIMELINE SCRUBBER & BUTTONS
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StavioTimelineScrubber(
    durationMs: Long,
    currentPosMs: Long,
    bufferedPosMs: Long,
    isLive: Boolean,
    isInteracting: Boolean,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit
) {
    val durationSafe = durationMs.coerceAtLeast(0L)
    val rangeEnd = durationSafe.coerceAtLeast(1L).toFloat()
    val bufferedFraction = (bufferedPosMs.toFloat() / rangeEnd).coerceIn(0f, 1f)

    val trackThickness by animateDpAsState(
        targetValue = if (isInteracting) 8.dp else 5.5.dp,
        animationSpec = tween(durationMillis = 150),
        label = "stavio_scrubber_thickness"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = currentPosMs.coerceIn(0L, durationSafe).toFloat(),
            onValueChange = { value ->
                onScrubChange(value.toLong().coerceIn(0L, durationSafe))
            },
            onValueChangeFinished = {
                onScrubFinished(currentPosMs.coerceIn(0L, durationSafe))
            },
            valueRange = 0f..rangeEnd,
            enabled = !isLive && durationSafe > 0L,
            thumb = {}, // Clean thumbless capsule bar matching Screenshot 2
            track = { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .drawBehind {
                            val trackHeight = trackThickness.toPx()
                            val trackOrigin = Offset(0f, size.height / 2 - trackHeight / 2)
                            val radius = CornerRadius(trackHeight / 2)

                            // Background track
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.24f),
                                topLeft = trackOrigin,
                                size = Size(size.width, trackHeight),
                                cornerRadius = radius
                            )
                            // Buffered track
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.38f),
                                topLeft = trackOrigin,
                                size = Size(size.width * bufferedFraction, trackHeight),
                                cornerRadius = radius
                            )
                            // Played bar (Pure White as in Screenshot 2)
                            val progressFraction = (currentPosMs.toFloat() / rangeEnd).coerceIn(0f, 1f)
                            drawRoundRect(
                                color = Color.White,
                                topLeft = trackOrigin,
                                size = Size(size.width * progressFraction, trackHeight),
                                cornerRadius = radius
                            )
                        }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Floating seek bubble during scrub
        if (isInteracting && durationSafe > 0L) {
            val progressFraction = (currentPosMs.toFloat() / rangeEnd).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .align(Alignment.CenterStart)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xE618181B),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(
                            text = formatTime(currentPosMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = StavioFontFamily,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StavioToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    iconTint: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
fun StavioBottomPillButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) Color(0xFF6366F1).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f),
        border = BorderStroke(
            1.dp,
            if (isActive) Color(0xFF818CF8).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .height(36.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFFA5B4FC) else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun StavioChannelQuickSwitchModal(
    channels: List<ChannelEntity>,
    currentChannelUrl: String,
    groupTitle: String,
    onSelect: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit
) {
    StavioOverlayScaffold(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0B0F1A).copy(alpha = 0.96f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.52f)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = groupTitle.ifEmpty { "Hızlı Kanal & Bölüm Geçişi" },
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${channels.size} İçerik Hazır",
                            color = Color(0xFF818CF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Kapat",
                            tint = Color.White.copy(alpha = 0.70f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(channels, key = { "${it.id}_${it.url}" }) { ch ->
                        val isPlayingNow = ch.url == currentChannelUrl
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isPlayingNow) Color(0xFF6366F1).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(
                                1.dp,
                                if (isPlayingNow) Color(0xFF818CF8) else Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(ch) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    if (ch.logoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = ch.logoUrl,
                                            contentDescription = ch.name,
                                            modifier = Modifier.fillMaxSize().padding(3.dp)
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = Icons.Filled.Tv,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ch.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isPlayingNow) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isPlayingNow) {
                                        Text(
                                            text = "● Şu An Oynatılıyor",
                                            color = Color(0xFF4ADE80),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StavioCenterControlButton(
    icon: ImageVector,
    contentDescription: String,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isPrimary) Color(0xFF6366F1).copy(alpha = 0.90f) else Color.Black.copy(alpha = 0.48f),
        border = BorderStroke(
            width = if (isPrimary) 1.5.dp else 1.dp,
            color = if (isPrimary) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.22f)
        ),
        shadowElevation = if (isPrimary) 12.dp else 2.dp,
        modifier = Modifier
            .size(buttonSize)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun StavioActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    hasBadge: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        if (hasBadge) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFF6366F1), CircleShape)
                    .align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
fun StavioVerticalGestureBar(
    icon: ImageVector,
    level: Float,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.70f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = Modifier
            .width(42.dp)
            .height(140.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )

            // Vertical gauge
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(level.coerceIn(0f, 1f))
                        .background(Color(0xFF6366F1))
                )
            }

            Text(
                text = "${(level * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ModernButton(
    text: String,
    icon: ImageVector,
    isSecondary: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSecondary) Color.White.copy(alpha = 0.12f) else Color(0xFF6366F1),
        border = if (isSecondary) BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)) else null,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// -------------------------------------------------------------
// HELPER UTILITIES
// -------------------------------------------------------------

fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun formatStavioRuntime(positionMs: Long, durationMs: Long, showRemainingTime: Boolean): String {
    return if (showRemainingTime) {
        val remainingMs = (durationMs.coerceAtLeast(0L) - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L)
        "−${formatTime(remainingMs)}"
    } else {
        "${formatTime(positionMs)} / ${formatTime(durationMs)}"
    }
}
