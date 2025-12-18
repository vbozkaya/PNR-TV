package com.pnr.tv

import android.os.Bundle
import android.view.View
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
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
