package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

enum class HwAccelMode(val id: String, val titleTr: String, val titleEn: String) {
    AUTO("auto", "Otomatik (Önerilen)", "Auto (Recommended)"),
    HARDWARE_ONLY("hardware_only", "Yalnızca Donanım (MediaCodec)", "Hardware Only (MediaCodec)"),
    SOFTWARE_FALLBACK("software_fallback", "Yazılım Destekli (CPU)", "Software Fallback (CPU)")
}

enum class BufferMode(
    val id: String, 
    val titleTr: String, 
    val titleEn: String, 
    val minBufferMs: Int, 
    val maxBufferMs: Int, 
    val playbackBufferMs: Int
) {
    LOW_LATENCY("low_latency", "Düşük Gecikme (5s Hızlı Kanal Değişimi)", "Low Latency (5s Fast Zap)", 5_000, 15_000, 1_500),
    STANDARD("standard", "Standart Denge (15s)", "Standard Balanced (15s)", 15_000, 50_000, 2_500),
    ANTIFREEZE("antifreeze", "Gelişmiş Donma Önleyici (30s)", "Anti-Freeze Buffer (30s)", 30_000, 90_000, 5_000),
    EXTREME("extreme", "Maksimum Kararlılık (60s)", "Extreme Stability (60s)", 60_000, 150_000, 8_000)
}

enum class UserAgentPreset(val id: String, val title: String, val userAgentString: String) {
    DEFAULT("default", "Stavio Pro (Varsayılan)", "StavioPro/1.0 (Android TV/Mobile; ExoPlayer)"),
    VLC("vlc", "VLC Media Player 3.0", "VLC/3.0.18 LibVLC/3.0.18 (Linux; Android 14)"),
    KODI("kodi", "Kodi 20.2 Nexus", "Kodi/20.2 (Linux; Android 14; Build/UP1A.231005.007)"),
    APPLE("apple", "Apple CoreMedia (iOS Safari)", "AppleCoreMedia/1.0.0.19G68 (iPhone; U; CPU OS 15_6 like Mac OS X)"),
    SMARTERS("smarters", "IPTV Smarters Pro", "IPTVSmartersPro/3.1.5 (Android; Mobile)"),
    CUSTOM("custom", "Özel Tanımlı (Custom)", "")
}

class DeveloperOptionsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("stavio_prefs", Context.MODE_PRIVATE)

    // Hardware Acceleration
    private val _hwAccel = MutableStateFlow(
        HwAccelMode.entries.find { it.id == prefs.getString("dev_hw_accel", "auto") } ?: HwAccelMode.AUTO
    )
    val hwAccel: StateFlow<HwAccelMode> = _hwAccel.asStateFlow()

    // Buffer Mode
    private val _bufferMode = MutableStateFlow(
        BufferMode.entries.find { it.id == prefs.getString("dev_buffer_mode", "standard") } ?: BufferMode.STANDARD
    )
    val bufferMode: StateFlow<BufferMode> = _bufferMode.asStateFlow()

    // User Agent Preset
    private val _userAgentPreset = MutableStateFlow(
        UserAgentPreset.entries.find { it.id == prefs.getString("dev_user_agent_preset", "default") } ?: UserAgentPreset.DEFAULT
    )
    val userAgentPreset: StateFlow<UserAgentPreset> = _userAgentPreset.asStateFlow()

    private val _customUserAgent = MutableStateFlow(
        prefs.getString("dev_custom_user_agent", "") ?: ""
    )
    val customUserAgent: StateFlow<String> = _customUserAgent.asStateFlow()

    // FPS & Stream Stats Overlay
    private val _showFpsOverlay = MutableStateFlow(
        prefs.getBoolean("dev_show_fps_overlay", false)
    )
    val showFpsOverlay: StateFlow<Boolean> = _showFpsOverlay.asStateFlow()

    // SSL Bypass
    private val _bypassSsl = MutableStateFlow(
        prefs.getBoolean("dev_bypass_ssl", true)
    )
    val bypassSsl: StateFlow<Boolean> = _bypassSsl.asStateFlow()

    // Network Timeout
    private val _networkTimeoutSeconds = MutableStateFlow(
        prefs.getInt("dev_network_timeout", 15)
    )
    val networkTimeoutSeconds: StateFlow<Int> = _networkTimeoutSeconds.asStateFlow()

    // Real-time in-memory debug logs (Circular buffer max 100 entries)
    private val _debugLogs = MutableStateFlow<List<String>>(
        listOf("[${getTimestamp()}] Stavio Engine initialized.")
    )
    val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()

    fun log(message: String) {
        val entry = "[${getTimestamp()}] $message"
        val current = _debugLogs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 100) {
            current.removeAt(current.size - 1)
        }
        _debugLogs.value = current
    }

    private fun getTimestamp(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    }

    fun setHwAccel(mode: HwAccelMode) {
        prefs.edit().putString("dev_hw_accel", mode.id).apply()
        _hwAccel.value = mode
        log("Donanım hızlandırma modu ayarlandı: ${mode.name}")
    }

    fun setBufferMode(mode: BufferMode) {
        prefs.edit().putString("dev_buffer_mode", mode.id).apply()
        _bufferMode.value = mode
        log("Arabellek modu değiştirildi: ${mode.name} (Min: ${mode.minBufferMs}ms, Max: ${mode.maxBufferMs}ms)")
    }

    fun setUserAgentPreset(preset: UserAgentPreset, custom: String = "") {
        prefs.edit()
            .putString("dev_user_agent_preset", preset.id)
            .putString("dev_custom_user_agent", custom)
            .apply()
        _userAgentPreset.value = preset
        _customUserAgent.value = custom
        log("User-Agent güncellendi: ${preset.title}")
    }

    fun setShowFpsOverlay(show: Boolean) {
        prefs.edit().putBoolean("dev_show_fps_overlay", show).apply()
        _showFpsOverlay.value = show
        log("Canlı FPS ve Akış İstatistikleri katmanı: ${if (show) "AÇIK" else "KAPALI"}")
    }

    fun setBypassSsl(bypass: Boolean) {
        prefs.edit().putBoolean("dev_bypass_ssl", bypass).apply()
        _bypassSsl.value = bypass
        log("Güvensiz SSL doğrulaması: ${if (bypass) "ETKİN" else "DEVRE DIŞI"}")
    }

    fun setNetworkTimeout(seconds: Int) {
        prefs.edit().putInt("dev_network_timeout", seconds).apply()
        _networkTimeoutSeconds.value = seconds
        log("Ağ zaman aşımı süresi: ${seconds}s")
    }

    fun resetToDefaults() {
        prefs.edit()
            .remove("dev_hw_accel")
            .remove("dev_buffer_mode")
            .remove("dev_user_agent_preset")
            .remove("dev_custom_user_agent")
            .remove("dev_show_fps_overlay")
            .remove("dev_bypass_ssl")
            .remove("dev_network_timeout")
            .apply()

        _hwAccel.value = HwAccelMode.AUTO
        _bufferMode.value = BufferMode.STANDARD
        _userAgentPreset.value = UserAgentPreset.DEFAULT
        _customUserAgent.value = ""
        _showFpsOverlay.value = false
        _bypassSsl.value = true
        _networkTimeoutSeconds.value = 15
        log("Tüm geliştirici ayarları varsayılana sıfırlandı.")
    }

    fun getEffectiveUserAgent(): String {
        return when (_userAgentPreset.value) {
            UserAgentPreset.CUSTOM -> _customUserAgent.value.ifEmpty { UserAgentPreset.DEFAULT.userAgentString }
            else -> _userAgentPreset.value.userAgentString
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: DeveloperOptionsManager? = null

        fun getInstance(context: Context): DeveloperOptionsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeveloperOptionsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
