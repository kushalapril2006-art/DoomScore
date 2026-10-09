package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test

class FeedPresentationTest {
    @Test fun shortMetadataGapKeepsThePillButNotCountingContinuity() {
        val presentation=FeedPresentation();var count=0
        val sink=object:CounterSink {
            override fun recentlySeen(app:SourceApp,fingerprint:String,wall:Long)=false
            override fun remember(app:SourceApp,fingerprint:String,wall:Long) {}
            override fun event(kind:CounterEvent,app:SourceApp,wall:Long) {if(kind==CounterEvent.COUNT) count++}
            override fun watch(app:SourceApp,from:Long,to:Long) {}
        }
        val engine=ReelCounterEngine(sink);val short=Observation(SourceApp.YOUTUBE,"short")
        engine.observe(short,0,1000);presentation.active(SourceApp.YOUTUBE,true,0)
        engine.observe(null,500,1500)
        assertEquals(SourceApp.YOUTUBE,presentation.active(SourceApp.YOUTUBE,false,500))
        engine.observe(short,600,1600);presentation.active(SourceApp.YOUTUBE,true,600)
        engine.observe(short,1000,2000);assertEquals(0,count)
        engine.observe(short,1350,2350);assertEquals(1,count)
        assertNull(presentation.active(SourceApp.YOUTUBE,false,1501))
    }
    @Test fun leavingSwitchingAndResetNeverCarryPresentationIntoAnotherApp() {
        val p=FeedPresentation();p.active(SourceApp.YOUTUBE,true,0)
        assertNull(p.active(null,false,100));assertNull(p.active(SourceApp.YOUTUBE,false,200))
        p.active(SourceApp.YOUTUBE,true,300);assertNull(p.active(SourceApp.INSTAGRAM,false,400))
        p.active(SourceApp.YOUTUBE,true,500);p.reset();assertNull(p.active(SourceApp.YOUTUBE,false,600))
    }
}
