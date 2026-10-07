package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test

class ReelCounterEngineTest {
    private class Sink : CounterSink {
        val events=mutableListOf<CounterEvent>();val history=mutableMapOf<String,Long>();var watched=0L
        override fun recentlySeen(app:SourceApp,fingerprint:String,wall:Long) = history[app.key+fingerprint]?.let{wall-it<=300_000} ?: false
        override fun remember(app:SourceApp,fingerprint:String,wall:Long) {history[app.key+fingerprint]=wall}
        override fun event(kind:CounterEvent,app:SourceApp,wall:Long) {events+=kind}
        override fun watch(app:SourceApp,from:Long,to:Long) {watched+=to-from}
    }
    private val a=Observation(SourceApp.INSTAGRAM,"A")
    private val b=Observation(SourceApp.INSTAGRAM,"B")
    @Test fun countsOnlyAfterDwellAndNeverAgainWhileLooping() {
        val sink=Sink();val engine=ReelCounterEngine(sink)
        engine.observe(a,0,1000);engine.observe(a,749,1749);assertTrue(sink.events.isEmpty())
        engine.observe(a,750,1750);engine.observe(a,1000,2000);engine.observe(a,2000,3000)
        assertEquals(listOf(CounterEvent.COUNT),sink.events);assertEquals(2000L,sink.watched)
    }
    @Test fun returningToARecentReelSkipsItOnce() {
        val sink=Sink();val engine=ReelCounterEngine(sink)
        engine.observe(a,0,1000);engine.observe(a,750,1750)
        engine.observe(b,800,1800);engine.observe(b,1550,2550)
        engine.observe(a,1600,2600);engine.observe(a,2350,3350);engine.observe(a,2550,3550)
        assertEquals(listOf(CounterEvent.COUNT,CounterEvent.COUNT,CounterEvent.REWATCH_SKIPPED),sink.events)
    }
    @Test fun fastSwipeDoesNotCountOrPoisonHistory() {
        val sink=Sink();val engine=ReelCounterEngine(sink)
        engine.observe(a,0,1000);engine.observe(b,200,1200);engine.observe(a,400,1400);engine.observe(a,1150,2150)
        assertEquals(listOf(CounterEvent.COUNT),sink.events);assertFalse(sink.history.containsKey("instagramB"))
    }
    @Test fun adsNeitherCountNorAddOrganicViewingTime() {
        val sink=Sink();val engine=ReelCounterEngine(sink);val ad=a.copy(ad=true)
        engine.observe(ad,0,1000);engine.observe(ad,750,1750);engine.observe(ad,2000,3000)
        assertEquals(listOf(CounterEvent.AD_SKIPPED),sink.events);assertEquals(0L,sink.watched);assertTrue(sink.history.isEmpty())
    }
    @Test fun leavingTheAppCancelsPendingCount() {
        val sink=Sink();val engine=ReelCounterEngine(sink)
        engine.observe(a,0,1000);engine.observe(null,400,1400);engine.observe(null,2000,3000)
        assertTrue(sink.events.isEmpty())
    }
    @Test fun recentHistoryCanBeReusedAfterServiceRestart() {
        val sink=Sink();val engine=ReelCounterEngine(sink)
        engine.observe(a,0,1000);engine.observe(a,750,1750)
        ReelCounterEngine(sink).apply {observe(a,1000,2000);observe(a,1750,2750)}
        assertEquals(listOf(CounterEvent.COUNT,CounterEvent.REWATCH_SKIPPED),sink.events)
    }
    @Test fun reelCountsAgainAfterHistoryExpires() {
        val sink=Sink();val engine=ReelCounterEngine(sink)
        engine.observe(a,0,1000);engine.observe(a,750,1750);engine.stop()
        engine.observe(a,400000,401000);engine.observe(a,400750,401750)
        assertEquals(listOf(CounterEvent.COUNT,CounterEvent.COUNT),sink.events)
    }
    @Test fun sameMetadataInDifferentAppsDoesNotCollide() {
        val sink=Sink();val engine=ReelCounterEngine(sink)
        engine.observe(a,0,1000);engine.observe(a,750,1750)
        val yt=a.copy(app=SourceApp.YOUTUBE)
        engine.observe(yt,800,1800);engine.observe(yt,1550,2550)
        assertEquals(listOf(CounterEvent.COUNT,CounterEvent.COUNT),sink.events)
    }
    @Test fun aClockJumpDoesNotAccumulateHoursAfterCounting() {
        val sink=Sink();val engine=ReelCounterEngine(sink)
        engine.observe(a,0,1000);engine.observe(a,750,1750);engine.observe(a,1000,1000000)
        assertEquals(750L,sink.watched)
    }
    @Test fun aClockJumpDuringDwellDoesNotManufactureViewingTime() {
        val sink=Sink();val engine=ReelCounterEngine(sink)
        engine.observe(a,0,1000);engine.observe(a,750,1000000)
        assertEquals(listOf(CounterEvent.COUNT),sink.events)
        assertEquals(750L,sink.watched)
    }
}
