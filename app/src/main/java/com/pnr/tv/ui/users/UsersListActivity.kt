package com.pnr.tv.ui.users

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.pnr.tv.core.base.BaseActivity
import com.pnr.tv.ui.main.MainActivity
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.databinding.ActivityUsersListBinding
import com.pnr.tv.databinding.DialogDeleteUserBinding
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.premium.AdManager
import com.pnr.tv.premium.PremiumManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UsersListActivity : BaseActivity(), UsersListAdapter.OnUserActionListener {
    private lateinit var binding: ActivityUsersListBinding
    private val usersAdapter = UsersListAdapter(this)
    private val viewModel: UsersListViewModel by viewModels()

    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var premiumManager: PremiumManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvUsers.layoutManager = GridLayoutManager(this, UIConstants.Layout.USER_GRID_COLUMNS)
        binding.rvUsers.adapter = usersAdapter

        observeUsers()

        // Banner reklamı setup et
        setupBannerAd(adManager, premiumManager)
    }

    override fun onStart() {
        super.onStart()
        // Activity açıldığında RecyclerView'ın ilk öğesine focus ver
        binding.rvUsers.post {
            if (usersAdapter.itemCount > 0) {
                val firstItem = binding.rvUsers.findViewHolderForAdapterPosition(0)
                firstItem?.itemView?.requestFocus()
            }
        }
    }

    private fun observeUsers() {
        lifecycleScope.launch {
            viewModel.allUsers.collectLatest { users ->
                usersAdapter.submitList(users)
                binding.rvUsers.isVisible = users.isNotEmpty()
                binding.tvEmptyState.isVisible = users.isEmpty()

                // RecyclerView'a veri geldiğinde ilk öğeye focus ver
                if (users.isNotEmpty()) {
                    binding.rvUsers.post {
                        val firstItem = binding.rvUsers.findViewHolderForAdapterPosition(0)
                        firstItem?.itemView?.requestFocus()
                    }
                }
            }
        }
    }

    override fun getNavbarTitle(): String? {
        return getString(R.string.page_users_list)
    }

    override fun onSelect(user: UserAccountEntity) {
        viewModel.setCurrentUser(user)
        showCustomToast(getString(R.string.toast_user_selected, user.accountName))
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    override fun onEdit(user: UserAccountEntity) {
        val intent = Intent(this, AddUserActivity::class.java)
        intent.putExtra(AddUserActivity.EXTRA_EDIT_USER, user)
        startActivity(intent)
    }

    override fun onDelete(user: UserAccountEntity) {
        val binding = DialogDeleteUserBinding.inflate(LayoutInflater.from(this))

        // Başlık ve mesajı ayarla
        binding.tvDialogTitle.setText(R.string.dialog_delete_user_title)
        binding.tvDialogMessage.text = getString(R.string.dialog_delete_user_message, user.accountName)

        val dialog =
            AlertDialog.Builder(this)
                .setView(binding.root)
                .setCancelable(false)
                .create()

        // Dialog window ayarları
        val window = dialog.window
        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Buton click listener'ları
        binding.btnDialogYes.setOnClickListener {
            viewModel.deleteUser(user)
            showCustomToast(getString(R.string.toast_user_deleted))
            dialog.dismiss()
        }

        binding.btnDialogNo.setOnClickListener {
            dialog.dismiss()
        }

        // Buton focus listener'ları (TV remote için)
        binding.btnDialogNo.setOnFocusChangeListener { _, hasFocus ->
            binding.btnDialogNo.alpha = if (hasFocus) 1.0f else 0.7f
        }

        binding.btnDialogYes.setOnFocusChangeListener { _, hasFocus ->
            binding.btnDialogYes.alpha = if (hasFocus) 1.0f else 0.7f
        }

        dialog.show()

        // Güvenlik için "Hayır" butonuna focus ver
        binding.btnDialogNo.post {
            binding.btnDialogNo.requestFocus()
        }
    }
}
