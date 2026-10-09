package com.gridcc.doomscore.android.core

/** Bounded local traversal. Visible branches get priority over cached/off-screen pages.
 * The caller owns the root. Every acquired child is released, including after a read failure.
 */
internal class UiSnapshotReader<N:Any>(private val access:Access<N>,private val limit:Int=1600,private val discoveryLimit:Int=4096) {
    interface Access<N:Any> {
        fun describe(node:N,parent:Int):UiNode
        fun visible(node:N):Boolean
        fun childCount(node:N):Int
        fun child(node:N,index:Int):N?
        fun release(node:N)
    }
    data class Result(val nodes:List<UiNode>,val truncated:Boolean)
    private data class Pending<N:Any>(val node:N,val parent:Int,val depth:Int,val owned:Boolean)
    fun read(root:N):Result {
        require(limit>0 && discoveryLimit>=limit)
        val visible=ArrayDeque<Pending<N>>();val deferred=ArrayDeque<Pending<N>>()
        visible.add(Pending(root,-1,0,false));val result=mutableListOf<UiNode>()
        var discovered=1;var truncated=false
        try {
            while((visible.isNotEmpty() || deferred.isNotEmpty()) && result.size<limit) {
                val item=if(visible.isNotEmpty()) visible.removeFirst() else deferred.removeFirst()
                try {
                    val index=result.size;result+=access.describe(item.node,item.parent)
                    val count=access.childCount(item.node).coerceAtLeast(0)
                    if(item.depth>=96) {if(count>0) truncated=true;continue}
                    // Acquire only within the fixed work budget. Classification never retains text.
                    val readCount=count.coerceAtMost((discoveryLimit-discovered).coerceAtLeast(0))
                    if(readCount<count) truncated=true
                    for(childIndex in 0 until readCount) {
                        val child=access.child(item.node,childIndex) ?: continue
                        discovered++
                        val next=Pending(child,index,item.depth+1,true)
                        // Queue before classification so cleanup also covers a provider exception.
                        deferred.addLast(next)
                        if(access.visible(child)) {deferred.removeLast();visible.addLast(next)}
                    }
                } finally {if(item.owned) access.release(item.node)}
            }
            return Result(result,truncated || visible.isNotEmpty() || deferred.isNotEmpty())
        } finally {
            while(visible.isNotEmpty()) access.release(visible.removeFirst().node)
            while(deferred.isNotEmpty()) access.release(deferred.removeFirst().node)
        }
    }
}
