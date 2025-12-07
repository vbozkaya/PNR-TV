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

        // İlk container'a focus ver
        view.findViewById<View>(R.id.container_live_streams)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        getToolbarController()?.showTopMenu()
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
            view.findViewById<View>(config.viewId)?.setOnClickListener {
                config.navigationAction()
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
