package com.pnr.tv.util

import android.content.Context
import com.pnr.tv.R
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * CategoryNameHelper için unit testler.
 * Kategori isimlerinin doğru şekilde yerelleştirildiğini doğrular.
 */
class CategoryNameHelperTest {

    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mock()
        // Setup string resources
        whenever(mockContext.getString(R.string.category_action)).thenReturn("Aksiyon")
        whenever(mockContext.getString(R.string.category_comedy)).thenReturn("Komedi")
        whenever(mockContext.getString(R.string.category_drama)).thenReturn("Drama")
        whenever(mockContext.getString(R.string.category_science_fiction)).thenReturn("Bilim Kurgu")
        whenever(mockContext.getString(R.string.category_talk_show)).thenReturn("Sohbet Programı")
        whenever(mockContext.getString(R.string.category_tv_movie)).thenReturn("TV Filmi")
    }

    @Test
    fun `getLocalizedCategoryName should return empty string for null input`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, null)

        // Then
        assertEquals("", result)
    }

    @Test
    fun `getLocalizedCategoryName should return empty string for blank input`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, "   ")

        // Then
        assertEquals("", result)
    }

    @Test
    fun `getLocalizedCategoryName should localize Action category`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, "Action")

        // Then
        assertEquals("Aksiyon", result)
    }

    @Test
    fun `getLocalizedCategoryName should localize Comedy category`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, "Comedy")

        // Then
        assertEquals("Komedi", result)
    }

    @Test
    fun `getLocalizedCategoryName should localize Drama category`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, "Drama")

        // Then
        assertEquals("Drama", result)
    }

    @Test
    fun `getLocalizedCategoryName should handle case insensitive input`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, "ACTION")

        // Then
        assertEquals("Aksiyon", result)
    }

    @Test
    fun `getLocalizedCategoryName should handle Turkish input`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, "Aksiyon")

        // Then
        assertEquals("Aksiyon", result)
    }

    @Test
    fun `getLocalizedCategoryName should handle trimmed input`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, "  Action  ")

        // Then
        assertEquals("Aksiyon", result)
    }

    @Test
    fun `getLocalizedCategoryName should localize Science Fiction with contains check`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, "Science Fiction")

        // Then
        assertEquals("Bilim Kurgu", result)
    }

    @Test
    fun `getLocalizedCategoryName should localize Sci-Fi variant`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, "Sci-Fi")

        // Then
        assertEquals("Bilim Kurgu", result)
    }

    @Test
    fun `getLocalizedCategoryName should localize Talk Show with contains check`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, "Talk Show")

        // Then
        assertEquals("Sohbet Programı", result)
    }

    @Test
    fun `getLocalizedCategoryName should localize TV Movie with contains check`() {
        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, "TV Movie")

        // Then
        assertEquals("TV Filmi", result)
    }

    @Test
    fun `getLocalizedCategoryName should return original name for unknown category`() {
        // Given
        val unknownCategory = "Unknown Category"

        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, unknownCategory)

        // Then
        assertEquals(unknownCategory, result)
    }

    @Test
    fun `getLocalizedCategoryName should handle exception when string resource not found`() {
        // Given
        val exceptionContext = mock<Context>()
        whenever(exceptionContext.getString(R.string.category_action))
            .thenThrow(android.content.res.Resources.NotFoundException())

        // When
        val result = CategoryNameHelper.getLocalizedCategoryName(exceptionContext, "Action")

        // Then - Should return original name on exception
        assertEquals("Action", result)
    }

    @Test
    fun `getLocalizedCategoryName should handle Romance with multiple variants`() {
        // Given
        whenever(mockContext.getString(R.string.category_romance)).thenReturn("Romantik")

        // When
        val result1 = CategoryNameHelper.getLocalizedCategoryName(mockContext, "Romance")
        val result2 = CategoryNameHelper.getLocalizedCategoryName(mockContext, "Romantik")
        val result3 = CategoryNameHelper.getLocalizedCategoryName(mockContext, "Aşk")

        // Then
        assertEquals("Romantik", result1)
        assertEquals("Romantik", result2)
        assertEquals("Romantik", result3)
    }

    @Test
    fun `getLocalizedCategoryName should handle all common categories`() {
        // Given
        val categories = mapOf(
            "Action" to "Aksiyon",
            "Comedy" to "Komedi",
            "Drama" to "Drama",
            "Horror" to "Korku",
            "Thriller" to "Gerilim",
            "War" to "Savaş",
            "Sports" to "Spor",
            "News" to "Haber",
            "Kids" to "Çocuk",
            "All" to "Tümü"
        )

        // Setup string resources
        whenever(mockContext.getString(R.string.category_horror)).thenReturn("Korku")
        whenever(mockContext.getString(R.string.category_thriller)).thenReturn("Gerilim")
        whenever(mockContext.getString(R.string.category_war)).thenReturn("Savaş")
        whenever(mockContext.getString(R.string.category_sports)).thenReturn("Spor")
        whenever(mockContext.getString(R.string.category_news)).thenReturn("Haber")
        whenever(mockContext.getString(R.string.category_kids)).thenReturn("Çocuk")
        whenever(mockContext.getString(R.string.category_all)).thenReturn("Tümü")

        // When & Then
        categories.forEach { (input, expected) ->
            val result = CategoryNameHelper.getLocalizedCategoryName(mockContext, input)
            assertEquals("Category $input should be localized correctly", expected, result)
        }
    }
}



