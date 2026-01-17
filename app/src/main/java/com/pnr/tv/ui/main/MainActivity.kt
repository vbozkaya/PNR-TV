package com.pnr.tv.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.R
import com.pnr.tv.core.base.BaseActivity
import com.pnr.tv.core.base.ToolbarController
import com.pnr.tv.databinding.ActivityMainBinding
import com.pnr.tv.premium.AdManager
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.util.ads.MainAdHelper
import com.pnr.tv.ui.main.MainNavigationHandler
import com.pnr.tv.ui.main.MainUpdateHandler
import com.pnr.tv.util.ViewerInitializer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity(), ToolbarController {
    override fun shouldSetupNavbar(): Boolean = false

    override fun shouldLoadBackground(): Boolean = false

    private lateinit var binding: ActivityMainBinding
    private val sharedViewModel: com.pnr.tv.ui.shared.SharedViewModel by viewModels()

    @Inject
    lateinit var viewerInitializer: ViewerInitializer

    @Inject
    lateinit var premiumManager: PremiumManager

    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var adHelper: MainAdHelper

    @Inject
    lateinit var updateHandler: MainUpdateHandler

    @Inject
    lateinit var navigationHandler: MainNavigationHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Overlay'in tüm touch event'lerini yakala - kullanıcı etkileşimini engelle
        setupOverlayTouchInterceptor()

        observeCurrentUser()

        // Navigation handler'ı başlat
        navigationHandler.setup(
            activity = this,
            binding = binding,
        )

        // Update handler'ı başlat
        updateHandler.setup(
            binding = binding,
            viewModel = sharedViewModel,
            lifecycleScope = lifecycleScope,
            fragmentManager = supportFragmentManager,
        )

        binding.btnUpdate.setOnClickListener {
            showInterstitialAdIfNeeded(
                targetButton = binding.btnUpdate,
            ) {
                // Reklam kapandıktan sonra veya reklam yoksa doğrudan güncelleme işlemini yap
                sharedViewModel.refreshAllContent()
            }
        }

        // Retry butonu için click listener
        binding.btnRetryError.setOnClickListener {
            sharedViewModel.refreshAllContent()
        }

        binding.btnUsers.setOnClickListener {
            val intent = Intent(this, com.pnr.tv.ui.users.UserManagementActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.btnExit.setOnClickListener {
            navigationHandler.showExitDialog()
        }

        // Set initial focus
        binding.btnUpdate.requestFocus()

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, MainFragment())
            }
        }

        // Initialize default viewer if needed
        lifecycleScope.launch {
            viewerInitializer.initializeIfNeeded()
        }

        // Banner reklamı yükle ve premium durumuna göre göster/gizle
        adHelper.setupBannerAd(lifecycleScope, binding.adViewBanner)

        // Interstitial reklamı arka planda önceden yükle
        adManager.preloadInterstitialAd()
    }

    override fun onPause() {
        super.onPause()
        adHelper.safePauseAdView(binding.adViewBanner)
    }

    override fun onDestroy() {
        adHelper.safeDestroyAdView(binding.adViewBanner)
        super.onDestroy()
    }

    /**
     * Overlay'in tüm touch event'lerini yakalar ve arka plandaki etkileşimleri engeller.
     */
    private fun setupOverlayTouchInterceptor() {
        binding.loadingOverlay.setOnTouchListener { _, _ ->
            // Overlay gösterilirken tüm touch event'lerini yakala ve işleme
            // Bu sayede arka plandaki view'lar tıklanamaz
            true
        }
    }

    override fun onResume() {
        super.onResume()

        // Navigation handler'ın back callback ve focus yönetimini yap
        navigationHandler.handleResumeBackCallback()
        navigationHandler.handleResumeFocus()

        // Banner reklamı resume et (premium değilse)
        adHelper.safeResumeAdView(lifecycleScope, binding.adViewBanner)
    }

    private fun observeCurrentUser() {
        sharedViewModel.currentUser.observe(this) { user ->
            if (user != null) {
                // Kullanıcı adını hemen göster
                binding.tvCurrentUser.text = getString(R.string.current_user_label, user.accountName)
            } else {
                binding.tvCurrentUser.text = getString(R.string.current_user_none)
            }
        }
    }

    override fun showTopMenu() {
        navigationHandler.showTopMenu()
    }

    override fun hideTopMenu() {
        navigationHandler.hideTopMenu()
    }

    /**
     * Geçiş animasyonu için overlay'i gösterir (siyah perde).
     */
    fun showTransitionOverlay() {
        binding.loadingOverlay.alpha = 1f
        binding.loadingOverlay.visibility = android.view.View.VISIBLE
        // Loading mesajını gizle, sadece siyah perde göster
        binding.txtLoadingMessage.visibility = android.view.View.GONE
        binding.txtAddedContent.visibility = android.view.View.GONE
        binding.btnRetryError.visibility = android.view.View.GONE
    }

    /**
     * Geçiş animasyonu için overlay'i fade_out ile kaldırır.
     */
    fun hideTransitionOverlay() {
        binding.loadingOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.loadingOverlay.visibility = android.view.View.GONE
            }
            .start()
    }

    /**
     * Üst menü butonlarını alpha animasyonuyla gizler.
     * Fragment'lar tarafından çağrılabilir.
     */
    fun hideTopMenuButtons() {
        navigationHandler.hideTopMenuButtons()
    }

    /**
     * Üst menü butonlarını alpha animasyonuyla gösterir.
     * Fragment'lar tarafından çağrılabilir.
     */
    fun showTopMenuButtons() {
        navigationHandler.showTopMenuButtons()
    }

    /**
     * Üst menü butonlarından birine focus verilmiş mi kontrol eder.
     * Fragment'lar tarafından çağrılabilir.
     */
    fun isTopMenuButtonFocused(): Boolean {
        return navigationHandler.isTopMenuButtonFocused()
    }

    /**
     * Güncelle butonuna odak verir. Fragment'lar tarafından çağrılabilir.
     */
    fun requestFocusOnUpdateButton() {
        binding.btnUpdate.requestFocus()
    }

    /**
     * Veri yoksa gösterilecek uyarı mesajı.
     * Fragment'lar tarafından çağrılır.
     * @param message Gösterilecek mesaj (varsayılan: data_required_warning)
     */
    fun showDataRequiredWarning(message: String? = null) {
        updateHandler.showDataRequiredWarning(message)
    }

    /**
     * Veri yoksa gösterilen uyarı mesajını gizler.
     */
    fun hideDataRequiredWarning() {
        updateHandler.hideDataRequiredWarning()
    }

    /**
     * Kullanıcı yoksa gösterilecek uyarı mesajı.
     * Fragment'lar tarafından çağrılır.
     */
    fun showUserRequiredWarning() {
        updateHandler.showUserRequiredWarning()
    }

    /**
     * Kullanıcı yoksa gösterilen uyarı mesajını gizler.
     */
    fun hideUserRequiredWarning() {
        updateHandler.hideUserRequiredWarning()
    }

    /**
     * Interstitial reklamı gösterir (eğer yüklüyse) ve reklam kapandıktan sonra
     * veya reklam yoksa doğrudan işlemi yapar.
     * TV için focus yönetimi ile kullanıcı deneyimini korur.
     *
     * @param targetButton Reklam kapandıktan sonra focus verilecek buton
     * @param onContinue Reklam kapandıktan sonra veya reklam yoksa çalıştırılacak callback
     */
    fun showInterstitialAdIfNeeded(
        targetButton: android.view.View? = null,
        onContinue: () -> Unit,
    ) {
        try {
            // Odaklanılan butonu kaydet (reklam kapandıktan sonra geri vermek için)
            val buttonToRestoreFocus = targetButton ?: window?.currentFocus

            val adShown =
                adManager.showInterstitialAd(
                    activity = this,
                    onAdClosed = {
                        // Reklam kapandıktan sonra focus'u geri ver
                        if (buttonToRestoreFocus != null && !isDestroyed && !isFinishing) {
                            try {
                                // Kısa bir gecikme ile focus'u geri ver (reklam animasyonu bitene kadar bekle)
                                binding.root.postDelayed({
                                    if (!isDestroyed && !isFinishing) {
                                        buttonToRestoreFocus.requestFocus()
                                    }
                                }, 100)
                            } catch (e: Exception) {
                                Timber.w(e, "Focus geri verilirken hata")
                            }
                        }
                        // Reklam kapandıktan sonra asıl işlemi yap
                        onContinue()
                    },
                )

            // Reklam gösterilmediyse (yüklenmemiş veya premium kullanıcı) doğrudan devam et
            if (!adShown) {
                onContinue()
            }
        } catch (e: Exception) {
            Timber.e(e, "Interstitial reklam gösterilirken hata")
            // Hata durumunda kullanıcı deneyimini bozmadan devam et
            onContinue()
        }
    }
}
