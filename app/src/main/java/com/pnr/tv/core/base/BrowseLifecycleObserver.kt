package com.pnr.tv.core.base

import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pnr.tv.ui.main.MainActivity

/**
 * BaseBrowseFragment için lifecycle yönetimini otomatikleştiren observer.
 *
 * Bu observer, fragment lifecycle metodlarını (onResume, onPause, onSaveInstanceState)
 * otomatik olarak yönetir ve focusManager ile MainActivity navigation handler'ı koordine eder.
 *
 * Fragment içinde bu metodları override etmeye gerek kalmaz.
 * Not: onCreate fragment'ta kalmalı çünkü observer'ı orada initialize ediyoruz.
 */
class BrowseLifecycleObserver(
    private val focusManager: BrowseFocusManager,
    private val fragment: BaseBrowseFragment,
    private val arguments: Bundle?,
) : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        // Toolbar'ı gizle
        (fragment.activity as? ToolbarController)?.hideTopMenu()

        // ContentRecyclerView'ı refresh et (Player'dan dönüş için)
        fragment.refreshContentRecyclerViewIfNeeded()

        // Focus manager'ı resume et
        focusManager.onResume(arguments)

        // MainActivity navigation handler'ı disable et
        (fragment.activity as? MainActivity)?.navigationHandler?.disableBackCallbackForBrowseFragment()
    }

    override fun onPause(owner: LifecycleOwner) {
        // Focus manager'ı pause et
        focusManager.onPause()

        // MainActivity navigation handler'ı enable et
        (fragment.activity as? MainActivity)?.navigationHandler?.enableBackCallbackForBrowseFragment()
    }
}
