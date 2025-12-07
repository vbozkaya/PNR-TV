package com.pnr.tv.repository

import com.pnr.tv.SessionManager
import com.pnr.tv.db.dao.UserDao
import com.pnr.tv.db.entity.UserAccountEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class UserRepository
    @Inject
    constructor(
        private val userDao: UserDao,
        private val sessionManager: SessionManager,
    ) {
        val allUsers: Flow<List<UserAccountEntity>> = userDao.getAllUsers()

        @OptIn(ExperimentalCoroutinesApi::class)
        val currentUser: Flow<UserAccountEntity?> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId == null) {
                    kotlinx.coroutines.flow.flowOf(null)
                } else {
                    userDao.getUserById(userId)
                }
            }

        suspend fun addUser(user: UserAccountEntity): Long {
            return userDao.insertUser(user)
        }

        suspend fun updateUser(user: UserAccountEntity) {
            userDao.updateUser(user)
        }

        suspend fun deleteUser(user: UserAccountEntity) {
            userDao.deleteUser(user)
        }

        suspend fun setCurrentUser(user: UserAccountEntity) {
            sessionManager.saveCurrentUser(user.id)
        }
    }




