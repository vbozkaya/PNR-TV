package com.pnr.tv.ui.series

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import com.pnr.tv.core.base.BaseActivity
import com.pnr.tv.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Dizi detay sayfası için Activity.
 * SeriesDetailFragment'ı host eder.
 */
@AndroidEntryPoint
class SeriesDetailActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_container)

        val seriesId = intent.getIntExtra(EXTRA_SERIES_ID, -1)
        if (seriesId == -1) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, SeriesDetailFragment.newInstance(seriesId))
            }
        }
    }

    companion object {
        private const val EXTRA_SERIES_ID = "series_id"

        fun newIntent(
            context: Context,
            seriesId: Int,
        ): Intent {
            return Intent(context, SeriesDetailActivity::class.java).apply {
                putExtra(EXTRA_SERIES_ID, seriesId)
            }
        }
    }
}
