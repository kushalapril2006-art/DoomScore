package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class GoobShapeTest {
    @Test fun everyTierAndReactionStaysInsideTheCanvasSafetyMargin() {
        val x=FloatArray(GoobShape.VERTICES);val y=FloatArray(GoobShape.VERTICES)
        for(level in 0..6) for(step in 0..64) for(reaction in listOf(0f,.5f,1f)) {
            GoobShape.frame(step*2f*PI.toFloat()/64,level,reaction,x,y)
            assertTrue(x.all {it.isFinite() && abs(it)<1.27f})
            assertTrue(y.all {it.isFinite() && abs(it)<1.27f})
        }
    }
    @Test fun cycleReturnsToTheSameContourWithoutASeam() {
        val x0=FloatArray(GoobShape.VERTICES);val y0=FloatArray(GoobShape.VERTICES)
        val x1=FloatArray(GoobShape.VERTICES);val y1=FloatArray(GoobShape.VERTICES)
        GoobShape.frame(0f,6,0f,x0,y0)
        GoobShape.frame(2f*PI.toFloat(),6,0f,x1,y1)
        assertArrayEquals(x0,x1,.00001f);assertArrayEquals(y0,y1,.00001f)
    }
    @Test fun consecutiveIdleFramesMoveGentlyAndTapChangesTheExpressionContour() {
        val a=FloatArray(GoobShape.VERTICES);val b=FloatArray(GoobShape.VERTICES)
        val ax=FloatArray(GoobShape.VERTICES);val bx=FloatArray(GoobShape.VERTICES)
        GoobShape.frame(1f,4,0f,a,ax)
        GoobShape.frame(1f+2f*PI.toFloat()/240,4,0f,b,bx)
        assertTrue(a.indices.all {abs(a[it]-b[it])<.01f && abs(ax[it]-bx[it])<.01f})
        GoobShape.frame(1f,4,1f,b,bx)
        assertTrue(a.indices.any {abs(a[it]-b[it])>.001f})
    }
    @Test fun invalidPhaseAndReactionFallBackToAStableShape() {
        val x=FloatArray(GoobShape.VERTICES);val y=FloatArray(GoobShape.VERTICES)
        GoobShape.frame(Float.NaN,999,Float.POSITIVE_INFINITY,x,y)
        assertTrue(x.all {it.isFinite()});assertTrue(y.all {it.isFinite()})
    }
}
