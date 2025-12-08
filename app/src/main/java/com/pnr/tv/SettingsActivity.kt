package com.pnr.tv

import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.databinding.DialogUserStatusBinding
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.ui.viewers.ViewersActivity
import com.pnr.tv.util.LocaleHelper
import com.pnr.tv.util.ViewerInitializer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class SettingsActivity : BaseActivity() {
    private val viewModel: MainViewModel by viewModels()
    
    @Inject
    lateinit var userRepository: UserRepository
    
    @Inject
    lateinit var viewerInitializer: ViewerInitializer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btn_user_status)?.setOnClickListener {
            showUserStatusDialog()
        }

        findViewById<View>(R.id.btn_app_language)?.setOnClickListener {
            showLanguageSelectionDialog()
        }

        findViewById<View>(R.id.btn_viewers)?.setOnClickListener {
            startActivity(Intent(this, ViewersActivity::class.java))
        }

        findViewById<View>(R.id.btn_clear_cache)?.setOnClickListener {
            showClearCacheDialog()
        }

        // Kullanıcı bilgilerini çek
        viewModel.fetchUserInfo()
        
        // İlk butona focus ver
        findViewById<View>(R.id.btn_user_status)?.requestFocus()
    }

    override fun getNavbarTitle(): String? {
        return getString(R.string.page_settings)
    }

    private fun showUserStatusDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_user_status, null)
        dialog.setContentView(view)

        val window = dialog.window
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val binding = DialogUserStatusBinding.bind(view)

        // Dialog açıldığında yeniden bilgi çek
        viewModel.fetchUserInfo()

        // Kullanıcı bilgilerini gözlemle
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                binding.tvCurrentUser.text = getString(R.string.current_user_label, user.accountName)
            } else {
                binding.tvCurrentUser.text = getString(R.string.current_user_none)
                Timber.w("Mevcut kullanıcı bulunamadı")
            }
        }

        viewModel.userInfo.observe(this) { authResponse ->
            if (authResponse != null) {
                // extractUserInfo() metodu wrapper varsa onu, yoksa direkt alanları kullanır
                val userInfo = authResponse.extractUserInfo()
                
                if (userInfo == null) {
                    Timber.w("Kullanıcı bilgisi parse edilemedi - API response formatı beklenenle uyuşmuyor")
                    val unavailable = getString(R.string.data_unavailable)
                    binding.tvStatus.text = getString(R.string.status_label, unavailable)
                    binding.tvExpiryDate.text = getString(R.string.expiry_date_label, unavailable)
                    binding.tvConnection.text = getString(R.string.connection_label, unavailable, unavailable)
                    binding.tvTrial.text = getString(R.string.trial_label, unavailable)
                    return@observe
                }

                // Durum
                val unavailable = getString(R.string.data_unavailable)
                val status = userInfo.status ?: unavailable
                binding.tvStatus.text = getString(R.string.status_label, status)

                // Bitiş Tarihi
                val expDate = userInfo.expDate
                val formattedDate =
                    if (!expDate.isNullOrEmpty() && expDate != "0") {
                        try {
                            val timestamp = expDate.toLong() * Constants.TIMESTAMP_TO_MILLIS_MULTIPLIER
                            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                            dateFormat.format(Date(timestamp))
                        } catch (e: Exception) {
                            Timber.e(e, "Bitiş tarihi formatlanamadı: $expDate")
                            unavailable
                        }
                    } else {
                        unavailable
                    }
                binding.tvExpiryDate.text = getString(R.string.expiry_date_label, formattedDate)

                // Bağlantı
                val activeCons = userInfo.activeCons ?: "0"
                val maxConnections = userInfo.maxConnections ?: "0"
                binding.tvConnection.text = getString(R.string.connection_label, activeCons, maxConnections)

                // Trial
                val isTrial = userInfo.isTrial ?: "0"
                val trialText = if (isTrial == "1") getString(R.string.yes) else getString(R.string.no)
                binding.tvTrial.text = getString(R.string.trial_label, trialText)
            } else {
                Timber.w("Kullanıcı bilgileri null - API çağrısı başarısız olabilir")
                val unavailable = getString(R.string.data_unavailable)
                binding.tvStatus.text = getString(R.string.status_label, unavailable)
                binding.tvExpiryDate.text = getString(R.string.expiry_date_label, unavailable)
                binding.tvConnection.text = getString(R.string.connection_label, unavailable, unavailable)
                binding.tvTrial.text = getString(R.string.trial_label, unavailable)
            }
        }

        // Hata mesajlarını gözlemle
        lifecycleScope.launch {
            viewModel.errorMessage.collectLatest { errorMsg ->
                if (errorMsg != null && viewModel.userInfo.value == null) {
                    Toast.makeText(this@SettingsActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // İlk focus'u Kapat butonuna ver
        binding.btnClose.requestFocus()

        dialog.show()
    }
    
    private fun showLanguageSelectionDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_language_selection, null)
        dialog.setContentView(view)

        val window = dialog.window
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val recyclerLanguages = view.findViewById<RecyclerView>(R.id.recycler_languages)
        val btnClose = view.findViewById<View>(R.id.btn_close)
        
        val languages = LocaleHelper.SupportedLanguage.values().toList()
        val currentLanguageCode = LocaleHelper.getSavedLanguage(this)
        
        val adapter = object : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {
            // Seçili dil adapter içinde tutuluyor
            private var selectedLanguageCode: String? = null
            
            fun getSelectedLanguageCode(): String? = selectedLanguageCode
            
            fun updateSelectedLanguage(newLanguageCode: String?) {
                val oldPosition = if (selectedLanguageCode != null) {
                    languages.indexOfFirst { it.code == selectedLanguageCode }
                } else {
                    -1
                }
                selectedLanguageCode = newLanguageCode
                val newPosition = if (selectedLanguageCode != null) {
                    languages.indexOfFirst { it.code == selectedLanguageCode }
                } else {
                    -1
                }
                
                // Eski ve yeni pozisyonları güncelle
                if (oldPosition >= 0) notifyItemChanged(oldPosition)
                if (newPosition >= 0) notifyItemChanged(newPosition)
            }
            
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): LanguageAdapter.LanguageViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false)
                return LanguageAdapter.LanguageViewHolder(view)
            }
            
            override fun onBindViewHolder(holder: LanguageAdapter.LanguageViewHolder, position: Int) {
                val language = languages[position]
                val isCurrentLanguage = language.code == currentLanguageCode
                // Adapter içindeki selectedLanguageCode'u kullan
                val isSelected = this.selectedLanguageCode != null && language.code == this.selectedLanguageCode
                
                // Dil adını göster
                val languageNameRes = when (language) {
                    LocaleHelper.SupportedLanguage.TURKISH -> R.string.language_turkish
                    LocaleHelper.SupportedLanguage.ENGLISH -> R.string.language_english
                    LocaleHelper.SupportedLanguage.SPANISH -> R.string.language_spanish
                    LocaleHelper.SupportedLanguage.INDONESIAN -> R.string.language_indonesian
                    LocaleHelper.SupportedLanguage.HINDI -> R.string.language_hindi
                    LocaleHelper.SupportedLanguage.PORTUGUESE -> R.string.language_portuguese
                    LocaleHelper.SupportedLanguage.FRENCH -> R.string.language_french
                }
                
                holder.textView.text = holder.itemView.context.getString(languageNameRes)
                
                // Seçili dil için görsel işaretleme (sadece kullanıcının seçtiği dil)
                if (isSelected) {
                    holder.textView.alpha = 1.0f
                    holder.textView.textSize = 20f // Seçili dil için daha büyük
                    holder.selectedIndicator.visibility = android.view.View.VISIBLE
                } else {
                    // Mevcut dil veya seçilmemiş dil
                    holder.textView.alpha = if (isCurrentLanguage) 0.9f else 0.7f
                    holder.textView.textSize = 18f // Normal boyut
                    holder.selectedIndicator.visibility = android.view.View.GONE
                }
                
                holder.itemView.setOnClickListener {
                    // Sadece seçili dili güncelle, dialog kapanmasın
                    // Eğer aynı dil tekrar seçilirse, seçimi kaldır
                    if (this.selectedLanguageCode == language.code) {
                        updateSelectedLanguage(null)
                    } else {
                        updateSelectedLanguage(language.code)
                    }
                }
            }
            
            override fun getItemCount() = languages.size
        }
        
        recyclerLanguages.layoutManager = LinearLayoutManager(this)
        recyclerLanguages.adapter = adapter
        
        btnClose.setOnClickListener {
            // Seçili dil varsa kaydet, yoksa mevcut dili koru
            val languageToSave = adapter.getSelectedLanguageCode() ?: currentLanguageCode
            LocaleHelper.saveLanguage(this, languageToSave)
            
            // Tüm uygulamayı yeniden başlat
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finishAffinity()
            
            dialog.dismiss()
        }
        
        // İlk focus'u ilk dil seçeneğine ver
        recyclerLanguages.post {
            val firstItem = recyclerLanguages.findViewHolderForAdapterPosition(0)
            firstItem?.itemView?.requestFocus() ?: btnClose.requestFocus()
        }
        
        dialog.show()
    }
    
    private class LanguageAdapter {
        class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView = itemView.findViewById<android.widget.TextView>(R.id.tv_language_name)
            val selectedIndicator = itemView.findViewById<android.widget.TextView>(R.id.tv_selected_indicator)
        }
    }

    private fun showClearCacheDialog() {
        val dialog = AlertDialog.Builder(this, R.style.FullscreenDialogTheme)
            .setTitle(R.string.dialog_clear_cache_title)
            .setMessage(R.string.dialog_clear_cache_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                lifecycleScope.launch {
                    try {
                        userRepository.clearAllData()
                        viewerInitializer.clearInitializationFlag()
                        Toast.makeText(this@SettingsActivity, getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
                        
                        // Ana sayfaya dön
                        val intent = Intent(this@SettingsActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(this@SettingsActivity, getString(R.string.error_with_message, e.message ?: getString(R.string.error_unknown)), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_no, null)
            .setCancelable(false)
            .create()
        
        dialog.show()
        // Güvenlik için "Hayır" butonuna focus ver
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
    }
}
