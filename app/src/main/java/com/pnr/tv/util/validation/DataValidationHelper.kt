package com.pnr.tv.util.validation

import com.pnr.tv.network.dto.LiveStreamDto
import com.pnr.tv.network.dto.MovieDto
import com.pnr.tv.network.dto.SeriesDto
import timber.log.Timber

/**
 * API'den gelen verilerin eksikliklerini kontrol eden utility sınıfı.
 * Hangi field'ların eksik geldiğini tespit eder ve loglar.
 */
object DataValidationHelper {
    /**
     * Film verilerinin eksikliklerini kontrol eder ve raporlar.
     */
    fun validateMovies(movies: List<MovieDto>): DataValidationReport {
        val report = DataValidationReport("Movies", movies.size)

        if (movies.isEmpty()) {
            Timber.w("⚠️  Film listesi boş!")
            return report
        }

        movies.forEachIndexed { index, movie ->
            val missingFields = mutableListOf<String>()
            val criticalFields = mutableListOf<String>()
            val importantFields = mutableListOf<String>()

            // Kritik field'ları kontrol et (bunlar mutlaka olmalı)
            if (movie.streamId == null && movie.num == null) {
                missingFields.add("streamId/num")
                criticalFields.add("streamId/num")
            }
            if (movie.name.isNullOrBlank()) {
                missingFields.add("name")
                criticalFields.add("name")
            }

            // Opsiyonel ama önemli field'lar (bunlar eksik olabilir)
            if (movie.streamIconUrl.isNullOrBlank()) {
                missingFields.add("streamIconUrl")
                importantFields.add("streamIconUrl")
            }
            if (movie.rating.isNullOrBlank()) {
                missingFields.add("rating")
                importantFields.add("rating")
            }
            if (movie.plot.isNullOrBlank()) {
                missingFields.add("plot")
                importantFields.add("plot")
            }
            if (movie.categoryId.isNullOrBlank() && (movie.categoryIds.isNullOrEmpty())) {
                missingFields.add("categoryId")
                importantFields.add("categoryId")
            }
            if (movie.tmdb.isNullOrBlank()) {
                missingFields.add("tmdb")
                importantFields.add("tmdb")
            }
            if (movie.containerExtension.isNullOrBlank()) {
                missingFields.add("containerExtension")
                importantFields.add("containerExtension")
            }

            // Sadece kritik field'lar eksikse veya önemli field'lardan en az biri eksikse raporla
            val hasCriticalMissing = criticalFields.isNotEmpty()
            val hasImportantMissing = importantFields.isNotEmpty()

            // Raporla: Kritik field eksikse VEYA önemli field'lardan en az biri eksikse
            if (hasCriticalMissing || hasImportantMissing) {
                report.addMissingFields(
                    index,
                    movie.name ?: "Unknown (ID: ${movie.streamId ?: movie.num})",
                    missingFields,
                    criticalFields,
                    importantFields,
                )
            }
        }

        return report
    }

    /**
     * Dizi verilerinin eksikliklerini kontrol eder ve raporlar.
     */
    fun validateSeries(series: List<SeriesDto>): DataValidationReport {
        val report = DataValidationReport("Series", series.size)

        if (series.isEmpty()) {
            Timber.w("⚠️  Dizi listesi boş!")
            return report
        }

        series.forEachIndexed { index, serie ->
            val missingFields = mutableListOf<String>()
            val criticalFields = mutableListOf<String>()
            val importantFields = mutableListOf<String>()

            // Kritik field'ları kontrol et (bunlar mutlaka olmalı)
            if (serie.seriesId == null) {
                missingFields.add("seriesId")
                criticalFields.add("seriesId")
            }
            if (serie.name.isNullOrBlank()) {
                missingFields.add("name")
                criticalFields.add("name")
            }

            // Opsiyonel ama önemli field'lar (bunlar eksik olabilir)
            if (serie.coverUrl.isNullOrBlank()) {
                missingFields.add("coverUrl")
                importantFields.add("coverUrl")
            }
            if (serie.rating.isNullOrBlank()) {
                missingFields.add("rating")
                importantFields.add("rating")
            }
            if (serie.plot.isNullOrBlank()) {
                missingFields.add("plot")
                importantFields.add("plot")
            }
            if (serie.categoryId.isNullOrBlank()) {
                missingFields.add("categoryId")
                importantFields.add("categoryId")
            }
            if (serie.tmdb.isNullOrBlank()) {
                missingFields.add("tmdb")
                importantFields.add("tmdb")
            }
            if (serie.releaseDate.isNullOrBlank()) {
                missingFields.add("releaseDate")
                importantFields.add("releaseDate")
            }

            // Sadece kritik field'lar eksikse veya önemli field'lardan en az biri eksikse raporla
            val hasCriticalMissing = criticalFields.isNotEmpty()
            val hasImportantMissing = importantFields.isNotEmpty()

            if (hasCriticalMissing || hasImportantMissing) {
                report.addMissingFields(
                    index,
                    serie.name ?: "Unknown (ID: ${serie.seriesId})",
                    missingFields,
                    criticalFields,
                    importantFields,
                )
            }
        }

        return report
    }

    /**
     * Canlı yayın verilerinin eksikliklerini kontrol eder ve raporlar.
     */
    fun validateLiveStreams(streams: List<LiveStreamDto>): DataValidationReport {
        val report = DataValidationReport("LiveStreams", streams.size)

        if (streams.isEmpty()) {
            Timber.w("⚠️  Canlı yayın listesi boş!")
            return report
        }

        streams.forEachIndexed { index, stream ->
            val missingFields = mutableListOf<String>()
            val criticalFields = mutableListOf<String>()
            val importantFields = mutableListOf<String>()

            // Kritik field'ları kontrol et (bunlar mutlaka olmalı)
            if (stream.streamId == null) {
                missingFields.add("streamId")
                criticalFields.add("streamId")
            }
            if (stream.name.isNullOrBlank()) {
                missingFields.add("name")
                criticalFields.add("name")
            }

            // Opsiyonel ama önemli field'lar (bunlar eksik olabilir)
            if (stream.streamIconUrl.isNullOrBlank()) {
                missingFields.add("streamIconUrl")
                importantFields.add("streamIconUrl")
            }
            if (stream.categoryId == null) {
                missingFields.add("categoryId")
                importantFields.add("categoryId")
            }

            // Sadece kritik field'lar eksikse veya önemli field'lardan en az biri eksikse raporla
            val hasCriticalMissing = criticalFields.isNotEmpty()
            val hasImportantMissing = importantFields.isNotEmpty()

            if (hasCriticalMissing || hasImportantMissing) {
                report.addMissingFields(
                    index,
                    stream.name ?: "Unknown (ID: ${stream.streamId})",
                    missingFields,
                    criticalFields,
                    importantFields,
                )
            }
        }

        return report
    }

    /**
     * Veri doğrulama raporu.
     */
    data class DataValidationReport(
        val dataType: String,
        val totalCount: Int,
        private val itemsWithMissingFields: MutableMap<Int, MissingFieldData> = mutableMapOf(),
    ) {
        data class MissingFieldData(
            val name: String,
            val allMissing: List<String>,
            val criticalMissing: List<String>,
            val importantMissing: List<String>,
        )

        fun addMissingFields(
            index: Int,
            name: String,
            allFields: List<String>,
            criticalFields: List<String>,
            importantFields: List<String>,
        ) {
            itemsWithMissingFields[index] = MissingFieldData(name, allFields, criticalFields, importantFields)
        }

        fun logReport() {
            // Kritik field eksik olan kayıtları say (bunlar gerçekten eksik)
            val itemsWithCriticalMissing = itemsWithMissingFields.values.count { it.criticalMissing.isNotEmpty() }
            val itemsWithOnlyImportantMissing = itemsWithMissingFields.size - itemsWithCriticalMissing

            if (itemsWithCriticalMissing == 0 && itemsWithOnlyImportantMissing == 0) {
                return
            }

            // Field bazında istatistik (sadece önemli field'lar için)
            val fieldStats = mutableMapOf<String, Int>()
            itemsWithMissingFields.values.forEach { data ->
                data.importantMissing.forEach { field ->
                    fieldStats[field] = (fieldStats[field] ?: 0) + 1
                }
            }
        }

        /**
         * Field bazında eksiklik yüzdesini döndürür.
         */
        fun getFieldMissingPercentage(fieldName: String): Int {
            val count = itemsWithMissingFields.values.count { it.allMissing.contains(fieldName) }
            return if (totalCount > 0) (count * 100 / totalCount) else 0
        }

        /**
         * En çok eksik olan field'ları döndürür.
         */
        fun getMostMissingFields(limit: Int = 5): List<Pair<String, Int>> {
            val fieldStats = mutableMapOf<String, Int>()
            itemsWithMissingFields.values.forEach { data ->
                data.allMissing.forEach { field ->
                    fieldStats[field] = (fieldStats[field] ?: 0) + 1
                }
            }
            return fieldStats.toList().sortedByDescending { it.second }.take(limit)
        }
    }
}
