package com.pnr.tv.core.base

import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.pnr.tv.R
import com.pnr.tv.premium.AdManager
import com.pnr.tv.premium.PremiumManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Banner reklam yönetimini üstlenen lifecycle observer.
 * AdView'un pause/resume/destroy işlemlerini otomatik olarak yönetir.
 *
 * Kullanım:
 * ```kotlin
 * val adDelegate = BannerAdDelegate(activity, adManager, premiumManager)
 * lifecycle.addObserver(adDelegate)
 * adDelegate.setup()
 * ```
 *
 * @param activity Reklamın gösterileceği activity
 * @param adManager Reklam yönetimi için AdManager instance'ı
 * @param premiumManager Premium durum kontrolü için PremiumManager instance'ı
 */
class BannerAdDelegate(
    private val activity: BaseActivity,
    private val adManager: AdManager,
    private val premiumManager: PremiumManager,
) : DefaultLifecycleObserver {

    private var adView: AdView? = null

    /**
     * Banner reklamı yükler ve premium durumuna göre gösterir/gizler.
     * Bu metod, activity'nin layout'u hazır olduktan sonra çağrılmalıdır.
     */
    fun setup() {
        try {
            // Ad Unit ID'yi AdManager'dan al ve validasyon yap
            val adUnitId = adManager.getBannerAdUnitId()
            if (adUnitId.isBlank()) {
                Timber.e("Ad Unit ID boş veya geçersiz - reklam gösterilmeyecek - ${activity.javaClass.simpleName}")
                // Reklam alanını gizle
                hideAdView()
                return
            }

            // Premium durumunu gözlemle - UI/UX senkronizasyonu için optimize edilmiş
            activity.lifecycleScope.launch {
                premiumManager.isPremium().collectLatest {
                    try {
                        // Son kez premium kontrolü yap (race condition önleme)
                        val finalIsPremium = premiumManager.isPremiumSync()

                        val currentAdView = activity.findViewById<AdView>(R.id.ad_view_banner)
                        if (currentAdView == null) {
                            Timber.w("AdView bulunamadı - layout henüz hazır olmayabilir - ${activity.javaClass.simpleName}")
                            return@collectLatest
                        }

                        // AdView referansını sakla
                        adView = currentAdView

                        if (finalIsPremium) {
                            // Premium kullanıcı - reklamı anında gizle ve temizle
                            safeHideAdView(currentAdView)
                        } else {
                            // Premium değil - reklamı göster
                            safeShowAdView(currentAdView, adUnitId)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Reklam gösterimi/gizleme hatası - ${activity.javaClass.simpleName}")
                        // Hata durumunda reklamı gizle
                        try {
                            hideAdView()
                        } catch (ex: Exception) {
                            Timber.w(ex, "AdView gizlenirken hata")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "AdView kurulum hatası - ${activity.javaClass.simpleName}")
            // Hata durumunda reklam alanını gizle
            try {
                hideAdView()
            } catch (ex: Exception) {
                Timber.w(ex, "AdView gizlenirken hata")
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        // Activity resume olduğunda AdView'u resume et
        adView?.let { safeResumeAdView(it) }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        // Activity pause olduğunda AdView'u pause et
        adView?.let { safePauseAdView(it) }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // Activity destroy olduğunda AdView'u destroy et
        adView?.let { safeDestroyAdView(it) }
        adView = null
    }

    /**
     * AdView'u güvenli bir şekilde gizler ve temizler.
     */
    private fun safeHideAdView(adView: AdView) {
        try {
            // Anında gizle (UI/UX için önemli)
            adView.visibility = View.GONE
            // Pause ve destroy işlemlerini try-catch ile koru
            try {
                adView.pause()
            } catch (e: Exception) {
                Timber.w(e, "AdView pause edilemedi (muhtemelen zaten destroy edilmiş)")
            }
            try {
                adView.destroy()
            } catch (e: Exception) {
                Timber.w(e, "AdView destroy edilemedi (muhtemelen zaten destroy edilmiş)")
            }
        } catch (e: Exception) {
            Timber.e(e, "AdView gizlenirken beklenmeyen hata")
        }
    }

    /**
     * AdView'u güvenli bir şekilde gösterir ve reklam yükler.
     */
    private fun safeShowAdView(
        adView: AdView,
        adUnitId: String,
    ) {
        try {
            // AdView'un adUnitId'si zaten set edilmişse tekrar set etme (crash önleme)
            val currentAdUnitId = adView.adUnitId
            if (currentAdUnitId.isNullOrBlank()) {
                // AdUnitId henüz set edilmemiş, güvenle set edebiliriz
                adView.adUnitId = adUnitId
            } else if (currentAdUnitId != adUnitId) {
                // AdUnitId zaten set edilmiş ve farklı bir ID gelmiş
                // Google Ads SDK bir AdView instance'ına adUnitId'yi sadece bir kez set etmeye izin verir
                // Bu yüzden mevcut ID ile devam ediyoruz ve sadece log yazıyoruz
                Timber.w(
                    "AdView adUnitId zaten set edilmiş ve farklı: mevcut=$currentAdUnitId, yeni=$adUnitId - mevcut ID ile devam ediliyor - ${activity.javaClass.simpleName}",
                )
                // Mevcut ID ile devam et, yeni ID'yi set etmeye çalışma (crash önleme)
                // AdView zaten çalışıyor, sadece görünürlüğü kontrol et
            } else {
                // AdUnitId aynı, normal akışa devam et
            }

            // Reklam alanının boyutlarını önceden rezerve et (ekran zıplamasını önle)
            if (adView.visibility != View.VISIBLE) {
                adView.visibility = View.VISIBLE
            }

            // AdListener ekle - sadece hataları logla
            adView.adListener =
                object : AdListener() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        when (loadAdError.code) {
                            2 -> {
                                // Network error - emülatörde normal, loglama yapma
                            }
                            else -> {
                                // Diğer hatalar için kısa log
                                Timber.e("BannerAdDelegate - ${activity.javaClass.simpleName} - Banner reklam yüklenemedi: Code ${loadAdError.code}")
                            }
                        }
                    }
                }

            // Sadece reklam yüklenmemişse yükle
            try {
                val adRequest = AdRequest.Builder().build()
                adView.loadAd(adRequest)
            } catch (e: Exception) {
                Timber.e(e, "BannerAdDelegate - ${activity.javaClass.simpleName} - Reklam yükleme hatası: ${e.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "AdView gösterilirken beklenmeyen hata")
        }
    }

    /**
     * AdView'u güvenli bir şekilde resume eder.
     */
    private fun safeResumeAdView(adView: AdView) {
        try {
            adView.resume()
        } catch (e: Exception) {
            Timber.w(e, "AdView resume edilemedi - ${activity.javaClass.simpleName}")
        }
    }

    /**
     * AdView'u güvenli bir şekilde pause eder.
     */
    private fun safePauseAdView(adView: AdView) {
        try {
            adView.pause()
        } catch (e: Exception) {
            Timber.w(e, "AdView pause edilemedi (muhtemelen destroy edilmiş) - ${activity.javaClass.simpleName}")
        }
    }

    /**
     * AdView'u güvenli bir şekilde destroy eder.
     */
    private fun safeDestroyAdView(adView: AdView) {
        try {
            adView.destroy()
        } catch (e: Exception) {
            Timber.w(e, "AdView destroy edilemedi - ${activity.javaClass.simpleName}")
        }
    }

    /**
     * AdView'u gizler (findViewById ile).
     */
    private fun hideAdView() {
        val adView = activity.findViewById<AdView>(R.id.ad_view_banner)
        adView?.visibility = View.GONE
    }
}
