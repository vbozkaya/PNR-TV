package com.pnr.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.databinding.ActivityAddUserBinding
import com.pnr.tv.db.entity.UserAccountEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddUserActivity : BaseActivity() {
    private lateinit var binding: ActivityAddUserBinding
    private val viewModel: AddUserViewModel by viewModels()
    private var originalUser: UserAccountEntity? = null

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
        originalUser = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_EDIT_USER, UserAccountEntity::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_EDIT_USER)
        }
        originalUser?.let {
            binding.etAccountName.setText(it.accountName)
            binding.etUsername.setText(it.username)
            binding.etPassword.setText(it.password)
            binding.etDns.setText(it.dns)
            binding.btnSaveUser.text = getString(R.string.btn_update)
        } ?: run {
            binding.btnSaveUser.text = getString(R.string.btn_save)
        }

        binding.btnSaveUser.setOnClickListener {
            saveUser()
        }

        binding.etAccountName.requestFocus()
    }

    private fun saveUser() {
        val accountName = binding.etAccountName.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val dns = binding.etDns.text.toString().trim()

        if (accountName.isBlank() || username.isBlank() || password.isBlank() || dns.isBlank()) {
            Toast.makeText(this, getString(R.string.error_fill_fields), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            originalUser?.let {
                val updatedUser =
                    it.copy(
                        accountName = accountName,
                        username = username,
                        password = password,
                        dns = dns,
                    )
                viewModel.updateUser(updatedUser)
                Toast.makeText(this@AddUserActivity, getString(R.string.toast_user_updated), Toast.LENGTH_SHORT).show()
            } ?: run {
                val newUser =
                    UserAccountEntity(
                        accountName = accountName,
                        username = username,
                        password = password,
                        dns = dns,
                    )
                viewModel.addUser(newUser)
                Toast.makeText(this@AddUserActivity, getString(R.string.toast_user_saved), Toast.LENGTH_SHORT).show()
            }

            val intent = Intent(this@AddUserActivity, UsersListActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun setupInputFields() {
        val editTexts = listOf(binding.etAccountName, binding.etUsername, binding.etPassword, binding.etDns)
        editTexts.forEach { editText ->
            editText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.postDelayed({ showKeyboard(view) }, Constants.DelayDurations.KEYBOARD_SHOW_DELAY)
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
