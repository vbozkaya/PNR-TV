package com.pnr.tv

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import com.pnr.tv.util.BackgroundManager
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val splashImage = findViewById<ImageView>(R.id.splash_image)

        // Animasyonu başlat
        val animation = AnimationUtils.loadAnimation(this, R.anim.splash_zoom_in)
        splashImage.startAnimation(animation)

        Timber.d("Splash screen gösteriliyor")

        lifecycleScope.launch {
            // Arka planı önceden yükle (paralel olarak)
            val backgroundLoadJob =
                async {
                    loadBackgroundPreload()
                }

            // Minimum animasyon süresi (3 saniye - daha uzun)
            val minAnimationDuration =
                async {
                    delay(3000)
                }

            // Hem arka plan yüklemesini hem de minimum animasyon süresini bekle
            Timber.d("⏳ Arka plan yükleniyor ve animasyon tamamlanıyor...")
            backgroundLoadJob.await()
            minAnimationDuration.await()

            Timber.d("✅ Arka plan hazır ve animasyon tamamlandı, fade out başlatılıyor")

            // Fade out animasyonunu başlat ve tamamen bitmesini bekle
            suspendCoroutine<Unit> { continuation ->
                val fadeOutAnimation = AnimationUtils.loadAnimation(this@SplashActivity, R.anim.splash_fade_out)

                // Animasyon tamamen bittiğinde devam et
                fadeOutAnimation.setAnimationListener(
                    object : android.view.animation.Animation.AnimationListener {
                        override fun onAnimationStart(animation: android.view.animation.Animation?) {
                            Timber.d("🎬 Fade out animasyonu başladı")
                        }

                        override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}

                        override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                            Timber.d("✅ Fade out animasyonu tamamen bitti")
                            // Görseli tamamen gizle
                            splashImage.visibility = android.view.View.GONE
                            // Activity geçişini yap
                            continuation.resume(Unit)
                        }
                    },
                )

                splashImage.startAnimation(fadeOutAnimation)
            }

            Timber.d("✅ Fade out tamamlandı, MainActivity'ye geçiliyor")

            // MainActivity'ye geç (transition olmadan, çünkü splash zaten kayboldu)
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    /**
     * Arka planı önceden yükler ve cache'ler.
     * Bu sayede MainActivity açıldığında arka plan zaten hazır olur.
     */
    private suspend fun loadBackgroundPreload() {
        // Önce cache'de var mı kontrol et
        val cached = BackgroundManager.getCachedBackground()
        if (cached != null) {
            Timber.d("✅ Arka plan zaten cache'de, yükleme gerekmiyor")
            return
        }

        Timber.d("⏳ Arka plan yükleniyor (SplashActivity)...")

        // Arka planı yükle ve tamamlanmasını bekle
        // suspendCoroutine kullanarak callback'leri suspend fonksiyona çevir
        suspendCoroutine<Unit> { continuation ->
            // loadBackground'u callback'lerle çağır (suspend değil, callback kullanıyoruz)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                BackgroundManager.loadBackground(
                    context = this@SplashActivity,
                    imageLoader = imageLoader,
                    onSuccess = { drawable ->
                        Timber.d("✅ Arka plan başarıyla yüklendi ve cache'lendi (SplashActivity)")
                        continuation.resume(Unit)
                    },
                    onError = {
                        Timber.w("⚠️ Arka plan yüklenemedi, devam ediliyor (SplashActivity)")
                        // Hata olsa bile devam et, MainActivity fallback kullanacak
                        continuation.resume(Unit)
                    },
                )
            }
        }
    }
}
