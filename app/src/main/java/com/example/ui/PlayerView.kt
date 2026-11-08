package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

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
    onPlayerError: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    // Force Landscape Orientation
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

    var showSourcesDialog by remember { mutableStateOf(false) }
    var showTracksDialog by remember { mutableStateOf(false) }
    var subtitleSize by remember { mutableStateOf(26f) }
    var subtitleBg by remember { mutableStateOf(android.graphics.Color.TRANSPARENT) }
    var subtitleEdge by remember { mutableStateOf(androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE) }
    var subtitlePadding by remember { mutableStateOf(0.08f) } // Default 8% bottom padding

    var isLive by remember { mutableStateOf(false) }
    
    val trackSelector = remember { androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context) }
    
    val player = remember(channelUrl) {
        ExoPlayer.Builder(context)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            .setTrackSelector(trackSelector)
            .build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(channelUrl)
                    .apply {
                        if (channelUrl.contains(".m3u8") || channelUrl.contains(".m3u") || channelUrl.contains("m3u_plus") || channelUrl.contains("output=m3u8")) {
                            setMimeType(MimeTypes.APPLICATION_M3U8)
                        } else if (channelUrl.contains(".ts") || channelUrl.contains("output=ts")) {
                            setMimeType(MimeTypes.VIDEO_MP2T)
                        }
                    }
                    .build()
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPos by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPos by remember { mutableStateOf(0L) }

    LaunchedEffect(player, isDraggingSlider) {
        while (true) {
            isLive = player.isCurrentWindowLive
            if (!isDraggingSlider) {
                currentPos = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
            }
            delay(100L) // Smoother updates
        }
    }

    val animatedPos by animateFloatAsState(targetValue = if (isDraggingSlider) dragPos.toFloat() else currentPos.toFloat(), label = "progress")
    val animatedSubtitlePadding by animateFloatAsState(targetValue = subtitlePadding + if (showUi) 0.15f else 0f, label = "subtitle_padding")

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    streamError = null
                }
            }
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
            override fun onPlayerError(error: PlaybackException) {
                val userFriendlyMessage = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> 
                        "Baglanti Hatasi: Yayina ulasilamadi."
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED -> 
                        "Kod Cozucu Hatasi: Format desteklenmiyor."
                    else -> "Yayin Yuklenemedi veya Cevrimdisi."
                }
                streamError = userFriendlyMessage
                onPlayerError(userFriendlyMessage)
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.stop()
            player.release()
        }
    }

    LaunchedEffect(showUi, isPlaying, isDraggingSlider) {
        if (showUi && isPlaying && !isDraggingSlider) {
            delay(4000)
            showUi = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(isLocked, showSourcesDialog, showTracksDialog) {
                detectTapGestures(
                    onTap = {
                        if (!showSourcesDialog && !showTracksDialog) {
                            showUi = !showUi
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    this.useController = false
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
                playerView.subtitleView?.apply {
                    setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleSize)
                    setBottomPaddingFraction(animatedSubtitlePadding)
                    setStyle(
                        androidx.media3.ui.CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            subtitleBg,
                            android.graphics.Color.TRANSPARENT,
                            subtitleEdge,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && streamError == null) {
            CircularProgressIndicator(
                color = Color(0xFF4A65FF),
                modifier = Modifier.size(50.dp)
            )
        }

        streamError?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = msg, color = Color.White)
            }
        }

        if (isLocked) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showUi,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                IconButton(
                    onClick = { isLocked = false },
                    modifier = Modifier
                        .padding(bottom = 48.dp, start = 48.dp)
                        .size(48.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.LockOpen, contentDescription = "Kilidi Ac", tint = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Kilit Aç", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (!showSourcesDialog && !showTracksDialog) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showUi,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, start = 24.dp, end = 24.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Normal)
                            Spacer(Modifier.height(4.dp))
                            Text(subtitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(48.dp)) // Dengelemek icin
                    }

                    // Center Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(100.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Replay10, contentDescription = "-10", tint = Color.White, modifier = Modifier.size(56.dp))
                        }
                        
                        // Play/Pause
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .clickable { if (isPlaying) player.pause() else player.play() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPlaying) {
                                Icon(Icons.Filled.Pause, contentDescription = "Durdur", tint = Color.White, modifier = Modifier.size(64.dp))
                            } else {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Oynat", tint = Color.White, modifier = Modifier.size(64.dp))
                            }
                        }

                        // Forward 10
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Forward10, contentDescription = "+10", tint = Color.White, modifier = Modifier.size(56.dp))
                        }
                    }

                    // Bottom Controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 24.dp, start = 32.dp, end = 32.dp)
                    ) {
                        // Progress Bar Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(if (isDraggingSlider) dragPos else currentPos), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(16.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Slider(
                                    value = if (duration > 0) (animatedPos / duration).coerceIn(0f, 1f) else 0f,
                                    onValueChange = { percent ->
                                        isDraggingSlider = true
                                        dragPos = (percent * duration).toLong()
                                        player.seekTo(dragPos)
                                    },
                                    onValueChangeFinished = {
                                        isDraggingSlider = false
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF4A65FF), 
                                        activeTrackColor = Color.LightGray,
                                        inactiveTrackColor = Color.DarkGray
                                    )
                                )
                                // Highlight for mini preview
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isDraggingSlider,
                                    enter = androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.fadeOut()
                                ) {
                                    val percent = if (duration > 0) dragPos.toFloat() / duration.toFloat() else 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 36.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(percent)
                                                .align(Alignment.CenterStart)
                                        ) {
                                            Text(
                                                formatTime(dragPos),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .background(Color(0xFF4A65FF), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(if (isLive && duration <= 0) "CANLI" else formatTime(duration), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally)
                        ) {
                            ActionButton(icon = Icons.Filled.LockOpen, text = "Kilitle") { isLocked = true }
                            ActionButton(icon = Icons.Filled.CropFree, text = "Yeniden boyutlandır") { onChangeScaleMode() }
                            ActionButton(icon = Icons.Filled.PlaylistPlay, text = "Kaynak") { showSourcesDialog = true }
                            ActionButton(icon = Icons.Filled.Equalizer, text = "Parçalar") { showTracksDialog = true }
                        }
                    }
                }
            }
        }
        
        if (showSourcesDialog) {
            TrackSelectionDialog(
                title = "Kaynaklar / Altyazilar",
                player = player,
                trackType = androidx.media3.common.C.TRACK_TYPE_TEXT,
                subtitleSize = subtitleSize,
                onSubtitleSizeChange = { subtitleSize = it },
                subtitleBg = subtitleBg,
                onSubtitleBgChange = { subtitleBg = it },
                subtitleEdge = subtitleEdge,
                onSubtitleEdgeChange = { subtitleEdge = it },
                subtitlePadding = subtitlePadding,
                onSubtitlePaddingChange = { subtitlePadding = it },
                onDismiss = { showSourcesDialog = false }
            )
        }
        
        if (showTracksDialog) {
            TrackSelectionDialog(
                title = "Ses Parcalari",
                player = player,
                trackType = androidx.media3.common.C.TRACK_TYPE_AUDIO,
                subtitleSize = subtitleSize,
                onSubtitleSizeChange = { },
                subtitleBg = subtitleBg,
                onSubtitleBgChange = { },
                subtitleEdge = subtitleEdge,
                onSubtitleEdgeChange = { },
                subtitlePadding = subtitlePadding,
                onSubtitlePaddingChange = { },
                onDismiss = { showTracksDialog = false }
            )
        }
    }
}

@Composable
fun TrackSelectionDialog(
    title: String,
    player: ExoPlayer,
    trackType: Int,
    subtitleSize: Float,
    onSubtitleSizeChange: (Float) -> Unit,
    subtitleBg: Int,
    onSubtitleBgChange: (Int) -> Unit,
    subtitleEdge: Int,
    onSubtitleEdgeChange: (Int) -> Unit,
    subtitlePadding: Float,
    onSubtitlePaddingChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tracksInfo by remember { mutableStateOf(player.currentTracks) }
    var showSubtitleSettings by remember { mutableStateOf(false) }
    
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                tracksInfo = tracks
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val trackGroups = tracksInfo.groups.filter { it.type == trackType }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, 
                indication = null
            ) { onDismiss() }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, 
                        indication = null
                    ) { }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(16.dp))
                
                if (trackGroups.isEmpty()) {
                    Text("Secenek bulunmuyor.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp))
                } else {
                    trackGroups.forEach { group ->
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val isSelected = group.isTrackSelected(i)
                            val trackName = format.language ?: format.label ?: "Parca ${i + 1}"
                            val finalName = if (format.sampleMimeType?.contains("audio") == true) {
                                "$trackName " + (format.sampleMimeType?.substringAfter("/") ?: "")
                            } else {
                                trackName
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = "Secili", tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(12.dp))
                                } else {
                                    Spacer(Modifier.width(30.dp))
                                }
                                Text(finalName, color = if (isSelected) Color.White else Color.Gray, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
                
                if (trackType == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                    // Add default "Disable Subtitles"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                                    .setIgnoredTextSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                                    .build()
                            }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isNoneSelected = trackGroups.none { group -> (0 until group.length).any { group.isTrackSelected(it) } }
                        if (isNoneSelected) {
                            Icon(Icons.Filled.Check, contentDescription = "Secili", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                        } else {
                            Spacer(Modifier.width(30.dp))
                        }
                        Text("Altyazi yok", color = if (isNoneSelected) Color.White else Color.Gray, fontSize = 14.sp, fontWeight = if (isNoneSelected) FontWeight.Bold else FontWeight.Normal)
                    }

                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.DarkGray))
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSubtitleSettings = !showSubtitleSettings }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ayarlar", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Altyazi Ayarlari", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            if (trackType == androidx.media3.common.C.TRACK_TYPE_TEXT && showSubtitleSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .background(Color.Black.copy(alpha = 0.90f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, 
                            indication = null
                        ) { }
                        .padding(16.dp)
                ) {
                    Text("Altyazi Ayarlari", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp, top = 8.dp))
                    
                    Text("Boyut", color = Color.Gray, fontSize = 12.sp)
                    Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(16f, 20f, 26f, 32f, 40f).forEach { size ->
                            val isSelected = subtitleSize == size
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF4A65FF) else Color.DarkGray)
                                    .clickable { onSubtitleSizeChange(size) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("${size.toInt()}", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Yukseklik / Konum", color = Color.Gray, fontSize = 12.sp)
                    Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.04f to "Alt", 0.08f to "Normal", 0.12f to "Yukari", 0.16f to "En Yukari").forEach { (pad, name) ->
                            val isSelected = kotlin.math.abs(subtitlePadding - pad) < 0.01f
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF4A65FF) else Color.DarkGray)
                                    .clickable { onSubtitlePaddingChange(pad) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(name, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Arkaplan Rengi", color = Color.Gray, fontSize = 12.sp)
                    Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val transparent = android.graphics.Color.TRANSPARENT
                        val black = android.graphics.Color.BLACK
                        val semiBlack = android.graphics.Color.argb(128, 0, 0, 0)
                        
                        listOf(
                            "Yok" to transparent,
                            "Yari" to semiBlack,
                            "Siyah" to black
                        ).forEach { (name, colorValue) ->
                            val isSelected = subtitleBg == colorValue
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF4A65FF) else Color.DarkGray)
                                    .clickable { onSubtitleBgChange(colorValue) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(name, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Stil (Kalinlik)", color = Color.Gray, fontSize = 12.sp)
                    Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val noEdge = androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                        val dropShadow = androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                        val outline = androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                        
                        listOf(
                            "Normal" to noEdge,
                            "Golge" to dropShadow,
                            "Dıs Hat" to outline
                        ).forEach { (name, edgeValue) ->
                            val isSelected = subtitleEdge == edgeValue
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF4A65FF) else Color.DarkGray)
                                    .clickable { onSubtitleEdgeChange(edgeValue) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(name, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

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
