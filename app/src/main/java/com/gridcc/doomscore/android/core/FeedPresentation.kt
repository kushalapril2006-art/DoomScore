package com.gridcc.doomscore.android.core

/** A short UI grace period never extends the counter's required readable dwell. */
class FeedPresentation(private val graceMs:Long=900) {
    private var lastSource:SourceApp?=null
    private var lastReadable:Long?=null
    fun active(source:SourceApp?,readable:Boolean,now:Long):SourceApp? {
        if(source==null || source!=lastSource) reset()
        if(source==null) return null
        if(readable) {lastSource=source;lastReadable=now;return source}
        val last=lastReadable ?: return null
        if(now-last in 0..graceMs) return source
        reset();return null
    }
    fun reset() {lastSource=null;lastReadable=null}
}
