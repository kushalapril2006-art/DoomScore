package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test

class FocusedFeedWindowTest {
    @Test fun floatingAccessibilityWindowDoesNotStealTheFocusedApplication() {
        assertEquals(1,FocusedFeedWindow.select(listOf(FeedWindow(false,false),FeedWindow(true,true),FeedWindow(true,false))))
    }
    @Test fun unfocusedCachedAndPictureInPictureAppsAreNeverUsed() {
        assertNull(FocusedFeedWindow.select(listOf(FeedWindow(false,true),FeedWindow(true,false))))
    }
    @Test fun twoFocusedApplicationWindowsAreAmbiguous() {
        assertNull(FocusedFeedWindow.select(listOf(FeedWindow(true,true),FeedWindow(true,true))))
    }
    @Test fun missingWindowDoesNotReuseThePreviousApp() {assertNull(FocusedFeedWindow.select(emptyList()))}
}
