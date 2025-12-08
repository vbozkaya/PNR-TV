package com.pnr.tv

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.pnr.tv.databinding.ActivityUsersListBinding
import com.pnr.tv.db.entity.UserAccountEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UsersListActivity : BaseActivity(), UsersListAdapter.OnUserActionListener {
    private lateinit var binding: ActivityUsersListBinding
    private val usersAdapter = UsersListAdapter(this)
    private val viewModel: UsersListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvUsers.layoutManager = GridLayoutManager(this, Constants.Layout.USER_GRID_COLUMNS)
        binding.rvUsers.adapter = usersAdapter

        observeUsers()
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
        Toast.makeText(this, getString(R.string.toast_user_selected, user.accountName), Toast.LENGTH_SHORT).show()
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
        val dialog = AlertDialog.Builder(this, R.style.FullscreenDialogTheme)
            .setTitle(R.string.dialog_delete_user_title)
            .setMessage(getString(R.string.dialog_delete_user_message, user.accountName))
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                viewModel.deleteUser(user)
                Toast.makeText(this, getString(R.string.toast_user_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_no, null)
            .setCancelable(false)
            .create()
        
        dialog.show()
        // Güvenlik için "Hayır" butonuna focus ver
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
    }
}
