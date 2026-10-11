package com.gridcc.doomscore.android.core

import kotlin.math.*

/** Shared contour tables; the draw loop fills caller-owned arrays without allocating vertices. */
object GoobShape {
    const val VERTICES=64
    private val angles=FloatArray(VERTICES) {it*2f*PI.toFloat()/VERTICES}
    private val xs=FloatArray(VERTICES) {cos(angles[it])}
    private val ys=FloatArray(VERTICES) {sin(angles[it])}
    private val sin3=FloatArray(VERTICES) {sin(3*angles[it])}
    private val cos3=FloatArray(VERTICES) {cos(3*angles[it])}
    private val sin5=FloatArray(VERTICES) {sin(5*angles[it])}
    private val cos5=FloatArray(VERTICES) {cos(5*angles[it])}
    private val sin7=FloatArray(VERTICES) {sin(7*angles[it])}
    private val cos7=FloatArray(VERTICES) {cos(7*angles[it])}
    private val meltMask=FloatArray(VERTICES) {max(0f,ys[it]).pow(6)}

    fun frame(phase:Float,level:Int,reaction:Float,x:FloatArray,y:FloatArray) {
        require(x.size>=VERTICES && y.size>=VERTICES)
        val time=if(phase.isFinite()) phase else 0f
        val sc=sin(time);val cc=cos(time)
        val tier=level.coerceIn(0,6)
        val response=if(reaction.isFinite()) reaction.coerceIn(0f,1f) else 0f
        val wobble=.025f+tier*.008f
        for(i in 0 until VERTICES) {
            val wave3=sin3[i]*cc+cos3[i]*sc
            val wave5=sin5[i]*cc-cos5[i]*sc
            val wave7=sin7[i]*cc+cos7[i]*sc
            val k=1+wobble*(wave3+.4f*wave5)+response*.018f*wave7
            val melt=if(tier>=5) .13f*meltMask[i]*(.5f+.5f*wave7) else 0f
            x[i]=xs[i]*k;y[i]=ys[i]*(k+melt)
        }
    }
}
