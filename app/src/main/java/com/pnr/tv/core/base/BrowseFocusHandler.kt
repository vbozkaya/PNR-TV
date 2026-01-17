package com.pnr.tv.core.base

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.pnr.tv.ui.main.MainActivity

/**
 * BaseBrowseFragment içindeki back press navigasyon mantığını yöneten handler sınıfı.
 *
 * Bu sınıf, BaseBrowseFragment'tan back press yönetimi sorumluluklarını ayırarak
 * Fragment'ın daha temiz ve bakımı kolay olmasını sağlar.
 *
 * Sorumlulukları:
 * - Back tuşu callback setup
 * - Focus durumuna göre back press handling
 * - Fragment navigation (popBackStack)
 */
class BrowseFocusHandler(
    private val view: View?,
    private val activity: androidx.fragment.app.FragmentActivity?,
    private val contentRecyclerView: CustomContentRecyclerView,
    private val categoriesRecyclerView: CustomCategoriesRecyclerView,
    private val navbarView: View?,
    private val focusDelegate: BrowseFocusDelegate?,
    private val parentFragmentManager: FragmentManager,
    private val viewLifecycleOwner: LifecycleOwner,
    private val getNavbarTitle: () -> String,
    private val onBackPressedDispatcher: OnBackPressedDispatcher,
) {
    // Back tuşu callback'i - merkezi back tuşu yönetimi için
    private var backPressedCallback: OnBackPressedCallback? = null

    /**
     * Merkezi back tuşu yönetimi.
     * Focus durumuna göre karar verir:
     * - Focus içerik grid'deyse → kategorilere dön (fragment kapatma)
     * - Focus kategori listesindeyse → fragment kapat (ana sayfaya dön)
     * - Focus navbar'daysa → kategorilere dön veya fragment kapat
     */
    fun setup() {
        // Önceki callback'i kaldır (eğer varsa)
        backPressedCallback?.remove()

        backPressedCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPress()
                }
            }
        onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback!!)
    }

    /**
     * Back tuşu basıldığında çağrılır.
     * Focus durumuna göre uygun aksiyonu alır.
     */
    private fun handleBackPress() {
        // Mevcut focus'u kontrol et
        val currentFocus = view?.findFocus() ?: activity?.window?.currentFocus

        // Focus içerik grid'de mi?
        if (currentFocus != null) {
            // İçerik grid kontrolü: isViewInside ile hiyerarşiyi yukarıya doğru tara
            // CustomContentRecyclerView'a rastlanırsa kesinlikle true dön
            val isFocusInContentGrid = isViewInside(currentFocus, contentRecyclerView)
            if (isFocusInContentGrid) {
                // İçerik grid'deyse → kategorilere dön (fragment kapatma)
                focusDelegate?.navigateFocusToCategories()
                return
            }

            // Focus kategori listesinde mi?
            // isViewInside ile hiyerarşiyi yukarıya doğru tara
            // CustomCategoriesRecyclerView'a rastlanırsa kesinlikle true dön
            val isFocusInCategories = isViewInside(currentFocus, categoriesRecyclerView)
            if (isFocusInCategories) {
                backPressedCallback?.isEnabled = false

                // Butonları gizle
                (activity as? MainActivity)?.hideTopMenuButtons()

                parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                view?.post {
                    (activity as? MainActivity)?.requestFocusOnUpdateButton()
                }
                return
            }

            // Focus navbar'da mı?
            // isViewInside mantığını kullanarak hiyerarşiyi tara
            val isFocusInNavbar = isViewInside(currentFocus, navbarView)

            if (isFocusInNavbar) {
                backPressedCallback?.isEnabled = false

                // Butonları gizle
                (activity as? MainActivity)?.hideTopMenuButtons()

                parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                view?.post {
                    (activity as? MainActivity)?.requestFocusOnUpdateButton()
                }
                return
            }
        }

        // Diğer tüm durumlarda (veya focus null ise) varsayılan davranış olarak fragment'ı kapat.
        backPressedCallback?.isEnabled = false

        // Butonları gizle
        (activity as? MainActivity)?.hideTopMenuButtons()

        parentFragmentManager.popBackStack()
        view?.post {
            (activity as? MainActivity)?.requestFocusOnUpdateButton() // Garanti amaçlı.
        }
    }

    /**
     * View'un belirtilen target View içinde olup olmadığını kontrol eder.
     * Parent hiyerarşisini en tepeye kadar tarayarak %100 doğrulukla tespit eder.
     * Player/detay sayfasından döndükten sonra derin hiyerarşideki focus'u doğru algılar.
     *
     * Kontrol sırası:
     * 1. Reference equality kontrolü (en hızlı)
     * 2. Class type kontrolü (CustomContentRecyclerView veya CustomCategoriesRecyclerView)
     * 3. Parent hiyerarşisini en tepeye kadar tarama
     */
    private fun isViewInside(
        currentFocus: View?,
        target: View?,
    ): Boolean {
        if (currentFocus == null || target == null) {
            return false
        }

        // Target'ın tipini belirle
        val isTargetContentRecyclerView = target is CustomContentRecyclerView
        val isTargetCategoriesRecyclerView = target is CustomCategoriesRecyclerView

        var parent: android.view.ViewParent? = currentFocus.parent
        var depth = 0
        val maxDepth = 30 // Daha derin hiyerarşiler için artırıldı

        while (parent != null && depth < maxDepth) {
            // 1. Reference equality kontrolü (en güvenilir yöntem)
            if (parent === target) {
                return true
            }

            // 2. Class type kontrolü (CustomContentRecyclerView veya CustomCategoriesRecyclerView)
            if (parent is View) {
                when {
                    // ContentRecyclerView kontrolü
                    isTargetContentRecyclerView && parent is CustomContentRecyclerView -> {
                        return true
                    }
                    // CategoriesRecyclerView kontrolü
                    isTargetCategoriesRecyclerView && parent is CustomCategoriesRecyclerView -> {
                        return true
                    }
                }
            }

            parent = parent.parent
            depth++
        }

        return false
    }

    /**
     * Callback'i temizler (onDestroyView'da çağrılmalı).
     */
    fun cleanup() {
        backPressedCallback?.remove()
        backPressedCallback = null
    }
}
