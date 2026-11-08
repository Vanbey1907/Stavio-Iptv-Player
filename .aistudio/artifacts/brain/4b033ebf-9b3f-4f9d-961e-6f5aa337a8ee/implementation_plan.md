# Dinamik Oynatma Listesi Senkronizasyonu ve Performans Optimizasyon Planı

Yeni oynatma listesi eklendiğinde veya mevcut listeler arasında geçiş yapıldığında içeriğin anında güncellenmesini sağlamak, binlerce kanallı listelerde donmaları engelleyen sayfalı/kademeli yükleme (pagination) mimarisini kurmak ve ekran geçişlerini hafif, akıcı animasyonlarla optimize etmek için hazırlanan uygulama planıdır.

---

### Kullanıcı Tercihleri ve Onaylanan Kararlar

> [!IMPORTANT]
> Kullanıcı ile yapılan netleştirme görüşmesinde teyit edilen kararlar:
> - **Oynatma Listesi Değişimi**: Farklı bir oynatma listesi seçildiğinde veya yeni liste yüklendiğinde, seçilen listenin içeriği anında yüklenecek ve aktif liste olarak ayarlanacaktır.
> - **Uygulama Hızı ve Geçişler**: Kanallar sayfalanarak (lazy/paginated chunk) yüklenecek, UI thread'i tıkayan toplu regex hesaplamaları arka plana ve önbelleğe alınacak; ekranlar arasında hafif ve akıcı geçiş animasyonları kullanılacaktır.

---

### 1. Genel Bakış ve Temel Sorun Tespiti

1. **Liste Güncellenmeme Sorunu (Stale Playlist Bug)**:
   - Yeni bir M3U dosyası, URL veya MAG/Xtream hesabı eklendiğinde Room veritabanına kaydedilmesine rağmen `selectedPlaylist` durumu güncellenmemekteydi. Bu nedenle kullanıcı yeni liste eklese dahi ekranda ilk yüklenen listenin kanalları ve başlığı kalıyordu.
   - Liste seçim diyalogundan veya "IPTV Listeleri" sekmesinden başka bir listeye tıklandığında, UI seviyesindeki `groupedChannels` ve `allChannels` akışları eski filtreleme önbelleklerini temizlemeden kaldığı için içerik yenilenmiyordu.
2. **Performans ve Ekran Geçiş Yavaşlığı**:
   - Binlerce kanal (örneğin 7.000+ öğe) içeren listelerde `FilteredTabContent` ve `ChannelsScreen`, tüm liste üzerinde tek seferde ağır metin ayrıştırma ve gruplama yapıyordu.
   - Sekme ve ekran değişimlerinde animasyonların ağır olması ve ana thread üzerindeki ağır StateFlow hesaplamaları kare düşmelerine (jank) yol açıyordu.

---

### 2. Kullanıcı Deneyimi ve Arayüz Tasarımı (UX & Visual Design)

- **Anında Playlist Geçişi (Instant Switch UX)**:
  - Üst bardaki sağlayıcı seçiciden veya Listeler sekmesinden bir playlist seçildiği anda:
    - Başlık ve sayaç anında seçilen listeye göre yenilenecek.
    - Önceki listenin önbelleği sıfırlanıp yeni listenin ilk içerik paketi (< 50 ms) ekrana gelecektir.
    - Yeni bir liste yüklendiğinde otomatik olarak o liste aktif liste yapılacak ve doğrudan Canlı TV sekmesine yönlendirilecektir.
- **Sayfalamalı ve Kademeli Yükleme (Paginated Lazy Loading)**:
  - 10.000 kanallı büyük listelerde dahi dikey kaydırma sırasında UI donmayacak; kategoriler ve kanal kartları kullanıcı kaydırdıkça pürüzsüzce (chunked 50-100 öğe) render edilecektir.
- **Hafif ve Doğal Geçiş Animasyonları (Smooth Transitions)**:
  - Sekmeler arası geçişlerde ağır ölçekleme/zoom yerine hafif yatay kayma ve yumuşak fade (`fadeIn(animationSpec = tween(220)) + slideInHorizontally`) kullanılacaktır.
  - Oynatıcı açılışında ve kapanışında alt ekranın durumunu sıfırlamayan hafif yay (`spring`) animasyonları entegre edilecektir.

---

### 3. Kritik Ürün Kararları ve Mimari Tercihler

- **Karar 1: Yeni Liste Eklendiğinde Otomatik Aktifleşme (`Auto-Select on Import`)**:
  - *Seçilen Yaklaşım*: `addPlaylistFromUri`, `addPlaylistFromUrl` ve `addMagPlaylist` işlemlerinin sonunda yeni oluşturulan `PlaylistEntity` nesnesi doğrudan `selectedPlaylist.value` olarak atanacak ve `last_playlist_id` kalıcı olarak güncellenecektir.
  - *Gerekçe*: Kullanıcı yeni bir liste yüklediğinde hemen o listenin içeriğini görmek ister. Önceki listenin ekranda kalması kafa karışıklığı yaratmaktadır.
- **Karar 2: Reaktik DAO Sorgusu ve Playlist ID Anahtarı (`Reactive State Invalidation`)**:
  - *Seçilen Yaklaşım*: `DashboardScreen` ve `FilteredTabContent` bileşenlerindeki durumlar `selectedPlaylist?.id` anahtarı ile bağlanacaktır (`key(selectedPlaylist?.id)`). Liste değiştiğinde tüm alt grup ve filtre önbellekleri otomatik olarak temizlenip sıfırlanacaktır.
  - *Gerekçe*: Bellekte eski listeye ait kalan kalıntıların veya grupların yeni listede görünmesi engellenir.
- **Karar 3: Sayfalı Yükleme ve Önbellek İyileştirmesi (`Pagination & Pre-Classification`)**:
  - *Seçilen Yaklaşım*: M3U ayrıştırma sırasında veya arka plan akışında kanalların türü (`live`, `movie`, `series`) veritabanında saklanacak veya ID bazlı optimize ConcurrentHashMap ile UI bloke edilmeden sunulacaktır. `LazyColumn` içinde alt listeler sınırlı pencerelerle gösterilecektir.

---

### 4. Teknik Mimari ve Sistem Şeması

```
┌──────────────────────────────────────────────────────────────┐
│                    Kullanıcı Etkileşimi                      │
│   (Yeni M3U Ekleme / Sağlayıcı Seçici / Sekme Değişimi)      │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                        MainViewModel                         │
│  - addPlaylistFrom... -> playlistDao.insert -> selectPlaylist │
│  - selectPlaylistAndNavigate(playlist)                        │
│      ├── selectedPlaylist.value = playlist (ANINDA)          │
│      ├── prefs.putLong("last_playlist_id", playlist.id)      │
│      └── clearCaches() (mediaType & category cache temizliği)│
│                                                              │
│  - allChannels: flatMapLatest(selectedPlaylist)              │
│      └── channelDao.getChannelsByPlaylist(playlist.id)       │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                       DashboardScreen                        │
│  key(selectedPlaylist?.id) {                                 │
│      FilteredTabContent:                                     │
│        - Chunked / Paginated gruplandırma                    │
│        - Hafif tween(200) geçiş animasyonu                   │
│        - Anında güncellenen üst başlık ve sayaç              │
│  }                                                           │
└──────────────────────────────────────────────────────────────┘
```

---

### Yapılacak İş Adımları (Execution Steps)

1. **`MainViewModel.kt` İyileştirmesi**:
   - `addPlaylistFromUri`, `addPlaylistFromUrl` ve `addMagPlaylist` fonksiyonlarının başarı bloklarında, yeni eklenen liste `getPlaylistById` ile alınıp `selectPlaylistAndNavigate(newPlaylist)` çağrılacak.
   - `selectPlaylistAndNavigate` fonksiyonunda `mediaTypeCache` ve `seriesNameCache` önbellekleri temizlenecek.
   - `allChannels` akışının liste değişiminde anında reaktif tetiklenmesi güvence altına alınacak.
2. **`DashboardScreen.kt` ve `FilteredTabContent` İyileştirmesi**:
   - `FilteredTabContent` içinde `remember(selectedPlaylist?.id)` ve `LaunchedEffect(selectedPlaylist?.id, allChannels)` kullanılarak liste değişiminde eski listenin gösterilmesini engelleyen tam izolasyon sağlanacak.
   - Binlerce kanalın UI'ı dondurmasını önlemek için gruplandırılmış içerik ilk 30 kategori ve kategori başına ilk 20 öğe olarak kademeli (lazy paging) yüklenecek.
3. **Geçiş Animasyonlarının Hafifletilmesi**:
   - `IPTVHomeScreen.kt` ve `DashboardScreen.kt` içerisindeki ağır AnimatedContent geçişleri yerine akıcı ve hafif fade + micro slide (`tween(200)`) animasyonları uygulanacak.
4. **Doğrulama ve Derleme**:
   - `compile_applet` ile sıfır hata doğrulaması yapılacak.
   - Güncel uygulama tek bir `Stavio.apk` olarak ana dizine yerleştirilecek.
