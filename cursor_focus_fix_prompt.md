# Fix Initial Focus Race Condition in Live Streams

**Context:**
I am experiencing a race condition issue with initial focus assignment in the `LiveStreamsBrowseFragment` (and potentially other browse fragments).
Logs indicate that sometimes, when the initial category ("FAVORITES") is selected and focus is requested:
1.  Focus is requested on the TextView.
2.  A check 200ms later fails: `⚠️ Focus kayboldu! Mevcut focus: AppCompatTextView, Beklenen: TextView`.
3.  A retry mechanism (second check) successfully fixes it later.

**Diagnosis:**
This is a timing issue. The `RecyclerView` or `Adapter` might be recreating the view (rebinding) shortly after the initial focus request, causing the original View reference held by the check logic to become stale (detached), while the focus moves to the newly created identical View. The "Existing focus: AppCompatTextView" log strongly suggests focus is on the *correct type* of view, just not the *same instance* we checked against.

**Task:**
Refactor the initial focus logic in `BaseBrowseFragment.kt` (specifically inside `observeCategories` or `initializeViews`) to be more robust and event-driven, reducing reliance on arbitrary `postDelayed` timers.

**Required Changes:**

1.  **Modify `BaseBrowseFragment.kt`**:
    *   In the `observeCategories` collector, where the initial focus logic resides:
        ```kotlin
        // Current logic (conceptual):
        // if (isInitialLaunch) {
        //     onCategoryClicked(firstCategory)
        //     viewTreeObserver.addOnGlobalLayoutListener { ... requestFocus ... }
        // }
        ```
    *   **Improvement:** Instead of just checking `findViewHolderForAdapterPosition(0)` once inside the layout listener, implement a more robust mechanism using `post` on the *RecyclerView itself*, which guarantees the layout pass is complete.
    *   **Add a Safety Check:** Ensure `categoriesRecyclerView` doesn't have pending updates or layout requests before requesting focus.

2.  **Refine Focus Request Logic**:
    *   Use `categoriesRecyclerView.post { ... }` inside the `observeCategories` block after submitting the list.
    *   Inside the post block, find the ViewHolder.
    *   **Key Change:** If the ViewHolder is found, request focus and *immediately* check `hasFocus()`. If false, trigger a short retry loop (e.g., 3 attempts every 50ms) using a local handler/coroutine, but *searching for the view again* each time (dynamic lookup) instead of reusing the stale View reference.

3.  **Code Snippet Idea (for BaseBrowseFragment.kt)**:
    ```kotlin
    // Inside observeCategories flow collection:
    if (isInitialLaunch && categories.isNotEmpty() && pendingCategoryIdToRestore == null) {
        val firstCategory = categories.first()
        onCategoryClicked(firstCategory)

        // Robust Focus Logic
        categoriesRecyclerView.post {
            val viewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(0)
            if (viewHolder != null) {
                viewHolder.itemView.requestFocus()
                Timber.tag("FOCUS_DEBUG").d("✅ Initial focus requested on VH[0]")
            } else {
                // Fallback: If VH is null (unlikely inside post), try one more delayed attempt
                categoriesRecyclerView.postDelayed({
                   categoriesRecyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                }, 100)
            }
            // Reset flag
            arguments?.putBoolean("is_initial_launch", false)
        }
    }
    ```

**File Path:**
`C:/Users/vbozkaya/Desktop/PNR TV/app/src/main/java/com/pnr/tv/ui/base/BaseBrowseFragment.kt`

Please apply these changes to ensure the initial focus reliably lands on the first category without "ghosting" or needing long timeouts.