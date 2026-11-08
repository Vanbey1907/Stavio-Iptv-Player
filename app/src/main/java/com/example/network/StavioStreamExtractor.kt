package com.example.network

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class ExtractedStreamSource(
    val id: String,
    val title: String,
    val streamUrl: String,
    val format: String, // "M3U8", "MP4", "TS", "MPD", "MKV", "WEBM", "FLV", "STREAM"
    val quality: String = "Otomatik",
    val isHiddenSource: Boolean = false,
    val discoveryMethod: String = "Doğrudan Analiz",
    val referer: String = "",
    val origin: String = "",
    val cookie: String = "",
    val userAgent: String = DEFAULT_BROWSER_UA,
    val pageUrl: String = "",
    val addedAtMillis: Long = System.currentTimeMillis()
) {
    /**
     * ExoPlayer (PlayerView) için gerekli Referer / Origin / Cookie / User-Agent / Format
     * bilgilerini güvenli (URL-encoded) pipe (|) formatında birleştirir.
     */
    fun toPlayableUrlWithHeaders(): String {
        val cleanBase = streamUrl.substringBefore("|").trim()
        val headers = mutableListOf<String>()
        fun addParam(k: String, v: String) {
            if (v.isNotBlank()) {
                val encoded = runCatching { URLEncoder.encode(v, "UTF-8") }.getOrDefault(v)
                headers.add("$k=$encoded")
            }
        }
        addParam("Referer", referer)
        addParam("Origin", origin)
        val effectiveCookie = cookie.ifBlank {
            runCatching {
                CookieManager.getInstance().getCookie(cleanBase)
                    ?: if (referer.isNotBlank()) CookieManager.getInstance().getCookie(referer) else null
            }.getOrNull().orEmpty()
        }
        addParam("Cookie", effectiveCookie)
        addParam("User-Agent", userAgent)
        addParam("StreamFormat", format)
        return if (headers.isEmpty()) {
            cleanBase
        } else {
            "$cleanBase|${headers.joinToString("&")}"
        }
    }

    companion object {
        const val DEFAULT_BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

object StavioStreamExtractor {

    private const val PREFS_NAME = "stavio_saved_streams_prefs"
    private const val KEY_SAVED_STREAMS = "saved_extracted_streams_v1"

    // Tüm sertifikaları kabul eden esnek HTTP istemcisi (IPTV ve CDN sunucularındaki SSL hatalarını önler)
    val permissiveHttpClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // Doğrudan medya uzantılı bağlantılar (.m3u8, .mp4, .ts, .mpd, .mkv, .webm, .flv, .mov)
    private val DIRECT_MEDIA_REGEX = Regex(
        """((?:https?:)?//[^\s"'<>\\`\)\]]+?\.(?:m3u8|mp4|ts|mpd|mkv|webm|flv|mov|m4v)(?:\?[^\s"'<>\\`\)\]]*)?)""",
        RegexOption.IGNORE_CASE
    )

    // HLS / Manifest / Playlist uç noktaları (manifest.json gibi sahte eşleşmeler hariç)
    private val STREAM_ENDPOINT_REGEX = Regex(
        """((?:https?:)?//[^\s"'<>\\`\)\]]+?(?:/hls/[^\s"'<>\\`\)\]]+|/playlist\.m3u8[^\s"'<>\\`\)\]]*|master\.m3u8[^\s"'<>\\`\)\]]*|index\.m3u8[^\s"'<>\\`\)\]]*|chunklist[^\s"'<>\\`\)\]]*\.m3u8|output=(?:m3u8|ts|hls)[^\s"'<>\\`\)\]]*|/live/[^/\s"'<>\\]+/[^/\s"'<>\\]+/\d+(?:\.ts|\.m3u8)?))""",
        RegexOption.IGNORE_CASE
    )

    // Oynatıcı nesneleri içindeki kaynak tanımları (jwplayer, Clappr, Playerjs, Video.js, Hls.js, Artplayer vb.)
    private val PLAYER_SOURCE_PROP_REGEX = Regex(
        """["']?(?:file|src|source|hls|dash|stream|stream_url|streamUrl|video_url|videoUrl|contentUrl|embedUrl|manifest)["']?\s*[:=]\s*["']((?:https?:)?//[^"'\s<>\\]+|/[^"'\s<>\\]+\.(?:m3u8|mp4|ts|mpd|txt)[^"'\s<>\\]*)["']""",
        RegexOption.IGNORE_CASE
    )

    // Hem normal src hem de lazy-loaded (data-src, data-litespeed-src, data-lazy-src, data-frame, data-embed) iframe/embed etiketleri
    private val IFRAME_AND_EMBED_REGEX = Regex(
        """<(?:iframe|embed|frame|div|button|li|a)[^>]+?(?:src|data-src|data-lazy-src|data-litespeed-src|data-frame|data-embed|data-url|data-video|data-player)\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    // Sayfa içi kanal / film / dizi alt sayfaları (Direkt site linki girildiğinde alt sayfaları bulmak için)
    private val ANCHOR_HREF_REGEX = Regex(
        """<a[^>]+href\s*=\s*["']([^"'#]+)["'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val SCRIPT_SRC_REGEX = Regex(
        """<script[^>]+src\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    private val ATOB_BASE64_REGEX = Regex(
        """(?:atob|Base64\.decode)\s*\(\s*["']([A-Za-z0-9+/=]{16,})["']\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val RAW_BASE64_HTTP_REGEX = Regex(
        """["']((?:aHR0cHM6Ly|aHR0cDovL)[A-Za-z0-9+/=]{12,})["']"""
    )

    private val PACKED_JS_REGEX = Regex(
        """eval\(function\(p,a,c,k,e,[dr]\)\{.*?\}\(\s*'(.*?)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'(.*?)'\.split\('\|'\)""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    /**
     * Girilen URL'yi (doğrudan yayın linki, embed linki veya direkt ana site linki) derinlemesine tarar,
     * bulunan tüm aday linklerin gerçekten çalışıp çalışmadığını HTTP üzerinden doğrular ve
     * yalnızca çalışan gerçek video/yayın kaynaklarını döndürür.
     */
    suspend fun analyzeUrlDeep(
        rawInputUrl: String,
        onProgress: (String) -> Unit = {},
        onSourceFound: (ExtractedStreamSource) -> Unit = {}
    ): List<ExtractedStreamSource> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeInputUrl(rawInputUrl)
        if (normalizedUrl.isBlank()) return@withContext emptyList()

        val verifiedMap = Collections.synchronizedMap(LinkedHashMap<String, ExtractedStreamSource>())
        val checkedCandidateKeys = Collections.synchronizedSet(HashSet<String>())
        val verifySemaphore = Semaphore(6)

        suspend fun verifyAndRegister(candidate: ExtractedStreamSource) {
            val cleanUrl = cleanCandidateUrl(candidate.streamUrl)
            if (!isValidStreamCandidate(cleanUrl)) return

            // Tekil 2 saniyelik HLS .ts segment parçalarını (seg-1.ts, chunk-001.ts) ele
            if (isLikelyHlsSegmentChunk(cleanUrl)) return

            val key = cleanUrl.lowercase(Locale.ROOT)
            if (!checkedCandidateKeys.add(key)) return

            val verified = verifySemaphore.withPermit {
                verifyStreamPlayability(candidate.copy(streamUrl = cleanUrl))
            }
            if (verified != null) {
                val finalKey = verified.streamUrl.lowercase(Locale.ROOT)
                synchronized(verifiedMap) {
                    if (!verifiedMap.containsKey(finalKey)) {
                        val finalSrc = verified.copy(
                            id = "src_${finalKey.hashCode().toUInt()}"
                        )
                        verifiedMap[finalKey] = finalSrc
                        onSourceFound(finalSrc)
                    }
                }
            }
        }

        val baseOrigin = extractOrigin(normalizedUrl)

        onProgress("Bağlantı inceleniyor...")

        // 1. Önce girilen URL'nin kendisini çek
        val primaryResponse = fetchPageContent(normalizedUrl, referer = baseOrigin)
        if (primaryResponse == null) {
            // Eğer doğrudan medya uzantılıysa yine de doğrula
            if (looksLikeDirectStreamUrl(normalizedUrl)) {
                verifyAndRegister(
                    ExtractedStreamSource(
                        id = "",
                        title = guessStreamTitle(normalizedUrl, "Doğrudan Akış"),
                        streamUrl = normalizedUrl,
                        format = detectFormatBadge(normalizedUrl),
                        quality = guessQualityLabel(normalizedUrl),
                        isHiddenSource = false,
                        discoveryMethod = "Doğrudan Bağlantı",
                        referer = baseOrigin,
                        origin = baseOrigin,
                        pageUrl = normalizedUrl
                    )
                )
            }
            return@withContext verifiedMap.values.toList()
        }

        val contentType = primaryResponse.contentType.lowercase(Locale.ROOT)
        val body = primaryResponse.body
        val finalUrl = primaryResponse.finalUrl
        val finalOrigin = extractOrigin(finalUrl)

        // Durum A: Girilen link doğrudan bir M3U / M3U8 listesi ise
        if (body.trimStart().startsWith("#EXTM3U")) {
            onProgress("M3U / M3U8 yayın listesi ayrıştırılıyor...")
            // Ana M3U8 akışının kendisini de ekle (eğer HLS yayınıysa)
            if (body.contains("#EXT-X-STREAM-INF") || body.contains("#EXT-X-TARGETDURATION")) {
                val directM3u8 = ExtractedStreamSource(
                    id = "src_${finalUrl.lowercase(Locale.ROOT).hashCode().toUInt()}",
                    title = guessStreamTitle(finalUrl, "HLS Canlı / Video Akışı"),
                    streamUrl = finalUrl,
                    format = "M3U8",
                    quality = "Otomatik (Master)",
                    isHiddenSource = false,
                    discoveryMethod = "Doğrudan HLS Akışı",
                    referer = baseOrigin,
                    origin = finalOrigin,
                    cookie = primaryResponse.cookie,
                    pageUrl = normalizedUrl
                )
                verifiedMap[finalUrl.lowercase(Locale.ROOT)] = directM3u8
                onSourceFound(directM3u8)
            }

            val parsedItems = mutableListOf<ExtractedStreamSource>()
            parseM3uOrMasterPlaylist(
                m3uContent = body,
                baseUrl = finalUrl,
                referer = baseOrigin,
                parentTitle = guessStreamTitle(finalUrl, "Yayın"),
                onCollect = { parsedItems.add(it) }
            )
            parsedItems.take(60).forEach { item ->
                val finalKey = item.streamUrl.lowercase(Locale.ROOT)
                if (!verifiedMap.containsKey(finalKey)) {
                    val withId = item.copy(
                        id = "src_${finalKey.hashCode().toUInt()}",
                        cookie = primaryResponse.cookie
                    )
                    verifiedMap[finalKey] = withId
                    onSourceFound(withId)
                }
            }
            return@withContext verifiedMap.values.toList()
        }

        // Durum B: Girilen link doğrudan bir MP4 / TS / Video dosyası ise
        if (
            contentType.startsWith("video/") ||
            contentType.contains("mpegurl") ||
            contentType.contains("dash+xml") ||
            (contentType.contains("octet-stream") && looksLikeDirectStreamUrl(finalUrl))
        ) {
            val directSrc = ExtractedStreamSource(
                id = "src_${finalUrl.lowercase(Locale.ROOT).hashCode().toUInt()}",
                title = guessStreamTitle(finalUrl, "Doğrudan Medya"),
                streamUrl = finalUrl,
                format = detectFormatBadge(finalUrl, contentType),
                quality = guessQualityLabel(finalUrl),
                isHiddenSource = false,
                discoveryMethod = "Doğrudan Video Akışı",
                referer = baseOrigin,
                origin = finalOrigin,
                cookie = primaryResponse.cookie,
                pageUrl = normalizedUrl
            )
            verifiedMap[finalUrl.lowercase(Locale.ROOT)] = directSrc
            onSourceFound(directSrc)
            return@withContext verifiedMap.values.toList()
        }

        // Durum C: Girilen link bir Web Sayfası veya Direkt Site Linki ise
        if (body.isNotBlank()) {
            val pageTitle = extractHtmlTitle(body).ifBlank {
                guessStreamTitle(finalUrl, "Site Kaynağı")
            }

            onProgress("Sayfa kaynakları, gizli scriptler ve oynatıcılar taranıyor...")
            val rawCandidates = Collections.synchronizedList(mutableListOf<ExtractedStreamSource>())
            val discoveredEmbedUrls = Collections.synchronizedSet(LinkedHashSet<String>())

            collectCandidatesFromHtml(
                content = body,
                baseUrl = finalUrl,
                pageTitle = pageTitle,
                referer = finalUrl,
                origin = finalOrigin,
                cookie = primaryResponse.cookie,
                methodPrefix = "Sayfa Kaynağı",
                outCandidates = rawCandidates,
                outEmbedUrls = discoveredEmbedUrls
            )

            // Sayfadaki adayları doğrula
            coroutineScope {
                rawCandidates.toList().take(25).map { cand ->
                    async { verifyAndRegister(cand) }
                }.awaitAll()
            }

            // 2. Gömülü Iframe / Embed / Lazy-Iframe adreslerini derinlemesine tara
            val embedList = discoveredEmbedUrls.toList().take(8)
            if (embedList.isNotEmpty()) {
                onProgress("${embedList.size} gömülü oynatıcı (embed/iframe) çözülüyor...")
                val embedCandidates = Collections.synchronizedList(mutableListOf<ExtractedStreamSource>())
                val nestedEmbeds = Collections.synchronizedSet(LinkedHashSet<String>())

                coroutineScope {
                    embedList.mapIndexed { idx, embedUrl ->
                        async {
                            val embedResp = fetchPageContent(embedUrl, referer = finalUrl)
                            if (embedResp != null) {
                                val eType = embedResp.contentType.lowercase(Locale.ROOT)
                                if (embedResp.body.trimStart().startsWith("#EXTM3U") || eType.contains("mpegurl") || eType.startsWith("video/")) {
                                    embedCandidates.add(
                                        ExtractedStreamSource(
                                            id = "",
                                            title = "$pageTitle (Oynatıcı #${idx + 1})",
                                            streamUrl = embedResp.finalUrl,
                                            format = detectFormatBadge(embedResp.finalUrl, eType),
                                            quality = guessQualityLabel(embedResp.finalUrl),
                                            isHiddenSource = true,
                                            discoveryMethod = "Gömülü Oynatıcı #${idx + 1}",
                                            referer = finalUrl,
                                            origin = extractOrigin(embedResp.finalUrl),
                                            cookie = embedResp.cookie
                                        )
                                    )
                                } else if (embedResp.body.isNotBlank()) {
                                    val eTitle = extractHtmlTitle(embedResp.body)
                                        .takeIf { it.isNotBlank() && !it.equals("Document", ignoreCase = true) }
                                        ?: pageTitle
                                    collectCandidatesFromHtml(
                                        content = embedResp.body,
                                        baseUrl = embedResp.finalUrl,
                                        pageTitle = eTitle,
                                        referer = embedResp.finalUrl,
                                        origin = extractOrigin(embedResp.finalUrl),
                                        cookie = embedResp.cookie,
                                        methodPrefix = "Gizli Oynatıcı #${idx + 1}",
                                        outCandidates = embedCandidates,
                                        outEmbedUrls = nestedEmbeds
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }

                // İkinci seviye (iç içe) iframe varsa onları da çöz (ör. player -> embed -> m3u8)
                val secondLevel = (nestedEmbeds - embedList.toSet()).take(5)
                if (secondLevel.isNotEmpty()) {
                    coroutineScope {
                        secondLevel.mapIndexed { idx, nestedUrl ->
                            async {
                                val nestedResp = fetchPageContent(nestedUrl, referer = embedList.firstOrNull() ?: finalUrl)
                                if (nestedResp != null && nestedResp.body.isNotBlank()) {
                                    collectCandidatesFromHtml(
                                        content = nestedResp.body,
                                        baseUrl = nestedResp.finalUrl,
                                        pageTitle = pageTitle,
                                        referer = nestedResp.finalUrl,
                                        origin = extractOrigin(nestedResp.finalUrl),
                                        cookie = nestedResp.cookie,
                                        methodPrefix = "Derin Embed #${idx + 1}",
                                        outCandidates = embedCandidates,
                                        outEmbedUrls = Collections.synchronizedSet(LinkedHashSet())
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                }

                coroutineScope {
                    embedCandidates.toList().take(30).map { cand ->
                        async { verifyAndRegister(cand) }
                    }.awaitAll()
                }
            }

            // 3. Harici Player Scriptlerini tara
            val scriptUrls = extractPlayerScriptUrls(body, finalUrl).take(5)
            if (scriptUrls.isNotEmpty()) {
                val scriptCandidates = Collections.synchronizedList(mutableListOf<ExtractedStreamSource>())
                coroutineScope {
                    scriptUrls.map { scriptUrl ->
                        async {
                            val sResp = fetchPageContent(scriptUrl, referer = finalUrl)
                            if (sResp != null && sResp.body.isNotBlank()) {
                                collectCandidatesFromHtml(
                                    content = sResp.body,
                                    baseUrl = finalUrl,
                                    pageTitle = pageTitle,
                                    referer = finalUrl,
                                    origin = finalOrigin,
                                    cookie = sResp.cookie,
                                    methodPrefix = "Gizli Player Script",
                                    outCandidates = scriptCandidates,
                                    outEmbedUrls = Collections.synchronizedSet(LinkedHashSet())
                                )
                            }
                        }
                    }.awaitAll()
                }
                coroutineScope {
                    scriptCandidates.toList().take(20).map { cand ->
                        async { verifyAndRegister(cand) }
                    }.awaitAll()
                }
            }

            // 4. DİREKT SİTE LİNKİ DESTEĞİ:
            // Eğer kullanıcı bir medya sayfası değil de doğrudan bir sitenin ana/kategori adresini girdiyse
            // (ör. https://canlitv... veya film/dizi listeleme sitesi), sayfadaki kanal/film/izle alt sayfalarını
            // otomatik tespit edip içlerindeki yayın kaynaklarını çıkar!
            val subPages = extractMediaSubPages(body, finalUrl).take(8)
            if (subPages.isNotEmpty() && verifiedMap.size < 3) {
                onProgress("Site içindeki ${subPages.size} yayın/içerik sayfası taranıyor...")
                val subPageCandidates = Collections.synchronizedList(mutableListOf<ExtractedStreamSource>())
                val subPageEmbeds = Collections.synchronizedList(mutableListOf<Triple<String, String, String>>()) // (embedUrl, subReferer, subTitle)

                coroutineScope {
                    subPages.map { (subUrl, subLabel) ->
                        async {
                            val subResp = fetchPageContent(subUrl, referer = finalUrl)
                            if (subResp != null && subResp.body.isNotBlank()) {
                                val subTitle = subLabel.ifBlank {
                                    extractHtmlTitle(subResp.body).ifBlank { "Site İçeriği" }
                                }
                                val localEmbeds = Collections.synchronizedSet(LinkedHashSet<String>())
                                collectCandidatesFromHtml(
                                    content = subResp.body,
                                    baseUrl = subResp.finalUrl,
                                    pageTitle = subTitle,
                                    referer = subResp.finalUrl,
                                    origin = extractOrigin(subResp.finalUrl),
                                    cookie = subResp.cookie,
                                    methodPrefix = "Site Alt Sayfası",
                                    outCandidates = subPageCandidates,
                                    outEmbedUrls = localEmbeds
                                )
                                localEmbeds.take(2).forEach { emb ->
                                    subPageEmbeds.add(Triple(emb, subResp.finalUrl, subTitle))
                                }
                            }
                        }
                    }.awaitAll()
                }

                // Alt sayfalardaki embed oynatıcıları da tara
                if (subPageEmbeds.isNotEmpty()) {
                    coroutineScope {
                        subPageEmbeds.take(8).map { (embUrl, subRef, subTitle) ->
                            async {
                                val embResp = fetchPageContent(embUrl, referer = subRef)
                                if (embResp != null && embResp.body.isNotBlank()) {
                                    collectCandidatesFromHtml(
                                        content = embResp.body,
                                        baseUrl = embResp.finalUrl,
                                        pageTitle = subTitle,
                                        referer = embResp.finalUrl,
                                        origin = extractOrigin(embResp.finalUrl),
                                        cookie = embResp.cookie,
                                        methodPrefix = "Site Oynatıcısı",
                                        outCandidates = subPageCandidates,
                                        outEmbedUrls = Collections.synchronizedSet(LinkedHashSet())
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                }

                coroutineScope {
                    subPageCandidates.toList().take(30).map { cand ->
                        async { verifyAndRegister(cand) }
                    }.awaitAll()
                }
            }
        }

        // 5. Bulunan M3U8 Master listelerinin içindeki çözünürlük varyantlarını da (1080p, 720p, vb.) ayrıştır
        val m3u8Masters = verifiedMap.values.filter { it.format == "M3U8" }.take(3)
        for (master in m3u8Masters) {
            val mResp = fetchPageContent(master.streamUrl, referer = master.referer.ifBlank { finalUrl })
            if (mResp != null && mResp.body.contains("#EXT-X-STREAM-INF")) {
                val variants = mutableListOf<ExtractedStreamSource>()
                parseM3uOrMasterPlaylist(
                    m3uContent = mResp.body,
                    baseUrl = mResp.finalUrl,
                    referer = master.referer.ifBlank { finalUrl },
                    parentTitle = master.title,
                    onCollect = { variants.add(it) }
                )
                variants.forEach { v ->
                    val vKey = v.streamUrl.lowercase(Locale.ROOT)
                    if (!verifiedMap.containsKey(vKey)) {
                        val withId = v.copy(
                            id = "src_${vKey.hashCode().toUInt()}",
                            cookie = master.cookie.ifBlank { mResp.cookie }
                        )
                        verifiedMap[vKey] = withId
                        onSourceFound(withId)
                    }
                }
            }
        }

        verifiedMap.values.toList()
    }

    /**
     * Bir aday akış bağlantısının gerçekten çalışıp çalışmadığını (HTTP 200/206 ve geçerli medya içeriği)
     * kontrol eder. Sahte veya kırık (404/403/HTML) bağlantıları eler.
     */
    private fun verifyStreamPlayability(candidate: ExtractedStreamSource): ExtractedStreamSource? {
        val url = candidate.streamUrl
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return null
        }

        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", candidate.userAgent.ifBlank { ExtractedStreamSource.DEFAULT_BROWSER_UA })
                .header("Accept", "*/*")
                .header("Range", "bytes=0-2047")

            if (candidate.referer.isNotBlank()) {
                reqBuilder.header("Referer", candidate.referer)
            }
            if (candidate.origin.isNotBlank()) {
                reqBuilder.header("Origin", candidate.origin)
            }
            val cookie = candidate.cookie.ifBlank {
                runCatching {
                    CookieManager.getInstance().getCookie(url)
                        ?: if (candidate.referer.isNotBlank()) CookieManager.getInstance().getCookie(candidate.referer) else null
                }.getOrNull().orEmpty()
            }
            if (cookie.isNotBlank()) {
                reqBuilder.header("Cookie", cookie)
            }

            permissiveHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val code = response.code
                // 404 Not Found, 410 Gone, 500+ sunucu hatalarını kesinlikle ele
                if (code == 404 || code == 410 || code >= 500) {
                    return null
                }

                val cType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                val finalUrl = response.request.url.toString()

                // Yanıtın ilk birkaç yüz baytını kontrol et (#EXTM3U mu yoksa HTML hata sayfası mı?)
                val peekBytes = runCatching {
                    response.body?.byteStream()?.let { stream ->
                        val buf = ByteArray(512)
                        val read = stream.read(buf)
                        if (read > 0) String(buf, 0, read, Charsets.UTF_8) else ""
                    }.orEmpty()
                }.getOrDefault("")

                val trimmedPeek = peekBytes.trimStart()

                // Eğer içerik #EXTM3U ile başlıyorsa, uzantısı .txt veya uzantısız olsa bile gerçek bir M3U8 akışıdır!
                if (trimmedPeek.startsWith("#EXTM3U", ignoreCase = true)) {
                    return candidate.copy(
                        streamUrl = finalUrl,
                        format = "M3U8",
                        cookie = cookie
                    )
                }

                // Eğer yanıt bir HTML sayfasıysa (<!DOCTYPE html> veya <html), bu doğrudan bir medya akışı DEĞİLDİR!
                if (
                    cType.contains("text/html") ||
                    cType.contains("application/json") ||
                    cType.contains("javascript") ||
                    cType.contains("text/css") ||
                    trimmedPeek.startsWith("<!DOCTYPE", ignoreCase = true) ||
                    trimmedPeek.startsWith("<html", ignoreCase = true)
                ) {
                    return null
                }

                // 200 OK veya 206 Partial Content ve geçerli medya türü/uzantısı
                if (code in 200..299) {
                    val detectedFormat = detectFormatBadge(finalUrl, cType)
                    return candidate.copy(
                        streamUrl = finalUrl,
                        format = detectedFormat,
                        cookie = cookie
                    )
                }

                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * WebView ağ yakalayıcısı (shouldInterceptRequest) veya JS köprüsü tarafından yakalanan
     * canlı istekleri filtreler.
     */
    fun inspectInterceptedNetworkRequest(
        requestUrl: String,
        mimeType: String?,
        requestHeaders: Map<String, String>,
        pageUrl: String,
        pageTitle: String
    ): ExtractedStreamSource? {
        val cleanUrl = cleanCandidateUrl(requestUrl)
        if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            return null
        }
        val lower = cleanUrl.lowercase(Locale.ROOT)
        val lowerMime = mimeType?.lowercase(Locale.ROOT).orEmpty()

        if (isIgnoredAssetUrl(lower)) return null
        if (isLikelyHlsSegmentChunk(cleanUrl)) return null

        val isMediaMime = lowerMime.contains("mpegurl") ||
            lowerMime.contains("video/") ||
            lowerMime.contains("dash+xml")

        val isMediaUrl = looksLikeDirectStreamUrl(cleanUrl)

        if (!isMediaMime && !isMediaUrl) return null

        val referer = requestHeaders["Referer"]
            ?: requestHeaders["referer"]
            ?: pageUrl
        val origin = requestHeaders["Origin"]
            ?: requestHeaders["origin"]
            ?: extractOrigin(referer)
        val ua = requestHeaders["User-Agent"]
            ?: requestHeaders["user-agent"]
            ?: ExtractedStreamSource.DEFAULT_BROWSER_UA
        val cookie = runCatching {
            CookieManager.getInstance().getCookie(cleanUrl)
                ?: CookieManager.getInstance().getCookie(pageUrl)
        }.getOrNull().orEmpty()

        val format = detectFormatBadge(cleanUrl, lowerMime)
        val quality = guessQualityLabel(cleanUrl)
        val cleanTitle = pageTitle
            .takeIf { it.isNotBlank() && !it.startsWith("http") }
            ?: guessStreamTitle(cleanUrl, "Yakalanan Akış")

        return ExtractedStreamSource(
            id = "net_${lower.hashCode().toUInt()}",
            title = if (quality != "Otomatik") "$cleanTitle ($quality)" else cleanTitle,
            streamUrl = cleanUrl,
            format = format,
            quality = quality,
            isHiddenSource = true,
            discoveryMethod = "Canlı Ağ Yakalayıcı",
            referer = referer,
            origin = origin,
            cookie = cookie,
            userAgent = ua,
            pageUrl = pageUrl
        )
    }

    private fun collectCandidatesFromHtml(
        content: String,
        baseUrl: String,
        pageTitle: String,
        referer: String,
        origin: String,
        cookie: String,
        methodPrefix: String,
        outCandidates: MutableList<ExtractedStreamSource>,
        outEmbedUrls: MutableSet<String>
    ) {
        val unescaped = unescapeEncodedContent(content)

        // 1. Gömülü Iframe / Lazy-Iframe / data-src / data-embed adreslerini topla
        IFRAME_AND_EMBED_REGEX.findAll(content).forEach { m ->
            val rawSrc = m.groupValues[1].trim()
            if (rawSrc.length > 4 && !rawSrc.startsWith("javascript:") && !rawSrc.startsWith("about:") && !rawSrc.startsWith("#")) {
                val resolved = resolveUrl(baseUrl, unescapeEncodedContent(rawSrc))
                if (resolved.startsWith("http") && !isIgnoredAssetUrl(resolved.lowercase(Locale.ROOT))) {
                    if (looksLikeDirectStreamUrl(resolved)) {
                        outCandidates.add(
                            ExtractedStreamSource(
                                id = "",
                                title = pageTitle,
                                streamUrl = resolved,
                                format = detectFormatBadge(resolved),
                                quality = guessQualityLabel(resolved),
                                isHiddenSource = true,
                                discoveryMethod = "$methodPrefix (Gömülü Medya)",
                                referer = referer,
                                origin = origin,
                                cookie = cookie,
                                pageUrl = baseUrl
                            )
                        )
                    } else {
                        outEmbedUrls.add(resolved)
                    }
                }
            }
        }

        // 2. Doğrudan medya uzantılı URL'ler (.m3u8, .mp4, .ts, .mpd, .mkv, vb.)
        DIRECT_MEDIA_REGEX.findAll(unescaped).forEach { match ->
            val candidate = resolveUrl(baseUrl, match.groupValues[1])
            outCandidates.add(
                ExtractedStreamSource(
                    id = "",
                    title = "$pageTitle • ${guessStreamTitle(candidate, detectFormatBadge(candidate))}",
                    streamUrl = candidate,
                    format = detectFormatBadge(candidate),
                    quality = guessQualityLabel(candidate),
                    isHiddenSource = methodPrefix != "Sayfa Kaynağı",
                    discoveryMethod = methodPrefix,
                    referer = referer,
                    origin = origin,
                    cookie = cookie,
                    pageUrl = baseUrl
                )
            )
        }

        // 3. HLS / Manifest / Live akış uç noktaları
        STREAM_ENDPOINT_REGEX.findAll(unescaped).forEach { match ->
            val candidate = resolveUrl(baseUrl, match.groupValues[1])
            outCandidates.add(
                ExtractedStreamSource(
                    id = "",
                    title = "$pageTitle • ${guessStreamTitle(candidate, "HLS Akışı")}",
                    streamUrl = candidate,
                    format = detectFormatBadge(candidate),
                    quality = guessQualityLabel(candidate),
                    isHiddenSource = true,
                    discoveryMethod = "$methodPrefix (Akış Uç Noktası)",
                    referer = referer,
                    origin = origin,
                    cookie = cookie,
                    pageUrl = baseUrl
                )
            )
        }

        // 4. Player yapılandırma özellikleri (file: "...", hls: "...", source: "...")
        PLAYER_SOURCE_PROP_REGEX.findAll(unescaped).forEach { match ->
            val candidate = resolveUrl(baseUrl, match.groupValues[1])
            if (candidate.startsWith("http") && !isIgnoredAssetUrl(candidate.lowercase(Locale.ROOT))) {
                if (looksLikeDirectStreamUrl(candidate) || candidate.contains("master") || candidate.contains("playlist") || candidate.contains(".txt")) {
                    outCandidates.add(
                        ExtractedStreamSource(
                            id = "",
                            title = "$pageTitle • ${guessStreamTitle(candidate, "Oynatıcı Kaynağı")}",
                            streamUrl = candidate,
                            format = detectFormatBadge(candidate),
                            quality = guessQualityLabel(candidate),
                            isHiddenSource = true,
                            discoveryMethod = "$methodPrefix (Player Config)",
                            referer = referer,
                            origin = origin,
                            cookie = cookie,
                            pageUrl = baseUrl
                        )
                    )
                } else {
                    outEmbedUrls.add(candidate)
                }
            }
        }

        // 5. Gizli Base64 / atob(...) kodlamalarını çöz
        val base64Candidates = mutableSetOf<String>()
        ATOB_BASE64_REGEX.findAll(content).forEach { base64Candidates.add(it.groupValues[1]) }
        RAW_BASE64_HTTP_REGEX.findAll(content).forEach { base64Candidates.add(it.groupValues[1]) }

        base64Candidates.forEach { b64 ->
            decodeBase64Safely(b64)?.let { decoded ->
                val cleanDecoded = unescapeEncodedContent(decoded).trim()
                if (cleanDecoded.startsWith("http") && !isIgnoredAssetUrl(cleanDecoded.lowercase(Locale.ROOT))) {
                    if (looksLikeDirectStreamUrl(cleanDecoded)) {
                        outCandidates.add(
                            ExtractedStreamSource(
                                id = "",
                                title = "$pageTitle • Gizli Kaynak",
                                streamUrl = cleanDecoded,
                                format = detectFormatBadge(cleanDecoded),
                                quality = guessQualityLabel(cleanDecoded),
                                isHiddenSource = true,
                                discoveryMethod = "$methodPrefix (Gizli Base64)",
                                referer = referer,
                                origin = origin,
                                cookie = cookie,
                                pageUrl = baseUrl
                            )
                        )
                    } else {
                        outEmbedUrls.add(cleanDecoded)
                    }
                } else {
                    DIRECT_MEDIA_REGEX.findAll(cleanDecoded).forEach { m ->
                        val url = resolveUrl(baseUrl, m.groupValues[1])
                        outCandidates.add(
                            ExtractedStreamSource(
                                id = "",
                                title = "$pageTitle • Gizli Kaynak",
                                streamUrl = url,
                                format = detectFormatBadge(url),
                                quality = guessQualityLabel(url),
                                isHiddenSource = true,
                                discoveryMethod = "$methodPrefix (Gizli Base64)",
                                referer = referer,
                                origin = origin,
                                cookie = cookie,
                                pageUrl = baseUrl
                            )
                        )
                    }
                }
            }
        }

        // 6. Gizli Sıkıştırılmış JS (eval(function(p,a,c,k,e,d)...)) paketlerini çöz
        PACKED_JS_REGEX.findAll(content).forEach { match ->
            val payload = match.groupValues[1]
            val radix = match.groupValues[2].toIntOrNull() ?: 36
            val count = match.groupValues[3].toIntOrNull() ?: 0
            val symtab = match.groupValues[4].split("|")
            val unpacked = unpackDeanEdwardsPacker(payload, radix, count, symtab)
            if (unpacked.isNotBlank()) {
                val unpackedClean = unescapeEncodedContent(unpacked)
                DIRECT_MEDIA_REGEX.findAll(unpackedClean).forEach { m ->
                    val url = resolveUrl(baseUrl, m.groupValues[1])
                    outCandidates.add(
                        ExtractedStreamSource(
                            id = "",
                            title = "$pageTitle • Gizli Oynatıcı",
                            streamUrl = url,
                            format = detectFormatBadge(url),
                            quality = guessQualityLabel(url),
                            isHiddenSource = true,
                            discoveryMethod = "$methodPrefix (Şifreli JS Unpacker)",
                            referer = referer,
                            origin = origin,
                            cookie = cookie,
                            pageUrl = baseUrl
                        )
                    )
                }
                STREAM_ENDPOINT_REGEX.findAll(unpackedClean).forEach { m ->
                    val url = resolveUrl(baseUrl, m.groupValues[1])
                    outCandidates.add(
                        ExtractedStreamSource(
                            id = "",
                            title = "$pageTitle • Gizli HLS",
                            streamUrl = url,
                            format = detectFormatBadge(url),
                            quality = guessQualityLabel(url),
                            isHiddenSource = true,
                            discoveryMethod = "$methodPrefix (Şifreli JS Unpacker)",
                            referer = referer,
                            origin = origin,
                            cookie = cookie,
                            pageUrl = baseUrl
                        )
                    )
                }
                PLAYER_SOURCE_PROP_REGEX.findAll(unpackedClean).forEach { m ->
                    val url = resolveUrl(baseUrl, m.groupValues[1])
                    if (url.startsWith("http")) {
                        outCandidates.add(
                            ExtractedStreamSource(
                                id = "",
                                title = "$pageTitle • Gizli Oynatıcı",
                                streamUrl = url,
                                format = detectFormatBadge(url),
                                quality = guessQualityLabel(url),
                                isHiddenSource = true,
                                discoveryMethod = "$methodPrefix (Şifreli JS Unpacker)",
                                referer = referer,
                                origin = origin,
                                cookie = cookie,
                                pageUrl = baseUrl
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Direkt bir site linki (ör. ana sayfa veya kategori sayfası) girildiğinde
     * sayfa içerisindeki kanal / film / dizi izleme alt sayfalarını bulur.
     */
    private fun extractMediaSubPages(html: String, baseUrl: String): List<Pair<String, String>> {
        val baseHost = runCatching { URI(baseUrl).host?.removePrefix("www.").orEmpty() }.getOrDefault("")
        if (baseHost.isBlank()) return emptyList()

        val results = LinkedHashMap<String, String>()
        val mediaPathKeywords = listOf(
            "izle", "watch", "canli", "live", "tv", "kanal", "channel",
            "film", "movie", "dizi", "series", "bolum", "episode", "embed", "player", "stream"
        )
        val ignoredKeywords = listOf(
            "login", "register", "kayit", "giris", "iletisim", "contact", "dmca",
            "privacy", "gizlilik", "hakkimizda", "about", "tag/", "etiket/", "author/", "wp-login"
        )

        ANCHOR_HREF_REGEX.findAll(html).forEach { match ->
            val rawHref = match.groupValues[1].trim()
            val innerHtml = match.groupValues[2]
            if (rawHref.isBlank() || rawHref.startsWith("javascript:") || rawHref.startsWith("mailto:")) return@forEach

            val resolved = resolveUrl(baseUrl, rawHref)
            if (!resolved.startsWith("http") || resolved == baseUrl) return@forEach

            val uHost = runCatching { URI(resolved).host?.removePrefix("www.").orEmpty() }.getOrDefault("")
            if (!uHost.equals(baseHost, ignoreCase = true)) return@forEach

            val lowerUrl = resolved.lowercase(Locale.ROOT)
            if (isIgnoredAssetUrl(lowerUrl)) return@forEach
            if (ignoredKeywords.any { lowerUrl.contains(it) }) return@forEach

            val path = runCatching { URI(resolved).path.orEmpty() }.getOrDefault("")
            if (path.length < 4 || path == "/") return@forEach

            val hasMediaKeyword = mediaPathKeywords.any { lowerUrl.contains(it) } ||
                innerHtml.contains("<img", ignoreCase = true) ||
                path.count { it == '/' } in 1..3 && path.length > 8

            if (hasMediaKeyword && !results.containsKey(resolved)) {
                val cleanText = innerHtml
                    .replace(Regex("""<[^>]+>"""), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                    .take(50)
                results[resolved] = cleanText
            }
        }
        return results.entries.map { it.key to it.value }
    }

    private fun unpackDeanEdwardsPacker(
        payload: String,
        radix: Int,
        count: Int,
        symtab: List<String>
    ): String {
        if (radix < 2 || radix > 62 || symtab.isEmpty()) return ""
        return try {
            val wordRegex = Regex("""\b[0-9a-zA-Z]+\b""")
            wordRegex.replace(payload) { matchResult ->
                val token = matchResult.value
                val index = parseBase62(token, radix)
                if (index in 0 until count && index < symtab.size && symtab[index].isNotEmpty()) {
                    symtab[index]
                } else {
                    token
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseBase62(token: String, radix: Int): Int {
        val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        var result = 0
        for (ch in token) {
            val digit = chars.indexOf(ch)
            if (digit < 0 || digit >= radix) return -1
            result = result * radix + digit
        }
        return result
    }

    private fun parseM3uOrMasterPlaylist(
        m3uContent: String,
        baseUrl: String,
        referer: String,
        parentTitle: String = "",
        onCollect: (ExtractedStreamSource) -> Unit
    ) {
        val lines = m3uContent.lines()
        val origin = extractOrigin(baseUrl)
        var pendingLabel = ""
        var pendingQuality = "Otomatik"

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                val resMatch = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE).find(line)
                val bwMatch = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE).find(line)
                val height = resMatch?.groupValues?.get(1)
                val kbps = bwMatch?.groupValues?.get(1)?.toLongOrNull()?.div(1000)
                pendingQuality = when {
                    height != null -> "${height}p"
                    kbps != null -> "${kbps} kbps"
                    else -> "Varyant"
                }
                pendingLabel = if (parentTitle.isNotBlank()) {
                    "$parentTitle ($pendingQuality)"
                } else {
                    "HLS Akışı ($pendingQuality)"
                }
            } else if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val titlePart = line.substringAfterLast(",", "").trim()
                pendingLabel = titlePart.ifBlank { "IPTV Kanalı" }
                pendingQuality = guessQualityLabel(pendingLabel)
            } else if (!line.startsWith("#")) {
                val resolved = resolveUrl(baseUrl, line)
                if (resolved.startsWith("http", ignoreCase = true) && !isLikelyHlsSegmentChunk(resolved)) {
                    val title = pendingLabel.ifBlank {
                        guessStreamTitle(resolved, "M3U Kaynağı")
                    }
                    onCollect(
                        ExtractedStreamSource(
                            id = "",
                            title = title,
                            streamUrl = resolved,
                            format = if (m3uContent.contains("#EXT-X-STREAM-INF")) "M3U8" else detectFormatBadge(resolved),
                            quality = pendingQuality,
                            isHiddenSource = pendingQuality != "Otomatik",
                            discoveryMethod = if (m3uContent.contains("#EXT-X-STREAM-INF")) {
                                "HLS Master Varyantı ($pendingQuality)"
                            } else {
                                "M3U Liste Kaynağı"
                            },
                            referer = referer,
                            origin = origin,
                            pageUrl = baseUrl
                        )
                    )
                    pendingLabel = ""
                    pendingQuality = "Otomatik"
                }
            }
        }
    }

    private data class HttpPageResult(
        val finalUrl: String,
        val contentType: String,
        val body: String,
        val cookie: String
    )

    private fun fetchPageContent(url: String, referer: String): HttpPageResult? {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", ExtractedStreamSource.DEFAULT_BROWSER_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")

            if (referer.isNotBlank()) {
                reqBuilder.header("Referer", referer)
            }
            val cookies = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull().orEmpty()
            if (cookies.isNotBlank()) {
                reqBuilder.header("Cookie", cookies)
            }

            permissiveHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val contentType = response.header("Content-Type").orEmpty()
                val finalUrl = response.request.url.toString()
                val setCookies = response.headers("Set-Cookie").joinToString("; ") { it.substringBefore(";") }
                val effectiveCookie = if (setCookies.isNotBlank()) {
                    if (cookies.isNotBlank()) "$cookies; $setCookies" else setCookies
                } else {
                    cookies
                }
                val lowerType = contentType.lowercase(Locale.ROOT)
                if (
                    lowerType.startsWith("video/") ||
                    lowerType.contains("application/octet-stream") ||
                    lowerType.contains("video/mp2t")
                ) {
                    return HttpPageResult(
                        finalUrl = finalUrl,
                        contentType = contentType,
                        body = "",
                        cookie = effectiveCookie
                    )
                }
                val bodyStr = response.body?.string()?.take(1_500_000).orEmpty()
                HttpPageResult(
                    finalUrl = finalUrl,
                    contentType = contentType,
                    body = bodyStr,
                    cookie = effectiveCookie
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun unescapeEncodedContent(raw: String): String {
        var text = raw
            .replace("""\/""", "/")
            .replace("""\u002F""", "/", ignoreCase = true)
            .replace("""\u0026""", "&", ignoreCase = true)
            .replace("""\u003D""", "=", ignoreCase = true)
            .replace("""\u003F""", "?", ignoreCase = true)
            .replace("&#038;", "&")
            .replace("&amp;", "&")

        if (text.contains("""\x""")) {
            val hexRegex = Regex("""\\x([0-9a-fA-F]{2})""")
            text = hexRegex.replace(text) { m ->
                m.groupValues[1].toInt(16).toChar().toString()
            }
        }

        if (text.contains("http%3A%2F%2F", ignoreCase = true) || text.contains("https%3A%2F%2F", ignoreCase = true)) {
            val encodedUrlRegex = Regex("""https?%3A%2F%2F[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)
            text = encodedUrlRegex.replace(text) { m ->
                runCatching { URLDecoder.decode(m.value, "UTF-8") }.getOrDefault(m.value)
            }
        }
        return text
    }

    private fun decodeBase64Safely(input: String): String? {
        return try {
            val bytes = Base64.decode(input.trim(), Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractPlayerScriptUrls(html: String, baseUrl: String): List<String> {
        val result = LinkedHashSet<String>()
        SCRIPT_SRC_REGEX.findAll(html).forEach { match ->
            val rawSrc = match.groupValues[1].trim()
            val lower = rawSrc.lowercase(Locale.ROOT)
            if (
                lower.contains("player") ||
                lower.contains("embed") ||
                lower.contains("stream") ||
                lower.contains("video") ||
                lower.contains("hls")
            ) {
                val resolved = resolveUrl(baseUrl, rawSrc)
                if (resolved.startsWith("http")) {
                    result.add(resolved)
                }
            }
        }
        return result.toList()
    }

    private fun extractHtmlTitle(html: String): String {
        val match = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
        return match?.groupValues?.get(1)
            ?.replace(Regex("""\s+"""), " ")
            ?.replace("&amp;", "&")
            ?.replace("&#8211;", "-")
            ?.replace("&#039;", "'")
            ?.trim()
            ?.take(65)
            .orEmpty()
    }

    fun normalizeInputUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> "https://$trimmed"
        }
    }

    private fun resolveUrl(baseUrl: String, candidate: String): String {
        val trimmed = candidate.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        if (trimmed.startsWith("//")) {
            val scheme = if (baseUrl.startsWith("http://", ignoreCase = true)) "http:" else "https:"
            return "$scheme$trimmed"
        }
        return try {
            URI(baseUrl).resolve(trimmed).toString()
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun extractOrigin(url: String): String {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return ""
            val portPart = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            "$scheme://$host$portPart"
        } catch (_: Exception) {
            ""
        }
    }

    private fun cleanCandidateUrl(raw: String): String {
        return raw.trim()
            .trimEnd('"', '\'', ')', ']', '}', ',', ';', '\\')
            .replace(" ", "%20")
    }

    /**
     * HLS akışlarının 2 saniyelik tekil .ts segment parçalarını (ör. seg-1-v1-a1.ts, chunk_00012.ts)
     * gerçek canlı yayın .ts akışlarından ayırt eder.
     */
    private fun isLikelyHlsSegmentChunk(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT).substringBefore("?")
        if (!lower.endsWith(".ts")) return false
        val fileName = lower.substringAfterLast("/")
        return Regex("""^(?:seg|segment|frag|fragment|chunk|media|hl|ts)[\-_]?\d+""").containsMatchIn(fileName) ||
            Regex("""[\-_]\d{3,6}\.ts$""").containsMatchIn(fileName) ||
            Regex("""seg-\d+-v\d+""").containsMatchIn(fileName)
    }

    private fun isIgnoredAssetUrl(lowerUrl: String): Boolean {
        if (lowerUrl.startsWith("blob:") || lowerUrl.startsWith("data:") || lowerUrl.startsWith("javascript:")) {
            return true
        }
        val pathPart = lowerUrl.substringBefore("?")
        if (
            pathPart.endsWith(".js") ||
            pathPart.endsWith(".css") ||
            pathPart.endsWith(".json") ||
            pathPart.endsWith(".webmanifest") ||
            pathPart.endsWith(".xml") ||
            pathPart.endsWith(".png") ||
            pathPart.endsWith(".jpg") ||
            pathPart.endsWith(".jpeg") ||
            pathPart.endsWith(".webp") ||
            pathPart.endsWith(".svg") ||
            pathPart.endsWith(".gif") ||
            pathPart.endsWith(".ico") ||
            pathPart.endsWith(".woff") ||
            pathPart.endsWith(".woff2") ||
            pathPart.endsWith(".ttf") ||
            pathPart.endsWith(".vtt") ||
            pathPart.endsWith(".srt") ||
            pathPart.endsWith(".html") ||
            pathPart.endsWith(".htm")
        ) {
            return true
        }
        if (
            lowerUrl.contains("google-analytics") ||
            lowerUrl.contains("googletagmanager") ||
            lowerUrl.contains("doubleclick.net") ||
            lowerUrl.contains("googlesyndication") ||
            lowerUrl.contains("facebook.com/tr") ||
            lowerUrl.contains("yandex.ru/metrika") ||
            lowerUrl.contains("manifest.json")
        ) {
            return true
        }
        return false
    }

    private fun isValidStreamCandidate(url: String): Boolean {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val lower = url.lowercase(Locale.ROOT)
        if (isIgnoredAssetUrl(lower)) return false
        return true
    }

    fun looksLikeDirectStreamUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (isIgnoredAssetUrl(lower)) return false
        val pathPart = lower.substringBefore("?")
        return pathPart.endsWith(".m3u8") ||
            pathPart.endsWith(".mp4") ||
            pathPart.endsWith(".ts") ||
            pathPart.endsWith(".mpd") ||
            pathPart.endsWith(".mkv") ||
            pathPart.endsWith(".webm") ||
            pathPart.endsWith(".flv") ||
            pathPart.endsWith(".mov") ||
            pathPart.endsWith(".m4v") ||
            pathPart.endsWith(".m3u") ||
            lower.contains(".m3u8?") ||
            lower.contains(".mp4?") ||
            lower.contains(".ts?") ||
            lower.contains(".mpd?") ||
            lower.contains("master.m3u8") ||
            lower.contains("index.m3u8") ||
            lower.contains("playlist.m3u8") ||
            lower.contains("output=m3u8") ||
            lower.contains("output=ts") ||
            Regex("""/live/[^/]+/[^/]+/\d+""").containsMatchIn(lower)
    }

    fun detectFormatBadge(url: String, mimeType: String = ""): String {
        val lower = url.lowercase(Locale.ROOT)
        val mime = mimeType.lowercase(Locale.ROOT)
        return when {
            lower.contains(".m3u8") || lower.contains("output=m3u8") || lower.contains("/hls/") || mime.contains("mpegurl") -> "M3U8"
            lower.contains(".mp4") || lower.contains(".m4v") || mime.contains("video/mp4") -> "MP4"
            lower.contains(".ts") || lower.contains("output=ts") || mime.contains("mp2t") || Regex("""/live/[^/]+/[^/]+/\d+""").containsMatchIn(lower) -> "TS"
            lower.contains(".mpd") || mime.contains("dash+xml") -> "MPD"
            lower.contains(".mkv") || mime.contains("matroska") -> "MKV"
            lower.contains(".webm") || mime.contains("webm") -> "WEBM"
            lower.contains(".flv") -> "FLV"
            lower.contains(".mov") -> "MOV"
            lower.contains(".m3u") -> "M3U"
            else -> "M3U8"
        }
    }

    fun guessQualityLabel(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("2160") || lower.contains("4k") || lower.contains("uhd") -> "4K UHD"
            lower.contains("1080") || lower.contains("fhd") -> "1080p FHD"
            lower.contains("720") || lower.contains("hd") -> "720p HD"
            lower.contains("480") || lower.contains("sd") -> "480p"
            lower.contains("360") -> "360p"
            else -> "Otomatik"
        }
    }

    private fun guessStreamTitle(url: String, fallback: String): String {
        return try {
            val clean = url.substringBefore("|")
            val path = URI(clean).path.orEmpty()
            val lastSeg = path.substringAfterLast('/')
                .substringBefore('?')
                .replace("%20", " ")
                .trim()
            if (lastSeg.length in 3..55 && !lastSeg.equals("index.m3u8", ignoreCase = true) && !lastSeg.equals("master.m3u8", ignoreCase = true) && !lastSeg.equals("playlist.m3u8", ignoreCase = true)) {
                lastSeg
            } else {
                val host = URI(clean).host.orEmpty().removePrefix("www.")
                if (host.isNotBlank()) "$fallback ($host)" else fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    // =========================================================================
    // KALICI KAYIT YÖNETİMİ (Kaydedilen Kaynaklar & Cihaza İndirme)
    // =========================================================================

    fun loadSavedStreams(context: Context): List<ExtractedStreamSource> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_SAVED_STREAMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(rawJson)
            val list = ArrayList<ExtractedStreamSource>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ExtractedStreamSource(
                        id = obj.optString("id", "saved_$i"),
                        title = obj.optString("title", "Kaydedilen Kaynak"),
                        streamUrl = obj.optString("streamUrl", ""),
                        format = obj.optString("format", "M3U8"),
                        quality = obj.optString("quality", "Otomatik"),
                        isHiddenSource = obj.optBoolean("isHiddenSource", false),
                        discoveryMethod = obj.optString("discoveryMethod", "Kaydedildi"),
                        referer = obj.optString("referer", ""),
                        origin = obj.optString("origin", ""),
                        cookie = obj.optString("cookie", ""),
                        userAgent = obj.optString("userAgent", ExtractedStreamSource.DEFAULT_BROWSER_UA),
                        pageUrl = obj.optString("pageUrl", ""),
                        addedAtMillis = obj.optLong("addedAtMillis", System.currentTimeMillis())
                    )
                )
            }
            list.filter { it.streamUrl.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveStreamToStorage(context: Context, source: ExtractedStreamSource, customTitle: String? = null): List<ExtractedStreamSource> {
        val current = loadSavedStreams(context).toMutableList()
        val finalSource = if (!customTitle.isNullOrBlank()) source.copy(title = customTitle.trim()) else source
        current.removeAll { it.streamUrl.equals(finalSource.streamUrl, ignoreCase = true) }
        current.add(0, finalSource)
        persistSavedStreams(context, current)
        return current
    }

    fun removeSavedStream(context: Context, streamUrl: String): List<ExtractedStreamSource> {
        val current = loadSavedStreams(context).toMutableList()
        current.removeAll { it.streamUrl.equals(streamUrl, ignoreCase = true) }
        persistSavedStreams(context, current)
        return current
    }

    fun clearAllSavedStreams(context: Context): List<ExtractedStreamSource> {
        persistSavedStreams(context, emptyList())
        return emptyList()
    }

    private fun persistSavedStreams(context: Context, list: List<ExtractedStreamSource>) {
        val arr = JSONArray()
        list.take(250).forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("streamUrl", item.streamUrl)
                put("format", item.format)
                put("quality", item.quality)
                put("isHiddenSource", item.isHiddenSource)
                put("discoveryMethod", item.discoveryMethod)
                put("referer", item.referer)
                put("origin", item.origin)
                put("cookie", item.cookie)
                put("userAgent", item.userAgent)
                put("pageUrl", item.pageUrl)
                put("addedAtMillis", item.addedAtMillis)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_STREAMS, arr.toString())
            .apply()
    }

    fun buildM3uPlaylistContent(sources: List<ExtractedStreamSource>): String {
        val sb = StringBuilder("#EXTM3U\n")
        sources.forEach { src ->
            val safeTitle = src.title.replace(",", " ").trim()
            sb.append("#EXTINF:-1 group-title=\"Stavio Kaynak Bulucu\",")
                .append(safeTitle)
                .append(" [")
                .append(src.format)
                .append("]\n")
            if (src.referer.isNotBlank()) {
                sb.append("#EXTVLCOPT:http-referrer=${src.referer}\n")
            }
            if (src.userAgent.isNotBlank()) {
                sb.append("#EXTVLCOPT:http-user-agent=${src.userAgent}\n")
            }
            sb.append(src.toPlayableUrlWithHeaders()).append("\n")
        }
        return sb.toString()
    }

    fun enqueueDirectDownload(context: Context, source: ExtractedStreamSource): Boolean {
        return try {
            val cleanUrl = source.streamUrl.substringBefore("|")
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val ext = when (source.format.uppercase(Locale.ROOT)) {
                "MP4" -> "mp4"
                "TS" -> "ts"
                "MKV" -> "mkv"
                "WEBM" -> "webm"
                "FLV" -> "flv"
                "MOV" -> "mov"
                "M3U8" -> "m3u8"
                else -> "mp4"
            }
            val safeName = source.title
                .replace(Regex("""[^a-zA-Z0-9ğüşöçıİĞÜŞÖÇ_\-\s]"""), "")
                .trim()
                .replace(Regex("""\s+"""), "_")
                .take(45)
                .ifBlank { "stavio_video" }

            val request = DownloadManager.Request(Uri.parse(cleanUrl)).apply {
                setTitle("${source.title} (${source.format})")
                setDescription("Stavio Kaynak Bulucu ile indiriliyor...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                if (source.referer.isNotBlank()) addRequestHeader("Referer", source.referer)
                if (source.origin.isNotBlank()) addRequestHeader("Origin", source.origin)
                if (source.cookie.isNotBlank()) addRequestHeader("Cookie", source.cookie)
                if (source.userAgent.isNotBlank()) addRequestHeader("User-Agent", source.userAgent)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${safeName}_${System.currentTimeMillis() % 10000}.$ext")
            }
            dm.enqueue(request)
            true
        } catch (_: Exception) {
            false
        }
    }
}
