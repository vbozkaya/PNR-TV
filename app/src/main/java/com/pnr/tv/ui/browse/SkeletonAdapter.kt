package com.pnr.tv.ui.browse

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R

/**
 * Skeleton loading için basit adapter.
 * Loading durumunda içerik grid'i yerine skeleton item'lar gösterir.
 */
class SkeletonAdapter(
    private val skeletonCount: Int = 10, // Varsayılan skeleton item sayısı
) : RecyclerView.Adapter<SkeletonAdapter.SkeletonViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): SkeletonViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_skeleton_content_item, parent, false)
        // Skeleton item'lar focusable olmamalı
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        return SkeletonViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: SkeletonViewHolder,
        position: Int,
    ) {
        holder.startShimmerAnimation()
    }

    override fun getItemCount(): Int = skeletonCount

    class SkeletonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val skeletonPoster: View = itemView.findViewById(R.id.skeleton_poster)
        private val skeletonTitle: View = itemView.findViewById(R.id.skeleton_title)

        private var shimmerAnimator: ObjectAnimator? = null

        init {
            // ViewHolder oluşturulduğunda shimmer animasyonunu başlat
            startShimmerAnimation()
        }

        fun startShimmerAnimation() {
            // Önceki animasyonu durdur
            stopShimmerAnimation()

            // Alpha animasyonu ile shimmer efekti oluştur
            shimmerAnimator =
                ObjectAnimator.ofFloat(skeletonPoster, "alpha", 0.5f, 1.0f).apply {
                    duration = 1000L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                }

            // Her iki View için de animasyon başlat (stagger effect için farklı delay)
            shimmerAnimator?.start()

            // Title için de aynı animasyonu başlat (biraz gecikme ile)
            ObjectAnimator.ofFloat(skeletonTitle, "alpha", 0.5f, 1.0f).apply {
                duration = 1000L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                startDelay = 200L // Poster'dan biraz sonra başlasın
                start()
            }
        }

        fun stopShimmerAnimation() {
            shimmerAnimator?.cancel()
            shimmerAnimator = null
            skeletonPoster.alpha = 1.0f
            skeletonTitle.alpha = 1.0f
        }
    }
}
