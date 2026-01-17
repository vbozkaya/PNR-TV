package com.pnr.tv.ui.users

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.core.base.BaseActivity
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.databinding.ActivityAddUserBinding
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.premium.AdManager
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.security.DataEncryption
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AddUserActivity : BaseActivity() {
    private lateinit var binding: ActivityAddUserBinding
    private val viewModel: AddUserViewModel by viewModels()
    private var originalUser: UserAccountEntity? = null

    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var premiumManager: PremiumManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        }

        binding = ActivityAddUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInputFields()

        // Parcelable kullanımı - API 33+ için tür güvenli versiyon
        originalUser =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_EDIT_USER, UserAccountEntity::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_EDIT_USER)
            }
        originalUser?.let {
            binding.etAccountName.setText(it.accountName)
            binding.etUsername.setText(it.username)
            // Şifrelenmiş password ve DNS'i çöz
            val decryptedPassword = DataEncryption.decryptSensitiveData(it.password, this)
            val decryptedDns = DataEncryption.decryptSensitiveData(it.dns, this)
            binding.etPassword.setText(decryptedPassword)
            binding.etDns.setText(decryptedDns)
            binding.btnSaveUser.text = getString(R.string.btn_update)
        } ?: run {
            binding.btnSaveUser.text = getString(R.string.btn_save)
        }

        binding.btnSaveUser.setOnClickListener {
            saveUser()
        }

        // ViewModel state'lerini dinle
        observeViewModelState()

        // Banner reklamı setup et
        setupBannerAd(adManager, premiumManager)
    }

    private fun observeViewModelState() {
        // Loading state
        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.progressBar.isVisible = isLoading
                binding.btnSaveUser.isEnabled = !isLoading

                // Loading sırasında tüm input alanlarını pasif yap
                binding.etAccountName.isEnabled = !isLoading
                binding.etUsername.isEnabled = !isLoading
                binding.etPassword.isEnabled = !isLoading
                binding.etDns.isEnabled = !isLoading
            }
        }

        // Error message
        lifecycleScope.launch {
            viewModel.errorMessage.collectLatest { errorMessage ->
                if (errorMessage != null) {
                    // Hata mesajını uzun süre göster (kullanıcı görebilsin)
                    showCustomToast(errorMessage, Toast.LENGTH_LONG)
                    // Error mesajını 3 saniye sonra temizle (kullanıcı görebilsin)
                    delay(3000)
                    viewModel.clearError()
                }
            }
        }

        // Success state - navigation için (observeViewModelState içinde)
        lifecycleScope.launch {
            viewModel.isSuccess.collectLatest { isSuccess ->
                if (isSuccess) {
                    val message =
                        if (originalUser != null) {
                            getString(R.string.toast_user_updated)
                        } else {
                            getString(R.string.toast_user_saved)
                        }
                    // Başarı mesajını uzun süre göster
                    showCustomToast(message, Toast.LENGTH_LONG)

                    // Kısa bir gecikme sonrası navigation yap (kullanıcı mesajı görebilsin)
                    delay(500)

                    val intent = Intent(this@AddUserActivity, UsersListActivity::class.java)
                    startActivity(intent)
                    finish()
                    viewModel.clearSuccess()
                }
            }
        }
    }

    private fun saveUser() {
        val accountName = binding.etAccountName.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val dns = binding.etDns.text.toString().trim()

        if (accountName.isBlank() || username.isBlank() || password.isBlank() || dns.isBlank()) {
            Timber.tag("ADD_USER_FLOW").w("[Activity] Form validasyonu başarısız - boş alanlar var")
            showCustomToast(getString(R.string.error_fill_fields))
            return
        }

        lifecycleScope.launch {
            // Password ve DNS'i şifrele
            val encryptedPassword = DataEncryption.encryptSensitiveData(password, this@AddUserActivity)
            val encryptedDns = DataEncryption.encryptSensitiveData(dns, this@AddUserActivity)

            originalUser?.let {
                // Güncelleme modu - önce sunucu doğrulaması yap
                val updatedUser =
                    it.copy(
                        accountName = accountName,
                        username = username,
                        password = encryptedPassword,
                        dns = encryptedDns,
                    )
                // ViewModel'e doğrulama için şifrelenmemiş bilgileri gönder
                viewModel.updateUser(
                    user = updatedUser,
                    dns = dns,
                    username = username,
                    password = password,
                )
            } ?: run {
                // Yeni kullanıcı ekleme modu - önce sunucu doğrulaması yap
                val newUser =
                    UserAccountEntity(
                        accountName = accountName,
                        username = username,
                        password = encryptedPassword,
                        dns = encryptedDns,
                    )

                // ViewModel'e doğrulama için şifrelenmemiş bilgileri gönder
                viewModel.addUser(
                    user = newUser,
                    dns = dns,
                    username = username,
                    password = password,
                )
            }
        }
    }

    private fun setupInputFields() {
        val editTexts = listOf(binding.etAccountName, binding.etUsername, binding.etPassword, binding.etDns)
        editTexts.forEach { editText ->
            editText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.postDelayed({ showKeyboard(view) }, UIConstants.DelayDurations.KEYBOARD_SHOW_DELAY)
                }
            }
            editText.setOnClickListener { showKeyboard(it) }
            editText.setOnEditorActionListener { view, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    hideKeyboard(view)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun showKeyboard(view: View) {
        val imm = ContextCompat.getSystemService(this, InputMethodManager::class.java)
        imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
    }

    private fun hideKeyboard(view: View) {
        val imm = ContextCompat.getSystemService(this, InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun getNavbarTitle(): String? {
        return getString(R.string.page_add_user)
    }

    companion object {
        const val EXTRA_EDIT_USER = "extra_edit_user"
    }
}
