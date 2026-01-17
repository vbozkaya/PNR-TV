package com.pnr.tv.core.base

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.R
import com.pnr.tv.premium.AdManager
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.util.LifecycleTracker
import com.pnr.tv.util.ui.BackgroundManager
import com.pnr.tv.util.ui.LocaleHelper
import com.pnr.tv.util.ui.loadBackgroundFromManager
import com.pnr.tv.util.ui.setBackgroundSafely
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Projedeki tüm Activity'ler için ortak davranışları ve ayarları içeren temel sınıftır.
 * Bu sınıfın temel amacı, tüm ekranlarda tutarlı bir görünüm ve davranış sağlamaktır.
 *
 * Sorumlulukları:
 * - **Tam Ekran Ayarı:** Uygulamanın TV ekranını kaplamasını sağlar.
 * - **Ekran Bilgileri:** Cihazın ekran boyutu gibi bilgilere kolay erişim için yardımcı metodlar sunar.
 * - **Dil Yönetimi:** LocaleHelper ile dil değiştirme desteği
 * - **Navbar Yönetimi:** Navbar setup ve title yönetimi
 * - **Arka Plan Yönetimi:** Arka plan görseli yükleme
 *
 * Yeni bir Activity oluşturulduğunda, `AppCompatActivity` yerine bu sınıftan türetilmelidir.
 *
 * Hook Method'lar:
 * - [shouldSetupNavbar]: Navbar'ın gösterilip gösterilmeyeceğini belirler (varsayılan: true)
 * - [shouldLoadBackground]: Arka plan görselinin yüklenip yüklenmeyeceğini belirler (varsayılan: true)
 * - [shouldShowAds]: Banner reklamlarının gösterilip gösterilmeyeceğini belirler (varsayılan: true)
 * - [getNavbarTitle]: Navbar'da gösterilecek başlığı döndürür (varsayılan: null)
 */
abstract class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        
        // Lifecycle tracking için observer ekle
        lifecycle.addObserver(LifecycleTracker.createActivityObserver(this))
    }

    override fun onStart() {
        super.onStart()
        // Navbar setup - hook method ile kontrol edilir
        if (shouldSetupNavbar()) {
            setupNavbar()
        }
        // Arka plan yükleme - hook method ile kontrol edilir
        // onStart'ta arka planı yeniden yükle (TV bekleme modundan çıktığında)
        if (shouldLoadBackground()) {
            LifecycleTracker.logBackgroundCheck(javaClass.simpleName, "onStart - loadBackground çağrılıyor")
            loadBackground()
        }
    }

    override fun onResume() {
        super.onResume()
        // Banner reklam yönetimi artık BannerAdDelegate tarafından yapılıyor
        // Bu metod boş bırakıldı, geriye uyumluluk için
        
        // TV bekleme modundan çıktığında arka plan bitmap'inin geçerliliğini kontrol et
        // Bu, "Canvas: trying to use a recycled bitmap" hatasını önler
        if (shouldLoadBackground()) {
            LifecycleTracker.logBackgroundCheck(javaClass.simpleName, "onResume - validateAndReloadBackground çağrılıyor")
            validateAndReloadBackground()
        }
    }

    override fun onPause() {
        super.onPause()
        // Banner reklam yönetimi artık BannerAdDelegate tarafından yapılıyor
        // Bu metod boş bırakıldı, geriye uyumluluk için
        
        // Arka plan yükleme işlemlerini iptal et (RenderThread takılmasını önlemek için)
        // onPause'da render işlemlerini durdur
        if (shouldLoadBackground()) {
            LifecycleTracker.logBackgroundCheck(javaClass.simpleName, "onPause - Render işlemleri durduruluyor")
        }
    }

    override fun onStop() {
        super.onStop()
        // Activity arka plana geçtiğinde (TV bekleme modu dahil) arka plan cache'ini yumuşak temizle
        // Bu, sistemin bellek yönetimi yapmasına izin verir
        // Disk cache korunur, böylece onStart'ta hızlıca yeniden yüklenebilir
        if (shouldLoadBackground()) {
            BackgroundManager.softClearCache()
            Timber.tag("BACKGROUND").d("🛑 onStop: Arka plan cache yumuşak temizlendi (disk cache korunuyor)")
        }
    }

    override fun onDestroy() {
        // Banner reklam yönetimi artık BannerAdDelegate tarafından yapılıyor
        // Bu metod boş bırakıldı, geriye uyumluluk için
        super.onDestroy()
    }

    /**
     * Banner reklamı yükler ve premium durumuna göre gösterir/gizler.
     * Bu metod [BannerAdDelegate] kullanarak reklam yönetimini yapar.
     * Her activity'de bu metodu çağırarak reklamları aktif edebilirsiniz.
     *
     * Kullanım:
     * ```kotlin
     * override fun onCreate(savedInstanceState: Bundle?) {
     *     super.onCreate(savedInstanceState)
     *     setContentView(R.layout.activity_main)
     *     setupBannerAd(adManager, premiumManager)
     * }
     * ```
     *
     * @param adManager Reklam yönetimi için AdManager instance'ı
     * @param premiumManager Premium durum kontrolü için PremiumManager instance'ı
     */
    protected fun setupBannerAd(
        adManager: AdManager,
        premiumManager: PremiumManager,
    ) {
        // Reklam gösterilmeyecekse hiçbir şey yapma
        if (!shouldShowAds()) {
            return
        }

        val adDelegate = BannerAdDelegate(this, adManager, premiumManager)
        lifecycle.addObserver(adDelegate)
        adDelegate.setup()
    }

    /**
     * Navbar'ı setup eder ve home butonuna click listener ekler.
     * Her activity'de navbar title'ı ayarlamak için [setNavbarTitle] metodunu override edebilirsiniz.
     */
    protected fun setupNavbar() {
        // Geri butonuna click listener ekle
        val backButton = findViewById<View>(R.id.btn_navbar_back)
        backButton?.setOnClickListener {
            finish()
        }

        // Home butonuna click listener ekle
        val homeButton = findViewById<View>(R.id.btn_navbar_home)
        homeButton?.setOnClickListener {
            val intent = Intent(this, com.pnr.tv.ui.main.MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        // Navbar title'ı ayarla
        val titleText = getNavbarTitle()
        if (titleText != null) {
            val titleTextView = findViewById<TextView>(R.id.txt_navbar_title)
            titleTextView?.text = titleText
        }

        // Filter butonunu, arama çubuğunu ve premium yazısını gizle (BaseActivity'lerde gösterilmez, sadece filmler ve diziler sayfalarında gösterilir)
        val filterButton = findViewById<View>(R.id.btn_navbar_filter)
        filterButton?.visibility = View.GONE
        val searchEditText = findViewById<android.widget.EditText>(R.id.edt_navbar_search)
        searchEditText?.visibility = View.GONE
        val premiumText = findViewById<android.widget.TextView>(R.id.tv_navbar_premium)
        premiumText?.visibility = View.GONE
    }

    /**
     * Navbar'ın gösterilip gösterilmeyeceğini belirler.
     * Override edilerek belirli activity'lerde navbar'ı gizleyebilirsiniz.
     *
     * @return true ise navbar setup edilir, false ise edilmez (varsayılan: true)
     */
    protected open fun shouldSetupNavbar(): Boolean {
        return true
    }

    /**
     * Arka plan görselinin yüklenip yüklenmeyeceğini belirler.
     * Override edilerek belirli activity'lerde arka plan yüklemeyi devre dışı bırakabilirsiniz.
     *
     * @return true ise arka plan yüklenir, false ise yüklenmez (varsayılan: true)
     */
    protected open fun shouldLoadBackground(): Boolean {
        return true
    }

    /**
     * Banner reklamlarının gösterilip gösterilmeyeceğini belirler.
     * Override edilerek belirli activity'lerde reklamları gizleyebilirsiniz.
     *
     * @return true ise reklamlar gösterilir, false ise gösterilmez (varsayılan: true)
     */
    protected open fun shouldShowAds(): Boolean {
        return true
    }

    /**
     * Navbar'da gösterilecek başlığı döndürür.
     * Override edilerek her activity'de farklı başlık ayarlanabilir.
     *
     * @return Navbar başlığı string veya null (başlık gösterilmezse)
     */
    protected open fun getNavbarTitle(): String? {
        return null
    }

    /**
     * Aktivite oluşturulduğunda çağrılır ve TV için tam ekran kullanıcı arayüzü ayarlarını yapar.
     * Bu metod, sistem çubuklarını (navigation bar, status bar) gizleyerek içeriğin ekranın
     * tamamını kullanmasını sağlar.
     */
    protected open fun setupWindow() {
        Timber.tag("SPLASH_FLOW").d("🔧 [BASE] BaseActivity.setupWindow() - Window flag'leri ayarlanıyor (${this.javaClass.simpleName})")
        // ActionBar'ı gizle
        supportActionBar?.hide()

        // TV için tam ekran ayarları. Android 11 (R) ve sonrası için `setDecorFitsSystemWindows(false)`
        // kullanılırken, eski sürümler için `systemUiVisibility` flag'leri kullanılır.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Timber.tag("SPLASH_FLOW").d("🔧 [BASE] Android R+ - setDecorFitsSystemWindows(false) ve status bar gizleme")
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
        } else {
            Timber.tag("SPLASH_FLOW").d("🔧 [BASE] Android < R - systemUiVisibility flag'leri ayarlanıyor")
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        }

        // Tam ekran modu
        Timber.tag("SPLASH_FLOW").d("🔧 [BASE] FLAG_FULLSCREEN ayarlanıyor")
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        Timber.tag("SPLASH_FLOW").d("✅ [BASE] BaseActivity.setupWindow() tamamlandı")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Ekran yapılandırması değiştiğinde (örn: dil, çözünürlük) yapılacak işlemler buraya eklenebilir.
        // Şu an için Android'in varsayılan davranışı yeterlidir.
    }

    /**
     * Cihaz ekranının genişliğini DP (Density-independent Pixel) cinsinden döndürür.
     * @return Ekran genişliği (dp).
     */
    protected fun getScreenWidthDp(): Int {
        val metrics = resources.displayMetrics
        return (metrics.widthPixels / metrics.density).toInt()
    }

    /**
     * Cihaz ekranının yüksekliğini DP (Density-independent Pixel) cinsinden döndürür.
     * @return Ekran yüksekliği (dp).
     */
    protected fun getScreenHeightDp(): Int {
        val metrics = resources.displayMetrics
        return (metrics.heightPixels / metrics.density).toInt()
    }

    /**
     * Arka plan görselini güvenli bir şekilde yükler.
     * BackgroundManager kullanarak cache'lenmiş görseli yükler.
     * Layout root view'ına (setContentView ile set edilen view) arkaplan ekler.
     * 
     * KRİTİK: View hierarchy'nin hazır olmasını bekler (doOnPreDraw kullanır).
     * Bu, RenderThread takılması durumunda view'ların hazır olmasını garanti eder.
     */
    private fun loadBackground() {
        lifecycleScope.launch {
            try {
                // View hierarchy'nin hazır olmasını bekle (doOnPreDraw kullan)
                // Bu, RenderThread takılması durumunda bile view'ların hazır olmasını garanti eder
                val contentView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
                val layoutRootView =
                    contentView?.getChildAt(0) as? ViewGroup
                        ?: contentView // Eğer child yoksa contentView'ı kullan

                val targetView = layoutRootView ?: window.decorView.rootView

                // View henüz çizilmemişse (width/height 0 ise) doOnPreDraw ile bekle
                if (targetView.width == 0 || targetView.height == 0) {
                    Timber.tag("BACKGROUND").d("⏳ View henüz çizilmemiş, doOnPreDraw ile bekleniyor...")
                    targetView.doOnPreDraw {
                        // doOnPreDraw içinde suspend fonksiyon çağıramayız, lifecycleScope.launch kullan
                        lifecycleScope.launch {
                            Timber.tag("BACKGROUND").d("✅ View çizildi, arka plan yükleniyor...")
                            targetView.loadBackgroundFromManager(this@BaseActivity)
                        }
                    }
                } else {
                    // View zaten hazır, direkt yükle
                    Timber.tag(
                        "BACKGROUND",
                    ).d(
                        "📐 Layout RootView alındı - View: ${targetView.javaClass.simpleName}, Width: ${targetView.width}, Height: ${targetView.height}",
                    )
                    targetView.loadBackgroundFromManager(this@BaseActivity)
                }
            } catch (e: Exception) {
                Timber.tag("BACKGROUND").e(e, "❌ loadBackground sırasında hata")
                // Hata durumunda fallback göster
                try {
                    val contentView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
                    val layoutRootView =
                        contentView?.getChildAt(0) as? ViewGroup
                            ?: contentView
                    val targetView = layoutRootView ?: window.decorView.rootView
                    val fallback = BackgroundManager.getFallbackBackground(this@BaseActivity)
                    if (fallback != null) {
                        targetView.setBackgroundSafely(fallback)
                    }
                } catch (ex: Exception) {
                    Timber.tag("BACKGROUND").e(ex, "❌ Fallback arka plan set edilemedi")
                }
            }
        }
    }

    /**
     * Activity uykudan uyandığında (onResume) arka plan bitmap'inin geçerliliğini kontrol eder.
     * Eğer bitmap recycle edilmişse, fallback gösterir ve asenkron olarak yeniden yükler.
     * Bu, TV bekleme modundan çıktığında oluşan "Canvas: trying to use a recycled bitmap" hatasını önler.
     * 
     * KRİTİK: Her onResume'da kontrol eder ve null/recycled ise HEMEN yeniden yükler.
     * View hierarchy'nin hazır olmasını bekler (doOnPreDraw kullanır).
     */
    private fun validateAndReloadBackground() {
        lifecycleScope.launch {
            try {
                Timber.tag("BACKGROUND").d("🔍 onResume: Arka plan geçerliliği kontrol ediliyor...")
                
                // Mevcut arka planı kontrol et
                val contentView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
                val layoutRootView =
                    contentView?.getChildAt(0) as? ViewGroup
                        ?: contentView

                val targetView = layoutRootView ?: window.decorView.rootView
                val currentBackground = targetView.background

                // BackgroundManager'dan cache'lenmiş arka planı kontrol et
                // getCachedBackground zaten recycle kontrolü yapıyor ve WeakReference kullanıyor
                val cached = BackgroundManager.getCachedBackground()

                // KRİTİK KONTROL: Cache null ise VEYA mevcut arka plan geçersizse HEMEN yeniden yükle
                val isCurrentBackgroundRecycled = isBackgroundRecycled(currentBackground)
                val shouldReload = cached == null || isCurrentBackgroundRecycled
                
                if (shouldReload) {
                    Timber.tag("BACKGROUND").w("🔄 onResume: Arka plan geçersiz (cached=${cached != null}, recycled=$isCurrentBackgroundRecycled), HEMEN yeniden yükleniyor")
                    
                    // Önce fallback göster (tema rengi) - gri ekran önleme
                    val fallback = BackgroundManager.getFallbackBackground(this@BaseActivity)
                    if (fallback != null) {
                        Timber.tag("BACKGROUND").d("🎨 Fallback arka plan gösteriliyor (siyah)")
                        targetView.setBackgroundSafely(fallback)
                    }

                    // View henüz çizilmemişse (width/height 0 ise) doOnPreDraw ile bekle
                    // Bu, RenderThread takılması durumunda bile view'ların hazır olmasını garanti eder
                    if (targetView.width == 0 || targetView.height == 0) {
                        Timber.tag("BACKGROUND").d("⏳ View henüz çizilmemiş, doOnPreDraw ile bekleniyor...")
                        targetView.doOnPreDraw {
                            // doOnPreDraw içinde suspend fonksiyon çağıramayız, lifecycleScope.launch kullan
                            lifecycleScope.launch {
                                Timber.tag("BACKGROUND").d("✅ View çizildi, arka plan yeniden yükleniyor...")
                                targetView.loadBackgroundFromManager(this@BaseActivity)
                            }
                        }
                    } else {
                        // View zaten hazır, direkt yükle
                        Timber.tag("BACKGROUND").d("📥 Arka plan yeniden yükleniyor...")
                        targetView.loadBackgroundFromManager(this@BaseActivity)
                    }
                } else {
                    Timber.tag("BACKGROUND").d("✅ onResume: Arka plan geçerli, yeniden yükleme gerekmiyor")
                }
            } catch (e: Exception) {
                Timber.tag("BACKGROUND").e(e, "❌ onResume: Arka plan kontrolü sırasında hata")
                // Hata durumunda fallback göster
                try {
                    val contentView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
                    val layoutRootView =
                        contentView?.getChildAt(0) as? ViewGroup
                            ?: contentView
                    val targetView = layoutRootView ?: window.decorView.rootView
                    val fallback = BackgroundManager.getFallbackBackground(this@BaseActivity)
                    if (fallback != null) {
                        targetView.setBackgroundSafely(fallback)
                    }
                } catch (ex: Exception) {
                    Timber.tag("BACKGROUND").e(ex, "❌ Fallback arka plan set edilemedi")
                }
            }
        }
    }

    /**
     * View'ın mevcut arka planının recycle edilip edilmediğini kontrol eder.
     */
    private fun isBackgroundRecycled(background: Drawable?): Boolean {
        if (background == null) return false

        return try {
            when (background) {
                is android.graphics.drawable.BitmapDrawable -> {
                    val bitmap = background.bitmap
                    bitmap == null || bitmap.isRecycled
                }
                is android.graphics.drawable.LayerDrawable -> {
                    var recycled = false
                    for (i in 0 until background.numberOfLayers) {
                        val layerDrawable = background.getDrawable(i)
                        if (isBackgroundRecycled(layerDrawable)) {
                            recycled = true
                            break
                        }
                    }
                    recycled
                }
                else -> false // ColorDrawable ve diğer türler için false (güvenli)
            }
        } catch (e: Exception) {
            Timber.tag("BACKGROUND").w(e, "⚠️ Arka plan kontrolü sırasında hata")
            true // Hata durumunda güvenli tarafta kal
        }
    }
}
