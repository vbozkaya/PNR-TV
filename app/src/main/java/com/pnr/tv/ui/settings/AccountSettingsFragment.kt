package com.pnr.tv.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.R
import com.pnr.tv.core.constants.TimeConstants
import com.pnr.tv.databinding.DialogUserStatusBinding
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.premium.PremiumManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Hesap ayarları için Fragment.
 * Kullanıcı durumu butonu ve hesap detayları dialog'unu yönetir.
 */
@AndroidEntryPoint
class AccountSettingsFragment : Fragment() {
    private val viewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var premiumManager: PremiumManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_account_settings, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupUserStatusButton(view)
        observePremiumStatus()
        // observeUserData() kaldırıldı - hata mesajı sadece buton tıklandığında gösterilecek

        // İlk yüklemede kullanıcı bilgilerini çek (sessizce, hata göstermeden)
        viewModel.fetchUserInfo()
    }

    /**
     * User Status butonunu premium durumuna göre ayarlar
     */
    private fun setupUserStatusButton(view: View) {
        val userStatusButton = view.findViewById<View>(R.id.btn_user_status)

        userStatusButton?.setOnClickListener {
            lifecycleScope.launch {
                val isPremium = premiumManager.isPremium().first()
                if (isPremium) {
                    showUserStatusDialog()
                }
                // Premium değilse tıklama işlemi yapılmaz (pasif)
            }
        }
    }

    /**
     * Premium durumunu gözlemler ve buton durumunu günceller
     */
    private fun observePremiumStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            premiumManager.isPremium().collectLatest { isPremium ->
                updateUserStatusButtonState(isPremium)
            }
        }
    }

    /**
     * User Status butonunun aktif/pasif durumunu günceller
     */
    private fun updateUserStatusButtonState(isPremium: Boolean) {
        val userStatusButton = view?.findViewById<View>(R.id.btn_user_status)
        val premiumText = view?.findViewById<android.widget.TextView>(R.id.tv_user_status_premium)

        if (isPremium) {
            // Premium ise - buton aktif, Premium yazısı gizli
            userStatusButton?.isEnabled = true
            userStatusButton?.isFocusable = true
            userStatusButton?.isFocusableInTouchMode = true
            userStatusButton?.isClickable = true
            userStatusButton?.alpha = 1.0f
            premiumText?.visibility = android.view.View.GONE
        } else {
            // Premium değilse - buton pasif, Premium yazısı görünür
            userStatusButton?.isEnabled = false
            userStatusButton?.isFocusable = false
            userStatusButton?.isFocusableInTouchMode = false
            userStatusButton?.isClickable = false
            userStatusButton?.alpha = 0.5f // Pasif görünüm için alpha değeri
            premiumText?.visibility = android.view.View.VISIBLE
        }
    }

    /**
     * Hesap durumu diyalogunu gösterir
     */
    private fun showUserStatusDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_user_status, null)
        dialog.setContentView(view)

        // Dialog genişliğini ekran genişliğinin %40'ı olarak ayarla
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels
        val dialogWidthPx = (screenWidthPx * 0.4).toInt()

        // CardView'ın genişliğini dinamik olarak ayarla
        val cardView = view.findViewById<androidx.cardview.widget.CardView>(R.id.root)
        val layoutParams = cardView.layoutParams
        layoutParams.width = dialogWidthPx
        cardView.layoutParams = layoutParams

        val window = dialog.window
        // Dialog'u ortala ve sabit genişlik kullan (CardView kendi genişliğini kullanacak)
        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        // Arka planı şeffaf yap (CardView kendi arka planını kullanacak)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val binding = DialogUserStatusBinding.bind(view)

        // Dialog açıldığında önceki hata state'ini temizle (stale hataları göstermemek için)
        viewModel.clearError()

        // Dialog açıldığında yeniden bilgi çek
        viewModel.fetchUserInfo()

        // Kullanıcı bilgilerini gözlemle
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.tvCurrentUser.text = user.accountName
            } else {
                binding.tvCurrentUser.text = getString(R.string.user_status_not_available)
                Timber.w("Mevcut kullanıcı bulunamadı")
            }
        }

        viewModel.userInfoLiveData.observe(viewLifecycleOwner) { authResponse ->
            if (authResponse != null) {
                // extractUserInfo() metodu wrapper varsa onu, yoksa direkt alanları kullanır
                val userInfo = authResponse.extractUserInfo()

                if (userInfo == null) {
                    Timber.w("Kullanıcı bilgisi parse edilemedi - API response formatı beklenenle uyuşmuyor")
                    val unavailable = getString(R.string.data_unavailable)
                    binding.tvStatus.text = unavailable
                    binding.tvExpiryDate.text = unavailable
                    binding.tvConnection.text = unavailable
                    binding.tvTrial.text = unavailable
                    return@observe
                }

                // Durum
                val unavailable = getString(R.string.data_unavailable)
                val status = userInfo.status ?: unavailable
                binding.tvStatus.text = status

                // Bitiş Tarihi
                val expDate = userInfo.expDate
                val formattedDate =
                    if (!expDate.isNullOrEmpty() && expDate != "0") {
                        try {
                            val timestamp = expDate.toLong() * TimeConstants.TIMESTAMP_TO_MILLIS_MULTIPLIER
                            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                            dateFormat.format(Date(timestamp))
                        } catch (e: Exception) {
                            Timber.e(e, "Bitiş tarihi formatlanamadı: $expDate")
                            unavailable
                        }
                    } else {
                        unavailable
                    }
                binding.tvExpiryDate.text = formattedDate

                // Bağlantı
                val activeCons = userInfo.activeCons ?: "0"
                val maxConnections = userInfo.maxConnections ?: "0"
                binding.tvConnection.text = getString(R.string.connection_format, activeCons, maxConnections)

                // Trial
                val isTrial = userInfo.isTrial ?: "0"
                val trialText = if (isTrial == "1") getString(R.string.yes) else getString(R.string.no)
                binding.tvTrial.text = trialText
            } else {
                Timber.w("Kullanıcı bilgileri null - API çağrısı başarısız olabilir")
                val unavailable = getString(R.string.data_unavailable)
                binding.tvStatus.text = unavailable
                binding.tvExpiryDate.text = unavailable
                binding.tvConnection.text = unavailable
                binding.tvTrial.text = unavailable
            }
        }

        // Hata mesajlarını gözlemle - sadece dialog açıldığında ve kullanıcı bilgisi gerçekten yoksa göster
        // Dialog açıldıktan sonra fetchUserInfo() çağrıldığı için, bu observer sadece buton tıklandıktan sonra oluşan hataları gösterecek
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorMessage.collectLatest { errorMsg ->
                // Sadece dialog açıkken ve kullanıcı bilgisi gerçekten yoksa hata göster
                if (errorMsg != null && viewModel.userInfo.value == null && dialog.isShowing) {
                    requireContext().showCustomToast(errorMsg, Toast.LENGTH_LONG)
                }
            }
        }

        binding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // İlk focus'u Kapat butonuna ver
        binding.btnClose.requestFocus()

        dialog.show()
        
        // Dialog açıldıktan sonra bir kez kontrol et - eğer kullanıcı bilgisi yoksa ve hata varsa göster
        // fetchUserInfo() tamamlanmasını beklemek için kısa bir gecikme
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            if (dialog.isShowing && viewModel.userInfo.value == null && viewModel.errorMessage.value != null) {
                viewModel.errorMessage.value?.let { errorMsg ->
                    requireContext().showCustomToast(errorMsg, Toast.LENGTH_LONG)
                }
            }
        }
    }
}
