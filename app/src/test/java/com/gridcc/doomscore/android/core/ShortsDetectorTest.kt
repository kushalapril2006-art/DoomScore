package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test

class ShortsDetectorTest {
    private val detector=FeedDetector("shorts-fixture-salt")
    private fun feed(player:String="shorts_player",title:String="A cooking tutorial",description:String="Shorts player")=listOf(
        UiNode(id="reel_watch_fragment_root",top=0,bottom=1000,right=500),
        UiNode(id=player,description=description,top=150,bottom=780,right=500,parent=0),
        UiNode(id="reel_title",text=title,top=800,bottom=850,right=450,parent=0),
        UiNode(id="reel_channel_name",text="@fixture_creator",top=860,bottom=900,right=450,parent=0))
    private fun detect(nodes:List<UiNode>)=detector.detect(SourceApp.YOUTUBE,nodes,1000,0,0,500)
    @Test fun legacyAndModernPlayerLayoutsIncludeSiblingMetadata() {
        for(player in listOf("reel_watch_player","reel_player","shorts_player","shorts_player_container")) assertNotNull("Player $player",detect(feed(player)))
    }
    @Test fun currentShortsMetadataIdsAreRecognized() {
        val modern=feed().map {when(it.id) {"reel_title"->it.copy(id="shorts_video_title");"reel_channel_name"->it.copy(id="shorts_channel_name");else->it}}
        assertEquals(detect(feed()),detect(modern))
    }
    @Test fun pageRootCanExposeTitleWithoutASeparateAccessibleVideoNode() {
        assertNotNull(detect(feed().filter {it.id!="shorts_player"}))
    }
    @Test fun changingPlayerStateDoesNotManufactureAnotherShort() {
        val playing=detect(feed(description="Shorts player, playing, 123 likes"))
        assertNotNull(playing)
        assertEquals(playing,detect(feed(description="Shorts player, paused, 999 likes")))
        assertNotEquals(playing,detect(feed(title="A different cooking tutorial")))
    }
    @Test fun titleAboutAdsIsNotAPlatformDisclosure() {
        assertFalse(detect(feed(title="Sponsored"))!!.ad)
        assertTrue(detect(feed()+UiNode(id="ad_disclosure",text="Sponsored",top=920,bottom=960,right=400,parent=0))!!.ad)
    }
    @Test fun sameCreatorWithoutVideoIdentityFailsClosed() {
        assertNull(detect(feed().filter {it.id!="reel_title"}))
    }
    @Test fun knownShortsWithoutCaptionKeepsPresentationWithoutCounting() {
        val result=detector.inspect(SourceApp.YOUTUBE,feed().filter {it.id!="reel_title"},1000,0,0,500)
        assertTrue(result.feedVisible);assertNull(result.observation)
        assertFalse(detector.inspect(SourceApp.YOUTUBE,listOf(UiNode(text="Shorts",top=0,bottom=1000,right=500)),1000).feedVisible)
        assertFalse(detector.inspect(SourceApp.YOUTUBE,feed()+UiNode(editable=true,top=700,bottom=800,right=500,parent=0),1000).feedVisible)
    }
    @Test fun toolbarTitleAndShortsNavigationDoNotIdentifyAVideo() {
        assertNull(detect(feed().filter {it.id!="reel_title"}+UiNode(id="title",text="YouTube",top=10,bottom=40,right=300)))
        assertNull(detect(listOf(UiNode(id="title",text="Shorts",top=0,bottom=1000,right=500))))
    }
    @Test fun cachedPageSiblingTitleAndAdCannotPolluteCurrentShort() {
        val cached=listOf(UiNode(id="reel_watch_fragment_root",top=990,bottom=1990,right=500),
            UiNode(id="shorts_player",top=1100,bottom=1700,right=500,parent=4),
            UiNode(id="reel_title",text="A stale next title",top=810,bottom=850,right=450,parent=4),
            UiNode(id="ad_disclosure",text="Sponsored",top=860,bottom=900,right=450,parent=4))
        assertEquals(detect(feed()),detect(feed()+cached))
    }
    @Test fun twoOverlappingShortsPlayersFailClosed() {
        assertNull(detect(feed()+UiNode(id="shorts_player",top=150,bottom=780,right=500,parent=-1)))
    }
    @Test fun legacyCachedPlayersSharingAFeedRootDoNotExcludeTheCurrentPage() {
        val cached=listOf(UiNode(id="shorts_player",top=990,bottom=1800,right=500,parent=0),
            UiNode(id="reel_title",text="A cached title",top=810,bottom=850,right=450,parent=4))
        assertEquals(detect(feed()),detect(feed()+cached))
    }
    @Test fun commentsAndSplitScreenRemainSafe() {
        assertNull(detect(feed()+UiNode(editable=true,top=700,bottom=800,right=500,parent=0)))
        val shifted=feed().map {it.copy(top=it.top/2+1000,bottom=it.bottom/2+1000,left=100,right=it.right+100)}
        assertNotNull(detector.detect(SourceApp.YOUTUBE,shifted,1500,1000,100,600))
    }
    private fun virtualPanel(caption:String="A virtual-view cooking tutorial",subscribe:String="Subscribe to creator",audio:String="Remix this sound")=listOf(
        UiNode(id="reel_watch_fragment_root",top=0,bottom=1000,right=500), // 0
        UiNode(id="reel_player_underlay",top=0,bottom=1000,right=500,parent=0),
        UiNode(id="reel_watch_refresher",top=0,bottom=1000,right=500,parent=0),
        UiNode(id="reel_player_page_container",top=0,bottom=1000,right=500,parent=2),
        UiNode(id="reel_player_overlay_v2_root",top=0,bottom=1000,right=500,parent=3),
        UiNode(id="reel_watch_player",top=0,bottom=1000,right=500,parent=3),
        UiNode(id="metapanel",top=720,bottom=900,right=450,parent=4),
        UiNode(top=720,bottom=900,right=450,parent=6),
        UiNode(top=720,bottom=800,right=450,parent=7),
        UiNode(top=800,bottom=860,right=450,parent=7),
        UiNode(top=860,bottom=900,right=450,parent=7),
        UiNode(description="Visit fixture creator channel",className="android.widget.ImageView",top=730,bottom=780,right=50,parent=8),
        UiNode(description=caption,className="android.view.ViewGroup",top=810,bottom=850,right=450,parent=9),
        UiNode(description=audio,className="android.view.ViewGroup",top=865,bottom=895,right=450,parent=10),
        UiNode(description=subscribe,className="android.view.ViewGroup",top=730,bottom=780,left=300,right=450,parent=8),
        UiNode(className="android.widget.ImageView",top=870,bottom=890,right=20,parent=13))
    @Test fun realYouTubePageDecorationsAndIdlessCaptionPanelAreSupported() {
        val first=detect(virtualPanel());assertNotNull(first)
        assertEquals(first,detect(virtualPanel(subscribe="Subscribed to creator",audio="Remix another sound")))
        assertNotEquals(first,detect(virtualPanel(caption="Another virtual-view cooking tutorial")))
        assertNull(detect(virtualPanel(caption="")))
    }
    @Test fun optionalAudioRowDoesNotChangeIdentityOrBecomeAnUntitledVideo() {
        val full=virtualPanel()
        assertEquals(detect(full),detect(full.mapIndexed {index,node->if(index in setOf(10,13,15)) node.copy(visible=false) else node}))
        assertNull(detect(full.mapIndexed {index,node->if(index in setOf(9,12)) node.copy(visible=false) else node}))
    }
}
