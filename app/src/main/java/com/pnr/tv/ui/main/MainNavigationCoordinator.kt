package com.pnr.tv.ui.main

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.pnr.tv.R
import com.pnr.tv.model.ContentType
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.ui.browse.ContentBrowseFragment
import com.pnr.tv.ui.livestreams.LiveStreamsBrowseFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * MainFragment için navigasyon işlemlerini yöneten koordinatör sınıf.
 * Reklam gösterimi, kullanıcı kontrolü, veri kontrolü ve fragment geçişlerini
 * tek bir yerden yöneterek MainFragment'ı sadeleştirir.
 *
 * @param userRepository Kullanıcı kontrolü için repository
 * @param contentRepository Veri kontrolü için repository
 */
class MainNavigationCoordinator
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val contentRepository: ContentRepository,
    ) {
        private var mainActivity: MainActivity? = null
        private var fragment: Fragment? = null
        private var fragmentManager: FragmentManager? = null
        private var lifecycleScope: CoroutineScope? = null
        private var fragmentView: View? = null

        // Navigasyon debounce için
        private var isNavigating = false
        private var lastNavigateTime = 0L

        /**
         * Koordinatörü başlatır. Fragment'ın lifecycle metodlarında çağrılmalıdır.
         *
         * @param mainActivity MainActivity referansı
         * @param fragment MainFragment referansı
         * @param fragmentManager Fragment geçişleri için fragment manager
         * @param lifecycleScope Coroutine scope
         * @param fragmentView Fragment'ın root view'ı
         */
        fun setup(
            mainActivity: MainActivity,
            fragment: Fragment,
            fragmentManager: FragmentManager,
            lifecycleScope: CoroutineScope,
            fragmentView: View,
        ) {
            this.mainActivity = mainActivity
            this.fragment = fragment
            this.fragmentManager = fragmentManager
            this.lifecycleScope = lifecycleScope
            this.fragmentView = fragmentView
        }

        /**
         * İçerik türüne göre navigasyon yapar (Movies veya Series).
         */
        fun navigateToContent(contentType: ContentType) {
            val activity =
                mainActivity
                    ?: run {
                        Timber.w("MainActivity bulunamadı")
                        return
                    }

            // Önce odaklanılan view'ı al (reklam kapandıktan sonra geri vermek için)
            val focusedView = activity.window?.currentFocus
            val containerView =
                when (contentType) {
                    ContentType.MOVIES -> fragmentView?.findViewById<View>(R.id.container_movies)
                    ContentType.SERIES -> fragmentView?.findViewById<View>(R.id.container_series)
                    else -> null
                }

            activity.showInterstitialAdIfNeeded(
                targetButton = focusedView ?: containerView,
            ) {
                val scope = lifecycleScope ?: return@showInterstitialAdIfNeeded
                scope.launch {
                    // Önce kullanıcı kontrolü yap
                    val hasUser =
                        try {
                            val allUsers = userRepository.allUsers.firstOrNull() ?: emptyList()
                            allUsers.isNotEmpty()
                        } catch (e: Exception) {
                            Timber.e(e, "Kullanıcı kontrolü hatası: ${e.message}")
                            false
                        }

                    if (!hasUser) {
                        // Kullanıcı yoksa uyarı göster ve navigate etme
                        activity.showUserRequiredWarning()
                        delay(3000L) // 3 saniye
                        activity.hideUserRequiredWarning()
                        return@launch
                    }

                    // Kullanıcı varsa veri kontrolü yap
                    val hasData =
                        try {
                            when (contentType) {
                                ContentType.MOVIES -> contentRepository.hasMovies()
                                ContentType.SERIES -> contentRepository.hasSeries()
                                else -> false
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Veri kontrolü hatası: ${e.message}")
                            false
                        }

                    if (!hasData) {
                        // Veri yoksa uyarı göster ve navigate etme
                        activity.showDataRequiredWarning()
                        delay(3000L) // 3 saniye
                        activity.hideDataRequiredWarning()
                        return@launch
                    }

                    // Kullanıcı ve veri varsa navigate et
                    performNavigation {
                        ContentBrowseFragment.newInstance(contentType, true)
                    }
                }
            }
        }

        /**
         * Canlı yayınlar sayfasına navigasyon yapar.
         */
        fun navigateToLiveStreams() {
            val activity =
                mainActivity
                    ?: run {
                        Timber.w("MainActivity bulunamadı")
                        return
                    }

            // Önce odaklanılan view'ı al (reklam kapandıktan sonra geri vermek için)
            val focusedView = activity.window?.currentFocus
            val containerView = fragmentView?.findViewById<View>(R.id.container_live_streams)

            activity.showInterstitialAdIfNeeded(
                targetButton = focusedView ?: containerView,
            ) {
                val scope = lifecycleScope ?: return@showInterstitialAdIfNeeded
                scope.launch {
                    // Önce kullanıcı kontrolü yap
                    val hasUser =
                        try {
                            val allUsers = userRepository.allUsers.firstOrNull() ?: emptyList()
                            allUsers.isNotEmpty()
                        } catch (e: Exception) {
                            Timber.e(e, "Kullanıcı kontrolü hatası: ${e.message}")
                            false
                        }

                    if (!hasUser) {
                        // Kullanıcı yoksa uyarı göster ve navigate etme
                        activity.showUserRequiredWarning()
                        delay(3000L) // 3 saniye
                        activity.hideUserRequiredWarning()
                        return@launch
                    }

                    // Kullanıcı varsa veri kontrolü yap
                    val hasData =
                        try {
                            contentRepository.hasLiveStreams()
                        } catch (e: Exception) {
                            Timber.e(e, "Veri kontrolü hatası: ${e.message}")
                            false
                        }

                    if (!hasData) {
                        // Veri yoksa uyarı göster ve navigate etme
                        activity.showDataRequiredWarning()
                        delay(3000L) // 3 saniye
                        activity.hideDataRequiredWarning()
                        return@launch
                    }

                    // Kullanıcı ve veri varsa navigate et
                    performNavigation {
                        LiveStreamsBrowseFragment.newInstance(true)
                    }
                }
            }
        }

        /**
         * Movies sayfasına navigasyon yapar.
         */
        fun navigateToMovies() {
            navigateToContent(ContentType.MOVIES)
        }

        /**
         * Series sayfasına navigasyon yapar.
         */
        fun navigateToSeries() {
            navigateToContent(ContentType.SERIES)
        }

        /**
         * Debounce kontrolü yaparak fragment geçişini gerçekleştirir.
         */
        private fun performNavigation(fragmentFactory: () -> Fragment) {
            val manager = fragmentManager ?: return
            val scope = lifecycleScope ?: return

            // Debounce: Aynı butona milisaniyeler içinde iki kez basılmasını engelle
            val currentTime = System.currentTimeMillis()
            if (isNavigating || (currentTime - lastNavigateTime < 500)) {
                return
            }
            isNavigating = true
            lastNavigateTime = currentTime

            // Fragment'ın üst üste binmesini engellemek için önce backstack'i temizle
            manager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            manager.commit {
                replace(
                    R.id.fragment_container,
                    fragmentFactory(),
                )
                addToBackStack(null)
            }

            // Debounce flag'ini resetle
            scope.launch {
                delay(600)
                isNavigating = false
            }
        }

        /**
         * Koordinatörü temizler. Fragment'ın onDestroy metodunda çağrılmalıdır.
         */
        fun cleanup() {
            mainActivity = null
            fragment = null
            fragmentManager = null
            lifecycleScope = null
            fragmentView = null
            isNavigating = false
            lastNavigateTime = 0L
        }
    }
