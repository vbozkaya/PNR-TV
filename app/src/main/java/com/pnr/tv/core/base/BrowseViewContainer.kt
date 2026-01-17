package com.pnr.tv.core.base

import android.view.View
import android.widget.Button
import android.widget.TextView

/**
 * Browse fragment'larındaki tüm view referanslarını tutan container sınıfı.
 *
 * Bu sınıf, findViewById çağrılarını ve view referanslarını merkezi bir yerde toplar,
 * böylece BaseBrowseFragment daha temiz ve stateless bir yapıya yaklaşır.
 */
data class BrowseViewContainer(
    val rootView: View,
    val categoriesRecyclerView: CustomCategoriesRecyclerView,
    val contentRecyclerView: CustomContentRecyclerView,
    val emptyStateTextView: TextView,
    val errorContainer: View? = null,
    val errorMessage: TextView? = null,
    val retryButton: Button? = null,
    val loadingContainer: View? = null,
    val emptyStateContainer: View? = null,
    val navbarView: View? = null,
) {
    companion object {
        /**
         * View'dan tüm view referanslarını toplar ve BrowseViewContainer oluşturur.
         */
        fun from(
            view: View,
            categoriesRecyclerViewId: Int,
            contentRecyclerViewId: Int,
            emptyStateTextViewId: Int,
        ): BrowseViewContainer {
            return BrowseViewContainer(
                rootView = view,
                categoriesRecyclerView = view.findViewById(categoriesRecyclerViewId),
                contentRecyclerView = view.findViewById(contentRecyclerViewId),
                emptyStateTextView = view.findViewById(emptyStateTextViewId),
                errorContainer = view.findViewById(com.pnr.tv.R.id.error_container),
                errorMessage = view.findViewById(com.pnr.tv.R.id.txt_error_message),
                retryButton = view.findViewById(com.pnr.tv.R.id.btn_retry),
                loadingContainer = view.findViewById(com.pnr.tv.R.id.loading_container),
                emptyStateContainer = view.findViewById(com.pnr.tv.R.id.empty_state_container),
                navbarView = view.findViewById(com.pnr.tv.R.id.navbar),
            )
        }
    }
}
