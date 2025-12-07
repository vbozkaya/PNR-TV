package com.pnr.tv

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.viewModels
import com.pnr.tv.databinding.DialogUserStatusBinding
import com.pnr.tv.ui.viewers.ViewersActivity
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class SettingsActivity : BaseActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btn_user_status)?.setOnClickListener {
            showUserStatusDialog()
        }

        findViewById<View>(R.id.btn_viewers)?.setOnClickListener {
            startActivity(Intent(this, ViewersActivity::class.java))
        }

        // Kullanıcı bilgilerini çek
        viewModel.fetchUserInfo()
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

        // Kullanıcı bilgilerini gözlemle
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                binding.tvCurrentUser.text = getString(R.string.current_user_label, user.accountName)
            } else {
                binding.tvCurrentUser.text = getString(R.string.current_user_none)
            }
        }

        viewModel.userInfo.observe(this) { authResponse ->
            if (authResponse != null) {
                val userInfo = authResponse.userInfo

                // Durum
                val status = userInfo.status ?: "-"
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
                            "-"
                        }
                    } else {
                        "-"
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
                binding.tvStatus.text = getString(R.string.status_label, "-")
                binding.tvExpiryDate.text = getString(R.string.expiry_date_label, "-")
                binding.tvConnection.text = getString(R.string.connection_label, "-", "-")
                binding.tvTrial.text = getString(R.string.trial_label, "-")
            }
        }

        binding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // İlk focus'u Kapat butonuna ver
        binding.btnClose.requestFocus()

        dialog.show()
    }
}
