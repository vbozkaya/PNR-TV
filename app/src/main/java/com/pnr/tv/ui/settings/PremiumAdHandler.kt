package com.pnr.tv.ui.settings

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.pnr.tv.R
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.premium.RewardedAdManager
import com.pnr.tv.util.validation.AdultContentPreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Premium ödüllü reklam işlemlerini yöneten yardımcı sınıf.
 * PremiumSettingsFragment'teki ödüllü reklam mantığını buraya taşır.
 */
class PremiumAdHandler
    @Inject
    constructor(
        private val rewardedAdManager: RewardedAdManager,
        private val adultContentPreferenceManager: AdultContentPreferenceManager,
    ) {
        /**
         * Ödüllü reklam diyalogu gösterir ve reklam akışını başlatır.
         * Kullanıcı reklam izleyerek 15 dakikalık geçici erişim kazanır.
         *
         * @param context Dialog oluşturmak ve string resources almak için
         * @param activity Reklam göstermek için
         * @param lifecycleScope Coroutine'ler için
         * @param focusedView Focus yönetimi için (TV için önemli)
         */
        fun showRewardedAdDialog(
            context: Context,
            activity: Activity,
            lifecycleScope: LifecycleCoroutineScope,
            focusedView: View,
        ) {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null)
            val dialog = Dialog(context)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(dialogView)

            val window = dialog.window
            window?.setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
            window?.setBackgroundDrawableResource(android.R.color.transparent)

            val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_title)
            val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_message)
            val btnYes = dialogView.findViewById<android.widget.TextView>(R.id.btn_dialog_yes)
            val btnNo = dialogView.findViewById<android.widget.TextView>(R.id.btn_dialog_no)

            tvTitle?.text = context.getString(R.string.adult_content_reward_dialog_title)
            tvMessage?.text = context.getString(R.string.adult_content_reward_dialog_message)
            btnYes?.text = context.getString(R.string.adult_content_reward_dialog_yes)
            btnNo?.text = context.getString(R.string.adult_content_reward_dialog_no)

            btnYes?.setOnClickListener {
                dialog.dismiss()
                // Reklam yükleme ve gösterme
                lifecycleScope.launch {
                    if (!rewardedAdManager.isRewardedAdLoaded()) {
                        // Reklam yüklenmemiş, önce yükle
                        context.showCustomToast(context.getString(R.string.adult_content_reward_ad_loading), Toast.LENGTH_SHORT)
                        rewardedAdManager.loadRewardedAd()

                        // Reklam yüklenmesini bekle (max 10 saniye)
                        var waited = 0
                        while (!rewardedAdManager.isRewardedAdLoaded() && waited < 10000) {
                            delay(500)
                            waited += 500
                        }

                        if (!rewardedAdManager.isRewardedAdLoaded()) {
                            context.showCustomToast(context.getString(R.string.adult_content_reward_ad_error), Toast.LENGTH_LONG)
                            // Focus'u geri ver
                            focusedView.post {
                                focusedView.requestFocus()
                            }
                            return@launch
                        }
                    }

                    // Reklam göster
                    val adShown =
                        rewardedAdManager.showRewardedAd(
                            activity = activity,
                            onUserEarnedReward = {
                                // Kullanıcı ödül kazandı - 15 dakikalık erişim ver
                                lifecycleScope.launch {
                                    val accessUntil = System.currentTimeMillis() + (15 * 60 * 1000) // 15 dakika
                                    adultContentPreferenceManager.setTemporaryAdultAccessUntil(accessUntil)
                                    adultContentPreferenceManager.setAdultContentEnabled(true)
                                    context.showCustomToast(
                                        context.getString(R.string.adult_content_reward_dialog_title),
                                        Toast.LENGTH_SHORT,
                                    )
                                }
                            },
                            onAdClosed = {
                                // Reklam kapandı - focus'u geri ver
                                focusedView.post {
                                    focusedView.requestFocus()
                                }
                                // Bir sonraki kullanım için reklamı yeniden yükle
                                rewardedAdManager.loadRewardedAd()
                            },
                        )

                    if (!adShown) {
                        context.showCustomToast(context.getString(R.string.adult_content_reward_ad_error), Toast.LENGTH_LONG)
                        focusedView.post {
                            focusedView.requestFocus()
                        }
                    }
                }
            }

            btnNo?.setOnClickListener {
                dialog.dismiss()
                // Focus'u geri ver
                focusedView.post {
                    focusedView.requestFocus()
                }
            }

            dialog.setCancelable(false)
            dialog.show()

            // Güvenlik için "İptal" butonuna focus ver
            btnNo?.requestFocus()
        }
    }
