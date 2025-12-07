package com.pnr.tv.ui.series

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.pnr.tv.PlayerActivity
import com.pnr.tv.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SeriesDetailFragment için Espresso UI testleri.
 * 
 * NOT: Bu testler Hilt DI kullanır ve gerçek ViewModel/Repository ile çalışır.
 * Production ortamında API mock'laması yapılması gerekebilir.
 */
@MediumTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SeriesDetailFragmentTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    private lateinit var scenario: FragmentScenario<SeriesDetailFragment>

    @Before
    fun setup() {
        hiltRule.inject()
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
        if (::scenario.isInitialized) {
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }
    }

    /**
     * Test: Fragment açıldığında navbar doğru başlık ile görüntülenir
     */
    @Test
    fun fragmentLaunch_shouldDisplayNavbarWithCorrectTitle() {
        // Given
        val args = createFragmentArgs(seriesId = 123)

        // When
        scenario = launchFragmentInContainer<SeriesDetailFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_Leanback
        )

        // Then
        onView(withId(R.id.txt_navbar_title))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.page_series_details)))
    }

    /**
     * Test: Geri butonuna basıldığında Fragment kapatılır
     */
    @Test
    fun clickBackButton_shouldCloseFragment() {
        // Given
        val args = createFragmentArgs(seriesId = 123)
        scenario = launchFragmentInContainer<SeriesDetailFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_Leanback
        )

        // When
        onView(withId(R.id.btn_navbar_back))
            .perform(click())

        // Then - Fragment destroyed olmalı
        scenario.onFragment { fragment ->
            assert(fragment.isRemoving || !fragment.isAdded)
        }
    }

    /**
     * Test: Loading indicator başlangıçta gösterilir
     * NOT: Bu test gerçek API çağrısı yaptığı için mock gerekebilir
     */
    @Test
    fun fragmentLaunch_shouldShowLoadingIndicatorInitially() {
        // Given
        val args = createFragmentArgs(seriesId = 123)

        // When
        scenario = launchFragmentInContainer<SeriesDetailFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_Leanback
        )

        // Then - İlk anda loading gösterilmeli (hızlı API'de göremeyebiliriz)
        // Bu test gerçek ortamda flaky olabilir, mock gerekir
        onView(withId(R.id.loading_indicator))
            .check(matches(isDisplayed()))
    }

    /**
     * Test: Sezon sekmeleri doğru şekilde görüntülenir
     * NOT: Bu test gerçek veri gerektirir, mock önerilir
     */
    @Test
    fun fragmentWithData_shouldDisplaySeasonTabs() {
        // Given
        val args = createFragmentArgs(seriesId = 123)

        // When
        scenario = launchFragmentInContainer<SeriesDetailFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_Leanback
        )

        // Veri yüklenene kadar bekle (production'da mock kullanılmalı)
        Thread.sleep(2000)

        // Then
        onView(withId(R.id.tab_layout_seasons))
            .check(matches(isDisplayed()))
    }

    /**
     * Test: Bölüm listesi görüntülenir
     * NOT: Bu test gerçek veri gerektirir, mock önerilir
     */
    @Test
    fun fragmentWithData_shouldDisplayEpisodesList() {
        // Given
        val args = createFragmentArgs(seriesId = 123)

        // When
        scenario = launchFragmentInContainer<SeriesDetailFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_Leanback
        )

        // Veri yüklenene kadar bekle
        Thread.sleep(2000)

        // Then
        onView(withId(R.id.recycler_episodes))
            .check(matches(isDisplayed()))
    }

    /**
     * Test: Bölüme tıklandığında PlayerActivity başlatılır
     * NOT: Bu test gerçek veri gerektirir ve Intent kontrolü yapar
     */
    @Test
    fun clickEpisode_shouldLaunchPlayerActivity() {
        // Given
        val args = createFragmentArgs(seriesId = 123)
        scenario = launchFragmentInContainer<SeriesDetailFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_Leanback
        )

        // Veri yüklenene kadar bekle
        Thread.sleep(2000)

        // When - İlk bölüme tıkla
        onView(withId(R.id.recycler_episodes))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<EpisodesAdapter.EpisodeViewHolder>(
                    0,
                    click()
                )
            )

        // Then - PlayerActivity başlatılmalı
        intended(
            allOf(
                hasComponent(PlayerActivity::class.java.name),
                hasExtra(PlayerActivity.EXTRA_VIDEO_URL, anyString())
            )
        )
    }

    /**
     * Test: Empty state doğru görüntülenir (bölüm yoksa)
     * NOT: Bu test için empty response mock'lanmalı
     */
    @Test
    fun fragmentWithNoEpisodes_shouldDisplayEmptyState() {
        // Bu test için API mock'laması yapılmalı
        // Şu anki hali gerçek API'ye bağlı olduğu için implementasyon bekliyor
        
        // Given - API'den empty response dönmeli (mock gerekli)
        // When
        // Then
        // onView(withId(R.id.txt_empty_state))
        //     .check(matches(isDisplayed()))
    }

    /**
     * Test: Error dialog gösterilir (API hatası)
     * NOT: Bu test için error response mock'lanmalı
     */
    @Test
    fun fragmentWithApiError_shouldDisplayErrorDialog() {
        // Bu test için API mock'laması yapılmalı
        // Şu anki hali gerçek API'ye bağlı olduğu için implementasyon bekliyor
        
        // Given - API'den error dönmeli (mock gerekli)
        // When
        // Then
        // onView(withText(R.string.error_title))
        //     .check(matches(isDisplayed()))
    }

    /**
     * Test: Accessibility - Content descriptions doğru atanmış
     */
    @Test
    fun fragmentLaunch_shouldHaveAccessibilityContentDescriptions() {
        // Given
        val args = createFragmentArgs(seriesId = 123)

        // When
        scenario = launchFragmentInContainer<SeriesDetailFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_Leanback
        )

        // Then
        onView(withId(R.id.img_series_poster))
            .check(matches(withContentDescription(R.string.cd_series_poster)))

        onView(withId(R.id.btn_navbar_back))
            .check(matches(withContentDescription(R.string.cd_navbar_back)))

        onView(withId(R.id.btn_navbar_home))
            .check(matches(withContentDescription(R.string.cd_navbar_home)))

        onView(withId(R.id.loading_indicator))
            .check(matches(withContentDescription(R.string.cd_loading)))
    }

    // ═══════════════════════════════════════════════════════════
    // Helper Functions
    // ═══════════════════════════════════════════════════════════

    private fun createFragmentArgs(seriesId: Int): Bundle {
        return Bundle().apply {
            putInt("series_id", seriesId)
        }
    }

    private fun anyString(): String = ""
}



