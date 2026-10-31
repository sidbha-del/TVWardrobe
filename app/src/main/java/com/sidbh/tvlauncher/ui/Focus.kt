package com.sidbh.tvlauncher.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/** Lazy lists compose their items during layout, so the requester may not be attached for a few frames. */
suspend fun requestFocusWhenReady(requester: FocusRequester) {
    repeat(10) {
        withFrameNanos { }
        if (runCatching { requester.requestFocus() }.isSuccess) return
    }
}
