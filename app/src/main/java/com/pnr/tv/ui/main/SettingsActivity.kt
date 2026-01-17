package com.pnr.tv.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import com.pnr.tv.R
import com.pnr.tv.core.base.BaseActivity
import com.pnr.tv.premium.AdManager
import com.pnr.tv.premium.PremiumManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : BaseActivity() {
    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var premiumManager: PremiumManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Fragment'ları yükle
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.settings_container, com.pnr.tv.ui.settings.AccountSettingsFragment())
                replace(R.id.general_settings_container, com.pnr.tv.ui.settings.GeneralSettingsFragment())
                replace(R.id.premium_settings_container, com.pnr.tv.ui.settings.PremiumSettingsFragment())
            }
        }

        // Crashlytics Debug paneli (sadece debug build'lerde görünür)
        val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        findViewById<View>(R.id.btn_crashlytics_debug)?.apply {
            visibility = if (isDebug) View.VISIBLE else View.GONE
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, com.pnr.tv.ui.debug.CrashlyticsDebugActivity::class.java))
            }
        }

        // Banner reklamı setup et
        setupBannerAd(adManager, premiumManager)
    }

    override fun getNavbarTitle(): String? {
        return getString(R.string.page_settings)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Hata state'ini temizle - önceki sayfalardan gelen hata mesajlarının Settings sayfasında görünmemesi için
        // SettingsViewModel'i activity scope'unda kullanıyorsak, burada temizleyebiliriz
        // Ancak fragment scope'unda kullanıldığı için fragment'ın kendi lifecycle'ında temizlenir
        // Bu metod sadece ek güvenlik için eklendi
    }
}
