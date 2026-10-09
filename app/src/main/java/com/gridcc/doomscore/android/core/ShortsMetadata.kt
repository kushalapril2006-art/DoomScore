package com.gridcc.doomscore.android.core

import java.util.IdentityHashMap
import java.util.Locale

/** Resolve semantic fields within an already verified Shorts page, never the whole YouTube screen.
 * Virtual captions may have no IDs, any row order, overlapping wrappers or several text children.
 * Resource IDs/roles take precedence; unknown icon rows and action controls are not video identity.
 */
internal class ShortsMetadata(private val all:List<UiNode>, region:List<UiNode>) {
    private val indices=IdentityHashMap<UiNode,Int>().apply {all.forEachIndexed {i,node->put(node,i)}}
    private val allowed=region.mapNotNull {indices[it]}.toSet()
    private val ids=all.map {it.id.substringAfterLast('/').lowercase(Locale.ROOT)}
    private val values=all.map {it.text.ifBlank {it.description}.trim()}
    private fun id(index:Int)=ids[index]
    private fun raw(index:Int)=values[index]
    private val panelIds=setOf("reel_watch_metadata","shorts_metadata","reel_metadata","shorts_metadata_container")
    private val genericTitleIds=setOf("title","caption","description")
    private val channelIds=setOf("channel_name","channel_handle","channel_avatar","creator_avatar")
    private val controlIds=listOf("subscribe","like_button","dislike","comment_button","share_button","reel_sound","audio","music","remix","reel_pivot","ad_disclosure","sponsored_label")
    private fun findAncestors(index:Int):List<Int> {
        val result=mutableListOf<Int>();var current=all[index].parent
        while(current in all.indices && current !in result && result.size<64) {result+=current;current=all[current].parent}
        return result
    }
    private val ancestry=Array(all.size) {findAncestors(it)}
    private fun ancestors(index:Int)=ancestry[index]
    private fun within(index:Int,root:Int)=index==root || root in ancestors(index)
    private fun panel(index:Int)=id(index).let {it.contains("metapanel") || it in panelIds}
    private fun titleId(index:Int)=id(index).let {it.contains("reel_title") || it.contains("shorts_title") || it.contains("shorts_video_title") || it.contains("shorts_video_caption") ||
        (it in genericTitleIds && ancestors(index).any(::panel))}
    private fun channelId(index:Int)=id(index).let {it.contains("reel_channel") || it.contains("shorts_channel") || it in channelIds}
    private fun decoration(index:Int)=id(index).let {it.contains("chevron") || it.contains("expand") || it.contains("more_icon")}
    private fun image(index:Int)=all[index].className.endsWith("ImageView") && !decoration(index)
    private fun actionId(index:Int)=id(index).let {value->controlIds.any {value.contains(it)}}
    private val actionWords=setOf("subscribe","subscribed","subscription","like","dislike","share","comments","more","remix","install","download","shop now","learn more","not interested",
        "abonnieren","abonniert","s'abonner","abonné","suscribirse","suscrito","inscrever-se","inscrito","iscriviti","iscritto",
        "подписаться","подписки","सदस्यता लें","सब्सक्राइब करें","購読","チャンネル登録","登録済み","구독","구독중","订阅","已订阅")
    private val adWords=setOf("sponsored","ad","advertisement","paid partnership","publicité","gesponsert","patrocinado","प्रायोजित","विज्ञापन")
    private val numericOnly=Regex("[\\p{N}\\p{P}\\p{Z}\\s]+")
    private fun action(index:Int)=actionId(index) || all[index].className.let {it.endsWith("Button") || it.endsWith("CheckBox") || it.endsWith("SeekBar")} || raw(index).lowercase(Locale.ROOT).let {it in actionWords || it.startsWith("subscribe to ") || it.startsWith("subscribed to ") || it.startsWith("remix this ")}
    private val textFields=allowed.filter {raw(it).isNotBlank()}
    private val images=allowed.filter(::image)
    private val allImages=all.indices.filter(::image)
    private val handlePattern=Regex("@[\\p{L}\\p{N}_.-]{1,100}")
    private fun handle(index:Int)=handlePattern.matches(raw(index))
    private fun blockedContainer(index:Int)=id(index).let {it.contains("delegated_overlay") || it.contains("pivot_bar") || it.contains("toolbar") || it.contains("navigation") || actionId(index)}
    // In some virtual layouts the metadata panel itself has no resource ID. Infer only a compact
    // creator + separate caption group inside the verified page, away from the right action rail.
    private val pageLeft=region.minOfOrNull {it.left} ?: 0
    private val pageRight=region.maxOfOrNull {it.right} ?: 0
    private val pageTop=region.minOfOrNull {it.top} ?: 0
    private val pageBottom=region.maxOfOrNull {it.bottom} ?: 0
    private val pageWidth=(pageRight-pageLeft).coerceAtLeast(1)
    private val pageHeight=(pageBottom-pageTop).coerceAtLeast(1)
    private val markers=textFields.filter {channelId(it) || handle(it) || image(it)}.filter {index->
        all[index].left<pageLeft+pageWidth*2/5 && !action(index) && ancestors(index).none(::panel) && !ancestors(index).any(::blockedContainer)
    }
    private val inferredPanels=markers.flatMap {marker->ancestors(marker).filter {group->
        val bounds=all[group]
        group in allowed && !blockedContainer(group) && bounds.bottom-bounds.top in 1..(pageHeight*2/3) &&
            bounds.right-bounds.left>=pageWidth/3 && bounds.left<pageLeft+pageWidth*2/5 &&
            textFields.any {caption->caption!=marker && within(caption,group) && !image(caption) && !handle(caption) && !channelId(caption) && !action(caption) &&
                all[caption].right-all[caption].left>=pageWidth/4 &&
                (all[caption].top>=all[marker].bottom || all[caption].bottom<=all[marker].top)}
    }}.toSet()
    private fun owner(index:Int)=ancestors(index).firstOrNull(::panel) ?: ancestors(index).firstOrNull {it in inferredPanels}
    private fun iconRow(index:Int,panel:Int):Boolean {
        // An audio/action virtual node often owns its icon; a known expand chevron is harmless.
        if(allImages.any {within(it,index)}) return true
        val node=all[index];val center=node.top.toLong()+(node.bottom.toLong()-node.top)/2
        for(parent in ancestors(index).let {it.take((it.indexOf(panel)+1).coerceAtLeast(0))}) {
            // Creator avatar and subscription/channel label sit beside each other. Caption wrappers
            // may overlap vertically, so use visible leaf geometry rather than parent row bounds.
            if(images.any {other->within(other,parent) && !within(other,index) &&
                all[other].top<=center && all[other].bottom>=center &&
                (node.left>=all[other].right || node.right<=all[other].left)}) return true
        }
        return false
    }
    private val whitespace=Regex("\\s+")
    private val normalizedValues=values.map {it.replace(whitespace," ").lowercase(Locale.ROOT)}
    private fun embedded(parent:Int,child:Int)=normalizedValues[child].isNotBlank() && normalizedValues[parent].contains(normalizedValues[child])
    private fun virtualTitle(index:Int):Boolean {
        val owner=owner(index) ?: return false
        if(raw(index).isBlank() || numericOnly.matches(raw(index)) || image(index) || channelId(index) || handle(index) || action(index) || raw(index).lowercase(Locale.ROOT) in adWords) return false
        if(ancestors(index).takeWhile {it!=owner}.any {actionId(it) || channelId(it) || all[it].className.endsWith("Button")}) return false
        // Captions can own clickable hashtag/link buttons. Keep their aggregate caption;
        // reject only wrappers that repeat independent, non-action text descendants.
        if(textFields.any {child->child!=index && within(child,index) && !image(child) && !action(child) && !embedded(index,child) &&
                ancestors(child).takeWhile {it!=index}.none(::action)}) return false
        return !iconRow(index,owner)
    }
    private val explicitRoots=allowed.filter(::titleId)
    private val explicitTitles=explicitRoots.flatMap {root->
        if(raw(root).isNotBlank()) listOf(root) else allowed.filter {within(it,root) && raw(it).isNotBlank() && !image(it) && !action(it) && !ancestors(it).takeWhile {it!=root}.any(::actionId)}
    }
    private val virtualTitles=textFields.filter(::virtualTitle).let {fields->fields.filter {child->
        fields.none {parent->parent!=child && within(child,parent) && embedded(parent,child)}
    }}
    val titles:Set<UiNode> = explicitTitles.ifEmpty {virtualTitles}.map {all[it]}.toSet()
    val channels:Set<UiNode> = textFields.filter {index->
        raw(index).isNotBlank() && (channelId(index) || ((image(index) || handle(index)) && owner(index)!=null))
    }.filter {index->
        // Caption expansion icons, audio thumbnails and their changing labels must stay out.
        !decoration(index) && !actionId(index) && !ancestors(index).takeWhile {it!=owner(index)}.any(::actionId) &&
            (channelId(index) || handle(index) || !textFields.any {other->other!=index && !image(other) && within(index,other)})
    }.map {all[it]}.toSet()
}
