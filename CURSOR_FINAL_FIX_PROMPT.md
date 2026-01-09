# ANDROID TV FOCUS & INIT ORDER FIX

Sen Expert bir Android Geliştiricisisin. Aşağıdaki iki kritik hatayı düzeltmek için belirtilen dosyalarda değişiklik yap.

**HEDEF:**
1.  `ContentBrowseFragment` içindeki ViewModel başlatma sırasını düzeltmek (Null Safety / Race Condition).
2.  `BaseBrowseFragment` içindeki "İlk Açılış" bayrağını tek kullanımlık hale getirmek (Sticky Argument Fix).

---

### 1. `ContentBrowseFragment.kt` Düzenlemesi

**Sorun:** `onCreate` içinde `super.onCreate()` çağrıldığında `BaseBrowseFragment`, `viewModel` getter'ına erişiyor. Ancak `contentType` henüz set edilmediği için getter yanlış (default) ViewModel'i döndürüyor.
**Çözüm:** `contentType` atamasını `super.onCreate` çağrısından **ÖNCEYE** taşı.

**Mevcut Kod:**
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // HATA: Önce Base çalışıyor
        @Suppress("DEPRECATION")
        contentType = arguments?.getSerializable(ARG_CONTENT_TYPE) as? ContentType
        // ...
    }
```

**İstenen Kod:**
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        // [FIX]: ViewModel getter'ı super.onCreate içinde çağrıldığı için
        // contentType değeri super.onCreate'ten önce atanmalı.
        @Suppress("DEPRECATION")
        contentType = arguments?.getSerializable(ARG_CONTENT_TYPE) as? ContentType
        
        super.onCreate(savedInstanceState)
        
        // ... diğer kodlar
    }
```

---

### 2. `BaseBrowseFragment.kt` Düzenlemesi

**Sorun:** Detay sayfasından geri dönüldüğünde (`replace` sonrası View recreation), `arguments` içindeki `is_initial_launch` değeri hala `true` kaldığı için sistem kendini "ilk açılış" sanıp restore etmek yerine hafızayı siliyor.
**Çözüm:** `is_initial_launch` bayrağını okuduktan sonra `false` olarak güncelle.

**Mevcut Kod (onCreate içi):**
```kotlin
        // INITIAL LAUNCH kontrolü: Ana menüden ilk kez açılıyorsa hafızayı temizle
        val isInitialLaunch = arguments?.getBoolean("is_initial_launch", false) ?: false
        if (isInitialLaunch) {
            viewModel.lastFocusedContentPosition = null
            viewModel.lastSelectedCategoryId = null
            savedLastFocusedPosition = null
            savedLastSelectedCategoryId = null
        }
```

**İstenen Kod (onCreate içi):**
```kotlin
        // INITIAL LAUNCH kontrolü: Ana menüden ilk kez açılıyorsa hafızayı temizle
        val isInitialLaunch = arguments?.getBoolean("is_initial_launch", false) ?: false
        if (isInitialLaunch) {
            viewModel.lastFocusedContentPosition = null
            viewModel.lastSelectedCategoryId = null
            savedLastFocusedPosition = null
            savedLastSelectedCategoryId = null
            
            // [FIX]: Bayrağı false yap ki geri dönüşlerde (fragment recreation) tekrar sıfırlamasın.
            arguments?.putBoolean("is_initial_launch", false)
        }
```

Sadece bu iki değişikliği yap. Başka hiçbir koda dokunma.
