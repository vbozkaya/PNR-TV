package com.pnr.tv.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.db.dao.MovieCategoryDao
import com.pnr.tv.db.dao.MovieDao
import com.pnr.tv.db.entity.MovieCategoryEntity
import com.pnr.tv.db.entity.MovieEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import app.cash.turbine.test
import retrofit2.Retrofit

@OptIn(ExperimentalCoroutinesApi::class)
class MovieRepositoryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockRetrofitBuilder: Retrofit.Builder = mock()
    private val mockUserRepository: UserRepository = mock()
    private val mockTmdbRepository: TmdbRepository = mock()
    private val mockMovieDao: MovieDao = mock()
    private val mockMovieCategoryDao: MovieCategoryDao = mock()
    private val mockContext: android.content.Context = mock()

    private lateinit var repository: MovieRepository

    @Before
    fun setup() {
        repository = MovieRepository(
            retrofitBuilder = mockRetrofitBuilder,
            userRepository = mockUserRepository,
            tmdbRepository = mockTmdbRepository,
            movieDao = mockMovieDao,
            movieCategoryDao = mockMovieCategoryDao,
            context = mockContext
        )
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    @Test
    fun `getMovies should return flow from movieDao`() = runTest {
        // Given
        val testMovies = listOf(
            MovieEntity(
                streamId = 1,
                name = "Movie 1",
                streamIconUrl = null,
                rating = 8.5,
                plot = "Plot 1",
                categoryId = "1",
                added = "2024-01-01",
                tmdbId = 100,
                containerExtension = "mp4"
            )
        )
        whenever(mockMovieDao.getAll()).thenReturn(flowOf(testMovies))

        // When
        val result = repository.getMovies()

        // Then
        result.test {
            val movies = awaitItem()
            assertEquals(testMovies, movies)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getMoviesByCategoryId should return flow from movieDao`() = runTest {
        // Given
        val categoryId = "1"
        val testMovies = listOf(
            MovieEntity(
                streamId = 1,
                name = "Movie 1",
                streamIconUrl = null,
                rating = 8.5,
                plot = "Plot 1",
                categoryId = categoryId,
                added = "2024-01-01",
                tmdbId = 100,
                containerExtension = "mp4"
            )
        )
        whenever(mockMovieDao.getByCategoryId(categoryId)).thenReturn(flowOf(testMovies))

        // When
        val result = repository.getMoviesByCategoryId(categoryId)

        // Then
        result.test {
            val movies = awaitItem()
            assertEquals(testMovies, movies)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Not: refreshMovies ve refreshMovieCategories metodları BaseContentRepository'deki
    // safeApiCall metodunu kullanıyor ve Retrofit.Builder mock'laması karmaşık.
    // Bu metodlar için integration test yazılması daha uygun olur.
    // Unit testler için DAO metodlarını test etmek yeterli.
}

