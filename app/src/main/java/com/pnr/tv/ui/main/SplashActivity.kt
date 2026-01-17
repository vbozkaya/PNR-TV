package com.pnr.tv.ui.main

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.pnr.tv.R
import com.pnr.tv.core.base.BaseActivity
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.util.ui.BackgroundManager
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Uygulama açılışında gösterilen splash screen activity'si.
 * Görsel animasyonlu olarak gösterilir ve ardından MainActivity'ye geçiş yapılır.
 * Arka plan hazır olana kadar splash ekranı gösterilir, böylece MainActivity'ye geçişte
 * gri/siyah ekran görünmez.
 */
class SplashActivity : BaseActivity() {
    override fun shouldSetupNavbar(): Boolean = false

    override fun shouldLoadBackground(): Boolean = false

    /**
     * BaseActivity'deki setupWindow metodunu bypass et.
     * Window flag'lerinin pencereyi (pikselleri) kaydırmasını engelle.
     * Splash ekranında window background zaten doğru ayarlanmış, ekstra window manipülasyonu gerekmez.
     */
    override fun setupWindow() {
        Timber.tag("SPLASH_FLOW").d("🔧 [1] setupWindow() - BYPASS (BaseActivity window flag'leri atlandı)")
        // Boş bırak - window background zaten splash_image olarak ayarlanmış
        // BaseActivity'deki flag'ler pencereyi kaydırmasın
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val startTime = System.currentTimeMillis()
        Timber.tag("SPLASH_FLOW").d("🚀 [START] onCreate() - Uygulama ikonuna basıldı, SplashActivity başlatılıyor")
        
        // Warm start kontrolü - savedInstanceState null ise cold start
        val isColdStart = savedInstanceState == null
        Timber.tag("SPLASH_FLOW").d("ℹ️ [1] Start Type: ${if (isColdStart) "COLD START" else "WARM START"} (savedInstanceState=${savedInstanceState != null})")
        
        // ÖNEMLİ: super.onCreate() çağrılmadan ÖNCE tema set et
        // Window background siyah olacak, sistem logosu gösterilmeyecek
        Timber.tag("SPLASH_FLOW").d("🎨 [2] setTheme(R.style.Theme_Splash) - Tema set ediliyor (windowBackground=black)")
        setTheme(R.style.Theme_Splash)
        
        Timber.tag("SPLASH_FLOW").d("⬆️ [3] super.onCreate() - BaseActivity.onCreate() çağrılıyor")
        super.onCreate(savedInstanceState)
        Timber.tag("SPLASH_FLOW").d("✅ [4] super.onCreate() tamamlandı")
        
        // TV'nin işlemciyi splash boyunca tam güçte tutmasını sağla
        Timber.tag("SPLASH_FLOW").d("🔋 [4.5] FLAG_KEEP_SCREEN_ON - Ekran açık kalacak, işlemci tam güçte")
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // HEMEN layout'u yükle - activity_splash.xml içeriği gösterilecek
        Timber.tag("SPLASH_FLOW").d("📄 [5] setContentView(R.layout.activity_splash) - Layout yükleniyor")
        setContentView(R.layout.activity_splash)

        Timber.tag("SPLASH_FLOW").d("🔍 [6] findViewById<ImageView>(R.id.splash_image) - ImageView bulunuyor")
        val splashImage = findViewById<ImageView>(R.id.splash_image)

        // Splash görselini yükle ve animasyonu başlat
        Timber.tag("SPLASH_FLOW").d("🖼️ [7] loadSplashImage() - ImageView'a görsel yükleniyor")
        loadSplashImage(splashImage)

        // Zoom in animasyonunu başlat
        Timber.tag("SPLASH_FLOW").d("🎬 [8] AnimationUtils.loadAnimation() - Zoom in animasyonu yükleniyor")
        val animation = AnimationUtils.loadAnimation(this, R.anim.splash_zoom_in)
        Timber.tag("SPLASH_FLOW").d("▶️ [9] splashImage.startAnimation() - Zoom in animasyonu başlatıldı (2 saniye)")
        splashImage.startAnimation(animation)

        val splashStartTime = System.currentTimeMillis()
        Timber.tag("SPLASH_FLOW").d("🔄 [10] lifecycleScope.launch - Coroutine başlatıldı (splash başlangıç zamanı: $splashStartTime)")
        lifecycleScope.launch {
            // Arka planı önceden yükle (paralel olarak)
            Timber.tag("SPLASH_FLOW").d("📦 [11] async { loadBackgroundPreload() } - Arka plan preload işlemi başlatıldı (paralel)")
            val backgroundLoadJob =
                async {
                    val preloadStart = System.currentTimeMillis()
                    loadBackgroundPreload()
                    val preloadDuration = System.currentTimeMillis() - preloadStart
                    Timber.tag("SPLASH_FLOW").d("⏱️ [PRELOAD] Arka plan preload süresi: ${preloadDuration}ms")
                }

            // KRİTİK: Minimum splash süresi (5 saniye) - Preload işlemi 1 saniyede bitse bile 5 saniye dolmadan geçiş yapılmayacak
            // Force Delay: delay(5000) süresinin kesin olarak bitmesini bekleyen await mekanizması
            Timber.tag("SPLASH_FLOW").d("⏱️ [12] async { delay(${UIConstants.DelayDurations.SPLASH_DELAY_MS}ms) } - Minimum bekleme süresi başlatıldı (paralel, 5 saniye)")
            val minSplashDuration =
                async {
                    val delayStart = System.currentTimeMillis()
                    delay(UIConstants.DelayDurations.SPLASH_DELAY_MS)
                    val delayDuration = System.currentTimeMillis() - delayStart
                    Timber.tag("SPLASH_FLOW").d("⏱️ [DELAY] Delay süresi: ${delayDuration}ms (hedef: ${UIConstants.DelayDurations.SPLASH_DELAY_MS}ms)")
                }

            // ÖNCE arka plan yüklemesini bekle (ne kadar sürerse sürsün)
            Timber.tag("SPLASH_FLOW").d("⏳ [13] backgroundLoadJob.await() - Arka plan yüklemesi bekleniyor...")
            backgroundLoadJob.await()
            Timber.tag("SPLASH_FLOW").d("✅ [14] Arka plan yüklemesi tamamlandı")
            
            // SONRA minimum splash süresinin kesin olarak bitmesini bekle (5 saniye garanti)
            // Bu await mekanizması loadBackgroundPreload() bitse bile 5 saniye dolmadan geçiş yapılmasını engeller
            Timber.tag("SPLASH_FLOW").d("⏳ [15] minSplashDuration.await() - Minimum bekleme süresi bekleniyor (5 saniye GARANTİ, preload bitse bile bekleniyor)...")
            minSplashDuration.await()
            val totalSplashTime = System.currentTimeMillis() - splashStartTime
            Timber.tag("SPLASH_FLOW").d("✅ [16] Minimum bekleme süresi tamamlandı - Toplam splash süresi: ${totalSplashTime}ms (hedef: ${UIConstants.DelayDurations.SPLASH_DELAY_MS}ms)")

            // Fade out animasyonunu başlat ve tamamen bitmesini bekle
            Timber.tag("SPLASH_FLOW").d("🎬 [17] Fade out animasyonu hazırlanıyor")
            suspendCoroutine<Unit> { continuation ->
                val fadeOutAnimation = AnimationUtils.loadAnimation(this@SplashActivity, R.anim.splash_fade_out)

                // Animasyon tamamen bittiğinde devam et
                fadeOutAnimation.setAnimationListener(
                    object : android.view.animation.Animation.AnimationListener {
                        override fun onAnimationStart(animation: android.view.animation.Animation?) {
                            Timber.tag("SPLASH_FLOW").d("▶️ [18] Fade out animasyonu başladı (0.8 saniye)")
                        }

                        override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}

                        override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                            Timber.tag("SPLASH_FLOW").d("✅ [19] Fade out animasyonu tamamlandı")
                            // Görseli tamamen gizle
                            Timber.tag("SPLASH_FLOW").d("👁️ [20] splashImage.visibility = GONE - ImageView gizlendi")
                            splashImage.visibility = android.view.View.GONE
                            
                            // Activity geçişini yap
                            Timber.tag("SPLASH_FLOW").d("🔄 [21] continuation.resume() - Fade out tamamlandı, devam ediliyor")
                            continuation.resume(Unit)
                        }
                    },
                )

                splashImage.startAnimation(fadeOutAnimation)
            }

            // MainActivity'ye geç - yumuşak fade geçişi için
            Timber.tag("SPLASH_FLOW").d("🎯 [22] Intent(MainActivity) - MainActivity için Intent oluşturuluyor")
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            Timber.tag("SPLASH_FLOW").d("🚀 [23] startActivity(intent) - MainActivity başlatılıyor")
            startActivity(intent)
            // Fade animasyonu ile yumuşak geçiş - "çat" diye geçiş etkisini önler
            Timber.tag("SPLASH_FLOW").d("🎬 [24] overridePendingTransition() - Activity geçiş animasyonu ayarlanıyor")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            Timber.tag("SPLASH_FLOW").d("🏁 [25] finish() - SplashActivity kapatılıyor")
            finish()
            Timber.tag("SPLASH_FLOW").d("✅ [END] SplashActivity tamamlandı, MainActivity'ye geçildi")
        }
    }

    /**
     * Splash görselini Glide ile optimize edilmiş şekilde yükler.
     * Glide kullanarak ekran boyutuna göre ölçeklendirir ve bellek kullanımını minimize eder.
     */
    private fun loadSplashImage(imageView: ImageView) {
        // Ekran boyutunu al
        val screenSize = getScreenSize()
        val maxWidth = screenSize.first.coerceIn(480, 1920) // 1080p için optimize
        val maxHeight = screenSize.second.coerceIn(320, 1080)

        Timber.tag("SplashActivity").d("📐 Splash görsel yükleniyor: maxSize=${maxWidth}x$maxHeight")

        // Glide ile güvenli ve ölçeklendirilmiş şekilde yükle
        // RGB_565 formatı bellek tüketimini %50 azaltır
        Glide.with(this)
            .load(R.drawable.splash_image)
            .override(maxWidth, maxHeight) // Ekran çözünürlüğüne göre ölçeklendir (max 1920x1080)
            .format(DecodeFormat.PREFER_RGB_565) // RGB_565 formatı - bellek kullanımını %50 azaltır
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Disk cache'i aktif et
            .centerCrop()
            .into(
                object : CustomTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?,
                    ) {
                        imageView.setImageDrawable(resource)
                        Timber.tag("SplashActivity").d("✅ Splash görsel yüklendi: ${resource.intrinsicWidth}x${resource.intrinsicHeight}")
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        // Görsel temizlendiğinde yapılacak işlemler
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        Timber.tag("SplashActivity").w("⚠️ Splash görsel yüklenemedi, placeholder gösteriliyor")
                    }
                },
            )
    }

    /**
     * Ekran boyutunu piksel cinsinden döndürür.
     */
    private fun getScreenSize(): Pair<Int, Int> {
        val windowManager =
            getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager
                ?: return Pair(1920, 1080) // Default fallback (1080p)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics?.bounds?.let { bounds ->
                return Pair(bounds.width(), bounds.height())
            }
        }

        // Fallback for older Android versions
        @Suppress("DEPRECATION")
        val displayMetrics = android.util.DisplayMetrics()
        windowManager.defaultDisplay?.getMetrics(displayMetrics)
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    /**
     * Arka planı önceden yükler ve cache'ler.
     * Bu sayede MainActivity açıldığında arka plan zaten hazır olur.
     */
    private suspend fun loadBackgroundPreload() {
        Timber.tag("SPLASH_FLOW").d("📦 [PRELOAD-START] loadBackgroundPreload() - Arka plan preload başladı")
        // Önce cache'de var mı kontrol et
        val cached = BackgroundManager.getCachedBackground()
        if (cached != null) {
            Timber.tag("SPLASH_FLOW").d("✅ [PRELOAD-CACHE] Arka plan cache'de bulundu, yükleme atlandı")
            return
        }

        Timber.tag("SPLASH_FLOW").d("📥 [PRELOAD-LOAD] Arka plan cache'de yok, yükleme başlatılıyor")
        // Arka planı yükle ve tamamlanmasını bekle
        // suspendCoroutine kullanarak callback'leri suspend fonksiyona çevir
        suspendCoroutine<Unit> { continuation ->
            // loadBackground'u callback'lerle çağır (suspend değil, callback kullanıyoruz)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                BackgroundManager.loadBackground(
                    context = this@SplashActivity,
                    imageLoader = null, // Artık kullanılmıyor, Glide kullanılıyor
                    onSuccess = { drawable ->
                        Timber.tag("SPLASH_FLOW").d("✅ [PRELOAD-SUCCESS] Arka plan başarıyla yüklendi")
                        continuation.resume(Unit)
                    },
                    onError = {
                        Timber.tag("SPLASH_FLOW").w("⚠️ [PRELOAD-ERROR] Arka plan yüklenemedi, devam ediliyor")
                        // Hata olsa bile devam et, MainActivity fallback kullanacak
                        continuation.resume(Unit)
                    },
                )
            }
        }
        Timber.tag("SPLASH_FLOW").d("✅ [PRELOAD-END] loadBackgroundPreload() tamamlandı")
    }
}
