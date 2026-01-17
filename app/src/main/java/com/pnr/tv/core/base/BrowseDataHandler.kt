package com.pnr.tv.core.base

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.ui.main.MainActivity
import com.pnr.tv.R
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.ui.browse.CategoryAdapter
import com.pnr.tv.ui.browse.ContentAdapter
import com.pnr.tv.util.error.ErrorSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BaseBrowseFragment içindeki veri yönetimi ve UI state yönetimi sorumluluklarını yöneten handler sınıfı.
 *
 * Bu sınıf, BaseBrowseFragment'tan veri gözlemleme ve UI state yönetimi sorumluluklarını ayırarak
 * Fragment'ın daha temiz ve bakımı kolay olmasını sağlar.
 *
 * Sorumlulukları:
 * - dataObserver başlatma ve startObserving() mantığı
 * - UI state yönetimi (loading, error, empty, content)
 * - Veri kontrolü ve uyarı gösterimi (checkDataAndShowWarningIfNeeded)
 */
class BrowseDataHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val userRepository: UserRepository,
    ) {
        private var viewModel: BaseViewModel? = null
        private var categoryAdapter: CategoryAdapter? = null
        private var contentAdapter: ContentAdapter? = null
        private var uiHandler: BrowseUiHandler? = null
        private var fragment: BaseBrowseFragment? = null
        private var categoriesRecyclerView: CustomCategoriesRecyclerView? = null
        private var contentRecyclerView: CustomContentRecyclerView? = null
        private var focusDelegate: BrowseFocusDelegate? = null
        private var lifecycleOwner: LifecycleOwner? = null

        private var dataObserver: BrowseDataObserver? = null

        /**
         * Handler'ı setup eder ve veri gözlemleme sürecini başlatır.
         * BaseBrowseFragment'tan initializeViews() içinde çağrılmalıdır.
         */
        fun setup(
            viewModel: BaseViewModel,
            categoryAdapter: CategoryAdapter,
            contentAdapter: ContentAdapter,
            uiHandler: BrowseUiHandler,
            fragment: BaseBrowseFragment,
            categoriesRecyclerView: CustomCategoriesRecyclerView,
            contentRecyclerView: CustomContentRecyclerView,
            focusDelegate: BrowseFocusDelegate,
            lifecycleOwner: LifecycleOwner,
        ) {
            this.viewModel = viewModel
            this.categoryAdapter = categoryAdapter
            this.contentAdapter = contentAdapter
            this.uiHandler = uiHandler
            this.fragment = fragment
            this.categoriesRecyclerView = categoriesRecyclerView
            this.contentRecyclerView = contentRecyclerView
            this.focusDelegate = focusDelegate
            this.lifecycleOwner = lifecycleOwner

            // Initialize data observer
            dataObserver =
                BrowseDataObserver(
                    lifecycleOwner = lifecycleOwner,
                    viewModel = viewModel,
                    categoryAdapter = categoryAdapter,
                    contentAdapter = contentAdapter,
                    focusDelegate = focusDelegate,
                    uiHandler = uiHandler,
                    fragment = fragment,
                    categoriesRecyclerView = categoriesRecyclerView,
                    contentRecyclerView = contentRecyclerView,
                )

            // Start observing data flows
            dataObserver?.startObserving()
        }

        /**
         * Loading state'i gösterir.
         * Skeleton loading kullanarak içerik grid'inde placeholder gösterir.
         */
        fun showLoadingState() {
            uiHandler?.show(BrowseUiState.Loading)
        }

        /**
         * Error state'i gösterir.
         * Tüm ekranlarda tutarlı error gösterimi sağlar.
         * Hata severity'ye göre otomatik kapanma süresi belirlenir.
         *
         * @param message Hata mesajı (kullanıcı dostu formatlanmış olmalı)
         * @param severity Hata şiddeti (varsayılan: MEDIUM - 5 saniye)
         */
        fun showErrorState(
            message: String,
            severity: ErrorSeverity = ErrorSeverity.MEDIUM,
        ) {
            uiHandler?.show(BrowseUiState.Error(message, severity))
        }

        /**
         * Empty state'i gösterir.
         * @param message Gösterilecek boş durum mesajı
         */
        fun showEmptyState(message: String) {
            uiHandler?.show(BrowseUiState.Empty(message))
        }

        /**
         * Normal state'i gösterir (empty state'i gizler).
         */
        fun showContentState() {
            uiHandler?.show(BrowseUiState.Content)
        }

        /**
         * onResume'da çağrılır, pending restore pozisyonunu ayarlar.
         */
        fun setPendingRestorePosition(position: Int?) {
            dataObserver?.pendingRestorePosition = position
        }

        /**
         * Veri kontrolü yapar ve eğer veri yoksa uyarı gösterir.
         * Child fragment'ların onInitialLoad() metodunu çağırmadan önce bu kontrol yapılır.
         */
        suspend fun checkDataAndShowWarningIfNeeded(): Boolean {
            val fragment = this.fragment ?: return false
            val lifecycleOwner = this.lifecycleOwner ?: return false

            // Önce kullanıcı kontrolü yap - eğer kullanıcı yoksa farklı uyarı göster
            val hasUser =
                try {
                    val allUsers = userRepository.allUsers.firstOrNull() ?: emptyList()
                    allUsers.isNotEmpty()
                } catch (e: Exception) {
                    // Kullanıcı kontrolü hatası
                    false
                }

            // Kullanıcı yoksa özel uyarı göster
            if (!hasUser) {
                val mainActivity = fragment.activity as? MainActivity
                if (mainActivity != null) {
                    mainActivity.showUserRequiredWarning()

                    // viewLifecycleOwner henüz hazır olmayabilir, lifecycleScope kullan
                    lifecycleOwner.lifecycleScope.launch {
                        delay(3000L) // 3 saniye
                        mainActivity.hideUserRequiredWarning()
                        if (fragment.isAdded && fragment.activity != null) {
                            fragment.activity?.onBackPressed()
                        }
                    }
                }
                return false
            }

            // Kullanıcı varsa veri kontrolü yap
            val hasData =
                try {
                    fragment.hasData()
                } catch (e: Exception) {
                    // Exception durumunda tekrar kullanıcı kontrolü yap (güvenlik için)
                    // Veri kontrolü hatası

                    val hasUserRetry =
                        try {
                            val allUsers = userRepository.allUsers.firstOrNull() ?: emptyList()
                            allUsers.isNotEmpty()
                        } catch (e2: Exception) {
                            false
                        }

                    if (!hasUserRetry) {
                        val mainActivity = fragment.activity as? MainActivity
                        if (mainActivity != null) {
                            mainActivity.showUserRequiredWarning()

                            // viewLifecycleOwner henüz hazır olmayabilir, lifecycleScope kullan
                            lifecycleOwner.lifecycleScope.launch {
                                delay(4000L) // 4 saniye
                                mainActivity.hideUserRequiredWarning()
                                if (fragment.isAdded && fragment.activity != null) {
                                    fragment.activity?.onBackPressed()
                                }
                            }
                        }
                        return false
                    }

                    false
                }

            if (!hasData) {
                // Veri yoksa MainActivity'ye erişip kullanıcı dostu mesaj göster
                val mainActivity = fragment.activity as? MainActivity
                if (mainActivity != null) {
                    // Kullanıcıyı sakinleştiren, işlemin devam ettiğini belirten mesaj göster
                    mainActivity.showDataRequiredWarning(fragment.getString(R.string.msg_preparing_content))

                    // 4.5 saniye sonra otomatik kapat ve ana sayfaya dön
                    // viewLifecycleOwner henüz hazır olmayabilir, lifecycleScope kullan
                    lifecycleOwner.lifecycleScope.launch {
                        delay(4500L) // 4.5 saniye
                        mainActivity.hideDataRequiredWarning()
                        // Ana sayfaya dön - fragment'ı kapat
                        if (fragment.isAdded && fragment.activity != null) {
                            fragment.activity?.onBackPressed()
                        }
                    }
                }
                return false
            }
            return true
        }
    }
