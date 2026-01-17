package com.pnr.tv.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pnr.tv.R
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.premium.BillingConnectionHandler
import com.pnr.tv.premium.BillingManager
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.ui.viewers.ViewersActivity
import com.pnr.tv.util.validation.AdultContentPreferenceManager
import com.pnr.tv.util.CrashlyticsHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Premium ayarlar için Fragment.
 * Viewers, Premium ve Adult Content butonlarını yönetir.
 */
@AndroidEntryPoint
class PremiumSettingsFragment : Fragment() {
    @Inject
    lateinit var premiumManager: PremiumManager

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var adultContentPreferenceManager: AdultContentPreferenceManager

    @Inject
    lateinit var rewardedAdManager: com.pnr.tv.premium.RewardedAdManager

    @Inject
    lateinit var premiumPurchaseHandler: PremiumPurchaseHandler

    @Inject
    lateinit var premiumAdHandler: PremiumAdHandler

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_premium_settings, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupViewersButton(view)
        setupPremiumToggle(view)
        setupAdultContentToggle(view)

        observePremiumStatusAndUpdateButtons(view)
        preloadRewardedAdIfNeeded()
        observePurchaseFlowErrors()
    }

    override fun onResume() {
        super.onResume()
        // Google Play penceresi kapandığında (ITEM_ALREADY_OWNED durumunda) premium durumunu güncelle
        // BillingManager bağlı olduğunda satın alımları sorgula
        viewLifecycleOwner.lifecycleScope.launch {
            val connectionState = billingManager.billingConnectionState.value
            if (connectionState == BillingConnectionHandler.BillingConnectionState.CONNECTED) {
                Timber.d("PremiumSettingsFragment.onResume - BillingClient bağlı, satın alımlar sorgulanıyor")

                // Premium durumunu kontrol et - eğer hala premium değilse mutlaka queryPurchases çağır
                val isPremium = premiumManager.isPremium().first()
                if (!isPremium) {
                    Timber.d("PremiumSettingsFragment.onResume - Premium değil, queryPurchases zorla çağrılıyor")
                    billingManager.queryPurchases(isRestore = true, showRestoreToast = false)
                } else {
                    Timber.d("PremiumSettingsFragment.onResume - Premium zaten aktif, queryPurchases atlanıyor")
                }
            }
        }

        // Geçici adult filtresi erişim kontrolü - 15 dakikalık süre dolduğunda otomatik kapat
        viewLifecycleOwner.lifecycleScope.launch {
            val isPremium = premiumManager.isPremium().first()
            if (!isPremium) {
                val temporaryAccessUntil = adultContentPreferenceManager.getTemporaryAdultAccessUntil().first()
                val currentTime = System.currentTimeMillis()
                val hasTemporaryAccess = temporaryAccessUntil != null && currentTime < temporaryAccessUntil

                if (!hasTemporaryAccess) {
                    // Geçici erişim süresi dolmuş - adult content'i kapat
                    val isCurrentlyEnabled = adultContentPreferenceManager.isAdultContentEnabled().first()
                    if (isCurrentlyEnabled) {
                        Timber.d("PremiumSettingsFragment.onResume - Geçici erişim süresi dolmuş, adult content kapatılıyor")
                        adultContentPreferenceManager.setAdultContentEnabled(false)
                    }
                }
            }
        }
    }

    /**
     * Premium durumunu gözlemler ve butonların aktif/pasif durumunu günceller.
     */
    private fun observePremiumStatusAndUpdateButtons(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            premiumManager.isPremium().collectLatest { isPremium ->
                updateViewersButtonState(view, isPremium)
                updateAdultContentButtonState(view, isPremium)
            }
        }
    }

    /**
     * Premium olmayan kullanıcılar için ödüllü reklamı arka planda önceden yükler.
     */
    private fun preloadRewardedAdIfNeeded() {
        viewLifecycleOwner.lifecycleScope.launch {
            val isPremium = premiumManager.isPremium().first()
            if (!isPremium) {
                rewardedAdManager.loadRewardedAd()
            }
        }
    }

    /**
     * BillingManager'dan gelen satın alma akışı hata mesajlarını dinler ve gösterir.
     */
    private fun observePurchaseFlowErrors() {
        viewLifecycleOwner.lifecycleScope.launch {
            billingManager.purchaseFlowError.collectLatest { errorMessage ->
                requireContext().showCustomToast(errorMessage, Toast.LENGTH_LONG)
                CrashlyticsHelper.log("Premium button: Purchase flow error - $errorMessage", "ERROR")
                FirebaseCrashlytics.getInstance().sendUnsentReports()
            }
        }
    }

    /**
     * Viewers butonunu premium durumuna göre ayarlar
     */
    private fun setupViewersButton(view: View) {
        val viewersButton = view.findViewById<View>(R.id.btn_viewers)

        viewersButton?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isPremium = premiumManager.isPremium().first()
                if (isPremium) {
                    startActivity(Intent(requireContext(), ViewersActivity::class.java))
                }
                // Premium değilse tıklama işlemi yapılmaz (pasif)
            }
        }
    }

    /**
     * Premium buton setup - Google Play Billing entegrasyonu
     */
    private fun setupPremiumToggle(view: View) {
        val premiumLayout = view.findViewById<View>(R.id.btn_premium)
        val premiumStatus = view.findViewById<android.widget.TextView>(R.id.tv_premium_status)

        // Mevcut durumu gözlemle ve güncelle
        viewLifecycleOwner.lifecycleScope.launch {
            premiumManager.isPremium().collectLatest { isPremium ->
                premiumStatus?.text =
                    if (isPremium) {
                        getString(R.string.settings_premium_on)
                    } else {
                        getString(R.string.settings_premium_off)
                    }
            }
        }

        // Premium butonuna tıklandığında restore veya satın alma işlemini başlat
        premiumLayout?.setOnClickListener {
            Timber.d("Premium butonuna tıklandı")

            // Firebase'e premium buton basıldı logunu gönder
            viewLifecycleOwner.lifecycleScope.launch {
                val isPremium = premiumManager.isPremium().first()
                val connectionState = billingManager.billingConnectionState.value

                // Crashlytics'e log gönder
                CrashlyticsHelper.setCustomKey("premium_button_clicked_timestamp", System.currentTimeMillis())
                CrashlyticsHelper.setCustomKey("premium_button_clicked_premium_status", isPremium)
                CrashlyticsHelper.setCustomKey("premium_button_clicked_connection_state", connectionState.toString())
                CrashlyticsHelper.log("Premium butonuna tıklandı - Premium: $isPremium, Connection: $connectionState", "INFO")

                // Logları hemen gönder
                FirebaseCrashlytics.getInstance().sendUnsentReports()

                Timber.d("Premium durumu: $isPremium")

                if (isPremium) {
                    // Zaten premium ise bilgi mesajı göster
                    Timber.d("Kullanıcı zaten premium")
                    requireContext().showCustomToast(
                        getString(R.string.settings_premium_on),
                        Toast.LENGTH_SHORT,
                    )
                } else {
                    // Premium değilse handler üzerinden satın alma işlemini başlat
                    Timber.d("BillingManager bağlantı durumu: $connectionState")
                    premiumPurchaseHandler.handlePurchase(
                        activity = requireActivity() as Activity,
                        context = requireContext(),
                        lifecycleScope = viewLifecycleOwner.lifecycleScope,
                        statusCallback =
                            object : PremiumPurchaseHandler.StatusUpdateCallback {
                                override fun updateStatus(text: String) {
                                    premiumStatus?.text = text
                                }
                            },
                    )
                }
            }
        }
    }

    /**
     * Adult Content toggle setup
     */
    private fun setupAdultContentToggle(view: View) {
        val adultContentLayout = view.findViewById<View>(R.id.btn_adult_content)
        val adultContentStatus = view.findViewById<android.widget.TextView>(R.id.tv_adult_content_status)

        // Mevcut durumu gözlemle ve güncelle
        viewLifecycleOwner.lifecycleScope.launch {
            adultContentPreferenceManager.isAdultContentEnabled().collectLatest { isEnabled ->
                adultContentStatus?.text =
                    if (isEnabled) {
                        getString(R.string.settings_adult_content_on)
                    } else {
                        getString(R.string.settings_adult_content_off)
                    }
            }
        }

        // Toggle işlevi - premium kontrolü ve reklam akışı ile
        adultContentLayout?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isPremium = premiumManager.isPremium().first()
                if (isPremium) {
                    // Premium kullanıcılar için normal toggle
                    val currentValue = adultContentPreferenceManager.isAdultContentEnabled().first()
                    adultContentPreferenceManager.setAdultContentEnabled(!currentValue)
                } else {
                    // Premium olmayan kullanıcılar için reklam akışı
                    val temporaryAccessUntil = adultContentPreferenceManager.getTemporaryAdultAccessUntil().first()
                    val currentTime = System.currentTimeMillis()
                    val hasTemporaryAccess = temporaryAccessUntil != null && currentTime < temporaryAccessUntil

                    if (!hasTemporaryAccess) {
                        // Geçici erişim süresi dolmuş, reklam diyalogu göster
                        premiumAdHandler.showRewardedAdDialog(
                            context = requireContext(),
                            activity = requireActivity(),
                            lifecycleScope = viewLifecycleOwner.lifecycleScope,
                            focusedView = adultContentLayout,
                        )
                    } else {
                        // Geçici erişim hala geçerli, toggle yapma (sadece reklam izleyerek erişim kazanılabilir)
                        // Kullanıcı zaten erişim hakkına sahip, ancak toggle yapamaz
                    }
                }
            }
        }
    }

    /**
     * Viewers butonunun aktif/pasif durumunu günceller
     */
    private fun updateViewersButtonState(
        view: View,
        isPremium: Boolean,
    ) {
        val viewersButton = view.findViewById<View>(R.id.btn_viewers)
        val premiumText = view.findViewById<android.widget.TextView>(R.id.tv_viewers_premium)

        if (isPremium) {
            // Premium ise - buton aktif, Premium yazısı gizli
            viewersButton?.isEnabled = true
            viewersButton?.isFocusable = true
            viewersButton?.isFocusableInTouchMode = true
            viewersButton?.isClickable = true
            viewersButton?.alpha = 1.0f
            premiumText?.visibility = android.view.View.GONE
        } else {
            // Premium değilse - buton pasif, Premium yazısı görünür
            viewersButton?.isEnabled = false
            viewersButton?.isFocusable = false
            viewersButton?.isFocusableInTouchMode = false
            viewersButton?.isClickable = false
            viewersButton?.alpha = 0.5f
            premiumText?.visibility = android.view.View.VISIBLE
        }
    }

    /**
     * Adult Content butonunun aktif/pasif durumunu günceller
     * Premium olmayan kullanıcılar reklam izleyebilir, bu yüzden buton aktif kalır
     */
    private fun updateAdultContentButtonState(
        view: View,
        isPremium: Boolean,
    ) {
        val adultContentButton = view.findViewById<View>(R.id.btn_adult_content)
        val premiumText = view.findViewById<android.widget.TextView>(R.id.tv_adult_content_premium)

        if (isPremium) {
            // Premium ise - buton aktif, Premium yazısı gizli
            adultContentButton?.isEnabled = true
            adultContentButton?.isFocusable = true
            adultContentButton?.isFocusableInTouchMode = true
            adultContentButton?.isClickable = true
            adultContentButton?.alpha = 1.0f
            premiumText?.visibility = android.view.View.GONE
        } else {
            // Premium değilse - buton aktif (reklam izleyebilir), Premium yazısı görünür
            adultContentButton?.isEnabled = true
            adultContentButton?.isFocusable = true
            adultContentButton?.isFocusableInTouchMode = true
            adultContentButton?.isClickable = true
            adultContentButton?.alpha = 1.0f
            premiumText?.visibility = android.view.View.VISIBLE
        }
    }
}
