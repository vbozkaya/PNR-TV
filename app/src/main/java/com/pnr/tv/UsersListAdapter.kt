package com.pnr.tv

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
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

            // Focus scroll: Focus alındığında item'ı görünür alana getir
            binding.root.setOnFocusChangeListener { focusedView, hasFocus ->
                if (hasFocus) {
                    val recyclerView = focusedView.parent as? RecyclerView
                    if (recyclerView != null) {
                        val layoutManager = recyclerView.layoutManager as? GridLayoutManager
                        if (layoutManager != null) {
                            val focusedPosition = recyclerView.getChildAdapterPosition(focusedView)
                            if (focusedPosition != RecyclerView.NO_POSITION) {
                                recyclerView.post {
                                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                                    val lastVisible = layoutManager.findLastVisibleItemPosition()

                                    var needsScroll = false
                                    if (focusedPosition < firstVisible || focusedPosition > lastVisible) {
                                        needsScroll = true
                                    } else {
                                        val viewHolder = recyclerView.findViewHolderForAdapterPosition(focusedPosition)
                                        viewHolder?.itemView?.let { view ->
                                            val top = view.top
                                            val bottom = view.bottom
                                            val recyclerTop = recyclerView.paddingTop
                                            val recyclerBottom = recyclerView.height - recyclerView.paddingBottom

                                            if (top < recyclerTop || bottom > recyclerBottom) {
                                                needsScroll = true
                                            }
                                        }
                                    }

                                    if (needsScroll || focusedPosition == firstVisible || focusedPosition == lastVisible) {
                                        layoutManager.scrollToPositionWithOffset(focusedPosition, recyclerView.paddingTop + 20)
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
