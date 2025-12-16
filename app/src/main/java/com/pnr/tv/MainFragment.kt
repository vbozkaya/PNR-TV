package com.pnr.tv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import com.pnr.tv.model.ContentType
import com.pnr.tv.ui.base.ToolbarController
import com.pnr.tv.ui.browse.ContentBrowseFragment
import com.pnr.tv.ui.livestreams.LiveStreamsBrowseFragment
import com.pnr.tv.util.BackgroundManager
import kotlinx.coroutines.launch

/**
 * Uygulamanın ana ekranını temsil eden fragment.
 * Android TV için içerik kategorilerini gösterir.
 *
 * @see MainActivity Ana aktivite tarafından barındırılır.
 */
class MainFragment : Fragment() {
    private val pendingRunnables = mutableListOf<Runnable>()

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

        // Arka plan görselini yükle (diğer sayfalarla aynı şekilde)
        loadBackground(view)

        // Android'in otomatik odak yönetimini devre dışı bırak
        // Root view'ı focusable yapma
        view.isFocusable = false
        view.isFocusableInTouchMode = false

        // Container'ları başlangıçta focusable yapma (XML'de zaten false)
        ensureContainersNotFocusable(view)

        // Container'lara tıklama listener'ları ekle
        setupContainerListeners(view)

        // Android'in otomatik odak yönetimini engelle ve update button'a odak ver
        setupInitialFocus()
    }

    override fun onResume() {
        super.onResume()
        getToolbarController()?.showTopMenu()

        // Container'ları önce focusable yapmayı engelle
        view?.let { ensureContainersNotFocusable(it) }

        // Android'in otomatik odak yönetimini engelle ve update button'a odak ver
        setupInitialFocus()
    }

    override fun onPause() {
        super.onPause()
        getToolbarController()?.hideTopMenu()

        // Activity geçişi sırasında container'ları focusable yapmayı engelle
        view?.let { ensureContainersNotFocusable(it) }

        // Bekleyen focus setup işlemlerini iptal et
        pendingRunnables.clear()
    }

    /**
     * Container'ları focusable yapmayı engeller.
     * Activity geçişlerinden önce ve sonra çağrılır.
     */
    private fun ensureContainersNotFocusable(view: View) {
        val containers =
            listOf(
                view.findViewById<View>(R.id.container_live_streams),
                view.findViewById<View>(R.id.container_movies),
                view.findViewById<View>(R.id.container_series),
            )
        containers.forEach { container ->
            container?.isFocusable = false
            container?.isFocusableInTouchMode = false
        }
    }

    /**
     * ToolbarController interface'ini güvenli bir şekilde alır.
     * Fragment ve Activity arasındaki sıkı bağlılığı giderir.
     */
    private fun getToolbarController(): ToolbarController? {
        return activity as? ToolbarController
    }

    /**
     * Android'in otomatik odak yönetimini engeller ve update button'a odak verir.
     * ViewTreeObserver kullanarak view'ların tamamen hazır olduğundan emin olur.
     * Activity geçişlerinden sonra container'ları focusable yapmayı geciktirir
     * böylece focus kaybı önlenir.
     */
    private fun setupInitialFocus() {
        view?.let { fragmentView ->
            val containers =
                listOf(
                    fragmentView.findViewById<View>(R.id.container_live_streams),
                    fragmentView.findViewById<View>(R.id.container_movies),
                    fragmentView.findViewById<View>(R.id.container_series),
                )

            // Önce container'ları kesinlikle focusable yapma
            ensureContainersNotFocusable(fragmentView)

            // ViewTreeObserver ile view'ların tamamen hazır olduğundan emin ol
            val observer = fragmentView.viewTreeObserver
            observer.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        // View'lar hazır, artık odak ayarlayabiliriz
                        fragmentView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                        // Önce update button'a odak ver (birden fazla kez deneyelim)
                        val activity = activity as? MainActivity
                        activity?.requestFocusOnUpdateButton()

                        // Activity geçişlerinden sonra focus kaybını önlemek için
                        // container'ları focusable yapmayı daha uzun bir süre geciktir
                        fragmentView.postDelayed({
                            // Container'ları hala focusable yapma
                            ensureContainersNotFocusable(fragmentView)
                            activity?.requestFocusOnUpdateButton()
                        }, 50)

                        fragmentView.postDelayed({
                            // Container'ları hala focusable yapma
                            ensureContainersNotFocusable(fragmentView)
                            activity?.requestFocusOnUpdateButton()
                        }, 150)

                        fragmentView.postDelayed({
                            // Container'ları hala focusable yapma
                            ensureContainersNotFocusable(fragmentView)
                            activity?.requestFocusOnUpdateButton()
                        }, 300)

                        // Container'ları odaklanabilir yapmayı daha uzun bir süre geciktir
                        // Bu sayede activity geçişlerinden sonra focus kaybı önlenir
                        val makeContainersFocusableRunnable =
                            Runnable {
                                // Update button'a hala focus verilmiş mi kontrol et
                                val currentFocus = activity?.window?.currentFocus
                                val isTopMenuFocused = activity?.isTopMenuButtonFocused() == true

                                // Sadece üst menü butonlarından birine focus verilmişse container'ları focusable yap
                                if (isTopMenuFocused) {
                                    containers.forEach { container ->
                                        container?.isFocusable = true
                                        container?.isFocusableInTouchMode = true
                                    }

                                    // Son bir kez daha update button'a odak ver
                                    activity?.requestFocusOnUpdateButton()
                                } else {
                                    // Eğer focus başka bir yerdeyse, update button'a odak ver ve container'ları focusable yap
                                    activity?.requestFocusOnUpdateButton()
                                    fragmentView.postDelayed({
                                        // Tekrar kontrol et
                                        val newFocus = activity?.window?.currentFocus
                                        val newIsTopMenuFocused = activity?.isTopMenuButtonFocused() == true
                                        if (newIsTopMenuFocused) {
                                            containers.forEach { container ->
                                                container?.isFocusable = true
                                                container?.isFocusableInTouchMode = true
                                            }
                                            activity?.requestFocusOnUpdateButton()
                                        }
                                    }, 100)
                                }
                            }
                        pendingRunnables.add(makeContainersFocusableRunnable)
                        fragmentView.postDelayed(makeContainersFocusableRunnable, 500) // Activity geçişlerinden sonra daha uzun gecikme
                    }
                },
            )
        }
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
                    navigationAction = ::navigateToLiveStreams,
                ),
                ContainerConfig(
                    viewId = R.id.container_movies,
                    navigationAction = ::navigateToMovies,
                ),
                ContainerConfig(
                    viewId = R.id.container_series,
                    navigationAction = ::navigateToSeries,
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

    private fun navigateToContent(contentType: ContentType) {
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                ContentBrowseFragment.newInstance(contentType, true),
            )
            addToBackStack(null)
        }
    }

    private fun navigateToLiveStreams() {
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                LiveStreamsBrowseFragment.newInstance(true),
            )
            addToBackStack(null)
        }
    }

    private fun navigateToMovies() {
        navigateToContent(ContentType.MOVIES)
    }

    private fun navigateToSeries() {
        navigateToContent(ContentType.SERIES)
    }

    /**
     * Arka plan görselini güvenli bir şekilde yükler.
     * BackgroundManager kullanarak cache'lenmiş görseli yükler.
     */
    private fun loadBackground(view: View) {
        timber.log.Timber.tag("BACKGROUND").d("🎬 MainFragment.loadBackground() çağrıldı")
        viewLifecycleOwner.lifecycleScope.launch {
            // Fragment'ın kendi root view'ına arkaplan ekle (view.rootView yerine view)
            timber.log.Timber.tag(
                "BACKGROUND",
            ).d("📐 Fragment View - View: ${view.javaClass.simpleName}, Width: ${view.width}, Height: ${view.height}")

            // Önce cache'den kontrol et (hızlı)
            val cached = BackgroundManager.getCachedBackground()
            if (cached != null) {
                timber.log.Timber.tag("BACKGROUND").d("✅ Cache'den arkaplan uygulanıyor (Fragment view)")
                view.background = cached
                timber.log.Timber.tag(
                    "BACKGROUND",
                ).d("✅ Arkaplan uygulandı - Fragment view background: ${view.background?.javaClass?.simpleName}")
                return@launch
            }

            timber.log.Timber.tag("BACKGROUND").d("⏳ Cache'de yok, yükleme başlatılıyor...")

            // Cache'de yoksa yükle
            BackgroundManager.loadBackground(
                context = requireContext(),
                imageLoader = requireContext().imageLoader,
                onSuccess = { drawable ->
                    timber.log.Timber.tag("BACKGROUND").d("✅ onSuccess callback çağrıldı - Drawable: ${drawable.javaClass.simpleName}")
                    view.background = drawable
                    timber.log.Timber.tag(
                        "BACKGROUND",
                    ).d("✅ Arkaplan uygulandı - Fragment view background: ${view.background?.javaClass?.simpleName}")
                },
                onError = {
                    timber.log.Timber.tag("BACKGROUND").w("⚠️ onError callback çağrıldı, fallback deneniyor...")
                    // Hata durumunda fallback kullan (theme'de zaten tanımlı)
                    val fallback = BackgroundManager.getFallbackBackground(requireContext())
                    if (fallback != null) {
                        view.background = fallback
                        timber.log.Timber.tag("BACKGROUND").d("✅ Fallback arkaplan uygulandı")
                    } else {
                        timber.log.Timber.tag("BACKGROUND").e("❌ Fallback arkaplan da null!")
                    }
                },
            )
        }
    }
}
