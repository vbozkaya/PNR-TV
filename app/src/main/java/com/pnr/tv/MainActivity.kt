package com.pnr.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.Constants
import com.pnr.tv.databinding.ActivityMainBinding
import com.pnr.tv.extensions.hide
import com.pnr.tv.extensions.show
import com.pnr.tv.ui.base.ToolbarController
import com.pnr.tv.util.ViewerInitializer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity(), ToolbarController {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var viewerInitializer: ViewerInitializer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeCurrentUser()
        observeUpdateState()

        binding.btnUpdate.setOnClickListener {
            viewModel.refreshAllContent()
        }

        binding.btnUsers.setOnClickListener {
            startActivity(Intent(this, UserManagementActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnExit.setOnClickListener {
            Timber.d("Exit button clicked")
            showExitDialog()
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
    }

    private fun observeCurrentUser() {
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                // Kullanıcı adını hemen göster
                binding.tvCurrentUser.text = getString(R.string.current_user_label, user.accountName)
            } else {
                binding.tvCurrentUser.text = getString(R.string.current_user_none)
            }
        }
    }

    private fun observeUpdateState() {
        lifecycleScope.launch {
            viewModel.updateState.collectLatest { state ->
                when (state) {
                    MainViewModel.UpdateState.LOADING -> {
                        // Yüklenme başladı - overlay'i göster ve mesajı ayarla
                        binding.loadingOverlay.show()
                        binding.txtLoadingMessage.text = getString(R.string.loading_content)
                    }
                    MainViewModel.UpdateState.COMPLETED -> {
                        // Güncelleme tamamlandı - overlay hala görünür, mesajı değiştir
                        binding.loadingOverlay.show()
                        binding.txtLoadingMessage.text = getString(R.string.loading_completed)
                        
                        // Belirli bir süre sonra durumu IDLE'a çek (UI mantığı)
                        lifecycleScope.launch {
                            delay(Constants.DelayDurations.UPDATE_COMPLETED_DELAY)
                            viewModel.resetUpdateState()
                        }
                    }
                    MainViewModel.UpdateState.ERROR -> {
                        // Hata durumu - overlay'i göster ve hata mesajını göster
                        binding.loadingOverlay.show()
                        val errorMsg = viewModel.errorMessage.value
                        binding.txtLoadingMessage.text =
                            getString(
                                R.string.error_with_message,
                                errorMsg ?: getString(R.string.error_unknown),
                            )
                    }
                    MainViewModel.UpdateState.IDLE -> {
                        // Durum sıfırlandı - overlay'i gizle
                        binding.loadingOverlay.hide()
                    }
                }
            }
        }
    }

    override fun showTopMenu() {
        binding.layoutCurrentUser.show()
        binding.btnExit.show()
        binding.btnSettings.show()
        binding.btnUsers.show()
        binding.btnUpdate.show()
    }

    override fun hideTopMenu() {
        binding.layoutCurrentUser.hide()
        binding.btnExit.hide()
        binding.btnSettings.hide()
        binding.btnUsers.hide()
        binding.btnUpdate.hide()
    }

    private fun showExitDialog() {
        try {
            Timber.d("showExitDialog called")
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_exit_title))
                .setMessage(getString(R.string.dialog_exit_message))
                .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                    Timber.d("User confirmed exit")
                    finishAffinity()
                }
                .setNegativeButton(getString(R.string.dialog_no)) { dialog, _ ->
                    Timber.d("User cancelled exit")
                    dialog.dismiss()
                }
                .setCancelable(false)
                .create()
                .show()
        } catch (e: Exception) {
            Timber.e(e, "Error showing dialog")
        }
    }
}
