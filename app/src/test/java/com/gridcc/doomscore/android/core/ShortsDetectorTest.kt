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
    @Test fun captionCanBeAboveTheCreatorRatherThanRowOne() {
        val original=virtualPanel()
        val swapped=original.mapIndexed {i,node->when(i) {
            8->node.copy(top=800,bottom=860);9->node.copy(top=720,bottom=800)
            11,14->node.copy(top=810,bottom=850);12->node.copy(top=730,bottom=780)
            else->node}}
        assertEquals(detect(original),detect(swapped))
    }
    @Test fun overlappingWrapperBoundsDoNotHideReadableLeafCaptions() {
        val original=virtualPanel()
        assertEquals(detect(original),detect(original.mapIndexed {i,node->if(i in 8..10) node.copy(top=720,bottom=900) else node}))
    }
    @Test fun singleCaptionPanelAndLocalizedShortTitlesCount() {
        for(title in listOf("Hi","猫","😊","एक छोटी कहानी","Un nouveau départ","قصة قصيرة")) {
            val nodes=listOf(UiNode(id="shorts_player",top=0,bottom=1000,right=500),
                UiNode(id="shorts_metadata",top=700,bottom=950,right=450,parent=0),
                UiNode(text=title,className="android.widget.TextView",top=800,bottom=840,right=450,parent=1))
            assertNotNull("Visible title $title",detect(nodes))
            assertEquals(detect(nodes),detector.detect(SourceApp.YOUTUBE,nodes.map {it.copy(top=it.top*2,bottom=it.bottom*2,right=it.right*2)},2000,0,0,1000))
        }
    }
    @Test fun wrappersDeeperThanEightLevelsStillExposeCaption() {
        val nodes=mutableListOf(UiNode(id="shorts_player",top=0,bottom=1000,right=500),UiNode(id="metapanel",top=700,bottom=950,right=450,parent=0))
        repeat(20) {nodes+=UiNode(top=700,bottom=950,right=450,parent=nodes.lastIndex)}
        nodes+=UiNode(description="A deeply nested caption",top=800,bottom=840,right=450,parent=nodes.lastIndex)
        assertNotNull(detect(nodes))
    }
    @Test fun nestedCaptionLinesDontHaveToBeInSiblingRowOne() {
        val base=virtualPanel().mapIndexed {i,node->if(i==12) node.copy(description="") else node}
        val first=base+UiNode(text="First caption line",top=805,bottom=825,right=450,parent=12)+UiNode(text="Second caption line",top=825,bottom=850,right=450,parent=12)
        assertNotNull(detect(first))
        assertNotEquals(detect(first),detect(first.map {if(it.text=="Second caption line") it.copy(text="A different second line") else it}))
    }
    @Test fun captionChevronIsNotAnAudioThumbnail() {
        val original=virtualPanel()
        assertEquals(detect(original),detect(original+UiNode(id="caption_expand_chevron",className="android.widget.ImageView",top=820,bottom=840,left=420,right=450,parent=12)))
    }
    @Test fun explicitTitleCanOwnAnIconOrExposeTextInChildren() {
        val direct=feed()
        assertEquals(detect(direct),detect(direct+UiNode(className="android.widget.ImageView",top=810,bottom=840,left=420,right=450,parent=2)))
        val nested=direct.map {if(it.id=="reel_title") it.copy(text="") else it}+UiNode(text="A cooking tutorial",className="android.widget.TextView",top=800,bottom=850,right=450,parent=2)
        assertEquals(detect(direct),detect(nested))
    }
    @Test fun controlsAloneMustNeverBecomeAnUntitledShort() {
        val controls=listOf(UiNode(id="shorts_player",top=0,bottom=1000,right=500),UiNode(id="metapanel",top=700,bottom=950,right=450,parent=0),
            UiNode(description="Subscribe to creator",top=720,bottom=760,right=450,parent=1),
            UiNode(text="Share",className="android.widget.Button",top=770,bottom=800,right=450,parent=1),
            UiNode(id="audio_title",text="A catchy song",top=830,bottom=860,right=450,parent=1))
        assertNull(detect(controls))
        assertNull(detect(controls.map {if(it.description.isNotBlank()) it.copy(description="Abonnieren") else it}))
    }
    @Test fun changingGenericPlayerAnnouncementsCannotCreateAVideoIdentity() {
        val unknown=feed(description="Playing the YouTube Shorts video, 00:12 elapsed").filter {it.id!="reel_title"}
        assertNull(detect(unknown))
    }
    @Test fun ordinaryWatchMetadataDoesNotQualifyAsShorts() {
        val watch=listOf(UiNode(id="watch_player",top=0,bottom=1000,right=500),UiNode(id="metapanel",top=700,bottom=950,right=450,parent=0),UiNode(text="An ordinary long video",top=800,bottom=850,right=450,parent=1))
        assertNull(detect(watch))
    }
    @Test fun realCaptionWordsAreNotRemovedAsPlaybackControls() {
        assertNotNull(detect(feed(title="Saved")))
        assertNotNull(detect(feed(title="Liked")))
        assertNotEquals(detect(feed(title="Saved")),detect(feed(title="Liked")))
    }
    @Test fun emptyLegacyTitlePlaceholderDoesNotHideAVirtualCaption() {
        val current=virtualPanel()
        assertEquals(detect(current),detect(current+UiNode(id="reel_title",top=800,bottom=850,right=450,parent=3)))
    }
    @Test fun metadataPanelWithoutAResourceIdUsesCreatorAndCaptionStructure() {
        val current=virtualPanel()
        val idless=current.map {if(it.id=="metapanel") it.copy(id="") else it}
        assertEquals(detect(current),detect(idless))
        assertNotEquals(detect(idless),detect(idless.map {if(it.description=="A virtual-view cooking tutorial") it.copy(description="Another caption without IDs") else it}))
    }
    @Test fun idlessCreatorHandleAndCaptionDoNotNeedAnAvatar() {
        val nodes=listOf(UiNode(id="shorts_player",top=0,bottom=1000,right=500),UiNode(top=700,bottom=950,right=450,parent=0),
            UiNode(description="@fixture_creator",top=720,bottom=760,right=250,parent=1),UiNode(description="A caption without panel IDs",top=800,bottom=850,right=450,parent=1),
            UiNode(description="Subscribe",top=720,bottom=760,left=300,right=450,parent=1))
        assertNotNull(detect(nodes))
        assertEquals(detect(nodes),detect(nodes.map {if(it.description=="Subscribe") it.copy(description="Subscribed") else it}))
        assertNull(detect(nodes.filter {it.description!="A caption without panel IDs"}))
    }
    @Test fun temporarilyHiddenAudioIconCannotPolluteCaptionIdentity() {
        val current=virtualPanel()
        assertEquals(detect(current),detect(current.mapIndexed {i,node->if(i==15) node.copy(visible=false) else node}))
    }
    @Test fun delegatedPromotionsAreNotInferredAsOrganicCaptionGroups() {
        val nodes=listOf(UiNode(id="shorts_player",top=0,bottom=1000,right=500),UiNode(id="reel_player_delegated_overlay",top=700,bottom=950,right=450,parent=0),
            UiNode(description="Brand thumbnail",className="android.widget.ImageView",top=720,bottom=760,right=50,parent=1),
            UiNode(description="A changing promotion",top=800,bottom=850,right=450,parent=1),UiNode(description="Install",top=860,bottom=900,right=450,parent=1))
        assertNull(detect(nodes))
    }
    @Test fun realCaptionWithClickableHashtagsStillIdentifiesAShort() {
        val current=virtualPanel(caption="A cooking tutorial #food #recipe")
        val linked=current+UiNode(description="#food",className="android.widget.Button",top=820,bottom=850,left=200,right=290,parent=12)+
            UiNode(description="#recipe",className="android.widget.Button",top=820,bottom=850,left=300,right=430,parent=12)
        assertEquals(detect(current),detect(linked))
        assertNotEquals(detect(linked),detect(linked.map {if(it.description=="A cooking tutorial #food #recipe") it.copy(description="A different cooking tutorial #food #recipe") else it}))
    }
    @Test fun nestedVirtualLinkLabelsDoNotMaskTheirCaptionOrBecomeIdentity() {
        val current=virtualPanel(caption="An Arabic caption with a clickable link")
        val linked=current+UiNode(className="android.widget.Button",top=820,bottom=850,left=200,right=430,parent=12)+
            UiNode(description="Open link",className="android.view.ViewGroup",top=820,bottom=850,left=200,right=430,parent=16)
        assertEquals(detect(current),detect(linked))
        assertEquals(detect(linked),detect(linked.map {if(it.description=="Open link") it.copy(description="Visit link") else it}))
    }
    @Test fun mergedCaptionKeepsFullIdentityWhenTagsAreVirtualTextInsteadOfButtons() {
        val current=virtualPanel(caption="A cooking tutorial #food #recipe")
        val linked=current+UiNode(description="#food",className="android.view.ViewGroup",top=820,bottom=850,left=200,right=290,parent=12)+
            UiNode(description="#recipe",className="android.view.ViewGroup",top=820,bottom=850,left=300,right=430,parent=12)
        assertEquals(detect(current),detect(linked))
        val different=linked.map {if(it.description=="A cooking tutorial #food #recipe") it.copy(description="Another cooking tutorial #food #recipe") else it}
        assertNotEquals(detect(linked),detect(different))
    }
    private fun flatPanel(title:String?)=listOf(
        UiNode(id="shorts_player",top=0,bottom=1000,right=500),UiNode(id="metapanel",top=700,bottom=950,right=450,parent=0),
        UiNode(description="Visit fixture creator channel",className="android.widget.ImageView",top=720,bottom=780,right=50,parent=1),
        UiNode(description="Fixture creator name",className="android.view.ViewGroup",top=720,bottom=780,left=60,right=250,parent=1)
    )+(title?.let {listOf(UiNode(description=it,className="android.view.ViewGroup",top=800,bottom=850,right=450,parent=1))} ?: emptyList())
    @Test fun flatCreatorLabelBesideAvatarIsNotAVideoCaption() {
        assertNull(detect(flatPanel(null)))
        assertNotNull(detect(flatPanel("A flat caption")))
    }
    @Test fun changingFlatCreatorControlsDoesNotChangeCaptionIdentity() {
        val current=flatPanel("A flat caption")
        assertEquals(detect(current),detect(current.map {if(it.description=="Fixture creator name") it.copy(description="Another creator button announcement") else it}))
        assertNotEquals(detect(current),detect(flatPanel("Another flat caption")))
    }
}
