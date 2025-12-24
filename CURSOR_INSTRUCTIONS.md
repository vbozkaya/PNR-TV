# Cursor Instruction: Finalize "Resume Playback" Feature

Please complete the "Resume Playback" (Kaldığı Yerden Devam Et) feature by applying the following changes to make it production-ready. The infrastructure supports the requirements; we only need to adjust the threshold and fix a playback visual glitch.

## 1. Update Resume Threshold to 10 Minutes
The user wants the resume dialog to appear only if the content has been watched for more than **10 minutes** (currently set to 1 minute for testing).

*   **File:** `app/src/main/java/com/pnr/tv/ui/movies/MovieDetailViewModel.kt`
    *   **Function:** `shouldShowResumeDialog`
    *   **Action:** Change the threshold variable `oneMinuteInMs` to `tenMinutesInMs` (value: `10 * 60 * 1000L`). Update the logic and comments to reflect the 10-minute rule.

*   **File:** `app/src/main/java/com/pnr/tv/ui/series/SeriesDetailViewModel.kt`
    *   **Function:** `shouldShowResumeDialog`
    *   **Action:** Change the threshold variable `oneMinuteInMs` to `tenMinutesInMs` (value: `10 * 60 * 1000L`). Update the logic and comments to reflect the 10-minute rule.

## 2. Fix Playback "Jump" Glitch (Race Condition)
Currently, when resuming, the video starts at 0:00 for a split second before jumping to the saved position. This is because `player.play()` is called before the asynchronous database check completes.

*   **File:** `app/src/main/java/com/pnr/tv/ui/player/PlayerViewModel.kt`
    *   **Functions:** `playVideo` and `playPlaylist`
    *   **Action:** Refactor the playback start logic.
        1.  Remove the immediate `player?.play()` call at the beginning of the function.
        2.  Inside the coroutine that fetches `contentRepository.getPlaybackPosition(contentId)`:
            *   **If a saved position exists:** Call `player?.seekTo(position)` **first**, then call `player?.play()`.
            *   **If no position exists:** Call `player?.play()` immediately.
        3.  Ensure `player?.play()` is also called immediately if `contentId` is null (no resume logic needed).

This ensures the user sees the frame at the resumed timestamp immediately, without seeing the beginning of the video first.