package com.pnr.tv.ui.movies

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import com.pnr.tv.core.base.BaseActivity
import com.pnr.tv.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Film detay sayfası için Activity.
 * MovieDetailFragment'ı host eder.
 */
@AndroidEntryPoint
class MovieDetailActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_container)

        val movieId = intent.getIntExtra(EXTRA_MOVIE_ID, -1)
        if (movieId == -1) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, MovieDetailFragment.newInstance(movieId))
            }
        }
    }

    companion object {
        private const val EXTRA_MOVIE_ID = "movie_id"

        fun newIntent(
            context: Context,
            movieId: Int,
        ): Intent {
            return Intent(context, MovieDetailActivity::class.java).apply {
                putExtra(EXTRA_MOVIE_ID, movieId)
            }
        }
    }
}
