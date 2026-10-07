package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test

class FeedDetectorTest {
    private val detector=FeedDetector("test-install-salt")
    private fun feed(caption:String="A cooking tutorial",desc:String="Reel by creator. 123 likes")=listOf(
        UiNode(id="com.instagram.android:id/clips_viewer_view_pager",top=0,bottom=1000,right=500),
        UiNode(id="com.instagram.android:id/clips_video_container",description=desc,top=100,bottom=950,right=500,parent=0),
        UiNode(id="com.instagram.android:id/clips_caption_component",text=caption,top=800,bottom=900,right=450,parent=1))
    @Test fun identifiesAReelUsingHashedMetadata() {val observation=detector.detect(SourceApp.INSTAGRAM,feed(),1000)!!;assertFalse(observation.ad);assertEquals(64,observation.fingerprint.length);assertFalse(observation.fingerprint.contains("cooking"))}
    @Test fun ordinaryFeedAndProfilesAreNotCounted() {assertNull(detector.detect(SourceApp.INSTAGRAM,feed().drop(1),1000))}
    @Test fun recognizesSponsoredDescription() {assertTrue(detector.detect(SourceApp.INSTAGRAM,feed(desc="Sponsored Reel by fixture_brand"),1000)!!.ad)}
    @Test fun recognizesVisibleSponsoredLabel() {assertTrue(detector.detect(SourceApp.INSTAGRAM,feed()+UiNode(text="Sponsored",top=150,bottom=200,right=200),1000)!!.ad)}
    @Test fun offscreenAdDoesNotContaminateCurrentReel() {assertFalse(detector.detect(SourceApp.INSTAGRAM,feed()+UiNode(text="Sponsored",top=1100,bottom=1200),1000)!!.ad)}
    @Test fun controlBelowTheReelIsNotAnAdLabel() {assertFalse(detector.detect(SourceApp.INSTAGRAM,feed()+UiNode(text="AD",top=960,bottom=990,right=150),1000)!!.ad)}
    @Test fun captionsMentioningAdsAreNotTreatedAsAdLabels() {assertFalse(detector.detect(SourceApp.INSTAGRAM,feed(caption="Why this ad is funny"),1000)!!.ad)}
    @Test fun commentsPanelDoesNotProduceAReel() {assertNull(detector.detect(SourceApp.INSTAGRAM,feed()+UiNode(editable=true,top=600,bottom=650),1000))}
    @Test fun likeCountersDoNotChangeIdentity() {assertEquals(detector.detect(SourceApp.INSTAGRAM,feed(desc="Reel by creator. 123 likes"),1000)!!.fingerprint,detector.detect(SourceApp.INSTAGRAM,feed(desc="Reel by creator. 999 likes"),1000)!!.fingerprint)}
    @Test fun differentCaptionsChangeIdentity() {assertNotEquals(detector.detect(SourceApp.INSTAGRAM,feed(),1000)!!.fingerprint,detector.detect(SourceApp.INSTAGRAM,feed("A very different tutorial"),1000)!!.fingerprint)}
    @Test fun missingIdentityFailsClosed() {assertNull(detector.detect(SourceApp.INSTAGRAM,feed(caption="",desc="video"),1000))}
    @Test fun installationsHaveDifferentHashes() {assertNotEquals(detector.detect(SourceApp.INSTAGRAM,feed(),1000)!!.fingerprint,FeedDetector("different-install").detect(SourceApp.INSTAGRAM,feed(),1000)!!.fingerprint)}
    @Test fun offscreenAnchorsFailClosed() {assertNull(detector.detect(SourceApp.INSTAGRAM,feed().map{it.copy(top=1200,bottom=1800)},1000))}
}
