package com.pnr.tv.core.base

import android.content.Context
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import com.pnr.tv.ui.browse.ContentAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * BaseBrowseFragment içindeki focus yönetimi ve lifecycle takibi sorumluluklarını yöneten manager sınıfı.
 *
 * Bu sınıf, BaseBrowseFragment'tan focus restore, pozisyon kaydetme ve lifecycle takibi
 * sorumluluklarını ayırarak Fragment'ın daha temiz ve bakımı kolay olmasını sağlar.
 *
 * Sorumlulukları:
 * - Fragment replace edildiğinde geri yüklenecek pozisyon ve kategori ID kaydetme
 * - Bundle'dan pozisyon okuma (sistem tarafından destroy edildiyse)
 * - onResume'da DEEP RESTORE mantığı (ViewModel'deki pozisyonu not alıp dataHandler'a set etme)
 * - onPause'da pozisyon ve kategori ID kaydetme
 * - onSaveInstanceState'da pozisyon kaydetme
 * - restoreFocusToGrid metodu (odak sahipsiz kaldığında grid'e zorla geri çekme)
 * - onDestroyView'da focusHandler temizleme
 */
class BrowseFocusManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val KEY_LAST_FOCUSED_CONTENT_POSITION = "last_focused_content_position"
        }

        // Fragment replace edildiğinde geri yüklenecek pozisyon ve kategori
        // onPause() içinde kaydedilir, kategoriler yüklendikten sonra geri yüklenir
        private var savedLastFocusedPosition: Int? = null
        private var savedLastSelectedCategoryId: String? = null

        // Bundle'dan okunan pozisyon (sistem tarafından destroy edildiyse)
        private var bundleSavedPosition: Int? = null

        // Setup edilen referanslar
        private var viewModel: BaseViewModel? = null
        private var dataHandler: BrowseDataHandler? = null
        private var focusHandler: BrowseFocusHandler? = null
        private var contentRecyclerView: CustomContentRecyclerView? = null
        private var contentAdapter: ContentAdapter? = null
        private var focusDelegate: BrowseFocusDelegate? = null

        /**
         * Manager'ı setup eder. BaseBrowseFragment'tan initializeViews() içinde çağrılmalıdır.
         */
        fun setup(
            viewModel: BaseViewModel,
            dataHandler: BrowseDataHandler,
            focusHandler: BrowseFocusHandler?,
            contentRecyclerView: CustomContentRecyclerView,
            contentAdapter: ContentAdapter,
            focusDelegate: BrowseFocusDelegate?,
        ) {
            this.viewModel = viewModel
            this.dataHandler = dataHandler
            this.focusHandler = focusHandler
            this.contentRecyclerView = contentRecyclerView
            this.contentAdapter = contentAdapter
            this.focusDelegate = focusDelegate
        }

        /**
         * onCreate lifecycle metodunda çağrılmalıdır.
         * Bundle'dan kaydedilmiş pozisyonu okur ve initial launch kontrolü yapar.
         */
        fun onCreate(
            savedInstanceState: Bundle?,
            arguments: Bundle?,
        ) {
            // Bundle'dan kaydedilmiş pozisyonu oku (sistem tarafından destroy edildiyse)
            bundleSavedPosition = savedInstanceState?.getInt(KEY_LAST_FOCUSED_CONTENT_POSITION, -1)?.takeIf { it != -1 }

            // INITIAL LAUNCH kontrolü: Ana menüden ilk kez açılıyorsa hafızayı temizle
            val isInitialLaunch = arguments?.getBoolean("is_initial_launch", false) ?: false
            if (isInitialLaunch) {
                viewModel?.let { vm ->
                    vm.lastFocusedContentPosition = null
                    vm.lastSelectedCategoryId = null // Ana sayfadan geldiğinde null yap ki restore ve initial focus arasında kalmasın
                }
                savedLastFocusedPosition = null
                savedLastSelectedCategoryId = null

                // [FIX]: Bayrağı false yap ki geri dönüşlerde (fragment recreation) tekrar sıfırlamasın.
                arguments?.putBoolean("is_initial_launch", false)
            }
        }

        /**
         * onResume lifecycle metodunda çağrılmalıdır.
         * DEEP RESTORE mantığını uygular: ViewModel'deki pozisyonu not alıp dataHandler'a set eder.
         */
        fun onResume(arguments: Bundle?) {
            val vm = viewModel ?: return
            val handler = dataHandler ?: return

            // Değerleri ViewModel'e geri yükle
            savedLastFocusedPosition?.let { vm.lastFocusedContentPosition = it }
            savedLastSelectedCategoryId?.let { vm.lastSelectedCategoryId = it }
            bundleSavedPosition?.let { vm.lastFocusedContentPosition = it }

            // DEEP RESTORE: Pozisyonu not al, veriler yüklenince (observeContents içinde) uygulayacağız
            val isInitialLaunch = arguments?.getBoolean("is_initial_launch", false) ?: false
            val positionToRestore = vm.lastFocusedContentPosition

            if (positionToRestore != null && !isInitialLaunch) {
                handler.setPendingRestorePosition(positionToRestore)
            } else {
                handler.setPendingRestorePosition(null)
            }
        }

        /**
         * onPause lifecycle metodunda çağrılmalıdır.
         * Fragment replace edilmeden önce pozisyon ve kategori ID'yi kaydeder.
         */
        fun onPause() {
            val vm = viewModel ?: return

            // Fragment replace edilmeden önce hem kategoriyi hem de pozisyonu kaydet
            // onPause() çağrıldığında ViewModel hala mevcut olduğu için
            // değerleri kaydedebiliriz
            vm.lastFocusedContentPosition?.let { position ->
                savedLastFocusedPosition = position
            }
            vm.lastSelectedCategoryId?.let { categoryId ->
                savedLastSelectedCategoryId = categoryId
            }
            // Ayrıca currentSelectedCategoryId'yi de kaydet (eğer ViewModel'de yoksa)
            focusDelegate?.currentSelectedCategoryId?.let { categoryId ->
                if (savedLastSelectedCategoryId == null) {
                    savedLastSelectedCategoryId = categoryId
                    vm.lastSelectedCategoryId = categoryId
                }
            }
        }

        /**
         * onSaveInstanceState lifecycle metodunda çağrılmalıdır.
         * Sistem tarafından destroy edildiğinde pozisyonu kaydeder.
         */
        fun onSaveInstanceState(outState: Bundle) {
            viewModel?.lastFocusedContentPosition?.let { position ->
                outState.putInt(KEY_LAST_FOCUSED_CONTENT_POSITION, position)
            }
        }

        /**
         * Odak sahipsiz kaldığında (Back tuşuna kaçtığında) grid'e zorla geri çeker.
         */
        fun restoreFocusToGrid(position: Int) {
            val recyclerView = contentRecyclerView ?: return
            val adapter = contentAdapter ?: return

            if (position < 0 || position >= adapter.itemCount) {
                return
            }

            // Önce scroll yap
            val layoutManager = recyclerView.layoutManager as? GridLayoutManager
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(position, 200)
            } else {
                recyclerView.scrollToPosition(position)
            }

            // Sonra focus ver
            recyclerView.post {
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                if (viewHolder != null) {
                    viewHolder.itemView.requestFocus()
                } else {
                    // ViewHolder henüz hazır değilse, bir kez daha dene
                    recyclerView.postDelayed({
                        val retryViewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                        if (retryViewHolder != null) {
                            retryViewHolder.itemView.requestFocus()
                        }
                    }, 50)
                }
            }
        }

        /**
         * onDestroyView lifecycle metodunda çağrılmalıdır.
         * Lifecycle güvenliği: focusHandler referansını temizler.
         */
        fun onDestroyView() {
            // Lifecycle güvenliği: focusHandler referansını temizle
            // Fragment destroy olduktan sonra bellek sızıntısını önlemek için
            focusHandler?.cleanup()
            focusHandler = null
        }

        /**
         * savedLastSelectedCategoryId değişkenini temizler.
         * BrowseDataObserver tarafından çağrılır.
         */
        fun clearSavedLastSelectedCategoryId() {
            savedLastSelectedCategoryId = null
        }
    }
