package com.gridcc.doomscore.android.core

import java.security.MessageDigest
import java.util.Locale

data class UiNode(val id: String = "", val text: String = "", val description: String = "", val visible: Boolean = true,
    val top: Int = 0, val bottom: Int = 0, val left: Int = 0, val right: Int = 0, val parent: Int = -1,
    val className: String = "", val editable: Boolean = false) {
    val content get() = listOf(text, description).filter { it.isNotBlank() }.distinct().joinToString(" ")
}

data class FeedDetection(val observation:Observation?=null,val feedVisible:Boolean=false)

/** UI heuristics, not video recognition. Uncertain/non-feed screens fail closed. No raw strings leave this method. */
class FeedDetector(private val salt: String) {
    private val adLabels = setOf("sponsored", "ad", "advertisement", "paid partnership", "publicité", "gesponsert", "patrocinado", "प्रायोजित", "विज्ञापन")
    private val volatile = Regex("(?i)\\b[\\d,.]+\\s*[kmb]?\\s+(likes?|comments?|shares?|views?|plays?)\\b|\\b(liked|not liked|saved|not saved)\\b")
    fun detect(app: SourceApp, all: List<UiNode>, height: Int, top:Int=0, left:Int=0, right:Int=Int.MAX_VALUE): Observation? = inspect(app,all,height,top,left,right).observation
    fun inspect(app: SourceApp, all: List<UiNode>, height: Int, top:Int=0, left:Int=0, right:Int=Int.MAX_VALUE): FeedDetection {
        if(height<=top || right<=left) return FeedDetection()
        val viewportHeight=height-top
        val nodes = all.filter { it.visible && it.bottom > top && it.top < height &&
            (it.right<=it.left || (it.right>left && it.left<right)) }
        if (nodes.isEmpty()) return FeedDetection()
        if (nodes.any { it.editable || it.id.contains("comment_thread_edittext") || it.id.contains("comment_input") || it.id.contains("igds_snackbar") }) return FeedDetection()
        val anchors = when (app) {
            SourceApp.INSTAGRAM -> {
                if (nodes.none { it.id.endsWith("clips_viewer_view_pager") }) return FeedDetection()
                nodes.filter { it.id.endsWith("clips_video_container") || it.id.endsWith("clips_media_component") }
            }
            SourceApp.YOUTUBE -> {
                val pages=nodes.filter {it.id.substringAfterLast('/')=="reel_player_page_container"}
                val players=nodes.filter {youtubePlayer(it)}
                // Underlays, overlays and the recycler are decorations, not separate videos.
                when {pages.isNotEmpty()->pages;players.isNotEmpty()->players;else->nodes.filter {youtubePageRoot(it)}}
            }
            SourceApp.TIKTOK -> {
                val hasFeed = nodes.any { it.description.startsWith("Video by ", true) || it.id.contains("video_container") || it.id.contains("feed_view") }
                if (!hasFeed || nodes.count { Regex("(?i)like|comment|share").containsMatchIn(it.content) } < 2) return FeedDetection()
                nodes.filter { it.description.startsWith("Video by ", true) || it.id.contains("video_container") }
            }
            SourceApp.SNAPCHAT -> {
                if (nodes.none { it.content.equals("Spotlight", true) || it.id.contains("spotlight") }) return FeedDetection()
                nodes.filter { it.id.contains("spotlight_video") || it.description.startsWith("Video by ", true) }
            }
        }
        // Two pages can be 'visible' during a swipe. Don't guess which reel the user chose.
        val center = top + viewportHeight / 2
        val centerX = left.toLong() + (right.toLong()-left)/2
        val centered = anchors.filter { it.top <= center && it.bottom >= center && it.bottom - it.top >= viewportHeight / 3 &&
            (right==Int.MAX_VALUE || (it.left<=centerX && it.right>=centerX)) }
        val anchor = centered.maxByOrNull { (it.bottom - it.top).toLong() * (it.right - it.left).coerceAtLeast(1) } ?: return FeedDetection()
        // During swipes, two unrelated pages must not compete for the same viewport centre.
        val anchorIndex=all.indexOf(anchor)
        if(centered.any {it!==anchor && !descendantOf(all,it,anchorIndex) && !descendantOf(all,anchor,all.indexOf(it))}) return FeedDetection()
        val pageIndex=if(app==SourceApp.YOUTUBE) youtubePage(all,anchorIndex) else anchorIndex
        val otherPages=(anchors.filter {it!==anchor && !descendantOf(all,it,anchorIndex) && !descendantOf(all,anchor,all.indexOf(it))}.map {
            val index=all.indexOf(it)
            if(app==SourceApp.YOUTUBE) youtubePage(all,index).let {if(it==pageIndex) index else it} else index
        } + if(app==SourceApp.YOUTUBE) all.indices.filter {it!=pageIndex && it!=anchorIndex && youtubePageRoot(all[it]) && !descendantOf(all,anchor,it) && !descendantOf(all,all[it],anchorIndex)} else emptyList()).distinct()
        // Shorts metadata can be below a letterboxed player, beside it, or in a sibling overlay.
        // Keep it within the app viewport and exclude the cached neighbouring pages.
        val region = nodes.filter { (if(app==SourceApp.YOUTUBE) {
                it.top>=top && it.bottom<=height && (pageIndex==anchorIndex || it===all.getOrNull(pageIndex) || descendantOf(all,it,pageIndex))
            } else it.top >= anchor.top.coerceAtLeast(top) && it.bottom <= anchor.bottom.coerceAtMost(height)) &&
            (it.right<=it.left || (it.left>=(if(app==SourceApp.YOUTUBE) left else anchor.left.coerceAtLeast(left)) && it.right<=(if(app==SourceApp.YOUTUBE) right else anchor.right.coerceAtMost(right)))) &&
            otherPages.none {page -> it===all.getOrNull(page) || descendantOf(all,it,page)} }
        val ad = anchor.description.startsWith("Sponsored Reel by ", true) || region.any { node ->
            // A caption saying "Sponsored" is not itself the platform's ad disclosure.
            !(node.id.contains("clips_caption_component") || ancestorContains(all,node,"clips_caption_component") ||
                (app==SourceApp.YOUTUBE && youtubeTitle(all,node))) && sequenceOf(node.text, node.description).any { raw -> raw.split('\n', '·', '•', '|', ',').any { part ->
                val s = part.trim().lowercase(Locale.ROOT)
                s in adLabels || s.startsWith("paid partnership with ")
            } }
        }
        val desc = stable(anchor.description)
        val captionNodes = region.filter { node ->
            // Caption overlays can be siblings of the player on legitimate app layouts.
            when (app) {
                SourceApp.INSTAGRAM -> node.id.contains("clips_caption_component") || ancestorContains(all, node, "clips_caption_component")
                SourceApp.YOUTUBE -> youtubeTitle(all,node) || youtubeChannel(all,node)
                SourceApp.TIKTOK -> node.id.contains("desc") || node.id.contains("caption") || node.id.contains("author") || node.id.contains("user_name")
                SourceApp.SNAPCHAT -> node.id.contains("caption") || node.id.contains("username") || node.id.contains("title")
            }
        }
        val metadata = captionNodes.map { stable(if(app==SourceApp.YOUTUBE) it.text.ifBlank {it.description} else it.content) }.filter { it.length > 2 }.distinct().sorted().joinToString("|")
        val titleReadable=app!=SourceApp.YOUTUBE || captionNodes.any {youtubeTitle(all,it) && stable(it.text.ifBlank {it.description}).length>2}
        val meaningfulDescription=desc.length>12 && !Regex("(?:youtube )?(?:shorts? |reel )?(?:video )?player|video|shorts? video").matches(desc)
        val identity = when {
            // Player descriptions also announce playback/like state. They are not video IDs.
            metadata.isNotBlank() && titleReadable -> if(app==SourceApp.YOUTUBE) metadata else "$desc|$metadata"
            meaningfulDescription -> if(metadata.isNotBlank()) "$desc|$metadata" else desc
            ad -> "ad|" + region.map { stable(it.content) }.filter { it.length > 4 }.distinct().sorted().joinToString("|")
            else -> return FeedDetection(feedVisible=true)
        }
        val hash = MessageDigest.getInstance("SHA-256").digest("$salt|${app.key}|$identity".toByteArray()).joinToString("") { "%02x".format(it) }
        return FeedDetection(Observation(app, hash, ad),true)
    }
    private fun youtubePlayer(node:UiNode):Boolean {
        val id=node.id.substringAfterLast('/')
        return id in setOf("reel_watch_player","reel_watch_player_container","reel_player","reel_player_view","shorts_player","shorts_player_container")
    }
    private fun youtubeTitle(all:List<UiNode>,node:UiNode):Boolean {
        val id=node.id.substringAfterLast('/')
        return id.contains("reel_title") || id.contains("shorts_title") || id.contains("shorts_video_title") ||
            id.contains("shorts_video_caption") || (id=="title" && (ancestorContains(all,node,"reel_watch_metadata") || ancestorContains(all,node,"shorts_metadata"))) ||
            (youtubeMetadataRow(all,node)==1 && node.className!="android.widget.ImageView" &&
                all.none {it.className=="android.widget.ImageView" && descendantOf(all,it,all.indexOf(node))} &&
                stable(node.text.ifBlank {node.description}) !in adLabels)
    }
    private fun youtubeChannel(all:List<UiNode>,node:UiNode):Boolean {
        val id=node.id.substringAfterLast('/')
        return id.contains("reel_channel") || id.contains("shorts_channel") || id=="channel_name" || id=="channel_handle" ||
            (youtubeMetadataRow(all,node)==0 && node.className=="android.widget.ImageView" && node.description.isNotBlank())
    }
    /** Modern virtual-view captions have no IDs. The panel is creator/caption/optional audio.
     * Read only the caption and creator; Subscribe, sound and playback controls are not identity.
     * Unknown/overlapping layouts fail closed instead of hashing every changing control. */
    private fun youtubeMetadataRow(all:List<UiNode>,node:UiNode):Int? {
        if(node.text.isBlank() && node.description.isBlank()) return null
        if(!ancestorContains(all,node,"metapanel")) return null
        var branch=all.indexOf(node)
        repeat(16) {
            val parent=all.getOrNull(branch)?.parent ?: return null
            val container=all.getOrNull(parent) ?: return null
            val rows=all.indices.filter {all[it].parent==parent && all[it].visible && all[it].bottom>all[it].top}.sortedBy {all[it].top}
            if(rows.size>=2 && rows.zipWithNext().all {(a,b)->all[a].bottom<=all[b].top}) return rows.indexOf(branch).takeIf {it>=0}
            if(container.id.substringAfterLast('/')=="metapanel") return null
            branch=parent
        }
        return null
    }
    private fun youtubePage(all:List<UiNode>,anchor:Int):Int {
        if(all.getOrNull(anchor)?.let {youtubePageRoot(it)}==true) return anchor
        var index=all.getOrNull(anchor)?.parent ?: return anchor
        repeat(32) {
            val node=all.getOrNull(index) ?: return anchor
            if(youtubePageRoot(node)) return index
            index=node.parent
        }
        return anchor
    }
    private fun youtubePageRoot(node:UiNode)=node.id.substringAfterLast('/') in setOf("reel_watch_fragment_root","shorts_watch_fragment_root","shorts_page","reel_player_page","reel_player_page_container")
    private fun descendantOf(all:List<UiNode>,node:UiNode,ancestor:Int):Boolean {
        if(ancestor<0) return false
        var index=node.parent
        repeat(32) {
            if(index==ancestor) return true
            index=all.getOrNull(index)?.parent ?: return false
        }
        return false
    }
    private fun ancestorContains(all: List<UiNode>, node: UiNode, needle: String): Boolean {
        var index = node.parent
        repeat(8) {
            val parent = all.getOrNull(index) ?: return false
            if (parent.id.contains(needle)) return true
            index = parent.parent
        }
        return false
    }
    private fun stable(raw: String) = volatile.replace(raw, " ").replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT).take(600)
}
