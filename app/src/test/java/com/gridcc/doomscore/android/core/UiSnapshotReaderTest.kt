package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test

class UiSnapshotReaderTest {
    private class Node(val label:String,val shown:Boolean=true,val children:List<Node> = emptyList())
    private class Access(private val failOn:String?=null):UiSnapshotReader.Access<Node> {
        val released=mutableListOf<Node>()
        override fun describe(node:Node,parent:Int):UiNode {if(node.label==failOn) error("provider disappeared");return UiNode(id=node.label,visible=node.shown,parent=parent)}
        override fun visible(node:Node)=node.shown
        override fun childCount(node:Node)=node.children.size
        override fun child(node:Node,index:Int)=node.children[index]
        override fun release(node:Node) {released+=node}
    }
    @Test fun activeCaptionIsReadBeforeHundredsOfHiddenCachedNodes() {
        val caption=Node("caption");val page=Node("active-page",children=listOf(caption))
        val hidden=List(1000) {Node("hidden-$it",false)};val root=Node("root",children=hidden+page)
        val access=Access();val result=UiSnapshotReader(access,limit=800,discoveryLimit=2048).read(root)
        assertEquals(listOf("root","active-page","caption"),result.nodes.take(3).map {it.id})
        assertEquals(1,result.nodes[2].parent);assertTrue(result.truncated)
        assertEquals(1002,access.released.size);assertEquals(access.released.size,access.released.toSet().size)
        assertFalse(root in access.released)
    }
    @Test fun invisibleWrapperCanStillHaveAnAccessibleCaption() {
        val root=Node("root",children=listOf(Node("wrapper",false,listOf(Node("caption")))))
        val result=UiSnapshotReader(Access()).read(root)
        assertEquals(listOf("root","wrapper","caption"),result.nodes.map {it.id});assertFalse(result.truncated)
    }
    @Test fun providerReadFailureReleasesCurrentAndQueuedChildren() {
        val children=listOf(Node("broken"),Node("queued"));val root=Node("root",children=children);val access=Access("broken")
        try {UiSnapshotReader(access).read(root);fail("Expected provider failure")} catch(_:IllegalStateException) {}
        assertEquals(children.toSet(),access.released.toSet());assertEquals(2,access.released.size)
    }
    @Test fun discoveryBudgetBoundsVeryWideHierarchies() {
        val access=Access();val result=UiSnapshotReader(access,limit=10,discoveryLimit=20).read(Node("root",children=List(10000) {Node("child-$it")}))
        assertEquals(10,result.nodes.size);assertTrue(result.truncated);assertEquals(19,access.released.size)
    }
}
