package com.pnr.tv

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.databinding.ItemUserEntryBinding
import com.pnr.tv.db.entity.UserAccountEntity

class UsersListAdapter(
    private val listener: OnUserActionListener,
) : ListAdapter<UserAccountEntity, UsersListAdapter.UserViewHolder>(UserDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): UserViewHolder {
        val binding =
            ItemUserEntryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: UserViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(
        private val binding: ItemUserEntryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(userAccount: UserAccountEntity) {
            binding.ivUserIcon.setImageResource(R.drawable.ic_current_users)
            binding.tvAccountLabel.text = binding.root.context.getString(R.string.label_account_name)
            binding.tvAccountValue.text = userAccount.accountName

            binding.btnSelectUser.setOnClickListener { listener.onSelect(userAccount) }
            binding.btnEditUser.setOnClickListener { listener.onEdit(userAccount) }
            binding.btnDeleteUser.setOnClickListener { listener.onDelete(userAccount) }
        }
    }

    interface OnUserActionListener {
        fun onSelect(user: UserAccountEntity)

        fun onEdit(user: UserAccountEntity)

        fun onDelete(user: UserAccountEntity)
    }

    private class UserDiffCallback : DiffUtil.ItemCallback<UserAccountEntity>() {
        override fun areItemsTheSame(
            oldItem: UserAccountEntity,
            newItem: UserAccountEntity,
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: UserAccountEntity,
            newItem: UserAccountEntity,
        ): Boolean {
            return oldItem == newItem
        }
    }
}
