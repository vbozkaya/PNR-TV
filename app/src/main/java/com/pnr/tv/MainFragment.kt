package com.pnr.tv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.pnr.tv.model.ContentType
import com.pnr.tv.ui.base.ToolbarController
import com.pnr.tv.ui.browse.ContentBrowseFragment
import com.pnr.tv.ui.livestreams.LiveStreamsBrowseFragment

/**
 * Uygulamanın ana ekranını temsil eden fragment.
 * Android TV için içerik kategorilerini gösterir.
 *
 * @see MainActivity Ana aktivite tarafından barındırılır.
 */
class MainFragment : Fragment() {
    // Focus state kaydetme için
    private var lastFocusedContainerId: Int = R.id.container_live_streams
    
    private companion object {
        const val KEY_LAST_FOCUSED_CONTAINER_ID = "main_fragment_last_focused_container_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fragment yeniden yaratıldığında kaydedilen hafızayı geri yükle
        if (savedInstanceState != null) {
            lastFocusedContainerId = savedInstanceState.getInt(KEY_LAST_FOCUSED_CONTAINER_ID, R.id.container_live_streams)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        
        // Fragment yok edilmeden önce hafızayı kaydet
        outState.putInt(KEY_LAST_FOCUSED_CONTAINER_ID, lastFocusedContainerId)
    }

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

        // Container'lara tıklama listener'ları ekle
        setupContainerListeners(view)

        // Sadece ilk açılışta ve üst menü butonlarına focus yoksa container'a focus ver
        view.post {
            if (!isTopMenuButtonFocused()) {
                val lastFocusedContainer = view.findViewById<View>(lastFocusedContainerId)
                if (lastFocusedContainer != null) {
                    lastFocusedContainer.requestFocus()
                } else {
                    view.findViewById<View>(R.id.container_live_streams)?.requestFocus()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        getToolbarController()?.showTopMenu()
        
        // Fragment geri geldiğinde, sadece üst menü butonlarına focus yoksa container'a focus ver
        view?.post {
            if (!isTopMenuButtonFocused()) {
                val lastFocusedContainer = view?.findViewById<View>(lastFocusedContainerId)
                if (lastFocusedContainer != null) {
                    lastFocusedContainer.requestFocus()
                }
            }
        }
    }
    
    /**
     * Üst menü butonlarından birine focus verilmiş mi kontrol eder
     */
    private fun isTopMenuButtonFocused(): Boolean {
        return (activity as? MainActivity)?.isTopMenuButtonFocused() ?: false
    }

    override fun onPause() {
        super.onPause()
        getToolbarController()?.hideTopMenu()
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
        val containerConfigs = listOf(
            ContainerConfig(
                viewId = R.id.container_live_streams,
                navigationAction = ::navigateToLiveStreams
            ),
            ContainerConfig(
                viewId = R.id.container_movies,
                navigationAction = ::navigateToMovies
            ),
            ContainerConfig(
                viewId = R.id.container_series,
                navigationAction = ::navigateToSeries
            )
        )

        containerConfigs.forEach { config ->
            view.findViewById<View>(config.viewId)?.apply {
                setOnClickListener {
                    // Container'a tıklandığında focus state'i kaydet
                    lastFocusedContainerId = config.viewId
                    config.navigationAction()
                }
                // Focus değiştiğinde kaydet (D-Pad ile gezinme için)
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        lastFocusedContainerId = config.viewId
                    }
                }
            }
        }
    }

    /**
     * Container yapılandırması için data class.
     */
    private data class ContainerConfig(
        val viewId: Int,
        val navigationAction: () -> Unit
    )

    private fun navigateToContent(contentType: ContentType) {
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                ContentBrowseFragment.newInstance(contentType),
            )
            addToBackStack(null)
        }
    }

    private fun navigateToLiveStreams() {
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                LiveStreamsBrowseFragment.newInstance(),
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
}
