package com.gridcc.doomscore.fixture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*

/** Synthetic accessibility hierarchy. NEVER distribute or install over real Instagram. */
class FixtureActivity : Activity() {
    private val updates=android.os.Handler(android.os.Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {super.onCreate(savedInstanceState);render(intent.getStringExtra("reel") ?: "A")}
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);render(intent.getStringExtra("reel") ?: "A")}
    private fun render(reel: String) {
        updates.removeCallbacksAndMessages(null)
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(14,14,20));setPadding(16,16,16,16)}
        val header=TextView(this).apply {text="Doomscore synthetic reel feed";textSize=18f;setTextColor(Color.WHITE);setPadding(12,12,12,12)}
        root.addView(header)
        if(reel=="home") {root.addView(TextView(this).apply{text="Home · regular feed, no reels";setTextColor(Color.WHITE)});setContentView(root);return}
        val pager=FrameLayout(this).apply {id=R.id.clips_viewer_view_pager}
        val video=FrameLayout(this).apply {
            id=R.id.clips_video_container
            contentDescription=if(reel=="ad") "Sponsored Reel by fixture_brand" else "Reel by fixture_creator_$reel"
            setBackgroundColor(if(reel=="ad") Color.rgb(95,25,65) else Color.rgb(30,90,80))
        }
        val title=TextView(this).apply {text="${if(reel=="ad") "Sponsored" else "Reel $reel"}\nSynthetic test content";textSize=28f;setTextColor(Color.WHITE);gravity=Gravity.CENTER}
        video.addView(title,FrameLayout.LayoutParams(-1,-1))
        val caption=TextView(this).apply {
            id=R.id.clips_caption_component
            text="A stable caption for fixture reel $reel."
            contentDescription="A stable caption for fixture reel $reel."
            textSize=16f;setTextColor(Color.WHITE);setPadding(20,20,20,40)
        }
        video.addView(caption,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        if(reel=="comments") video.addView(EditText(this).apply{id=R.id.layout_comment_thread_edittext;hint="Add a comment"},FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))
        pager.addView(video,FrameLayout.LayoutParams(-1,-1))
        root.addView(pager,LinearLayout.LayoutParams(-1,0,1f))
        val controls=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        listOf("A","B","ad","comments","home").forEach {item->controls.addView(Button(this).apply{text=item;setOnClickListener{render(item)}},LinearLayout.LayoutParams(0,96,1f))}
        root.addView(controls)
        setContentView(root)
        if(intent.getBooleanExtra("busy",false)) {
            var ticks=0
            val tick=object:Runnable {
                override fun run() {
                    header.text="Synthetic playback · ${++ticks}"
                    if(ticks<100) updates.postDelayed(this,40)
                }
            }
            updates.postDelayed(tick,40)
        }
    }
    override fun onDestroy() {updates.removeCallbacksAndMessages(null);super.onDestroy()}
}
