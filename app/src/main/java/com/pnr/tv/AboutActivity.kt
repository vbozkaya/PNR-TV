package com.pnr.tv

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.pnr.tv.premium.AdManager
import com.pnr.tv.premium.PremiumManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AboutActivity : BaseActivity() {
    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var premiumManager: PremiumManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // Versiyon bilgisini göster
        setupVersionInfo()

        // Banner reklamı setup et
        setupBannerAd(adManager, premiumManager)
    }

    private fun setupVersionInfo() {
        val versionTextView = findViewById<TextView>(R.id.tv_app_version)
        if (versionTextView != null) {
            val versionName = BuildConfig.VERSION_NAME
            val versionCode = BuildConfig.VERSION_CODE
            versionTextView.text = getString(R.string.about_app_version_label, versionName, versionCode)
        }
    }

    override fun onStart() {
        super.onStart()
        // Activity açıldığında Home butonuna focus ver
        findViewById<View>(R.id.root)?.post {
            val navbar = findViewById<View>(R.id.navbar)
            val homeButton = navbar?.findViewById<View>(R.id.btn_navbar_home)
            homeButton?.requestFocus()
        }
    }

    override fun getNavbarTitle(): String? {
        return getString(R.string.about_app_title)
    }
}
