package com.gridcc.doomscore.android.core

/** A new Android binding invalidates all work/presentation queued by an older binding. */
class CounterConnection {
    @Volatile private var generation=0L
    @Volatile var connected=false;private set
    @Synchronized fun bind():Long {generation++;connected=true;return generation}
    @Synchronized fun release() {connected=false;generation++}
    fun token()=generation
    fun accepts(token:Long)=connected && generation==token
}
