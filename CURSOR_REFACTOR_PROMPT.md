# 🎯 Android TV Focus & Navigation Refactoring Task

**Role:** Senior Android Engineer (TV Specialist)
**Objective:** Refactor `BaseBrowseFragment` to remove flaky, time-based focus logic (`postDelayed`) and replace it with a deterministic, lifecycle-aware architecture.

## 🛑 The Problem
The current codebase relies on "patching" focus behavior. It uses:
1.  `postDelayed` (10ms, 50ms) to force focus after data loads.
2.  Retry loops to "catch" lost focus.
3.  Aggressive locking of the Navbar (setting `isFocusable = false` continuously) during navigation.

**Result:** Focus "jumps" visibly (e.g., Navbar -> Category -> Content) and is unreliable during back navigation.

## ✅ Acceptance Criteria

### 1. Deterministic Initial Load
- When the fragment opens, focus must land **immediately** on the first category (Index 0).
- No visual jumping from Navbar or empty space.
- **Requirement:** Use `RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY` or `doOnPreDraw` instead of arbitrary delays.

### 2. Clean Back Navigation (Restore State)
- When returning from a detail screen (Film/Live/Series), focus must return **directly** to the item the user left.
- The correct Category must be selected.
- **Requirement:** The UI must wait for data to be ready before drawing, to prevent focus from defaulting to the top-left and then snapping to the correct position.

### 3. Remove "Focus Fighting" Code
- **DELETE** the logic inside `observeSelectedCategory` that constantly sets Navbar views to `isFocusable = false`. The Navbar should remain focusable; we simply control *where* focus goes via `nextFocusUp` or KeyListeners, not by disabling the views.
- **DELETE** the retry loops and "aggressive focus protection" blocks in `BaseBrowseFragment`.
- **DELETE** `restoreNavbarFocusability` flag checks that prevent valid restoration.

## 🛠️ Technical Implementation Steps for Cursor

1.  **Analyze `BaseBrowseFragment.kt`:**
    - Locate `observeCategories` and `observeContents`.
    - Identify and remove the `postDelayed`, `Handler`, and `retry` blocks used for focus.
    
2.  **Fix RecyclerView Configuration:**
    - In `createAdapters` or `initializeViews`, set `adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY`. This tells the RecyclerView to wait for data before restoring state, fixing the "jump".

3.  **Refactor Navbar Logic:**
    - Remove the code block in `observeSelectedCategory` that disables Navbar focusability.
    - Simplify `navigateFocusToNavbar`. It should just request focus. If the layout is correct, it will work without disabling other views.

4.  **Synchronize State:**
    - Ensure `categoryAdapter.submitList` and `contentAdapter.submitList` commit their data before we attempt to restore specific selections. Use `commitCallback` or `doOnPreDraw` to request focus *only* when the view is actually laid out.

## 📂 Key Files
- `app/src/main/java/com/pnr/tv/ui/base/BaseBrowseFragment.kt` (Main logic)
- `app/src/main/java/com/pnr/tv/ui/browse/CategoryAdapter.kt`
- `app/src/main/java/com/pnr/tv/ui/browse/ContentAdapter.kt`

---
**Instruction to AI:** Do not apply "band-aid" fixes. If the architecture needs to wait for two flows (Categories + Content) to be ready before determining focus, implement that coordination rather than using a timer.