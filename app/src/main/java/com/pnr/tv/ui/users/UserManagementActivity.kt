package com.pnr.tv.ui.users

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.pnr.tv.core.base.BaseActivity
import com.pnr.tv.R
import com.pnr.tv.premium.AdManager
import com.pnr.tv.premium.PremiumManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class UserManagementActivity : BaseActivity() {
    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var premiumManager: PremiumManager

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

        // Banner reklamı setup et
        setupBannerAd(adManager, premiumManager)
    }

    override fun getNavbarTitle(): String? {
        return getString(R.string.page_user_management)
    }
}
