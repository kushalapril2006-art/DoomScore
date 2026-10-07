package com.gridcc.doomscore.android.tracking

import android.graphics.*
import com.gridcc.doomscore.android.core.ScrollTier
import kotlin.math.*

/** Small native Goob for overlays and notification hosts; never reads screen pixels. */
object GoobIcon {
    fun bitmap(tier: ScrollTier, size: Int = 96): Bitmap {
        val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val c=size/2f;val r=size*.41f
        val path=Path()
        for(i in 0..72) {
            val a=i/72f*2*PI.toFloat()
            val wobble=.025f+tier.level*.008f
            val k=1+wobble*(sin(3*a)+.4f*sin(5*a))
            val melt=if(tier.level>=5) .13f*max(0f,sin(a)).pow(6)*(.5f+.5f*sin(7*a)) else 0f
            val x=c+cos(a)*r*k;val y=c+sin(a)*r*(k+melt)
            if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        path.close()
        paint.shader=LinearGradient(0f,0f,size.toFloat(),size.toFloat(),tier.argb,if(tier.level<3) 0xFFC6FF3D.toInt() else 0xFFFF4FB3.toInt(),Shader.TileMode.CLAMP)
        canvas.drawPath(path,paint);paint.shader=null
        paint.color=0x47FFFFFF;canvas.drawOval(c-r*.6f,c-r*.6f,c-r*.12f,c-r*.4f,paint)
        for(side in listOf(-1,1)) {
            val x=c+side*r*.31f;val y=c-r*.08f
            paint.color=Color.WHITE;canvas.drawOval(x-r*.23f,y-r*.26f,x+r*.23f,y+r*.26f,paint)
            paint.color=0xFF09090F.toInt();canvas.drawCircle(x,y+r*.045f,r*(.13f-tier.level*.008f),paint)
            paint.color=Color.WHITE;canvas.drawCircle(x-r*.04f,y+r*.005f,r*.035f,paint)
        }
        path.reset();path.moveTo(c-r*.18f,c+r*.4f);path.quadTo(c,c+r*(if(tier.level<4) .63f else .78f),c+r*.18f,c+r*.4f)
        paint.color=0xFF09090F.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=size*.017f;paint.strokeCap=Paint.Cap.ROUND;canvas.drawPath(path,paint)
        paint.style=Paint.Style.FILL
        if(tier.level<3) for(side in listOf(-1,1)) {paint.color=0x66FF4FB3;canvas.drawCircle(c+side*r*.5f,c+r*.27f,r*.075f,paint)}
        return bitmap
    }
}
