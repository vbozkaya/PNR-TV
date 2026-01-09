# ANDROID TV FOCUS RESTORE FIX (STICKY ARGUMENT)

Sen Expert bir Android TV Geliştiricisisin. Aşağıdaki talimatı `BaseBrowseFragment.kt` dosyasında, **yorum yapmadan ve başka hiçbir şeye dokunmadan** uygula.

**SORUN:**
Filmler ve Diziler sayfasında, bir içeriğin detayına gidip (`FragmentTransaction.replace`) geri gelindiğinde (`BACK`), sistem odaklanılan eski içeriği hatırlamak yerine en başa (ilk kategoriye) dönüyor.
Bunun sebebi, `arguments` içindeki `is_initial_launch` değerinin kalıcı olmasıdır. Fragment geri geldiğinde View yeniden oluşturuluyor, `observeCategories` çalışıyor ve `arguments` hala `true` olduğu için sistem kendini "ilk açılış" sanıp sıfırlıyor.

**HEDEF:**
`is_initial_launch` argümanını "Tek Kullanımlık" (One-time use) hale getirmek. Fragment ilk oluşturulduğunda bu değer `true` ise, gerekli temizliği yapmalı ve hemen ardından bu değeri `false` olarak güncellemelidir.

---

### 🛠️ UYGULAMA ADIMI

**Dosya:** `app/src/main/java/com/pnr/tv/ui/base/BaseBrowseFragment.kt`
**Metod:** `onCreate`

Mevcut `onCreate` metodunu aşağıdaki mantıkla güncelle (Mevcut kodun yapısını koruyarak, sadece `putBoolean` satırını ekle):

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
            
            // [CRITICAL FIX]: Bayrağı tükettik, artık false yap.
            // Böylece detay sayfasından geri dönüldüğünde (View recreation) sistem bunu "ilk açılış" sanmayacak.
            arguments?.putBoolean("is_initial_launch", false)
        }
    }
```

**ÖNEMLİ:**
*   Sadece `onCreate` içindeki bu bloğu güncelle.
*   `observeCategories` veya `onResume` içindeki kodlara dokunma (Onlar zaten `arguments`'e bakıyor, biz kaynağı düzelttiğimiz için onlar da düzelecek).
*   Başka hiçbir dosyada değişiklik yapma.
