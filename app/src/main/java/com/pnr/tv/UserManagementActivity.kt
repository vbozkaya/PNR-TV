package com.pnr.tv

import android.content.Intent
import android.os.Bundle
import android.view.View

class UserManagementActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_management)

        // Mevcut Kullanıcılar butonuna click listener ekle
        findViewById<View>(R.id.btn_current_users)?.setOnClickListener {
            startActivity(Intent(this, UsersListActivity::class.java))
        }

        // Yeni Kullanıcı Ekle butonuna click listener ekle
        findViewById<View>(R.id.btn_add_user)?.setOnClickListener {
            startActivity(Intent(this, AddUserActivity::class.java))
        }

        // İlk focus'u Mevcut Kullanıcılar butonuna ver
        findViewById<View>(R.id.btn_current_users)?.requestFocus()
    }

    override fun getNavbarTitle(): String? {
        return getString(R.string.page_user_management)
    }
}
