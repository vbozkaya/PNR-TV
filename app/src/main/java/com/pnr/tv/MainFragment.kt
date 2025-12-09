package com.pnr.tv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
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

        // Android'in otomatik odak yönetimini devre dışı bırak
        // Root view'ı focusable yapma
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        
        // Container'lara tıklama listener'ları ekle
        setupContainerListeners(view)
        
        // Android'in otomatik odak yönetimini engelle ve update button'a odak ver
        setupInitialFocus()
    }
    
    override fun onResume() {
        super.onResume()
        getToolbarController()?.showTopMenu()
        
        // Android'in otomatik odak yönetimini engelle ve update button'a odak ver
        setupInitialFocus()
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
     * Android'in otomatik odak yönetimini engeller ve update button'a odak verir.
     * ViewTreeObserver kullanarak view'ların tamamen hazır olduğundan emin olur.
     */
    private fun setupInitialFocus() {
        view?.let { fragmentView ->
            val containers = listOf(
                fragmentView.findViewById<View>(R.id.container_live_streams),
                fragmentView.findViewById<View>(R.id.container_movies),
                fragmentView.findViewById<View>(R.id.container_series)
            )
            
            // Container'lar zaten XML'de focusable="false" olarak ayarlandı
            // Bu sayede Android otomatik odak yönetimi onlara odak veremez
            
            // ViewTreeObserver ile view'ların tamamen hazır olduğundan emin ol
            val observer = fragmentView.viewTreeObserver
            observer.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // View'lar hazır, artık odak ayarlayabiliriz
                    fragmentView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    
                    // Önce update button'a odak ver (birden fazla kez deneyelim)
                    val activity = activity as? MainActivity
                    activity?.requestFocusOnUpdateButton()
                    
                    // Birkaç kez daha deneyelim (Android'in otomatik odak yönetimi ile yarışmak için)
                    fragmentView.postDelayed({
                        activity?.requestFocusOnUpdateButton()
                        
                        // Container'ları odaklanabilir yap (artık güvenli)
                        containers.forEach { container ->
                            container?.isFocusable = true
                            container?.isFocusableInTouchMode = true
                        }
                        
                        // Son bir kez daha update button'a odak ver
                        fragmentView.postDelayed({
                            activity?.requestFocusOnUpdateButton()
                        }, 50)
                    }, 100)
                }
            })
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
        val navigationAction: () -> Unit
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
}
