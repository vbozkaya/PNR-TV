# Android Expert Task: Fix Watch Status Synchronization

## Context
The project is an Android TV application (PNR TV) using MVVM, Hilt, and Room Database.
Currently, there is a disconnect between the **Player** and the **Series Detail UI**.

## The Problem
The user reports: "Why do I see a Green (Fully Watched) status after watching a video for only 10 seconds and exiting?"

**Analysis:**
1.  **Visual Status Logic (`SeriesDetailViewModel.kt`):**
    The app determines the border color based on `WatchedEpisodeEntity.watchProgress`:
    - **Green (Completed):** `watchProgress >= 90`
    - **Red (In Progress):** `watchProgress > 10`
    - **White (Not Watched):** `watchProgress <= 10` or `null`

2.  **Player Logic (`PlayerViewModel.kt`):**
    Currently, the `PlayerViewModel` only saves the **Playback Position** (resume point) into `PlaybackPositionEntity` via `ContentRepository`.
    **CRITICAL MISSING LINK:** It *does not* update the `WatchedEpisodeEntity`.

**Root Cause:**
Since the player never updates `WatchedEpisodeEntity` automatically:
- The "Green" status the user sees is likely stale data (e.g., the episode was previously marked as watched manually, setting `watchProgress = 100`).
- A fresh watch session (e.g., 10 seconds) does not overwrite this status because no write operation happens to `WatchedEpisodeEntity` during playback.

## The Goal
Implement automatic synchronization so that watching a video updates the `watchProgress` in real-time (or periodically), reflecting the correct status (White/Red/Green) in the UI upon return.

## Implementation Plan

Please perform the following changes using Kotlin and Coroutines:

### 1. Update `PlayerViewModel.kt`
You need to inject `WatchedEpisodeDao` (or expose it through `ContentRepository`) to update the watch status.

**Logic to Add in `saveCurrentPosition` (and periodic save):**
1.  **Calculate Progress:**
    ```kotlin
    val progressPercentage = ((currentPosition.toFloat() / duration.toFloat()) * 100).toInt()
    ```
2.  **Update Database:**
    - If `contentId` corresponds to an episode (you may need to parse the ID or pass extra metadata), update `WatchedEpisodeEntity`.
    - Set `watchProgress` to `progressPercentage`.
    - Set `watchedTimestamp` to `System.currentTimeMillis()`.

### 2. Update `ContentRepository.kt` (Optional but Recommended)
Ideally, add a function `updateWatchProgress(contentId: String, progress: Int, position: Long)` to handle both `PlaybackPosition` and `WatchedEpisode` updates in one place, keeping the ViewModel clean.

### 3. Parse Episode Info
The `PlayerViewModel` currently receives a `contentId` (often a String). Ensure this maps correctly to the `episodeId` required by `WatchedEpisodeEntity`.
- *Note:* Check how `SeriesDetailViewModel` passes the ID. It seems to use `episodeId`.

## Expected Outcome
- If I watch 5% of a video -> Status becomes **White** (Not Watched).
- If I watch 50% of a video -> Status becomes **Red** (In Progress).
- If I watch 95% of a video -> Status becomes **Green** (Fully Watched).
- The `WatchedEpisodeEntity` table is updated automatically when the player stops or periodically saves.
