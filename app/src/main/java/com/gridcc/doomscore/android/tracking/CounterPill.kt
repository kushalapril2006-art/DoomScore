package com.gridcc.doomscore.android.tracking

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gridcc.doomscore.android.core.ScrollTier

/** A bounded, non-focusable island; taps outside its pill still reach the reel app. */
class CounterPill(context: Context): LinearLayout(context) {
    private val density=resources.displayMetrics.density
    private fun dp(v:Int)=(v*density).toInt()
    private val mascot=ImageView(context)
    private val score=TextView(context).apply {textSize=15f;typeface=android.graphics.Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER}
    private var lastCount=-1
    private var level=-1
    private var pulse:ValueAnimator?=null
    init {
        orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        setPadding(dp(7),dp(5),dp(13),dp(5));minimumHeight=dp(48)
        addView(mascot,LayoutParams(dp(38),dp(38)))
        addView(score,LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT).apply {marginStart=dp(5)})
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        mascot.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        score.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun update(count:Int) {
        val tier=ScrollTier.of(count)
        if(level!=tier.level) {
            level=tier.level;mascot.setImageBitmap(GoobIcon.bitmap(tier));score.setTextColor(tier.argb)
            background=GradientDrawable().apply {setColor(0xF509090F.toInt());cornerRadius=dp(28).toFloat();setStroke(dp(1).coerceAtLeast(1),tier.argb)}
        }
        if(lastCount==count) return
        val previous=lastCount;lastCount=count
        score.text="$count reels"
        contentDescription="Goob: $count reels today, ${tier.title}. Tap to open Doomscore. Drag to move."
        pulse?.cancel()
        if(previous>=0 && count>previous && ValueAnimator.areAnimatorsEnabled()) {
            pulse=ValueAnimator.ofFloat(1f,1.08f,1f).apply {duration=220;addUpdateListener {mascot.scaleX=it.animatedValue as Float;mascot.scaleY=mascot.scaleX};start()}
        } else {mascot.scaleX=1f;mascot.scaleY=1f}
    }
    override fun onDetachedFromWindow() {pulse?.cancel();pulse=null;super.onDetachedFromWindow()}
}
