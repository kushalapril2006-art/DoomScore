package com.gridcc.doomscore.android.core

internal data class FeedWindow(val application:Boolean,val focused:Boolean)

/** Never guess the foreground app from cached/PiP windows or the last reel package. */
internal object FocusedFeedWindow {
    fun select(windows:List<FeedWindow>):Int? = windows.indices.filter {windows[it].application && windows[it].focused}.singleOrNull()
}
