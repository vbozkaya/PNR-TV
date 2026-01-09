# ANDROID TV FOCUS & NAVIGATION REFACTORING INSTRUCTIONS

Sen Expert bir Android TV Geliştiricisisin. Aşağıdaki talimatları, belirtilen dosyalarda, **yorum yapmadan, alternatif önermeden ve inisiyatif kullanmadan** adım adım uygulayacaksın.

**Mevcut Durum:** Projede `BaseBrowseFragment` ve Custom RecyclerView'lar üzerinden yürüyen bir Focus yapısı var. Ancak "Focus Stealing" (Kategori değişince odağın istemsizce içeriğe kayması) ve "Stale Restore" (Eski pozisyonun yanlışlıkla hatırlanması) sorunları yaşanıyor.

**HEDEF:** Mevcut "Yama"lı yapıyı temizleyip, aşağıdaki **4 KESİN KURALA** dayalı bir yapı kurmak.

---

### 🛑 KESİN KURALLAR (THE 4 COMMANDMENTS)

1.  **INITIAL LAUNCH (SIFIR BAŞLANGIÇ):**
    *   Kullanıcı Ana Menüden "Canlı TV", "Filmler" veya "Diziler" sayfasına ilk kez girdiğinde;
    *   Hafıza (ViewModel'deki son pozisyon/kategori) **SİLİNECEK**.
    *   Her zaman **1. Kategori** seçili gelecek ve odak orada olacak.

2.  **NAVIGASYON (KATEGORİ -> İÇERİK):**
    *   Kullanıcı Kategori Listesinden İçerik Gridine (`SAĞ` veya `OK` tuşu ile) geçtiğinde;
    *   Daha önce nerede kaldığına bakılmaksızın **HER ZAMAN 1. ÖĞEYE (Index 0)** odaklanılacak.
    *   Grid içerisinde `BACK` veya en soldayken `SOL` tuşuna basıldığında, o anki aktif kategoriye dönülecek.

3.  **DATA LOADING (ODAK ÇALMA YASAK):**
    *   Kullanıcı kategoriler arasında gezerken (Aşağı/Yukarı), sağ taraftaki içerik güncellendiğinde;
    *   **ASLA** otomatik olarak içeriğe odaklanılmayacak. Odak kategoride kalacak.
    *   `observeContents` içindeki "restore" mantığı tamamen kaldırılacak.

4.  **DEEP RESTORE (SADECE GERİ DÖNÜŞTE):**
    *   "Restore" (Eski pozisyonu hatırlama) işlemi SADECE kullanıcı bir içeriği izleyip/detayına girip `BACK` tuşuyla geri döndüğünde (`onResume`) çalışacak.
    *   Bu işlem tek seferlik olacak ve çalıştıktan sonra hafıza temizlenecek.

---

### 🛠️ UYGULAMA ADIMLARI (DOSYA BAZLI)

#### ADIM 1: `BaseBrowseFragment.kt` Düzenlemesi

1.  **`observeContents` Temizliği:**
    *   `collectLatest` bloğu içerisindeki tüm `targetPositionToRestore`, `isRestoringFocus`, `scrollToPosition` ve `requestFocus` mantıklarını **SİL**.
    *   Bu blok sadece `submitList` yapmalı ve boş durum (empty state) kontrolü yapmalı. Başka hiçbir şeye karışmamalı.
    *   Böylece kategori değiştiğinde odağın çalınması engellenmiş olacak.

2.  **`onResume` Restore Mantığı:**
    *   Restore işlemini `onResume` metoduna taşı.
    *   Mantık şu olmalı:
        *   Eğer `viewModel.lastFocusedContentPosition` != null VE `!isInitialLaunch` ise:
        *   `targetPositionToRestore` değişkenine ata.
        *   Grid'e `scrollToPosition` yap (Hemen, delay olmadan).
        *   `doOnPreDraw` ile `requestFocus` yap.
        *   İşlem bitince `targetPositionToRestore`'u null yap.

3.  **`isInitialLaunch` Kontrolü:**
    *   `onCreate` içinde `arguments`'den `KEY_IS_INITIAL_LAUNCH` değerini oku.
    *   Eğer `true` ise: `viewModel.lastFocusedContentPosition = null`, `viewModel.lastSelectedCategoryId = null` yap ve ilk kategoriyi seçtirt.

#### ADIM 2: `BrowseFocusDelegate.kt` Düzenlemesi

1.  **`navigateToContentStart` Fonksiyonu Ekle:**
    *   Bu fonksiyon çağrıldığında direkt olarak `contentRecyclerView.scrollToPosition(0)` ve `findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()` yapmalı.
    *   Bu fonksiyon, Kategori -> İçerik geçişinde kullanılacak.

#### ADIM 3: Navigasyon Mantığı (Custom View'lar ve Delegate)

1.  **Kategori -> İçerik (Sıfırlama):**
    *   `BaseBrowseFragment` veya `BrowseFocusDelegate` içinde; Kategori üzerindeyken `SAĞ` veya `OK` (DPAD_CENTER) tuşuna basıldığında `navigateToContentStart()` (Index 0'a git) fonksiyonunu çağıracak yapıyı kur.
    *   Varsa eski "kaldığı yerden devam et" kodlarını sil.

2.  **İçerik -> Kategori:**
    *   `CustomContentRecyclerView.kt` içinde `LEFT` tuşu veya `BACK` tuşu ile çıkış yapıldığında, direkt olarak o anki aktif kategoriye odaklanmasını sağla (Bu zaten büyük ölçüde var, sadece çalıştığından emin ol).

#### ADIM 4: `ContentBrowseFragment.kt` ve `LiveStreamsBrowseFragment.kt`

1.  **Instance Kontrolü:**
    *   `newInstance` metodunda `isInitialLaunch` parametresinin doğru set edildiğinden emin ol.
    *   `onCreate` içinde `checkDataAndShowWarningIfNeeded` çağrısından önce, eğer `isInitialLaunch` ise ViewModel state'ini temizlemesini sağla (Base'deki yapıya uygun olarak).

---

### ⚠️ KISITLAMALAR (YAPMA!)

*   **ASLA** `throttle` sürelerini (400ms vb.) değiştirme.
*   **ASLA** yeni bir kütüphane ekleme.
*   **ASLA** XML layout dosyalarında değişiklik yapma.
*   **ASLA** `CustomGridLayoutManager` içindeki `onFocusSearchFailed` mantığını bozma (O şu an doğru çalışıyor).
*   **ASLA** veri yükleme (Fetching) mekanizmasını `onResume` içine taşıma. Veri sadece `InitialLoad` veya `Güncelle` butonu ile yüklenmeli.

Sadece yukarıdaki 4 kuralı hayata geçirecek kod refactoring işlemini yap. Başka hiçbir şeye dokunma.
