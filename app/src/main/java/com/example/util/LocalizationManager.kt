package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val flag: String
) {
    TR("tr", "Türkçe", "Turkish", "🇹🇷"),
    EN("en", "English", "English", "🇬🇧"),
    ES("es", "Español", "Spanish", "🇪🇸"),
    FR("fr", "Français", "French", "🇫🇷"),
    DE("de", "Deutsch", "German", "🇩🇪"),
    AR("ar", "العربية", "Arabic", "🇸🇦");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: TR
        }
    }
}

class LocalizationManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("stavio_prefs", Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(
        AppLanguage.fromCode(prefs.getString("app_language", "tr") ?: "tr")
    )
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private val _hasSelectedInitialLanguage = MutableStateFlow(
        prefs.getBoolean("has_selected_language", false)
    )
    val hasSelectedInitialLanguage: StateFlow<Boolean> = _hasSelectedInitialLanguage.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        prefs.edit()
            .putString("app_language", language.code)
            .putBoolean("has_selected_language", true)
            .apply()
        _currentLanguage.value = language
        _hasSelectedInitialLanguage.value = true
    }

    // Dynamic localized strings lookup
    fun getString(key: String): String {
        val lang = _currentLanguage.value
        return translations[key]?.get(lang) 
            ?: translations[key]?.get(AppLanguage.EN) 
            ?: translations[key]?.get(AppLanguage.TR) 
            ?: key
    }

    companion object {
        @Volatile
        private var INSTANCE: LocalizationManager? = null

        fun getInstance(context: Context): LocalizationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalizationManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private val translations: Map<String, Map<AppLanguage, String>> = mapOf(
            // Navigation
            "nav_live_tv" to mapOf(
                AppLanguage.TR to "Canlı TV",
                AppLanguage.EN to "Live TV",
                AppLanguage.ES to "TV en Vivo",
                AppLanguage.FR to "TV en Direct",
                AppLanguage.DE to "Live-TV",
                AppLanguage.AR to "بث مباشر"
            ),
            "nav_movies" to mapOf(
                AppLanguage.TR to "Filmler",
                AppLanguage.EN to "Movies",
                AppLanguage.ES to "Películas",
                AppLanguage.FR to "Films",
                AppLanguage.DE to "Filme",
                AppLanguage.AR to "أفلام"
            ),
            "nav_series" to mapOf(
                AppLanguage.TR to "Diziler",
                AppLanguage.EN to "Series",
                AppLanguage.ES to "Series",
                AppLanguage.FR to "Séries",
                AppLanguage.DE to "Serien",
                AppLanguage.AR to "مسلسلات"
            ),
            "nav_playlists" to mapOf(
                AppLanguage.TR to "IPTV Listeleri",
                AppLanguage.EN to "Playlists",
                AppLanguage.ES to "Listas IPTV",
                AppLanguage.FR to "Listes IPTV",
                AppLanguage.DE to "Wiedergabelisten",
                AppLanguage.AR to "قوائم التشغيل"
            ),
            "nav_settings" to mapOf(
                AppLanguage.TR to "Ayarlar",
                AppLanguage.EN to "Settings",
                AppLanguage.ES to "Ajustes",
                AppLanguage.FR to "Paramètres",
                AppLanguage.DE to "Einstellungen",
                AppLanguage.AR to "الإعدادات"
            ),

            // General & Common
            "app_name" to mapOf(
                AppLanguage.TR to "Stavio IPTV Pro",
                AppLanguage.EN to "Stavio IPTV Pro",
                AppLanguage.ES to "Stavio IPTV Pro",
                AppLanguage.FR to "Stavio IPTV Pro",
                AppLanguage.DE to "Stavio IPTV Pro",
                AppLanguage.AR to "ستافيو آي بي تي في برو"
            ),
            "search_placeholder" to mapOf(
                AppLanguage.TR to "Kanal, film veya dizi ara...",
                AppLanguage.EN to "Search channels, movies, series...",
                AppLanguage.ES to "Buscar canales, películas, series...",
                AppLanguage.FR to "Rechercher chaînes, films, séries...",
                AppLanguage.DE to "Kanäle, Filme oder Serien suchen...",
                AppLanguage.AR to "ابحث عن القنوات، الأفلام، المسلسلات..."
            ),
            "search_no_results" to mapOf(
                AppLanguage.TR to "Eşleşen içerik bulunamadı",
                AppLanguage.EN to "No matching content found",
                AppLanguage.ES to "No se encontró contenido coincidente",
                AppLanguage.FR to "Aucun contenu correspondant trouvé",
                AppLanguage.DE to "Keine passenden Inhalte gefunden",
                AppLanguage.AR to "لم يتم العثور على محتوى مطابق"
            ),
            "all" to mapOf(
                AppLanguage.TR to "Tümü",
                AppLanguage.EN to "All",
                AppLanguage.ES to "Todos",
                AppLanguage.FR to "Tout",
                AppLanguage.DE to "Alle",
                AppLanguage.AR to "الكل"
            ),
            "favorites" to mapOf(
                AppLanguage.TR to "★ Favoriler",
                AppLanguage.EN to "★ Favorites",
                AppLanguage.ES to "★ Favoritos",
                AppLanguage.FR to "★ Favoris",
                AppLanguage.DE to "★ Favoriten",
                AppLanguage.AR to "★ المفضلة"
            ),
            "play_now" to mapOf(
                AppLanguage.TR to "Hemen İzle",
                AppLanguage.EN to "Watch Now",
                AppLanguage.ES to "Ver Ahora",
                AppLanguage.FR to "Regarder Maintenant",
                AppLanguage.DE to "Jetzt Anschauen",
                AppLanguage.AR to "شاهد الآن"
            ),
            "details" to mapOf(
                AppLanguage.TR to "Detaylar",
                AppLanguage.EN to "Details",
                AppLanguage.ES to "Detalles",
                AppLanguage.FR to "Détails",
                AppLanguage.DE to "Details",
                AppLanguage.AR to "التفاصيل"
            ),
            "live_badge" to mapOf(
                AppLanguage.TR to "● CANLI",
                AppLanguage.EN to "● LIVE",
                AppLanguage.ES to "● EN VIVO",
                AppLanguage.FR to "● DIRECT",
                AppLanguage.DE to "● LIVE",
                AppLanguage.AR to "● مباشر"
            ),
            "loading" to mapOf(
                AppLanguage.TR to "Yükleniyor...",
                AppLanguage.EN to "Loading...",
                AppLanguage.ES to "Cargando...",
                AppLanguage.FR to "Chargement...",
                AppLanguage.DE to "Wird geladen...",
                AppLanguage.AR to "جارٍ التحميل..."
            ),
            "cancel" to mapOf(
                AppLanguage.TR to "İptal",
                AppLanguage.EN to "Cancel",
                AppLanguage.ES to "Cancelar",
                AppLanguage.FR to "Annuler",
                AppLanguage.DE to "Abbrechen",
                AppLanguage.AR to "إلغاء"
            ),
            "save" to mapOf(
                AppLanguage.TR to "Kaydet",
                AppLanguage.EN to "Save",
                AppLanguage.ES to "Guardar",
                AppLanguage.FR to "Enregistrer",
                AppLanguage.DE to "Speichern",
                AppLanguage.AR to "حفظ"
            ),
            "confirm" to mapOf(
                AppLanguage.TR to "Onayla",
                AppLanguage.EN to "Confirm",
                AppLanguage.ES to "Confirmar",
                AppLanguage.FR to "Confirmer",
                AppLanguage.DE to "Bestätigen",
                AppLanguage.AR to "تأكيد"
            ),
            "continue_action" to mapOf(
                AppLanguage.TR to "Devam Et",
                AppLanguage.EN to "Continue",
                AppLanguage.ES to "Continuar",
                AppLanguage.FR to "Continuer",
                AppLanguage.DE to "Weiter",
                AppLanguage.AR to "متابعة"
            ),
            "close" to mapOf(
                AppLanguage.TR to "Kapat",
                AppLanguage.EN to "Close",
                AppLanguage.ES to "Cerrar",
                AppLanguage.FR to "Fermer",
                AppLanguage.DE to "Schließen",
                AppLanguage.AR to "إغلاق"
            ),

            // Language Selection Onboarding
            "welcome_title" to mapOf(
                AppLanguage.TR to "Stavio'ya Hoş Geldiniz",
                AppLanguage.EN to "Welcome to Stavio",
                AppLanguage.ES to "Bienvenido a Stavio",
                AppLanguage.FR to "Bienvenue sur Stavio",
                AppLanguage.DE to "Willkommen bei Stavio",
                AppLanguage.AR to "مرحبًا بك في ستافيو"
            ),
            "select_language_desc" to mapOf(
                AppLanguage.TR to "Lütfen tercih ettiğiniz uygulama dilini seçin. Bunu daha sonra ayarlardan değiştirebilirsiniz.",
                AppLanguage.EN to "Please select your preferred application language. You can change this later in settings.",
                AppLanguage.ES to "Por favor, seleccione el idioma preferido. Puede cambiarlo más tarde en ajustes.",
                AppLanguage.FR to "Veuillez choisir la langue de l'application. Vous pourrez la modifier ultérieurement dans les paramètres.",
                AppLanguage.DE to "Bitte wählen Sie Ihre bevorzugte Sprache. Sie können dies später in den Einstellungen ändern.",
                AppLanguage.AR to "يرجى تحديد لغة التطبيق المفضلة لديك. يمكنك تغيير هذا لاحقًا في الإعدادات."
            ),

            // Settings Screen
            "settings_player_section" to mapOf(
                AppLanguage.TR to "OYNATICI AYARLARI",
                AppLanguage.EN to "PLAYER SETTINGS",
                AppLanguage.ES to "AJUSTES DE REPRODUCTOR",
                AppLanguage.FR to "PARAMÈTRES DU LECTEUR",
                AppLanguage.DE to "PLAYER-EINSTELLUNGEN",
                AppLanguage.AR to "إعدادات المشغل"
            ),
            "settings_screen_lock_title" to mapOf(
                AppLanguage.TR to "Ekranı Uyanık Tut",
                AppLanguage.EN to "Keep Screen Awake",
                AppLanguage.ES to "Mantener Pantalla Encendida",
                AppLanguage.FR to "Garder l'Écran Allumé",
                AppLanguage.DE to "Bildschirm Aktiv Halten",
                AppLanguage.AR to "إبقاء الشاشة قيد التشغيل"
            ),
            "settings_screen_lock_desc" to mapOf(
                AppLanguage.TR to "Oynatma sırasında ekranın kapanmasını engeller",
                AppLanguage.EN to "Prevents the screen from sleeping during playback",
                AppLanguage.ES to "Evita que la pantalla se apague durante la reproducción",
                AppLanguage.FR to "Empêche l'écran de s'éteindre pendant la lecture",
                AppLanguage.DE to "Verhindert das Ausschalten des Bildschirms während der Wiedergabe",
                AppLanguage.AR to "يمنع قفل الشاشة أثناء تشغيل الفيديو"
            ),
            "settings_aspect_ratio_title" to mapOf(
                AppLanguage.TR to "Video Boyutu (Ölçekleme)",
                AppLanguage.EN to "Video Scale Mode",
                AppLanguage.ES to "Escala de Vídeo",
                AppLanguage.FR to "Mise à l'Échelle Vidéo",
                AppLanguage.DE to "Video-Skalierungsmodus",
                AppLanguage.AR to "وضع قياس الفيديو"
            ),
            "settings_providers_section" to mapOf(
                AppLanguage.TR to "SAĞLAYICI VE LİSTELER",
                AppLanguage.EN to "PROVIDERS & PLAYLISTS",
                AppLanguage.ES to "PROVEEDORES Y LISTAS",
                AppLanguage.FR to "FOURNISSEURS ET LISTES",
                AppLanguage.DE to "ANBIETER & LISTEN",
                AppLanguage.AR to "المزودون وقوائم التشغيل"
            ),
            "settings_manage_playlists" to mapOf(
                AppLanguage.TR to "Oynatma Listelerini Yönet",
                AppLanguage.EN to "Manage Playlists",
                AppLanguage.ES to "Administrar Listas",
                AppLanguage.FR to "Gérer les Listes de Lecture",
                AppLanguage.DE to "Wiedergabelisten Verwalten",
                AppLanguage.AR to "إدارة قوائم التشغيل"
            ),
            "settings_manage_playlists_desc" to mapOf(
                AppLanguage.TR to "M3U ve MAG listelerini ekle, düzenle veya sil",
                AppLanguage.EN to "Add, edit, or remove M3U and MAG playlists",
                AppLanguage.ES to "Añadir, editar o eliminar listas M3U y MAG",
                AppLanguage.FR to "Ajouter, modifier ou supprimer des listes M3U et MAG",
                AppLanguage.DE to "M3U- und MAG-Listen hinzufügen, bearbeiten oder löschen",
                AppLanguage.AR to "إضافة قوائم M3U و MAG أو تعديلها أو حذفها"
            ),
            "settings_clear_cache" to mapOf(
                AppLanguage.TR to "Önbelleği ve Verileri Temizle",
                AppLanguage.EN to "Clear Cache & Local Data",
                AppLanguage.ES to "Limpiar Caché y Datos",
                AppLanguage.FR to "Effacer le Cache et les Données",
                AppLanguage.DE to "Cache und Daten Löschen",
                AppLanguage.AR to "مسح ذاكرة التخزين المؤقت والبيانات"
            ),
            "settings_clear_cache_desc" to mapOf(
                AppLanguage.TR to "Kanal önbelleğini, logoları ve geçici dosyaları sıfırla",
                AppLanguage.EN to "Reset channel cache, logos, and temporary stream files",
                AppLanguage.ES to "Restablecer caché de canales, logos y archivos temporales",
                AppLanguage.FR to "Réinitialiser le cache des chaînes, logos et fichiers temporaires",
                AppLanguage.DE to "Kanal-Cache, Logos und temporäre Dateien zurücksetzen",
                AppLanguage.AR to "إعادة ضبط ذاكرة القنوات والشعارات والملفات المؤقتة"
            ),
            "settings_app_info_section" to mapOf(
                AppLanguage.TR to "UYGULAMA VE GÖRÜNÜM",
                AppLanguage.EN to "APPLICATION & THEME",
                AppLanguage.ES to "APLICACIÓN Y TEMA",
                AppLanguage.FR to "APPLICATION ET THÈME",
                AppLanguage.DE to "ANWENDUNG & THEMA",
                AppLanguage.AR to "التطبيق والمظهر"
            ),
            "settings_theme_title" to mapOf(
                AppLanguage.TR to "Görsel Tema",
                AppLanguage.EN to "Visual Theme",
                AppLanguage.ES to "Tema Visual",
                AppLanguage.FR to "Thème Visuel",
                AppLanguage.DE to "Visuelles Design",
                AppLanguage.AR to "المظهر والتصميم"
            ),
            "settings_language_title" to mapOf(
                AppLanguage.TR to "Uygulama Dili",
                AppLanguage.EN to "App Language",
                AppLanguage.ES to "Idioma de la Aplicación",
                AppLanguage.FR to "Langue de l'Application",
                AppLanguage.DE to "App-Sprache",
                AppLanguage.AR to "لغة التطبيق"
            ),
            "settings_about_title" to mapOf(
                AppLanguage.TR to "Stavio Hakkında",
                AppLanguage.EN to "About Stavio",
                AppLanguage.ES to "Acerca de Stavio",
                AppLanguage.FR to "À Propos de Stavio",
                AppLanguage.DE to "Über Stavio",
                AppLanguage.AR to "حول ستافيو"
            ),

            // Developer Options
            "settings_dev_section" to mapOf(
                AppLanguage.TR to "GELİŞTİRİCİ SEÇENEKLERİ",
                AppLanguage.EN to "DEVELOPER OPTIONS",
                AppLanguage.ES to "OPCIONES DE DESARROLLADOR",
                AppLanguage.FR to "OPTIONS DÉVELOPPEUR",
                AppLanguage.DE to "ENTWICKLEROPTIONEN",
                AppLanguage.AR to "خيارات المطور"
            ),
            "dev_hw_accel_title" to mapOf(
                AppLanguage.TR to "Donanım Hızlandırma",
                AppLanguage.EN to "Hardware Acceleration",
                AppLanguage.ES to "Aceleración por Hardware",
                AppLanguage.FR to "Accélération Matérielle",
                AppLanguage.DE to "Hardwarebeschleunigung",
                AppLanguage.AR to "تسريع العتاد"
            ),
            "dev_hw_accel_desc" to mapOf(
                AppLanguage.TR to "Video çözücü: Donanım MediaCodec veya Yazılım yedeği",
                AppLanguage.EN to "Video decoder: Hardware MediaCodec or Software fallback",
                AppLanguage.ES to "Decodificador: MediaCodec por hardware o reserva por software",
                AppLanguage.FR to "Décodeur vidéo: MediaCodec matériel ou secours logiciel",
                AppLanguage.DE to "Videodecoder: Hardware MediaCodec oder Software-Fallback",
                AppLanguage.AR to "مفكك ترميز الفيديو: عتاد MediaCodec أو برنامج بديل"
            ),
            "dev_buffer_title" to mapOf(
                AppLanguage.TR to "Akış Arabelleği (Donma Önleyici)",
                AppLanguage.EN to "Stream Buffer (Anti-Freeze)",
                AppLanguage.ES to "Búfer de Transmisión (Anti-Congelación)",
                AppLanguage.FR to "Mémoire Tampon de Flux (Anti-Figeage)",
                AppLanguage.DE to "Stream-Puffer (Anti-Freeze)",
                AppLanguage.AR to "مخزن البث المؤقت (مانع التقطيع)"
            ),
            "dev_buffer_desc" to mapOf(
                AppLanguage.TR to "Oynatıcı ön-yükleme arabelleği süresini ayarla",
                AppLanguage.EN to "Configure ExoPlayer pre-load buffer duration",
                AppLanguage.ES to "Configurar la duración del búfer de ExoPlayer",
                AppLanguage.FR to "Configurer la durée du tampon ExoPlayer",
                AppLanguage.DE to "ExoPlayer Vorlade-Pufferdauer anpassen",
                AppLanguage.AR to "ضبط مدة التخزين المسبق للمشغل"
            ),
            "dev_user_agent_title" to mapOf(
                AppLanguage.TR to "Özel User-Agent (İstemci Başlığı)",
                AppLanguage.EN to "Custom User-Agent",
                AppLanguage.ES to "User-Agent Personalizado",
                AppLanguage.FR to "User-Agent Personnalisé",
                AppLanguage.DE to "Benutzerdefinierter User-Agent",
                AppLanguage.AR to "معرف المستخدم المخصص (User-Agent)"
            ),
            "dev_user_agent_desc" to mapOf(
                AppLanguage.TR to "Sağlayıcı engellerini aşmak için VLC, Kodi veya Apple taklidi",
                AppLanguage.EN to "Emulate VLC, Kodi, or Apple headers to bypass IPTV blocks",
                AppLanguage.ES to "Emular cabeceras VLC, Kodi o Apple para evitar bloqueos",
                AppLanguage.FR to "Émuler VLC, Kodi ou Apple pour contourner les blocages",
                AppLanguage.DE to "VLC, Kodi oder Apple emulieren, um Sperren zu umgehen",
                AppLanguage.AR to "محاكاة VLC أو Kodi أو Apple لتجاوز قيود المزودين"
            ),
            "dev_fps_overlay_title" to mapOf(
                AppLanguage.TR to "Canlı FPS ve Akış Bilgisi Katmanı",
                AppLanguage.EN to "Live FPS & Stream Stats HUD",
                AppLanguage.ES to "Capa de FPS y Estadísticas de Flujo",
                AppLanguage.FR to "Affichage FPS et Statistiques de Flux",
                AppLanguage.DE to "Live FPS & Stream-Statistiken Overlay",
                AppLanguage.AR to "طبقة معدل الإطارات وإحصائيات البث"
            ),
            "dev_fps_overlay_desc" to mapOf(
                AppLanguage.TR to "Oynatıcıda gerçek zamanlı çözünürlük, FPS ve bit hızını göster",
                AppLanguage.EN to "Display real-time resolution, FPS, bitrate, and codec overlay",
                AppLanguage.ES to "Mostrar resolución, FPS, tasa de bits y códec en tiempo real",
                AppLanguage.FR to "Afficher résolution, FPS, débit binaire et codec en direct",
                AppLanguage.DE to "Echtzeit-Auflösung, FPS, Bitrate und Codec anzeigen",
                AppLanguage.AR to "عرض الدقة، معدل الإطارات، ومعدل البت في الوقت الفعلي"
            ),
            "dev_ssl_bypass_title" to mapOf(
                AppLanguage.TR to "Güvensiz SSL Sertifikalarına İzin Ver",
                AppLanguage.EN to "Allow Self-Signed / Insecure SSL",
                AppLanguage.ES to "Permitir Certificados SSL No Seguros",
                AppLanguage.FR to "Autoriser Certificats SSL Non Sécurisés",
                AppLanguage.DE to "Selbstsignierte / Unsichere SSL Erlauben",
                AppLanguage.AR to "السماح بشهادات SSL غير الموثوقة"
            ),
            "dev_ssl_bypass_desc" to mapOf(
                AppLanguage.TR to "Eski veya süresi geçmiş SSL sertifikalı IPTV sunucularını açar",
                AppLanguage.EN to "Enables streams with expired or self-signed server SSL certs",
                AppLanguage.ES to "Permite transmisiones con certificados SSL caducados",
                AppLanguage.FR to "Active les flux avec certificats SSL expirés ou auto-signés",
                AppLanguage.DE to "Ermöglicht Streams mit abgelaufenen oder selbstsignierten Zertifikaten",
                AppLanguage.AR to "تشغيل البث مع شهادات SSL منتهية أو ذاتية التوقيع"
            ),
            "dev_timeout_title" to mapOf(
                AppLanguage.TR to "Ağ Bağlantı Zaman Aşımı",
                AppLanguage.EN to "Network Connection Timeout",
                AppLanguage.ES to "Tiempo de Espera de Red",
                AppLanguage.FR to "Délai d'Attente Réseau",
                AppLanguage.DE to "Netzwerk-Timeout",
                AppLanguage.AR to "مهلة اتصال الشبكة"
            ),
            "dev_logs_title" to mapOf(
                AppLanguage.TR to "Canlı Akış ve Hata Günlükleri",
                AppLanguage.EN to "Live Stream & Debug Logs",
                AppLanguage.ES to "Registros de Transmisión y Depuración",
                AppLanguage.FR to "Journaux de Flux et Débogage",
                AppLanguage.DE to "Live-Stream- und Debug-Protokolle",
                AppLanguage.AR to "سجلات البث المباشر وتصحيح الأخطاء"
            ),
            "dev_logs_desc" to mapOf(
                AppLanguage.TR to "Bağlantı yanıtlarını, HTTP durumlarını ve hataları incele",
                AppLanguage.EN to "Inspect connection responses, HTTP statuses, and player errors",
                AppLanguage.ES to "Inspeccionar respuestas, códigos HTTP y errores",
                AppLanguage.FR to "Inspecter les réponses de connexion, statuts HTTP et erreurs",
                AppLanguage.DE to "Verbindungsantworten, HTTP-Codes und Fehler analysieren",
                AppLanguage.AR to "فحص استجابات الاتصال وحالات HTTP وأخطاء المشغل"
            ),
            "dev_reset_title" to mapOf(
                AppLanguage.TR to "Geliştirici Ayarlarını Sıfırla",
                AppLanguage.EN to "Reset Developer Options",
                AppLanguage.ES to "Restablecer Opciones de Desarrollador",
                AppLanguage.FR to "Réinitialiser les Options Développeur",
                AppLanguage.DE to "Entwickleroptionen Zurücksetzen",
                AppLanguage.AR to "إعادة ضبط خيارات المطور"
            ),
            "dev_reset_desc" to mapOf(
                AppLanguage.TR to "Tüm gelişmiş ağ ve çözücü parametrelerini varsayılana döndür",
                AppLanguage.EN to "Restore all network and decoder parameters to defaults",
                AppLanguage.ES to "Restaurar parámetros de red y decodificador a valores predeterminados",
                AppLanguage.FR to "Rétablir tous les paramètres réseau et décodeur par défaut",
                AppLanguage.DE to "Alle Netzwerk- und Decoder-Parameter auf Standard zurücksetzen",
                AppLanguage.AR to "استعادة كافة معلمات الشبكة والمشغل إلى الوضع الافتراضي"
            )
        )
    }
}
