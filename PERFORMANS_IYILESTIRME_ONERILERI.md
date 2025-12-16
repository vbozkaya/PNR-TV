# Performans İyileştirme Önerileri

## 🎯 Mevcut Durum Analizi

Yapılan iyileştirmeler:
- ✅ `CardPresenter.kt` - Hardware acceleration etkinleştirildi
- ✅ `CardPresenter.kt` - Dinamik resim boyutu
- ✅ `GridListRowPresenter.kt` - Özyineli view arama kaldırıldı

## 📋 Ek Öneriler

### 1. ⚠️ ContentAdapter.kt - Aynı Optimizasyonlar

**Sorun:** `ContentAdapter.kt` içinde hala eski performans sorunlu ayarlar var.

**Dosya:** `app/src/main/java/com/pnr/tv/ui/browse/ContentAdapter.kt` (satır 138-148)

**Mevcut Kod:**
```kotlin
contentImage.load(imageUrl) {
    placeholder(R.drawable.placeholder_image)
    error(R.drawable.placeholder_image)
    crossfade(true)
    scale(Scale.FILL)
    size(Size(1280, 720))  // ❌ Sabit büyük boyut
    allowHardware(false)   // ❌ Hardware devre dışı
    allowRgb565(true)
}
```

**Önerilen Değişiklik:**
```kotlin
// CardPresenter'daki gibi dinamik boyut hesapla
val screenWidth = contentImage.context.resources.displayMetrics.widthPixels
val cardWidth = (screenWidth / Constants.CARD_WIDTH_DIVISOR).toInt()
val cardHeight = (cardWidth * 9.0 / 16.0).roundToInt()

contentImage.load(imageUrl) {
    placeholder(R.drawable.placeholder_image)
    error(R.drawable.placeholder_image)
    crossfade(true)
    scale(Scale.FILL)
    size(Size(cardWidth, cardHeight))  // ✅ Dinamik boyut
    allowHardware(true)                // ✅ Hardware etkin
    allowRgb565(true)
}
```

**Etki:** ContentAdapter kullanılan yerlerde de aynı performans kazancı.

---

### 2. 🚀 ImageLoader - Paralel İstek Sayısını Artır

**Sorun:** `maxRequests = 1` çok düşük, resimler sırayla yükleniyor.

**Dosya:** `app/src/main/java/com/pnr/tv/PnrTvApplication.kt` (satır 72-76)

**Mevcut Kod:**
```kotlin
val dispatcher = Dispatcher().apply {
    maxRequests = 1        // ❌ Çok düşük
    maxRequestsPerHost = 1
}
```

**Önerilen Değişiklik:**
```kotlin
val dispatcher = Dispatcher().apply {
    maxRequests = 3        // ✅ TV için optimize (2-4 arası ideal)
    maxRequestsPerHost = 2 // ✅ Aynı host'tan 2 paralel istek
}
```

**Etki:** Resimler daha hızlı yüklenir, kullanıcı deneyimi iyileşir.

**Not:** Sunucu yükü endişesi varsa, önce 2 ile test edin.

---

### 3. 💾 Disk Cache Ekle

**Sorun:** Sadece memory cache var, disk cache yok.

**Dosya:** `app/src/main/java/com/pnr/tv/PnrTvApplication.kt` (satır 91-94)

**Mevcut Kod:**
```kotlin
return ImageLoader.Builder(this)
    .okHttpClient(okHttpClient)
    .memoryCache(memoryCache)
    .build()  // ❌ Disk cache yok
```

**Önerilen Değişiklik:**
```kotlin
import coil.disk.DiskCache

val diskCache = DiskCache.Builder()
    .directory(cacheDir.resolve("image_cache"))
    .maxSizeBytes(50 * 1024 * 1024) // 50 MB disk cache
    .build()

return ImageLoader.Builder(this)
    .okHttpClient(okHttpClient)
    .memoryCache(memoryCache)
    .diskCache(diskCache)  // ✅ Disk cache eklendi
    .build()
```

**Etki:** Uygulama yeniden açıldığında resimler disk'ten yüklenir, daha hızlı.

---

### 4. 🔄 HorizontalGridView - View Cache Ayarları

**Sorun:** Leanback HorizontalGridView için view cache ayarları yok.

**Dosya:** `app/src/main/java/com/pnr/tv/ui/livestreams/GridListRowPresenter.kt`

**Önerilen Değişiklik:**
```kotlin
override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
    val viewHolder = super.createRowViewHolder(parent)
    
    val gridView = viewHolder.view.findViewById<HorizontalGridView>(androidx.leanback.R.id.row_content)
    
    if (gridView != null) {
        gridView.setPadding(0, 0, 0, 0)
        
        // ✅ View cache ayarları - daha akıcı scroll için
        // RecycledViewPool kullanarak view'ları yeniden kullan
        // Bu, scroll sırasında view oluşturma maliyetini azaltır
        gridView.setItemViewCacheSize(10) // Önceden oluşturulmuş view'ları cache'le
    }
    
    return viewHolder
}
```

**Etki:** Scroll sırasında view oluşturma maliyeti azalır, daha akıcı gezinme.

---

### 5. 📱 RecyclerView - Item View Cache Size

**Sorun:** RecyclerView için itemViewCacheSize ayarlanmamış.

**Dosya:** `app/src/main/java/com/pnr/tv/ui/base/BaseBrowseFragment.kt` (satır 507-521)

**Mevcut Kod:**
```kotlin
contentRecyclerView.setHasFixedSize(true)
contentRecyclerView.isDrawingCacheEnabled = true
contentRecyclerView.itemAnimator = null
// ❌ itemViewCacheSize yok
```

**Önerilen Değişiklik:**
```kotlin
contentRecyclerView.setHasFixedSize(true)
contentRecyclerView.isDrawingCacheEnabled = true
contentRecyclerView.itemAnimator = null

// ✅ View cache ayarları - performans için
contentRecyclerView.setItemViewCacheSize(20) // Önceden oluşturulmuş view'ları cache'le
contentRecyclerView.recycledViewPool.setMaxRecycledViews(0, 15) // View pool boyutu
```

**Etki:** Liste scroll'unda view oluşturma maliyeti azalır.

---

### 6. 🎨 BackgroundManager - Hardware Acceleration

**Sorun:** BackgroundManager'da `allowHardware(false)` kullanılıyor.

**Dosya:** `app/src/main/java/com/pnr/tv/util/BackgroundManager.kt` (satır 86)

**Mevcut Kod:**
```kotlin
.allowHardware(false) // ❌ Hardware bitmap'leri devre dışı
```

**Önerilen Değişiklik:**
```kotlin
.allowHardware(true) // ✅ Hardware acceleration - arkaplan için de etkin
```

**Not:** Arkaplan görseli genellikle büyük olduğu için hardware acceleration önemli.

---

### 7. ⚡ Lazy Loading - Görünür Olmayan Resimleri Yükleme

**Sorun:** Tüm resimler aynı anda yüklenmeye çalışılıyor olabilir.

**Öneri:** Coil zaten lazy loading yapıyor, ancak ek optimizasyon için:

**CardPresenter.kt'de:**
```kotlin
cardView.mainImageView?.load(imageUrl) {
    placeholder(R.drawable.placeholder_image)
    error(R.drawable.placeholder_image)
    crossfade(true)
    scale(Scale.FILL)
    size(Size(cardWidth, cardHeight))
    allowHardware(true)
    allowRgb565(true)
    
    // ✅ Ek optimizasyonlar
    memoryCachePolicy(CachePolicy.ENABLED)  // Memory cache'i etkinleştir
    diskCachePolicy(CachePolicy.ENABLED)    // Disk cache'i etkinleştir
    networkCachePolicy(CachePolicy.ENABLED) // Network cache'i etkinleştir
}
```

---

### 8. 🔍 View Binding Optimizasyonu

**Sorun:** Her bind işleminde view araması yapılıyor olabilir.

**Öneri:** ViewHolder pattern'i zaten kullanılıyor, ancak view referanslarını cache'lemek:

```kotlin
class ContentAdapter.ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    // ✅ View referanslarını cache'le
    val contentImage: ImageView = itemView.findViewById(R.id.content_image)
    val contentTitle: TextView = itemView.findViewById(R.id.content_title)
    // ... diğer view'lar
}
```

Bu zaten yapılmış olabilir, kontrol edin.

---

### 9. 📊 Memory Profiling

**Öneri:** Android Studio Profiler ile memory kullanımını izleyin:

1. **Memory Profiler** açın
2. Uygulamayı çalıştırın
3. Listeler arasında gezinin
4. Memory kullanımını gözlemleyin
5. Memory leak'leri tespit edin

**Hedef:** Memory kullanımı stabil olmalı, sürekli artmamalı.

---

### 10. 🎯 Leanback Library Optimizasyonları

**Öneri:** Leanback kütüphanesi için ek ayarlar:

```kotlin
// BrowseFragment veya MainFragment'te
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // ✅ Leanback optimizasyonları
    setHeadersState(HEADERS_ENABLED) // Header'ları etkinleştir
    setHeadersTransitionOnBackEnabled(true) // Geri tuşu optimizasyonu
    
    // Presenter selector'ı optimize et
    setAdapter(ArrayObjectAdapter(presenterSelector))
}
```

---

## 📈 Öncelik Sırası

1. **Yüksek Öncelik:**
   - ✅ ContentAdapter.kt optimizasyonu (1. madde)
   - ✅ ImageLoader paralel istek artırma (2. madde)
   - ✅ Disk cache ekleme (3. madde)

2. **Orta Öncelik:**
   - ✅ HorizontalGridView view cache (4. madde)
   - ✅ RecyclerView itemViewCacheSize (5. madde)
   - ✅ BackgroundManager hardware (6. madde)

3. **Düşük Öncelik:**
   - Lazy loading optimizasyonları (7. madde)
   - View binding optimizasyonları (8. madde)
   - Memory profiling (9. madde)

---

## 🧪 Test Önerileri

1. **Gerçek TV Cihazında Test:**
   - Kategoriler arasında gezinme
   - Liste içinde scroll
   - Resim yükleme hızı
   - Memory kullanımı

2. **Performans Metrikleri:**
   - Frame rate (60 FPS hedef)
   - Memory kullanımı (stabil olmalı)
   - CPU kullanımı (scroll sırasında %50'nin altında)

3. **A/B Test:**
   - Önce mevcut versiyonu test edin
   - Sonra optimizasyonları uygulayın
   - Performans farkını ölçün

---

## ⚠️ Dikkat Edilmesi Gerekenler

1. **maxRequests Artırma:**
   - Sunucu yükünü artırabilir
   - Önce 2-3 ile test edin
   - Gerekirse geri alın

2. **Disk Cache:**
   - Disk alanı kullanır
   - 50 MB makul bir değer
   - Düşük disk alanı olan cihazlarda sorun olabilir

3. **View Cache:**
   - Memory kullanımını artırır
   - Çok yüksek değerler memory sorunlarına yol açabilir
   - 10-20 arası değerler genellikle güvenli

---

## 📝 Sonuç

Bu öneriler, özellikle düşük donanımlı Android TV cihazlarında performansı önemli ölçüde artıracaktır. Öncelik sırasına göre uygulayın ve her değişiklikten sonra test edin.



