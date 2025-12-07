package com.pnr.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsersListViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
    ) : ViewModel() {
        val allUsers = userRepository.allUsers

        fun setCurrentUser(user: UserAccountEntity) {
            viewModelScope.launch {
                userRepository.setCurrentUser(user)
            }
        }

        fun deleteUser(user: UserAccountEntity) {
            viewModelScope.launch {
                userRepository.deleteUser(user)
            }
        }
    }
