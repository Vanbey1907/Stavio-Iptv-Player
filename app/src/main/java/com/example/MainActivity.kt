package com.example

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.IPTVHomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ScreenState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Başlangıçta otomatik PiP modunu kesin olarak devre dışı bırak
        updatePipParams(isAllowed = false)

        setContent {
            val viewModel: MainViewModel = viewModel()
            mainViewModel = viewModel

            val currentScreen by viewModel.currentScreen.collectAsState()
            val isPlayerActive by viewModel.isPlayerActive.collectAsState()
            val currentlyPlaying by viewModel.currentlyPlaying.collectAsState()
            val isVideoActuallyPlaying by viewModel.isVideoActuallyPlaying.collectAsState()

            // PiP durumunu ekran değişimlerinde anlık güncelle:
            // Ana menüdeyken, kategori gezerken veya detay sayfasındayken PiP kesinlikle kapalıdır.
            LaunchedEffect(currentScreen, isPlayerActive, currentlyPlaying, isVideoActuallyPlaying) {
                val allowed = currentScreen == ScreenState.PLAYER &&
                    isPlayerActive &&
                    currentlyPlaying != null &&
                    isVideoActuallyPlaying
                updatePipParams(isAllowed = allowed)
            }

            LaunchedEffect(Unit) {
                handleIntent(intent)
            }

            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF050507)
                ) {
                    IPTVHomeScreen(viewModel)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel?.let { vm ->
                    combine(
                        vm.currentScreen,
                        vm.isPlayerActive,
                        vm.currentlyPlaying,
                        vm.isVideoActuallyPlaying
                    ) { screen, active, playingChannel, videoPlaying ->
                        screen == ScreenState.PLAYER && active && playingChannel != null && videoPlaying
                    }.collect { allowed ->
                        updatePipParams(isAllowed = allowed)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                mainViewModel?.playExternalVideo(uri, this)
            }
        }
    }

    private fun isPipCurrentlyEligible(): Boolean {
        val vm = mainViewModel ?: return false
        return vm.currentScreen.value == ScreenState.PLAYER &&
            vm.isPlayerActive.value &&
            vm.currentlyPlaying.value != null &&
            vm.isVideoActuallyPlaying.value
    }

    private fun updatePipParams(isAllowed: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(isAllowed)
                }
                setPictureInPictureParams(builder.build())
            } catch (_: Throwable) {
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // SADECE aktif tam ekran video oynatıcı açıkken ve video oynatılıyorken PiP moduna geç.
        // Ana menü, kategori veya film/dizi detay ekranındayken PiP moduna geçmeyi kesinlikle engelle.
        if (!isPipCurrentlyEligible()) {
            updatePipParams(isAllowed = false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Throwable) {
            }
        }
    }
}
