package com.gridcc.doomscore.android.core

import java.security.MessageDigest
import java.util.Locale

data class UiNode(val id: String = "", val text: String = "", val description: String = "", val visible: Boolean = true,
    val top: Int = 0, val bottom: Int = 0, val left: Int = 0, val right: Int = 0, val parent: Int = -1,
    val className: String = "", val editable: Boolean = false) {
    val content get() = listOf(text, description).filter { it.isNotBlank() }.distinct().joinToString(" ")
}

/** UI heuristics, not video recognition. Uncertain/non-feed screens fail closed. No raw strings leave this method. */
class FeedDetector(private val salt: String) {
    private val adLabels = setOf("sponsored", "ad", "advertisement", "paid partnership", "publicité", "gesponsert", "patrocinado", "प्रायोजित", "विज्ञापन")
    private val volatile = Regex("(?i)\\b[\\d,.]+\\s*[kmb]?\\s+(likes?|comments?|shares?|views?|plays?)\\b|\\b(liked|not liked|saved|not saved)\\b")
    fun detect(app: SourceApp, all: List<UiNode>, height: Int, top:Int=0, left:Int=0, right:Int=Int.MAX_VALUE): Observation? {
        if(height<=top || right<=left) return null
        val viewportHeight=height-top
        val nodes = all.filter { it.visible && it.bottom > top && it.top < height &&
            (it.right<=it.left || (it.right>left && it.left<right)) }
        if (nodes.isEmpty()) return null
        if (nodes.any { it.editable || it.id.contains("comment_thread_edittext") || it.id.contains("comment_input") || it.id.contains("igds_snackbar") }) return null
        val anchors = when (app) {
            SourceApp.INSTAGRAM -> {
                if (nodes.none { it.id.endsWith("clips_viewer_view_pager") }) return null
                nodes.filter { it.id.endsWith("clips_video_container") || it.id.endsWith("clips_media_component") }
            }
            SourceApp.YOUTUBE -> {
                if (nodes.none { it.id.contains("reel_watch") || it.id.contains("reel_player") || it.id.contains("shorts_player") }) return null
                nodes.filter { it.id.contains("reel_player") || it.id.contains("reel_watch") }
            }
            SourceApp.TIKTOK -> {
                val hasFeed = nodes.any { it.description.startsWith("Video by ", true) || it.id.contains("video_container") || it.id.contains("feed_view") }
                if (!hasFeed || nodes.count { Regex("(?i)like|comment|share").containsMatchIn(it.content) } < 2) return null
                nodes.filter { it.description.startsWith("Video by ", true) || it.id.contains("video_container") }
            }
            SourceApp.SNAPCHAT -> {
                if (nodes.none { it.content.equals("Spotlight", true) || it.id.contains("spotlight") }) return null
                nodes.filter { it.id.contains("spotlight_video") || it.description.startsWith("Video by ", true) }
            }
        }
        // Two pages can be 'visible' during a swipe. Don't guess which reel the user chose.
        val center = top + viewportHeight / 2
        val centerX = left.toLong() + (right.toLong()-left)/2
        val centered = anchors.filter { it.top <= center && it.bottom >= center && it.bottom - it.top >= viewportHeight / 3 &&
            (right==Int.MAX_VALUE || (it.left<=centerX && it.right>=centerX)) }
        val anchor = centered.maxByOrNull { (it.bottom - it.top).toLong() * (it.right - it.left).coerceAtLeast(1) } ?: return null
        // During swipes, two unrelated pages must not compete for the same viewport centre.
        val anchorIndex=all.indexOf(anchor)
        if(centered.any {it!==anchor && !descendantOf(all,it,anchorIndex) && !descendantOf(all,anchor,all.indexOf(it))}) return null
        val otherPages=anchors.filter {it!==anchor && !descendantOf(all,it,anchorIndex) && !descendantOf(all,anchor,all.indexOf(it))}.map {all.indexOf(it)}
        val region = nodes.filter { it.top >= anchor.top.coerceAtLeast(top) && it.bottom <= anchor.bottom.coerceAtMost(height) &&
            (it.right<=it.left || (it.left>=anchor.left.coerceAtLeast(left) && it.right<=anchor.right.coerceAtMost(right))) &&
            otherPages.none {page -> it===all.getOrNull(page) || descendantOf(all,it,page)} }
        val ad = anchor.description.startsWith("Sponsored Reel by ", true) || region.any { node ->
            // A caption saying "Sponsored" is not itself the platform's ad disclosure.
            !(node.id.contains("clips_caption_component") || ancestorContains(all,node,"clips_caption_component")) && sequenceOf(node.text, node.description).any { raw -> raw.split('\n', '·', '•', '|', ',').any { part ->
                val s = part.trim().lowercase(Locale.ROOT)
                s in adLabels || s.startsWith("paid partnership with ")
            } }
        }
        val desc = stable(anchor.description)
        val captionNodes = region.filter { node ->
            // Caption overlays can be siblings of the player on legitimate app layouts.
            when (app) {
                SourceApp.INSTAGRAM -> node.id.contains("clips_caption_component") || ancestorContains(all, node, "clips_caption_component")
                SourceApp.YOUTUBE -> node.id.contains("reel_title") || node.id.contains("reel_channel") || node.id.endsWith("title")
                SourceApp.TIKTOK -> node.id.contains("desc") || node.id.contains("caption") || node.id.contains("author") || node.id.contains("user_name")
                SourceApp.SNAPCHAT -> node.id.contains("caption") || node.id.contains("username") || node.id.contains("title")
            }
        }
        val metadata = captionNodes.map { stable(it.content) }.filter { it.length > 2 }.distinct().sorted().joinToString("|")
        val identity = when {
            metadata.isNotBlank() -> "$desc|$metadata"
            desc.length > 12 && !desc.equals("video", true) -> desc
            ad -> "ad|" + region.map { stable(it.content) }.filter { it.length > 4 }.distinct().sorted().joinToString("|")
            else -> return null
        }
        val hash = MessageDigest.getInstance("SHA-256").digest("$salt|${app.key}|$identity".toByteArray()).joinToString("") { "%02x".format(it) }
        return Observation(app, hash, ad)
    }
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
