package com.pnr.tv.core.base

import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.R
import com.pnr.tv.premium.PremiumManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Navbar yönetimini BaseBrowseFragment'tan ayıran handler sınıfı.
 * Navbar setup, premium kontrolleri ve focus yönetimi bu sınıfta yapılır.
 */
class BrowseNavbarHandler(
    private val navbarView: View?,
    private val premiumManager: PremiumManager,
    private val lifecycleOwner: LifecycleOwner,
    private val getNavbarTitle: () -> String,
    private val onNavigateToFirstCategory: () -> Unit,
    private val onNavigateToEmptyState: () -> Unit,
    private val onNavigateToContent: () -> Unit,
    private val isContentRecyclerViewVisible: () -> Boolean,
    private val isEmptyStateTextViewVisible: () -> Boolean,
    private val parentFragmentManager: FragmentManager,
    private val shouldShowPremiumText: () -> Boolean,
    private val setupFilterButton: () -> Unit,
) {
    // Navbar view'larının orijinal focusable durumları (focus başarılı olduğunda geri yüklemek için)
    private var navbarBackButtonWasFocusable: Boolean = false
    private var navbarHomeButtonWasFocusable: Boolean = false
    private var navbarTitleTextViewWasFocusable: Boolean = false
    private var navbarFilterButtonWasFocusable: Boolean = false
    private var navbarSearchEditTextWasFocusable: Boolean = false

    // Navbar restore işleminin sadece bir kez yapılması için flag
    private var navbarRestored: Boolean = false

    /**
     * Navbar'ı setup eder.
     */
    fun setup(view: View) {
        val titleTextView = navbarView?.findViewById<TextView>(R.id.txt_navbar_title)
        titleTextView?.text = getNavbarTitle()

        val backButton = navbarView?.findViewById<View>(R.id.btn_navbar_back)
        backButton?.setOnClickListener {
            // Back tuşu artık BaseBrowseFragment'ta merkezi olarak yönetiliyor
            // Sistem back tuşu handler'ını tetikle (merkezi mantık çalışacak)
            val activity = backButton.context
            if (activity is ComponentActivity) {
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }

        val homeButton = navbarView?.findViewById<View>(R.id.btn_navbar_home)
        homeButton?.setOnClickListener {
            parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        // Navbar -> Kategori geçişi için listener (sadece back button için)
        val navbarDownListener =
            View.OnKeyListener { view, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    onNavigateToFirstCategory()
                    return@OnKeyListener true // Olayı Tüket!
                }
                false
            }

        backButton?.setOnKeyListener(navbarDownListener)

        // Listener for Home Button (manages DOWN key)
        homeButton?.setOnKeyListener { view, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Aşağı tuşu: İlk kategoriye odaklan ve olayı tüket.
                        onNavigateToFirstCategory()
                        return@setOnKeyListener true
                    }
                }
            }
            // Diğer tüm tuşlar için varsayılan davranışa izin ver (sağa basıldığında arama çubuğuna geçiş yapılabilir).
            false
        }

        // Listener for Search Bar (manages DOWN key for grid navigation)
        val searchEditText = navbarView?.findViewById<View>(R.id.edt_navbar_search)
        searchEditText?.setOnKeyListener { view, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Aşağı tuşu: Eğer içerik listesi boşsa ve empty state görünürse, focus'u empty state'e taşı
                        if (isEmptyStateTextViewVisible() && !isContentRecyclerViewVisible()) {
                            onNavigateToEmptyState()
                            return@setOnKeyListener true
                        }
                        // Pasifize edildi: Android'in doğal Focus Search mekanizmasına bırakıldı
                        // nextFocusDown attribute'ları layout dosyalarında tanımlı
                    }
                }
            }
            // Sol tuşu ve diğer tuşlar için varsayılan davranışa izin ver (sola basıldığında Home butonuna geçiş yapılabilir).
            false
        }

        // Listener for Filter Button (manages DOWN key for grid navigation)
        val filterButton = navbarView?.findViewById<View>(R.id.btn_navbar_filter)
        filterButton?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Pasifize edildi: Android'in doğal Focus Search mekanizmasına bırakıldı
                        // nextFocusDown attribute'ları layout dosyalarında tanımlı
                        // if (isContentRecyclerViewVisible()) {
                        //     onNavigateToContent()
                        //     return@setOnKeyListener true
                        // }
                    }
                }
            }
            false
        }

        // Filter butonunu setup et
        setupFilterButton()

        // Premium durumuna göre navbar butonlarını ayarla
        setupPremiumControls()

        // Navbar view'larını focusable yap
        enableNavbarFocusability()
    }

    /**
     * Navbar'daki arama ve sort butonlarını premium durumuna göre ayarlar
     *
     * Cold start sırasında (navbarRestored == false) view'ların isFocusable property'lerini
     * değiştirmez, sadece backing field'ları günceller. Bu, initial focus jump'ı önler.
     * Runtime update'lerde (navbarRestored == true) direkt view'ları günceller.
     * Navbar elementleri her zaman focusable'dır.
     */
    private fun setupPremiumControls() {
        val searchEditText = navbarView?.findViewById<android.widget.EditText>(R.id.edt_navbar_search)
        val filterButton = navbarView?.findViewById<View>(R.id.btn_navbar_filter)
        val premiumText = navbarView?.findViewById<TextView>(R.id.tv_navbar_premium)

        // Premium kontrolü tamamlanana kadar navbar'ı gizle (alpha = 0f)
        navbarView?.alpha = 0f

        // Premium yazısının gösterilip gösterilmeyeceğini kontrol et
        val showPremiumText = shouldShowPremiumText()

        // Premium durumunu gözlemle ve butonları güncelle
        lifecycleOwner.lifecycleScope.launch {
            premiumManager.isPremium().collectLatest { isPremium ->
                val isColdStart = !navbarRestored

                // Visibility değişimlerini post bloğuna al
                navbarView?.post {
                    if (isPremium) {
                        // Premium ise - butonlar aktif, Premium yazısı gizli
                        searchEditText?.isEnabled = true
                        filterButton?.isEnabled = true
                        filterButton?.isClickable = true
                        searchEditText?.alpha = 1.0f
                        filterButton?.alpha = 1.0f
                        premiumText?.visibility = View.GONE

                        // Navbar elementlerini focusable yap
                        if (isColdStart) {
                            // Cold start: Sadece backing field'ları güncelle, view'ları değiştirme
                            navbarSearchEditTextWasFocusable = true
                            navbarFilterButtonWasFocusable = true
                        } else {
                            // Runtime update: Focusable yap
                            searchEditText?.isFocusable = true
                            searchEditText?.isFocusableInTouchMode = true
                            filterButton?.isFocusable = true
                            filterButton?.isFocusableInTouchMode = true
                        }
                    } else {
                        // Premium değilse - butonlar pasif ama yine de focusable (görsel geri bildirim için)
                        searchEditText?.isEnabled = false
                        filterButton?.isEnabled = false
                        filterButton?.isClickable = false
                        searchEditText?.alpha = 0.5f
                        filterButton?.alpha = 0.5f
                        premiumText?.visibility = if (showPremiumText) View.VISIBLE else View.GONE

                        // Navbar elementlerini focusable yap (premium olmasa bile)
                        if (isColdStart) {
                            // Cold start: Sadece backing field'ları güncelle, view'ları değiştirme
                            navbarSearchEditTextWasFocusable = true
                            navbarFilterButtonWasFocusable = true
                        } else {
                            // Runtime update: Focusable yap
                            searchEditText?.isFocusable = true
                            searchEditText?.isFocusableInTouchMode = true
                            filterButton?.isFocusable = true
                            filterButton?.isFocusableInTouchMode = true
                        }
                    }

                    // Tüm kontroller bittikten sonra navbar'ı yumuşakça göster
                    navbarView?.animate()?.alpha(1f)?.setDuration(300)?.start()
                }
            }
        }
    }

    /**
     * Navbar view'larını focusable yapar (kullanıcı erişebilsin).
     */
    fun enableNavbarFocusability() {
        val backButton = navbarView?.findViewById<View>(R.id.btn_navbar_back)
        val homeButton = navbarView?.findViewById<View>(R.id.btn_navbar_home)
        val titleTextView = navbarView?.findViewById<TextView>(R.id.txt_navbar_title)
        val filterButton = navbarView?.findViewById<View>(R.id.btn_navbar_filter)
        val searchEditText = navbarView?.findViewById<android.widget.EditText>(R.id.edt_navbar_search)

        // Orijinal durumları kaydet
        navbarBackButtonWasFocusable = backButton?.isFocusable ?: false
        navbarHomeButtonWasFocusable = homeButton?.isFocusable ?: false
        navbarTitleTextViewWasFocusable = titleTextView?.isFocusable ?: false
        navbarFilterButtonWasFocusable = filterButton?.isFocusable ?: false
        navbarSearchEditTextWasFocusable = searchEditText?.isFocusable ?: false

        // Restore flag'ini ayarla
        navbarRestored = true

        // Tüm navbar elementlerini focusable yap
        backButton?.isFocusable = true
        homeButton?.isFocusable = true
        titleTextView?.isFocusable = true
        filterButton?.isFocusable = true
        searchEditText?.isFocusable = true
        backButton?.isFocusableInTouchMode = true
        homeButton?.isFocusableInTouchMode = true
        titleTextView?.isFocusableInTouchMode = true
        filterButton?.isFocusableInTouchMode = true
        searchEditText?.isFocusableInTouchMode = true
    }

    /**
     * Navbar view'larını tekrar focusable yapar (kullanıcı erişebilsin).
     * enableNavbarFocusability() ile aynı işlevi görür.
     */
    fun restoreNavbarFocusability() {
        enableNavbarFocusability()
    }
}
