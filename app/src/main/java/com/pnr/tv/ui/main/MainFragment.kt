package com.pnr.tv.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.R
import com.pnr.tv.core.base.ToolbarController
import android.graphics.drawable.Drawable
import com.pnr.tv.util.ui.BackgroundManager
import com.pnr.tv.util.ui.setBackgroundSafely
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Uygulamanın ana ekranını temsil eden fragment.
 * Android TV için içerik kategorilerini gösterir.
 *
 * @see MainActivity Ana aktivite tarafından barındırılır.
 */
@AndroidEntryPoint
class MainFragment : Fragment() {
    @Inject
    lateinit var navigationCoordinator: MainNavigationCoordinator

    private var focusHandler: MainFocusHandler? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Android'in otomatik odak yönetimini devre dışı bırak
        // Root view'ı focusable yapma
        view.isFocusable = false
        view.isFocusableInTouchMode = false

        // Focus handler ve navigation coordinator'ı başlat
        val activity = activity as? MainActivity
        if (activity != null) {
            focusHandler = MainFocusHandler(view, activity)
            // Container'ları başlangıçta focusable yapma (XML'de zaten false)
            focusHandler?.ensureContainersNotFocusable()

            navigationCoordinator.setup(
                mainActivity = activity,
                fragment = this,
                fragmentManager = parentFragmentManager,
                lifecycleScope = lifecycleScope,
                fragmentView = view,
            )
        }

        // Container'lara tıklama listener'ları ekle
        setupContainerListeners(view)

        // Fragment tamamen çizildiğinde butonları göster ve arka planı yükle
        // view.doOnPreDraw view çizildikten hemen sonra çalışır, bu en güvenilir yöntemdir
        view.doOnPreDraw {
            activity?.showTopMenuButtons()
            // Arka plan görselini yükle - view tamamen hazır olduğunda
            loadBackground(view)
        }

        // Android'in otomatik odak yönetimini engelle ve update button'a odak ver
        focusHandler?.setupInitialFocus()
    }

    override fun onResume() {
        super.onResume()
        getToolbarController()?.showTopMenu()

        // Container'ları önce focusable yapmayı engelle
        focusHandler?.ensureContainersNotFocusable()

        // Android'in otomatik odak yönetimini engelle ve update button'a odak ver
        focusHandler?.setupInitialFocus()

        // TV bekleme modundan çıktığında arka plan bitmap'inin geçerliliğini kontrol et
        // Bu, "Canvas: trying to use a recycled bitmap" hatasını önler
        view?.let { validateAndReloadBackground(it) }
    }

    override fun onPause() {
        super.onPause()
        getToolbarController()?.hideTopMenu()

        // Activity geçişi sırasında container'ları focusable yapmayı engelle
        focusHandler?.ensureContainersNotFocusable()

        // Bekleyen focus setup işlemlerini iptal et
        focusHandler?.clearPendingRunnables()
    }

    override fun onStop() {
        super.onStop()
        // Fragment arka plana geçtiğinde (TV bekleme modu dahil) arka plan cache'ini yumuşak temizle
        // Bu, sistemin bellek yönetimi yapmasına izin verir
        // Disk cache korunur, böylece onResume'da hızlıca yeniden yüklenebilir
        BackgroundManager.softClearCache()
        timber.log.Timber.tag("BACKGROUND").d("🛑 Fragment onStop: Arka plan cache yumuşak temizlendi (disk cache korunuyor)")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Navigation coordinator'ı temizle
        navigationCoordinator.cleanup()
    }

    /**
     * ToolbarController interface'ini güvenli bir şekilde alır.
     * Fragment ve Activity arasındaki sıkı bağlılığı giderir.
     */
    private fun getToolbarController(): ToolbarController? {
        return activity as? ToolbarController
    }

    /**
     * Container listener'larını ayarlar.
     * Kod tekrarını azaltmak için ortak bir fonksiyon kullanır.
     *
     * Not: setOnKeyListener KEYCODE_DPAD_CENTER için gereksizdir,
     * çünkü setOnClickListener zaten D-Pad merkezi tıklamalarını yakalar.
     */
    private fun setupContainerListeners(view: View) {
        val containerConfigs =
            listOf(
                ContainerConfig(
                    viewId = R.id.container_live_streams,
                    navigationAction = { navigationCoordinator.navigateToLiveStreams() },
                ),
                ContainerConfig(
                    viewId = R.id.container_movies,
                    navigationAction = { navigationCoordinator.navigateToMovies() },
                ),
                ContainerConfig(
                    viewId = R.id.container_series,
                    navigationAction = { navigationCoordinator.navigateToSeries() },
                ),
            )

        containerConfigs.forEach { config ->
            view.findViewById<View>(config.viewId)?.apply {
                setOnClickListener {
                    config.navigationAction()
                }
            }
        }
    }

    /**
     * Container yapılandırması için data class.
     */
    private data class ContainerConfig(
        val viewId: Int,
        val navigationAction: () -> Unit,
    )

    /**
     * Arka plan görselini güvenli bir şekilde yükler.
     * BackgroundManager kullanarak cache'lenmiş görseli yükler.
     */
    private fun loadBackground(view: View) {
        // view.doOnPreDraw içinden çağrıldığı için view zaten tamamen hazır
        // BrowseUiHandler'daki gibi doğrudan lifecycleScope kullan (en güvenilir yöntem)
        lifecycleScope.launch {
            // Fragment'ın kendi root view'ına arkaplan ekle (view.rootView yerine view)
            timber.log.Timber.tag(
                "BACKGROUND",
            ).d("📐 Fragment View - View: ${view.javaClass.simpleName}, Width: ${view.width}, Height: ${view.height}")

            // Önce cache'den kontrol et (hızlı)
            // getCachedBackground zaten recycle kontrolü yapıyor
            val cached = BackgroundManager.getCachedBackground()
            if (cached != null) {
                view.setBackgroundSafely(cached)
                timber.log.Timber.tag(
                    "BACKGROUND",
                ).d("✅ Arkaplan uygulandı (cache'den) - Fragment view background: ${view.background?.javaClass?.simpleName}")
                return@launch
            }

            // Cache'de yoksa yükle
            // Artık kullanılmıyor, Glide kullanılıyor
            BackgroundManager.loadBackground(
                context = requireContext(),
                imageLoader = null,
                onSuccess = { drawable ->
                    // Güvenli set et - ekstra koruma
                    view.setBackgroundSafely(drawable)
                    timber.log.Timber.tag(
                        "BACKGROUND",
                    ).d("✅ Arkaplan uygulandı (yüklendi) - Fragment view background: ${view.background?.javaClass?.simpleName}")
                },
                onError = {
                    timber.log.Timber.tag("BACKGROUND").w("⚠️ onError callback çağrıldı, fallback deneniyor...")
                    // Hata durumunda fallback kullan (theme'de zaten tanımlı)
                    val fallback = BackgroundManager.getFallbackBackground(requireContext())
                    view.setBackgroundSafely(fallback)
                },
            )
        }
    }

    /**
     * Fragment uykudan uyandığında (onResume) arka plan bitmap'inin geçerliliğini kontrol eder.
     * Eğer bitmap recycle edilmişse, fallback gösterir ve asenkron olarak yeniden yükler.
     * Bu, TV bekleme modundan çıktığında oluşan "Canvas: trying to use a recycled bitmap" hatasını önler.
     */
    private fun validateAndReloadBackground(view: View) {
        lifecycleScope.launch {
            try {
                val currentBackground = view.background

                // BackgroundManager'dan cache'lenmiş arka planı kontrol et
                // getCachedBackground zaten recycle kontrolü yapıyor
                val cached = BackgroundManager.getCachedBackground()

                // Eğer cache'de geçersiz bir bitmap varsa veya mevcut arka plan geçersizse
                if (cached == null || isBackgroundRecycled(currentBackground)) {
                    timber.log.Timber.tag("BACKGROUND").d("🔄 Fragment onResume: Arka plan geçersiz, yeniden yükleniyor")
                    
                    // Önce fallback göster (tema rengi)
                    val fallback = BackgroundManager.getFallbackBackground(requireContext())
                    if (fallback != null) {
                        view.setBackgroundSafely(fallback)
                    }

                    // Asenkron olarak görseli yeniden yükle
                    loadBackground(view)
                } else {
                    timber.log.Timber.tag("BACKGROUND").d("✅ Fragment onResume: Arka plan geçerli, yeniden yükleme gerekmiyor")
                }
            } catch (e: Exception) {
                timber.log.Timber.tag("BACKGROUND").e(e, "❌ Fragment onResume: Arka plan kontrolü sırasında hata")
                // Hata durumunda fallback göster
                try {
                    val fallback = BackgroundManager.getFallbackBackground(requireContext())
                    view.setBackgroundSafely(fallback)
                } catch (ex: Exception) {
                    timber.log.Timber.tag("BACKGROUND").e(ex, "❌ Fallback arka plan set edilemedi")
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
            timber.log.Timber.tag("BACKGROUND").w(e, "⚠️ Arka plan kontrolü sırasında hata")
            true // Hata durumunda güvenli tarafta kal
        }
    }
}
