package com.pnr.tv.premium

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Premium özellik kontrolü için guard sınıfı.
 * PremiumManager'ı wrap ederek premium kontrolü yapar.
 */
@Singleton
class PremiumFeatureGuard
    @Inject
    constructor(
        private val premiumManager: PremiumManager,
    ) {
        /**
         * Premium durumunu Flow olarak döner.
         */
        fun isPremium(): Flow<Boolean> = premiumManager.isPremium()

        /**
         * Premium durumunu senkron olarak döner.
         */
        suspend fun isPremiumSync(): Boolean = premiumManager.isPremiumSync()
    }
