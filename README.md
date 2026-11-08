# 🎬 Stavio — Next-Gen IPTV & Stream Player for Android

**Stavio** is a modern, high-performance Android IPTV, VOD (Movies & Series), and Live TV player built with **Kotlin**, **Jetpack Compose**, and **Media3 (ExoPlayer)**. Designed with a sleek dark cinema aesthetic, Stavio delivers instant catalog browsing, smart metadata enrichment, built-in stream extraction, and a studio-grade playback experience.

---

## ✨ Key Features

### 📺 Universal IPTV & Playlist Support
- **Multi-Format Playlists:** Seamlessly import and manage **M3U / M3U8 URLs**, local **M3U files**, **Xtream Codes**, and **MAC Portal (Stalker)** accounts.
- **Smart Auto-Categorization:** Automatically classifies channels into **Live TV**, **Movies**, and **Series** with fast, precomputed category shelves and instant pill filtering.
- **Playlist-Scoped Continue Watching:** Tracks your exact playback position, remaining time, and episode progress per playlist—complete with landscape preview cards and progress bars.

### 🎥 Cinema-Grade Video Player
- **Powered by Media3 / ExoPlayer:** Hardware-accelerated playback supporting **HLS (`.m3u8`)**, **MPEG-TS (`.ts`)**, **MP4**, **MKV**, **DASH (`.mpd`)**, **WebM**, and **FLV**.
- **Track & Subtitle Memory:** Remembers your preferred audio language and subtitle tracks across sessions.
- **Custom Header & SSL Support:** Built-in support for protected streams requiring custom `Referer`, `Origin`, `Cookie`, and `User-Agent` headers.
- **Integrated Trailer Player:** Watch official movie and series trailers directly inside the detail screen with a minimalist custom control overlay.

### 🔍 Built-In Stream Extractor & Site Sniffer
- **Deep Link & Website Scanner:** Paste any website URL, embed link, or stream address to automatically discover playable **M3U8**, **MP4**, **TS**, **MPD**, and **MKV** sources.
- **Hidden Stream Detection:** Unpacks obfuscated JavaScript (`P.A.C.K.E.R.`), `Base64 / atob` payloads, lazy-loaded `<iframe>` embeds, and HLS master playlist quality variants (`4K`, `1080p`, `720p`).
- **Interactive Site Browser:** Browse websites directly inside the built-in silent sniffer to capture dynamic `XHR / Fetch` media streams on the fly, play them immediately, download them, or save them directly to your IPTV library.

### 🌍 Dynamic Metadata & Instant Translation
- **Rich VOD Details:** Automatically fetches posters, backdrops, cast, ratings, and synopses for movies and series.
- **Real-Time Localization:** Instantly translates movie and series descriptions into your selected app language.

---

## 🛠️ Tech Stack

- **Language:** 100% Kotlin (Coroutines & Flow)
- **UI Framework:** Jetpack Compose (Material Design 3, Custom Dark Glassmorphism UI)
- **Media Engine:** AndroidX Media3 (ExoPlayer, HLS, DASH, TS Extractors)
- **Local Storage:** Room Database + SharedPreferences
- **Networking:** OkHttp, Retrofit, Coil (Image Loading)

---

## 🚀 Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/stavio.git
