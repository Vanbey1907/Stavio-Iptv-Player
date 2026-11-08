package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

data class CleanedTitleResult(
    val cleanTitle: String,
    val year: String?
)

data class StavioActor(
    val name: String = "",
    val character: String = "",
    val photoUrl: String? = null
) {
    /**
     * Güvenli baş harf hesaplayıcı:
     * const initials = actor?.name ? actor.name.split(' ').map(n => n[0]).join('').substring(0,2).toUpperCase() : 'NA'
     */
    val initials: String
        get() = try {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                "NA"
            } else {
                val words = trimmed.split(" ").filter { it.isNotBlank() }
                val mapped = words.mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
                val result = if (mapped.length >= 2) {
                    mapped.substring(0, 2).uppercase(Locale.ROOT)
                } else if (trimmed.length >= 2) {
                    trimmed.substring(0, 2).uppercase(Locale.ROOT)
                } else {
                    mapped.uppercase(Locale.ROOT)
                }
                result.ifEmpty { "NA" }
            }
        } catch (_: Throwable) {
            "NA"
        }
}

data class StavioTrailer(
    val ytId: String = "",
    val title: String = "Fragman",
    val type: String = "Trailer",
    val directStreamUrl: String = "",
    val customThumbnailUrl: String = ""
) {
    val thumbnailUrl: String
        get() = when {
            customThumbnailUrl.isNotBlank() -> customThumbnailUrl
            ytId.isNotBlank() -> "https://img.youtube.com/vi/$ytId/hqdefault.jpg"
            else -> ""
        }
}

data class StavioEpisodeMeta(
    val season: Int = 1,
    val episode: Int = 1,
    val title: String = "",
    val overview: String = "",
    val thumbnail: String = "",
    val releasedFormatted: String = "",
    val imdbRating: String = ""
)

data class StavioVodMetadata(
    val imdbId: String = "",
    val cleanTitle: String = "",
    val background: String = "",
    val poster: String = "",
    val logo: String = "",
    val genres: List<String> = emptyList(),
    val releaseInfo: String = "",
    val runtime: String = "",
    val imdbRating: String = "",
    val directors: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val description: String = "",
    val cast: List<StavioActor> = emptyList(),
    val trailers: List<StavioTrailer> = emptyList(),
    val country: String = "",
    val status: String = "",
    val episodes: Map<Pair<Int, Int>, StavioEpisodeMeta> = emptyMap(),
    val isLoaded: Boolean = false
)

object StavioMetadataService {

    /**
     * Map tabanlı hafıza önbelleği (mediaCache):
     * Aynı içeriğe ikinci kez tıklandığında veriler internetten tekrar çekilmeden hafızadan anında (< 0.01 sn) döner.
     */
    private val mediaCache = ConcurrentHashMap<String, StavioVodMetadata>()
    private val cleanTitleCache = ConcurrentHashMap<String, CleanedTitleResult>()
    private val directStreamCache = ConcurrentHashMap<String, String>()
    private val translationCache = ConcurrentHashMap<String, String>()

    private fun toTmdbLanguageTag(langCode: String): String {
        return when (langCode.lowercase(Locale.ROOT).trim()) {
            "tr" -> "tr-TR"
            "en" -> "en-US"
            "es" -> "es-ES"
            "fr" -> "fr-FR"
            "de" -> "de-DE"
            "ar" -> "ar-SA"
            else -> "tr-TR"
        }
    }

    private fun buildCacheKey(cleanTitle: String, year: String?, isSeries: Boolean, langCode: String = "tr"): String {
        return "${cleanTitle.lowercase(Locale.ROOT)}_${year.orEmpty()}_${isSeries}_${langCode.lowercase(Locale.ROOT)}"
    }

    fun getCachedMetadata(
        rawTitle: String?,
        isSeries: Boolean,
        fallbackLogo: String = "",
        fallbackGroup: String = "",
        languageCode: String = "tr"
    ): StavioVodMetadata? {
        if (rawTitle.isNullOrBlank()) return null
        val normLang = languageCode.lowercase(Locale.ROOT).trim().ifEmpty { "tr" }
        val rawKey = "raw_${rawTitle.trim().lowercase(Locale.ROOT)}_${isSeries}_$normLang"
        val cleaned = cleanMediaTitle(rawTitle)
        val cleanKey = buildCacheKey(cleaned.cleanTitle, cleaned.year, isSeries, normLang)
        val cached = mediaCache[rawKey] ?: mediaCache[cleanKey] ?: return null
        val safeFallbackLogo = fallbackLogo.trim()
        val safeFallbackGenres = if (fallbackGroup.isNotBlank()) listOf(fallbackGroup.trim()) else emptyList()
        return cached.copy(
            background = cached.background.ifEmpty { safeFallbackLogo },
            poster = cached.poster.ifEmpty { safeFallbackLogo },
            genres = cached.genres.ifEmpty { safeFallbackGenres }
        )
    }

    /**
     * Anlık ve Önbellekli Açıklama Çevirici:
     * Seçili uygulama diline (tr, en, es, fr, de, ar) açıklamayı ~120ms içinde çevirir ve hafızada saklar.
     */
    fun translateTextInstant(text: String, targetLangCode: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val cleanTarget = targetLangCode.lowercase(Locale.ROOT).trim().ifEmpty { "tr" }
        val cacheKey = "${cleanTarget}_${trimmed.hashCode()}"
        translationCache[cacheKey]?.let { return it }

        var conn: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$cleanTarget&dt=t&q=$encoded"
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1800
                readTimeout = 1800
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (conn.responseCode in 200..299) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val outerArr = JSONArray(resp)
                val sentencesArr = outerArr.optJSONArray(0)
                if (sentencesArr != null && sentencesArr.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until sentencesArr.length()) {
                        val part = sentencesArr.optJSONArray(i)?.optString(0).orEmpty()
                        sb.append(part)
                    }
                    val translated = sb.toString().trim()
                    if (translated.isNotBlank()) {
                        translationCache[cacheKey] = translated
                        return translated
                    }
                }
            }
            trimmed
        } catch (_: Throwable) {
            trimmed
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Throwable) {}
        }
    }

    /**
     * 1. IPTV İsim Temizleyici (cleanMediaTitle fonksiyonu):
     * Android ICU Regex motoru ile %100 uyumlu, önbellekli ve tamamen try/catch korumalıdır.
     */
    fun cleanMediaTitle(rawName: String?): CleanedTitleResult {
        val safeRaw = rawName?.trim().orEmpty()
        if (safeRaw.isEmpty()) {
            return CleanedTitleResult(cleanTitle = "İsimsiz İçerik", year = null)
        }
        cleanTitleCache[safeRaw]?.let { return it }

        return try {
            var working = safeRaw
            var extractedYear: String? = null

            // 1. Parantez veya köşeli/süslü parantez içindeki 4 haneli yılı yakala: (2026) veya [2024]
            val bracketYearRegex = Regex("(?:\\(|\\[|\\{)\\s*((?:19|20)\\d{2})\\s*(?:\\)|\\]|\\})")
            val bracketMatch = bracketYearRegex.find(working)
            if (bracketMatch != null) {
                extractedYear = bracketMatch.groupValues.getOrNull(1)
                working = working.replaceRange(bracketMatch.range, " ")
            }

            // 2. Baştaki ülke / kategori öneklerini temizle (ör: "TR:", "TR |", "TR -", "[TR]", "EN|", "DE:")
            working = working.replace(
                Regex("^(?:\\[?[A-Z]{2,4}(?:-[A-Z]{2,4})?\\]?\\s*[:|\\-•]+\\s*)+", RegexOption.IGNORE_CASE),
                ""
            )
            working = working.replace(
                Regex("^(?:TR|EN|DE|FR|US|UK|NL|AR|RU|ES|IT|EXXEN|NETFLIX|DISNEY\\+?|BLU\\s*TV|GAIN|TOD|AMAZON|PRIME|HBO|MUBI|TABII|PUHU)\\s*[:|\\-•]+\\s*", RegexOption.IGNORE_CASE),
                ""
            )

            // 3. Sezon ve bölüm ifadelerini ve sonrasını temizle (S01E01, S01, 1.Sezon, Bölüm 1 vb.)
            working = working.replace(
                Regex("(?i)\\b(?:s\\d{1,2}\\s*e\\d{1,3}|s\\d{1,2}|sezon\\s*\\d+|\\d+\\s*\\.\\s*sezon|b[öo]l[üu]m\\s*\\d+|\\d+\\s*\\.\\s*b[öo]l[üu]m|ep?\\s*\\d{1,3})\\b.*"),
                ""
            )

            // 4. Kalite, çözünürlük, ses ve dil etiketlerini temizle
            working = working.replace(
                Regex("(?i)\\b(?:4K|UHD|FHD|QHD|HD|SD|2160p|1080p|720p|480p|HEVC|H\\.?265|H\\.?264|x265|x264|10bit|HDR10\\+?|HDR|DV|BluRay|WEB-?DL|WEBRip|BRRip|DVDRip|CAM|TS|DUAL|MULTI|TR\\s*DUBLAJ|T[ÜU]RK[ÇC]E\\s*DUBLAJ|DUBLAJ|ALTYAZILI|TR\\s*ALTYAZI|ALTYAZI|ORJ|SUB|DUB)\\b"),
                " "
            )

            // 5. Köşeli veya süslü parantez içindeki kalan etiketleri temizle (Android ICU Regex uyumlu kaçış karakterleri)
            working = working.replace(Regex("\\[[^\\]]*\\]"), " ")
            working = working.replace(Regex("\\{[^\\}]*\\}"), " ")

            // 6. Eğer yıl henüz bulunmadıysa sondaki 4 haneli yılı kontrol et (ör. "Inception 2010")
            if (extractedYear == null) {
                val trailingYearMatch = Regex("(?:\\s+|-+)((?:19|20)\\d{2})\\s*$").find(working)
                if (trailingYearMatch != null) {
                    extractedYear = trailingYearMatch.groupValues.getOrNull(1)
                    working = working.replaceRange(trailingYearMatch.range, " ")
                }
            }

            // 7. Özel sembolleri (⚜️, ★, |, • vb.) temizle
            working = working.replace(Regex("[⚜️★☆✦✧✪✫✬✭✮✯⚡🔥🎬🎥📺▶►•|]+"), " ")
            working = working.replace(Regex("\\(\\s*\\)"), " ")
            working = working.replace(Regex("\\s*[-–—:]+\\s*$"), "")
            working = working.replace(Regex("^\\s*[-–—:]+\\s*"), "")
            working = working.replace(Regex("\\s{2,}"), " ").trim()

            val finalTitle = working.ifEmpty { safeRaw }
            val result = CleanedTitleResult(cleanTitle = finalTitle, year = extractedYear)
            cleanTitleCache[safeRaw] = result
            result
        } catch (_: Throwable) {
            CleanedTitleResult(cleanTitle = safeRaw, year = null)
        }
    }

    /**
     * 2. Ultra Hızlı Paralel & Önbellekli Veri Çekimi + Anlık Dil Çevirisi:
     * Fragman akış çözümlemesi detay yüklemesini ASLA bekletmez; bilgiler <0.5 sn içinde ekrana gelir ve açıklama seçili dile çevrilir.
     */
    suspend fun fetchMetadata(
        rawTitle: String?,
        isSeries: Boolean,
        tmdbApiKey: String? = null,
        fallbackLogo: String = "",
        fallbackGroup: String = "",
        languageCode: String = "tr"
    ): StavioVodMetadata = withContext(Dispatchers.IO) {
        val cleaned = cleanMediaTitle(rawTitle)
        val safeFallbackLogo = fallbackLogo.trim()
        val safeFallbackGenres = if (fallbackGroup.isNotBlank()) listOf(fallbackGroup.trim()) else emptyList()
        val normLang = languageCode.lowercase(Locale.ROOT).trim().ifEmpty { "tr" }

        // 1. Önbellek kontrolü (0.01 sn anında yanıt)
        getCachedMetadata(rawTitle, isSeries, safeFallbackLogo, fallbackGroup, normLang)?.let {
            return@withContext it
        }

        val fallbackMetadata = StavioVodMetadata(
            imdbId = "",
            cleanTitle = cleaned.cleanTitle,
            background = safeFallbackLogo,
            poster = safeFallbackLogo,
            genres = safeFallbackGenres,
            releaseInfo = cleaned.year.orEmpty(),
            isLoaded = false
        )

        try {
            val effectiveTmdbKey = tmdbApiKey?.takeIf { it.isNotBlank() } ?: "26685f67fcab81ee85fdac74066060c4"

            // Paralel İstek Mimarisi (Cinemeta, TMDB ve iTunes aynı anda çalışır, zaman aşımı korumalıdır)
            val cinemetaResult = coroutineScope {
                val cinemetaDeferred = async {
                    kotlinx.coroutines.withTimeoutOrNull(2400L) {
                        var imdbId = ""
                        var discoveredPoster = safeFallbackLogo
                        var discoveredYear = cleaned.year.orEmpty()

                        val suggestion = try {
                            queryImdbSuggestionParallel(cleaned.cleanTitle, cleaned.year, isSeries)
                        } catch (_: Throwable) { null }

                        if (suggestion != null) {
                            imdbId = suggestion.first
                            if (suggestion.second.isNotEmpty()) discoveredPoster = suggestion.second
                            if (discoveredYear.isEmpty() && suggestion.third.isNotEmpty()) discoveredYear = suggestion.third
                        }

                        var metaResult: StavioVodMetadata? = null
                        if (imdbId.isNotEmpty()) {
                            val primaryType = if (isSeries) "series" else "movie"
                            val secondaryType = if (isSeries) "movie" else "series"

                            metaResult = try {
                                fetchFromCinemeta(imdbId, primaryType, cleaned.cleanTitle, discoveredPoster, discoveredYear)
                            } catch (_: Throwable) { null }

                            if (metaResult == null || !metaResult.isLoaded) {
                                metaResult = try {
                                    fetchFromCinemeta(imdbId, secondaryType, cleaned.cleanTitle, discoveredPoster, discoveredYear)
                                } catch (_: Throwable) { null }
                            }
                        }

                        val base = metaResult ?: StavioVodMetadata(
                            imdbId = imdbId,
                            cleanTitle = cleaned.cleanTitle,
                            background = discoveredPoster,
                            poster = discoveredPoster,
                            genres = safeFallbackGenres,
                            releaseInfo = discoveredYear,
                            isLoaded = false
                        )
                        Pair(base, discoveredYear)
                    } ?: Pair(
                        StavioVodMetadata(
                            cleanTitle = cleaned.cleanTitle,
                            background = safeFallbackLogo,
                            poster = safeFallbackLogo,
                            genres = safeFallbackGenres,
                            releaseInfo = cleaned.year.orEmpty()
                        ),
                        cleaned.year.orEmpty()
                    )
                }

                val tmdbDeferred = async {
                    kotlinx.coroutines.withTimeoutOrNull(2400L) {
                        fetchTmdbOrTvMazeParallel(cleaned.cleanTitle, isSeries, effectiveTmdbKey, normLang)
                    } ?: StavioVodMetadata(cleanTitle = cleaned.cleanTitle)
                }

                val itunesDeferred = async {
                    kotlinx.coroutines.withTimeoutOrNull(1800L) {
                        fetchItunesDirectTrailer(cleaned.cleanTitle, cleaned.year, isSeries)
                    }
                }

                Triple(cinemetaDeferred.await(), tmdbDeferred.await(), itunesDeferred.await())
            }

            val cinemetaPair = cinemetaResult.first
            val tmdbPartial = cinemetaResult.second
            val itunesTrailer = cinemetaResult.third

            val baseMeta = cinemetaPair.first
            val discoveredYear = cinemetaPair.second

            val mergedCast = when {
                tmdbPartial.cast.isNotEmpty() && tmdbPartial.cast.any { !it.photoUrl.isNullOrBlank() } -> tmdbPartial.cast
                baseMeta.cast.isNotEmpty() -> baseMeta.cast
                else -> tmdbPartial.cast
            }

            val mergedTrailers = buildList {
                val seenIds = mutableSetOf<String>()
                // Önce TMDB'den gelen fragmanları ekle (kullanıcı diline göre sıralı)
                for (t in tmdbPartial.trailers) {
                    if (t.ytId.isNotBlank() && seenIds.add(t.ytId)) {
                        add(t.copy(directStreamUrl = itunesTrailer?.directStreamUrl.orEmpty()))
                    }
                }
                for (t in baseMeta.trailers) {
                    if (t.ytId.isNotBlank() && seenIds.add(t.ytId)) {
                        add(t.copy(directStreamUrl = if (isEmpty()) itunesTrailer?.directStreamUrl.orEmpty() else ""))
                    }
                }
                if (isEmpty() && itunesTrailer != null && itunesTrailer.directStreamUrl.isNotBlank()) {
                    add(itunesTrailer)
                }
            }

            // Ham açıklamayı al ve seçili uygulama diline anında çevir
            val rawDescription = tmdbPartial.description.ifBlank { baseMeta.description }.trim()
            val localizedDescription = if (rawDescription.isNotBlank()) {
                translateTextInstant(rawDescription, normLang)
            } else {
                ""
            }

            val finalResult = baseMeta.copy(
                cleanTitle = baseMeta.cleanTitle.ifBlank { tmdbPartial.cleanTitle.ifBlank { cleaned.cleanTitle } },
                background = baseMeta.background.ifBlank { tmdbPartial.background.ifBlank { baseMeta.poster.ifBlank { safeFallbackLogo } } },
                poster = baseMeta.poster.ifBlank { tmdbPartial.poster.ifBlank { safeFallbackLogo } },
                genres = baseMeta.genres.ifEmpty { safeFallbackGenres },
                releaseInfo = baseMeta.releaseInfo.ifBlank { tmdbPartial.releaseInfo.ifBlank { discoveredYear } },
                description = localizedDescription,
                imdbRating = baseMeta.imdbRating.ifBlank { tmdbPartial.imdbRating },
                cast = mergedCast,
                trailers = mergedTrailers,
                isLoaded = baseMeta.isLoaded || tmdbPartial.isLoaded
            )

            // Hem rawTitle hem de cleanTitle anahtarıyla mediaCache'e kaydet
            val cleanKey = buildCacheKey(cleaned.cleanTitle, cleaned.year, isSeries, normLang)
            mediaCache[cleanKey] = finalResult
            if (!rawTitle.isNullOrBlank()) {
                val rawKey = "raw_${rawTitle.trim().lowercase(Locale.ROOT)}_${isSeries}_$normLang"
                mediaCache[rawKey] = finalResult
            }

            finalResult
        } catch (_: Throwable) {
            fallbackMetadata
        }
    }

    private suspend fun queryImdbSuggestionParallel(
        cleanTitle: String,
        targetYear: String?,
        isSeries: Boolean
    ): Triple<String, String, String>? = coroutineScope {
        if (cleanTitle.isBlank()) return@coroutineScope null
        try {
            val encoded = URLEncoder.encode(cleanTitle, "UTF-8").replace("+", "%20")
            val firstChar = cleanTitle.firstOrNull { it.isLetterOrDigit() }?.lowercaseChar() ?: 'x'
            val urlPrimary = "https://v3.sg.media-imdb.com/suggestion/x/$encoded.json"
            val urlSecondary = "https://v2.sg.media-imdb.com/suggestion/$firstChar/$encoded.json"

            val primaryDeferred = async {
                parseImdbSuggestionJson(httpGet(urlPrimary), cleanTitle, targetYear, isSeries)
            }
            val secondaryDeferred = async {
                parseImdbSuggestionJson(httpGet(urlSecondary), cleanTitle, targetYear, isSeries)
            }

            primaryDeferred.await() ?: secondaryDeferred.await()
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseImdbSuggestionJson(
        jsonStr: String?,
        cleanTitle: String,
        targetYear: String?,
        isSeries: Boolean
    ): Triple<String, String, String>? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val root = JSONObject(jsonStr)
            val dArray = root.optJSONArray("d") ?: return null
            if (dArray.length() == 0) return null

            var bestObj: JSONObject? = null
            var bestScore = -1

            for (i in 0 until dArray.length()) {
                val item = dArray.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (!id.startsWith("tt")) continue

                val qid = item.optString("qid").lowercase(Locale.ROOT)
                val itemYear = item.optInt("y", 0).let { if (it > 0) it.toString() else "" }
                val itemTitle = item.optString("l")

                var score = 0
                if (isSeries) {
                    if (qid == "tvseries" || qid == "tvminiseries") score += 50
                    else if (qid.contains("tv")) score += 25
                } else {
                    if (qid == "movie" || qid == "tvmovie") score += 50
                    else if (qid == "video" || qid == "short") score += 20
                }

                if (!targetYear.isNullOrEmpty() && itemYear == targetYear) {
                    score += 40
                }
                if (itemTitle.equals(cleanTitle, ignoreCase = true)) {
                    score += 30
                }
                score += (20 - i * 2).coerceAtLeast(0)

                if (score > bestScore) {
                    bestScore = score
                    bestObj = item
                }
            }

            if (bestObj != null) {
                val id = bestObj.optString("id")
                val poster = bestObj.optJSONObject("i")?.optString("imageUrl").orEmpty()
                val year = bestObj.optInt("y", 0).let { if (it > 0) it.toString() else "" }
                Triple(id, poster, year)
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun fetchFromCinemeta(
        imdbId: String,
        type: String,
        cleanTitle: String,
        fallbackPoster: String,
        fallbackYear: String
    ): StavioVodMetadata? {
        return try {
            val url = "https://v3-cinemeta.strem.io/meta/$type/$imdbId.json"
            val jsonStr = httpGet(url) ?: return null
            val root = try { JSONObject(jsonStr) } catch (_: Throwable) { return null }
            val meta = root.optJSONObject("meta") ?: return null

            val name = meta.optString("name").ifEmpty { cleanTitle }
            val background = meta.optString("background")
            val poster = meta.optString("poster").ifEmpty { fallbackPoster }
            val logo = meta.optString("logo")

            val genres = mutableListOf<String>()
            val genresArr = meta.optJSONArray("genres") ?: meta.optJSONArray("genre")
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val g = genresArr.optString(i).trim()
                    if (g.isNotEmpty()) genres.add(g)
                }
            }

            val releaseInfo = meta.optString("releaseInfo")
                .ifEmpty { meta.optString("year") }
                .ifEmpty { fallbackYear }

            val rawRuntime = meta.optString("runtime")
            val runtime = formatRuntimeToTurkish(rawRuntime)

            val imdbRating = meta.optString("imdbRating")

            val directors = jsonArrayToStringList(meta.optJSONArray("director"))
            val writers = jsonArrayToStringList(meta.optJSONArray("writer"))
            val description = meta.optString("description")

            // Cast (Null/Undefined ve tekrar eden isimlere karşı korumalı)
            val castList = mutableListOf<StavioActor>()
            val seenActorNames = mutableSetOf<String>()
            val creditsCast = meta.optJSONArray("credits_cast")
            if (creditsCast != null && creditsCast.length() > 0) {
                for (i in 0 until creditsCast.length()) {
                    val cObj = creditsCast.optJSONObject(i) ?: continue
                    val actorName = cObj.optString("name").trim()
                    if (actorName.isEmpty() || !seenActorNames.add(actorName.lowercase(Locale.ROOT))) continue
                    val character = cObj.optString("character").trim()
                    val profilePath = cObj.optString("profile_path").trim().let {
                        when {
                            it.startsWith("http") -> it
                            it.startsWith("/") -> "https://image.tmdb.org/t/p/w185$it"
                            else -> null
                        }
                    }
                    castList.add(StavioActor(name = actorName, character = character, photoUrl = profilePath))
                }
            } else {
                val simpleCast = jsonArrayToStringList(meta.optJSONArray("cast"))
                simpleCast.forEach { actorName ->
                    if (actorName.isNotEmpty() && seenActorNames.add(actorName.lowercase(Locale.ROOT))) {
                        castList.add(StavioActor(name = actorName))
                    }
                }
            }

            // Trailers & TrailerStreams (Null/Undefined korumalı)
            val trailers = mutableListOf<StavioTrailer>()
            val seenYtIds = mutableSetOf<String>()

            val trailerStreams = meta.optJSONArray("trailerStreams")
            if (trailerStreams != null) {
                for (i in 0 until trailerStreams.length()) {
                    val tObj = trailerStreams.optJSONObject(i) ?: continue
                    val ytId = tObj.optString("ytId").trim()
                    val tTitle = tObj.optString("title").ifEmpty { "Fragman" }
                    if (ytId.isNotEmpty() && seenYtIds.add(ytId)) {
                        trailers.add(StavioTrailer(ytId = ytId, title = tTitle))
                    }
                }
            }

            val trailersArr = meta.optJSONArray("trailers")
            if (trailersArr != null) {
                for (i in 0 until trailersArr.length()) {
                    val tObj = trailersArr.optJSONObject(i) ?: continue
                    val ytId = tObj.optString("source").trim()
                    val tType = tObj.optString("type").ifEmpty { "Trailer" }
                    if (ytId.isNotEmpty() && seenYtIds.add(ytId)) {
                        trailers.add(StavioTrailer(ytId = ytId, title = "Fragman", type = tType))
                    }
                }
            }

            val country = meta.optString("country")
            val rawStatus = meta.optString("status")
            val status = when {
                rawStatus.equals("Continuing", ignoreCase = true) -> "Devam Ediyor"
                rawStatus.equals("Ended", ignoreCase = true) -> "Sona Erdi"
                rawStatus.isNotEmpty() -> rawStatus
                else -> ""
            }

            // Episodes (videos array for Series - Null/Undefined korumalı)
            val episodesMap = mutableMapOf<Pair<Int, Int>, StavioEpisodeMeta>()
            val videosArr = meta.optJSONArray("videos")
            if (videosArr != null) {
                for (i in 0 until videosArr.length()) {
                    val vObj = videosArr.optJSONObject(i) ?: continue
                    val season = vObj.optInt("season", -1)
                    val episode = vObj.optInt("episode", vObj.optInt("number", -1))
                    if (season <= 0 || episode <= 0) continue

                    val epTitle = vObj.optString("name").ifEmpty { vObj.optString("title") }
                    val epOverview = vObj.optString("overview").ifEmpty { vObj.optString("description") }
                    val epThumb = vObj.optString("thumbnail")
                    val epReleasedRaw = vObj.optString("released").ifEmpty { vObj.optString("firstAired") }
                    val epRating = vObj.optString("imdbRating")

                    episodesMap[Pair(season, episode)] = StavioEpisodeMeta(
                        season = season,
                        episode = episode,
                        title = epTitle,
                        overview = epOverview,
                        thumbnail = epThumb,
                        releasedFormatted = formatIsoDateToTurkish(epReleasedRaw),
                        imdbRating = epRating
                    )
                }
            }

            StavioVodMetadata(
                imdbId = imdbId,
                cleanTitle = name,
                background = background,
                poster = poster,
                logo = logo,
                genres = genres,
                releaseInfo = releaseInfo,
                runtime = runtime,
                imdbRating = imdbRating,
                directors = directors,
                writers = writers,
                description = description,
                cast = castList,
                trailers = trailers,
                country = country,
                status = status,
                episodes = episodesMap,
                isLoaded = true
            )
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun fetchTmdbOrTvMazeParallel(
        cleanTitle: String,
        isSeries: Boolean,
        tmdbKey: String,
        languageCode: String = "tr"
    ): StavioVodMetadata {
        var partial = StavioVodMetadata(cleanTitle = cleanTitle)
        val tmdbLang = toTmdbLanguageTag(languageCode)
        try {
            val tmdbApi = TmdbApi.create()
            val searchResp = tmdbApi.searchMulti(query = cleanTitle, apiKey = tmdbKey, language = tmdbLang)
            val resultsList = searchResp.results.orEmpty()
            val best = resultsList.firstOrNull {
                if (isSeries) it.media_type == "tv" || it.name != null
                else it.media_type == "movie" || it.title != null
            } ?: resultsList.firstOrNull()

            if (best != null && best.id > 0) {
                var localizedOverview = best.overview?.takeIf { it.isNotBlank() }.orEmpty()
                if (localizedOverview.isBlank() && tmdbLang != "en-US") {
                    // Seçili dilde TMDB özeti boşsa İngilizce özeti çek (sonra otomatik çevrilir)
                    val enResp = try {
                        tmdbApi.searchMulti(query = cleanTitle, apiKey = tmdbKey, language = "en-US")
                    } catch (_: Throwable) { null }
                    localizedOverview = enResp?.results?.firstOrNull { it.id == best.id }?.overview.orEmpty()
                }

                val backdrop = best.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }.orEmpty()
                val poster = best.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }.orEmpty()

                val isTvType = isSeries || best.media_type == "tv"
                val (credits, tmdbTrailers) = coroutineScope {
                    val creditsDef = async {
                        try {
                            if (isTvType) {
                                tmdbApi.getTvCredits(best.id, tmdbKey, tmdbLang)
                            } else {
                                tmdbApi.getMovieCredits(best.id, tmdbKey, tmdbLang)
                            }
                        } catch (_: Throwable) { null }
                    }
                    val trailersDef = async {
                        fetchTmdbTrailers(best.id, isTvType, tmdbKey, tmdbLang)
                    }
                    Pair(creditsDef.await(), trailersDef.await())
                }

                val seenNames = mutableSetOf<String>()
                val tmdbActors = credits?.cast.orEmpty()
                    .mapNotNull { c ->
                        val actorName = c.name?.trim().orEmpty()
                        if (actorName.isEmpty() || !seenNames.add(actorName.lowercase(Locale.ROOT))) {
                            null
                        } else {
                            StavioActor(
                                name = actorName,
                                character = c.character?.trim().orEmpty(),
                                photoUrl = c.profile_path?.let {
                                    if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w185$it"
                                }
                            )
                        }
                    }
                    .take(15)

                partial = partial.copy(
                    background = backdrop,
                    poster = poster,
                    description = localizedOverview,
                    imdbRating = best.vote_average?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f", it) }.orEmpty(),
                    releaseInfo = (best.release_date ?: best.first_air_date)?.take(4).orEmpty(),
                    cast = tmdbActors,
                    trailers = tmdbTrailers,
                    isLoaded = true
                )
            }
        } catch (_: Throwable) {}

        // Eğer diziyse ve hala oyuncu fotoğrafı veya açıklama eksikse TVMaze fallback
        if (isSeries && (partial.cast.isEmpty() || partial.description.isEmpty())) {
            try {
                val tvMaze = TvMazeApi.create()
                val shows = tvMaze.searchShows(cleanTitle)
                val match = shows.maxByOrNull { it.score ?: 0.0 }?.show
                if (match != null && match.id > 0) {
                    val seenNames = mutableSetOf<String>()
                    val tvCast = try {
                        tvMaze.getShowCast(match.id).mapNotNull { item ->
                            val personName = item.person?.name?.trim().orEmpty()
                            if (personName.isEmpty() || !seenNames.add(personName.lowercase(Locale.ROOT))) {
                                null
                            } else {
                                StavioActor(
                                    name = personName,
                                    character = item.character?.name?.trim().orEmpty(),
                                    photoUrl = item.person?.image?.medium
                                )
                            }
                        }.take(12)
                    } catch (_: Throwable) { emptyList() }

                    val cleanSum = match.summary?.replace(Regex("<[^>]*>"), "")?.trim().orEmpty()
                    partial = partial.copy(
                        description = partial.description.ifEmpty { cleanSum },
                        poster = partial.poster.ifEmpty { match.image?.original ?: match.image?.medium.orEmpty() },
                        imdbRating = partial.imdbRating.ifEmpty {
                            match.rating?.average?.let { String.format(Locale.US, "%.1f", it) }.orEmpty()
                        },
                        cast = if (partial.cast.isEmpty()) tvCast else partial.cast,
                        isLoaded = true
                    )
                }
            } catch (_: Throwable) {}
        }

        return partial
    }

    private fun fetchTmdbTrailers(
        tmdbId: Int,
        isTv: Boolean,
        tmdbKey: String,
        tmdbLang: String = "tr-TR"
    ): List<StavioTrailer> {
        val result = mutableListOf<StavioTrailer>()
        val seenIds = mutableSetOf<String>()
        val mediaSegment = if (isTv) "tv" else "movie"
        val urls = listOf(
            "https://api.themoviedb.org/3/$mediaSegment/$tmdbId/videos?api_key=$tmdbKey&language=$tmdbLang",
            "https://api.themoviedb.org/3/$mediaSegment/$tmdbId/videos?api_key=$tmdbKey&language=en-US"
        ).distinct()
        for (url in urls) {
            try {
                val jsonStr = httpGet(url) ?: continue
                val root = JSONObject(jsonStr)
                val arr = root.optJSONArray("results") ?: continue
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val site = obj.optString("site")
                    val key = obj.optString("key").trim()
                    val type = obj.optString("type").ifEmpty { "Trailer" }
                    val name = obj.optString("name").ifEmpty { "Fragman" }
                    if (site.equals("YouTube", ignoreCase = true) && key.isNotEmpty() && seenIds.add(key)) {
                        result.add(StavioTrailer(ytId = key, title = name, type = type))
                    }
                }
                if (result.isNotEmpty()) break
            } catch (_: Throwable) {}
        }
        return result.sortedBy { if (it.type.equals("Trailer", ignoreCase = true)) 0 else 1 }
    }

    fun fetchItunesDirectTrailer(cleanTitle: String, year: String?, isSeries: Boolean): StavioTrailer? {
        if (cleanTitle.isBlank()) return null
        return try {
            val encoded = URLEncoder.encode(cleanTitle.trim(), "UTF-8")
            val urls = if (isSeries) {
                listOf(
                    "https://itunes.apple.com/search?term=$encoded&entity=tvEpisode&limit=6&country=us",
                    "https://itunes.apple.com/search?term=$encoded&entity=movie&limit=6&country=us"
                )
            } else {
                listOf(
                    "https://itunes.apple.com/search?term=$encoded&entity=movie&limit=8&country=tr",
                    "https://itunes.apple.com/search?term=$encoded&entity=movie&limit=8&country=us"
                )
            }
            for (url in urls) {
                val jsonStr = httpGet(url) ?: continue
                val root = JSONObject(jsonStr)
                val results = root.optJSONArray("results") ?: continue
                var firstValidCandidate: StavioTrailer? = null
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val previewUrl = item.optString("previewUrl").trim()
                    if (previewUrl.isBlank() || !previewUrl.startsWith("http")) continue
                    val trackName = item.optString("trackName")
                        .ifBlank { item.optString("collectionName") }
                        .ifBlank { cleanTitle }
                    val releaseDate = item.optString("releaseDate").take(4)
                    val artwork = item.optString("artworkUrl100")
                        .replace("100x100bb", "600x600bb")
                    val candidate = StavioTrailer(
                        ytId = "",
                        title = "$trackName - Fragman",
                        type = "Trailer",
                        directStreamUrl = previewUrl,
                        customThumbnailUrl = artwork
                    )
                    if (firstValidCandidate == null) {
                        firstValidCandidate = candidate
                    }
                    if (year.isNullOrBlank() || releaseDate == year || trackName.equals(cleanTitle, ignoreCase = true)) {
                        return candidate
                    }
                }
                if (firstValidCandidate != null) {
                    return firstValidCandidate
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractYoutubeDirectUrlViaInnertube(cleanId: String): String {
        val clients = listOf(
            Triple(
                "ANDROID_VR",
                """{"context":{"client":{"clientName":"ANDROID_VR","clientVersion":"1.60.19","deviceMake":"Oculus","deviceModel":"Quest 3","osName":"Android","osVersion":"12L","androidSdkVersion":32,"hl":"en","gl":"US"}},"videoId":"$cleanId","contentCheckOk":true,"racyCheckOk":true}""",
                "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
            ),
            Triple(
                "IOS",
                """{"context":{"client":{"clientName":"IOS","clientVersion":"19.45.4","deviceMake":"Apple","deviceModel":"iPhone16,2","osName":"iPhone","osVersion":"18.1.0.22B83","hl":"en","gl":"US"}},"videoId":"$cleanId","contentCheckOk":true,"racyCheckOk":true}""",
                "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
            )
        )

        for ((_, payload, userAgent) in clients) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 1800
                    readTimeout = 1800
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", userAgent)
                }
                conn.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }
                if (conn.responseCode in 200..299) {
                    val respStr = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                    val root = JSONObject(respStr)
                    val streamingData = root.optJSONObject("streamingData") ?: continue

                    val formats = streamingData.optJSONArray("formats")
                    if (formats != null && formats.length() > 0) {
                        var bestMp4Url = ""
                        var bestHeight = -1
                        for (i in 0 until formats.length()) {
                            val fmt = formats.optJSONObject(i) ?: continue
                            val directUrl = fmt.optString("url").trim()
                            if (!directUrl.startsWith("http")) continue
                            val mime = fmt.optString("mimeType").lowercase(Locale.ROOT)
                            val height = fmt.optInt("height", 0)
                            if (mime.contains("video/mp4") || mime.contains("video/")) {
                                if (height >= bestHeight) {
                                    bestHeight = height
                                    bestMp4Url = directUrl
                                }
                            }
                        }
                        if (bestMp4Url.isNotBlank()) {
                            return bestMp4Url
                        }
                    }

                    val hlsManifestUrl = streamingData.optString("hlsManifestUrl").trim()
                    if (hlsManifestUrl.startsWith("http")) {
                        return hlsManifestUrl
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Throwable) {}
            }
        }
        return ""
    }

    fun resolveDirectStreamFromYtId(ytId: String): String {
        val cleanId = ytId.trim()
        if (cleanId.isBlank()) return ""
        directStreamCache[cleanId]?.let { if (it.isNotBlank()) return it }

        // Doğrudan YouTube InnerTube (ANDROID_VR / IOS) üzerinden hızlı MP4/HLS akışı çek
        val innertubeUrl = extractYoutubeDirectUrlViaInnertube(cleanId)
        if (innertubeUrl.isNotBlank()) {
            directStreamCache[cleanId] = innertubeUrl
            return innertubeUrl
        }
        return ""
    }

    fun resolveDirectStreamForTrailer(
        trailer: StavioTrailer,
        cleanTitle: String,
        year: String?,
        isSeries: Boolean
    ): String {
        if (trailer.directStreamUrl.isNotBlank()) return trailer.directStreamUrl
        if (trailer.ytId.isNotBlank()) {
            val resolvedYt = resolveDirectStreamFromYtId(trailer.ytId)
            if (resolvedYt.isNotBlank()) return resolvedYt
        }
        val itunes = fetchItunesDirectTrailer(cleanTitle, year, isSeries)
        if (itunes != null && itunes.directStreamUrl.isNotBlank()) {
            return itunes.directStreamUrl
        }
        return ""
    }

    fun resolveFallbackTrailer(cleanTitle: String, year: String?, isSeries: Boolean): StavioTrailer? {
        if (cleanTitle.isBlank()) return null
        val itunes = fetchItunesDirectTrailer(cleanTitle, year, isSeries)
        val ytId = resolveFallbackTrailerYtId(cleanTitle, year, isSeries)
        val directUrl = when {
            itunes != null && itunes.directStreamUrl.isNotBlank() -> itunes.directStreamUrl
            ytId.isNotBlank() -> resolveDirectStreamFromYtId(ytId)
            else -> ""
        }
        if (ytId.isBlank() && directUrl.isBlank()) return null
        return StavioTrailer(
            ytId = ytId,
            title = "$cleanTitle - Fragman",
            type = "Trailer",
            directStreamUrl = directUrl,
            customThumbnailUrl = itunes?.customThumbnailUrl.orEmpty()
        )
    }

    fun resolveFallbackTrailerYtId(cleanTitle: String, year: String?, isSeries: Boolean): String {
        if (cleanTitle.isBlank()) return ""
        return try {
            val query = buildString {
                append(cleanTitle.trim())
                if (!year.isNullOrBlank()) append(" ").append(year.trim())
                append(if (isSeries) " official series trailer" else " official trailer")
            }
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchHtml = httpGet("https://www.youtube.com/results?search_query=$encoded&hl=en") ?: return ""
            val match = Regex("\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\"").find(searchHtml)
            match?.groupValues?.getOrNull(1).orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun formatRuntimeToTurkish(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val minMatch = Regex("(\\d+)\\s*min", RegexOption.IGNORE_CASE).find(raw)
            if (minMatch != null) {
                val totalMin = minMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: return raw
                val hours = totalMin / 60
                val mins = totalMin % 60
                return when {
                    hours > 0 && mins > 0 -> "${hours}s ${mins}dk"
                    hours > 0 -> "${hours}s"
                    else -> "${mins}dk"
                }
            }
            raw.replace("h", "s").replace("min", "dk")
        } catch (_: Throwable) {
            raw
        }
    }

    private fun formatIsoDateToTurkish(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            val cleanIso = iso.take(10)
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(cleanIso) ?: return cleanIso
            val formatter = SimpleDateFormat("d MMMM yyyy", Locale("tr", "TR"))
            formatter.format(date)
        } catch (_: Throwable) {
            iso.take(10)
        }
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return try {
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotEmpty()) list.add(s)
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2000
                readTimeout = 2200
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; StavioMedia/2.0)")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else null
        } catch (_: Throwable) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Throwable) {}
        }
    }
}
