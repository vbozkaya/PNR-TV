# ANDROID TV FOCUS RESTORE TIMING FIX

Sen Expert bir Android Geliştiricisisin. Aşağıdaki talimatları `BaseBrowseFragment.kt` dosyasında, **yorum yapmadan ve başka hiçbir şeye dokunmadan** uygula.

**SORUN:**
Detay sayfasından geri dönüldüğünde (`onResume`), içerik listesi henüz tam yüklenmediği veya `ViewHolder`'lar hazır olmadığı için `scrollToPosition` ve `requestFocus` çağrıları başarısız oluyor veya "Görünmez Focus" (Ghost Focus) yaratıyor.
Ayrıca `is_initial_launch` argümanı kalıcı olduğu için (Sticky Argument), fragment yeniden oluşturulduğunda sistem yanlışlıkla kendini sıfırlıyor.

**HEDEF:**
1.  Restore işlemini `onResume` anından, verinin kesin olarak yüklendiği `submitList` callback anına ertelemek (Pending Restore Pattern).
2.  `is_initial_launch` bayrağını tek kullanımlık hale getirmek.
3.  `ContentBrowseFragment`'ta ViewModel başlatma sırasını düzeltmek.

---

### 🛠️ ADIM 1: `BaseBrowseFragment.kt`

**1. Yeni Değişken Ekle:**
Sınıfın içine, restore edilecek pozisyonu geçici olarak tutacak bir değişken ekle:
```kotlin
    // Restore edilecek pozisyonu bekletmek için
    private var pendingRestorePosition: Int? = null
```

**2. `onCreate` Güncellemesi (Sticky Argument Fix):**
`onCreate` metodunu güncelle:
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bundle'dan kaydedilmiş pozisyonu oku (sistem tarafından destroy edildiyse)
        bundleSavedPosition = savedInstanceState?.getInt(KEY_LAST_FOCUSED_CONTENT_POSITION, -1)?.takeIf { it != -1 }
        
        // INITIAL LAUNCH kontrolü: Ana menüden ilk kez açılıyorsa hafızayı temizle
        val isInitialLaunch = arguments?.getBoolean("is_initial_launch", false) ?: false
        if (isInitialLaunch) {
            viewModel.lastFocusedContentPosition = null
            viewModel.lastSelectedCategoryId = null
            savedLastFocusedPosition = null
            savedLastSelectedCategoryId = null
            
            // [FIX]: Bayrağı false yap (Sticky Argument Fix)
            arguments?.putBoolean("is_initial_launch", false)
        }
    }
```

**3. `onResume` Güncellemesi (Erteleme Mantığı):**
`onResume` metodundaki mevcut restore kodunu sil ve yerine şunu yaz (Sadece not alıyoruz, işlem yapmıyoruz):
```kotlin
    override fun onResume() {
        super.onResume()
        (activity as? ToolbarController)?.hideTopMenu()

        // Değerleri ViewModel'e geri yükle
        savedLastFocusedPosition?.let { viewModel.lastFocusedContentPosition = it }
        savedLastSelectedCategoryId?.let { viewModel.lastSelectedCategoryId = it }
        bundleSavedPosition?.let { viewModel.lastFocusedContentPosition = it }
        
        // DEEP RESTORE: Pozisyonu not al, veriler yüklenince (observeContents içinde) uygulayacağız
        val isInitialLaunch = arguments?.getBoolean("is_initial_launch", false) ?: false
        val positionToRestore = viewModel.lastFocusedContentPosition
        
        if (positionToRestore != null && !isInitialLaunch) {
            pendingRestorePosition = positionToRestore
            Log.d("FOCUS_DEBUG", "📝 [BASE] onResume - Restore pozisyonu not alındı (Pending): $positionToRestore")
        } else {
            pendingRestorePosition = null
        }
    }
```

**4. `observeContents` Güncellemesi (Uygulama Anı):**
`observeContents` içindeki `submitList` bloğunu güncelle:
```kotlin
                    contentAdapter.submitList(contents) {
                        // [FIX]: Bekleyen bir restore işlemi varsa şimdi uygula
                        val pendingPos = pendingRestorePosition
                        if (pendingPos != null && pendingPos < contentAdapter.itemCount) {
                             Log.d("FOCUS_DEBUG", "🚀 [BASE] Pending Restore uygulanıyor - Position: $pendingPos")
                             
                             // Scroll yap
                             val layoutManager = contentRecyclerView.layoutManager as? androidx.recyclerview.widget.GridLayoutManager
                             if (layoutManager != null) {
                                 layoutManager.scrollToPositionWithOffset(pendingPos, 200)
                             } else {
                                 contentRecyclerView.scrollToPosition(pendingPos)
                             }
                             
                             // Focus ver (Hafif gecikmeli - View'ın yerine oturması için)
                             contentRecyclerView.post {
                                 val viewHolder = contentRecyclerView.findViewHolderForAdapterPosition(pendingPos)
                                 if (viewHolder != null) {
                                     viewHolder.itemView.requestFocus()
                                     Log.d("FOCUS_DEBUG", "🎯 [BASE] Focus verildi - Position: $pendingPos")
                                 } else {
                                     // Eğer hala bulunamadıysa kısa bir gecikme daha dene
                                     contentRecyclerView.postDelayed({
                                         contentRecyclerView.findViewHolderForAdapterPosition(pendingPos)?.itemView?.requestFocus()
                                     }, 50)
                                 }
                             }
                             
                             // İşlem tamam, değişkeni temizle
                             pendingRestorePosition = null
                             viewModel.lastFocusedContentPosition = null
                        }

                        // Veri hazır - ViewModel'e bildir
                        if (!viewModel.isDataReady && contents.isNotEmpty()) {
                            viewModel.isDataReady = true
                            Log.d("FOCUS_DEBUG", "✅ [BASE] Veri hazır")
                        }
                    }
```

---

### 🛠️ ADIM 2: `ContentBrowseFragment.kt`

**`onCreate` Sıralaması:**
`contentType` atamasını `super.onCreate`'in üzerine taşı:
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        // [FIX]: ViewModel getter'ı için önce contentType set edilmeli
        @Suppress("DEPRECATION")
        contentType = arguments?.getSerializable(ARG_CONTENT_TYPE) as? ContentType
        
        super.onCreate(savedInstanceState)
        
        // ...
    }
```

Sadece bu değişiklikleri yap. Başka hiçbir koda dokunma.
