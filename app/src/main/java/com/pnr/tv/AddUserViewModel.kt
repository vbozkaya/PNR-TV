package com.pnr.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddUserViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
    ) : ViewModel() {
        fun addUser(user: UserAccountEntity) {
            viewModelScope.launch {
                userRepository.addUser(user)
            }
        }

        fun updateUser(user: UserAccountEntity) {
            viewModelScope.launch {
                userRepository.updateUser(user)
            }
        }
    }

