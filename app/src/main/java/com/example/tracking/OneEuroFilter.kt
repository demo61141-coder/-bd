package com.example.tracking

import com.example.model.LiveFacePose
import com.example.model.Point2D
import kotlin.math.PI
import kotlin.math.abs

/**
 * High-precision 1D One Euro Filter for jitter-free real-time facial landmark
 * and blendshape stabilization.
 *
 * Reference: Casiez et al., "1€ Filter: A Simple Speed-based Low-pass Filter
 * for Noisy Input in Interactive Systems" (CHI 2012).
 */
class OneEuroScalarFilter(
    var minCutoff: Float = 1.2f,
    var beta: Float = 0.04f,
    private val dCutoff: Float = 1.0f
) {
    private var prevValue: Float? = null
    private var prevDerivative: Float = 0f
    private var prevTimestampSec: Double? = null

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = (1.0f / (2.0f * PI.toFloat() * cutoff.coerceAtLeast(0.001f)))
        return 1.0f / (1.0f + tau / dt.coerceAtLeast(0.0001f))
    }

    fun filter(value: Float, timestampSec: Double): Float {
        val lastVal = prevValue
        val lastTime = prevTimestampSec
        if (lastVal == null || lastTime == null) {
            prevValue = value
            prevTimestampSec = timestampSec
            return value
        }

        val dt = (timestampSec - lastTime).toFloat().coerceIn(0.001f, 0.25f)
        val rawDerivative = (value - lastVal) / dt
        val aDeriv = alpha(dCutoff, dt)
        val smoothedDerivative = prevDerivative + aDeriv * (rawDerivative - prevDerivative)

        val adaptiveCutoff = minCutoff + beta * abs(smoothedDerivative)
        val aValue = alpha(adaptiveCutoff, dt)
        val filtered = lastVal + aValue * (value - lastVal)

        prevValue = filtered
        prevDerivative = smoothedDerivative
        prevTimestampSec = timestampSec
        return filtered
    }

    fun reset() {
        prevValue = null
        prevDerivative = 0f
        prevTimestampSec = null
    }
}

/**
 * Bank of One Euro filters smoothing all facial mesh control points,
 * dense landmarks, head pose angles (yaw/pitch/roll), and expression blendshapes.
 */
class OneEuroFilterBank {
    private val pointXFilters = mutableMapOf<Int, OneEuroScalarFilter>()
    private val pointYFilters = mutableMapOf<Int, OneEuroScalarFilter>()
    private val denseXFilters = mutableMapOf<Int, OneEuroScalarFilter>()
    private val denseYFilters = mutableMapOf<Int, OneEuroScalarFilter>()

    private val jawFilter = OneEuroScalarFilter()
    private val leftBlinkFilter = OneEuroScalarFilter()
    private val rightBlinkFilter = OneEuroScalarFilter()
    private val smileFilter = OneEuroScalarFilter()
    private val browFilter = OneEuroScalarFilter()

    private val yawFilter = OneEuroScalarFilter()
    private val pitchFilter = OneEuroScalarFilter()
    private val rollFilter = OneEuroScalarFilter()

    private val centerXFilter = OneEuroScalarFilter()
    private val centerYFilter = OneEuroScalarFilter()
    private val scaleFilter = OneEuroScalarFilter()

    fun smoothPose(
        raw: LiveFacePose,
        smoothingStrength: Float,
        timestampMs: Long = System.currentTimeMillis()
    ): LiveFacePose {
        val strength = smoothingStrength.coerceIn(0f, 1f)
        if (strength <= 0.02f) {
            return raw
        }

        // Higher smoothingStrength -> lower minCutoff (stronger stabilization when still)
        // while beta keeps fast movements responsive.
        val minCutoff = (3.8f - strength * 3.35f).coerceAtLeast(0.35f)
        val beta = (0.15f - strength * 0.11f).coerceAtLeast(0.015f)
        val tSec = timestampMs / 1000.0

        fun updateParams(filter: OneEuroScalarFilter) {
            filter.minCutoff = minCutoff
            filter.beta = beta
        }

        val smoothedControlPoints = raw.controlPoints.mapIndexed { idx, pt ->
            val fx = pointXFilters.getOrPut(idx) { OneEuroScalarFilter() }.also(::updateParams)
            val fy = pointYFilters.getOrPut(idx) { OneEuroScalarFilter() }.also(::updateParams)
            Point2D(
                x = fx.filter(pt.x, tSec),
                y = fy.filter(pt.y, tSec)
            )
        }

        val smoothedDense = raw.denseLandmarks.mapIndexed { idx, pt ->
            val fx = denseXFilters.getOrPut(idx) { OneEuroScalarFilter() }.also(::updateParams)
            val fy = denseYFilters.getOrPut(idx) { OneEuroScalarFilter() }.also(::updateParams)
            Point2D(
                x = fx.filter(pt.x, tSec),
                y = fy.filter(pt.y, tSec)
            )
        }

        listOf(
            jawFilter, leftBlinkFilter, rightBlinkFilter, smileFilter, browFilter,
            yawFilter, pitchFilter, rollFilter, centerXFilter, centerYFilter, scaleFilter
        ).forEach(::updateParams)

        return raw.copy(
            controlPoints = smoothedControlPoints,
            denseLandmarks = smoothedDense,
            jawOpen = jawFilter.filter(raw.jawOpen, tSec).coerceIn(0f, 1f),
            eyeBlinkLeft = leftBlinkFilter.filter(raw.eyeBlinkLeft, tSec).coerceIn(0f, 1f),
            eyeBlinkRight = rightBlinkFilter.filter(raw.eyeBlinkRight, tSec).coerceIn(0f, 1f),
            smile = smileFilter.filter(raw.smile, tSec).coerceIn(0f, 1f),
            browRaise = browFilter.filter(raw.browRaise, tSec).coerceIn(0f, 1f),
            yawDegrees = yawFilter.filter(raw.yawDegrees, tSec),
            pitchDegrees = pitchFilter.filter(raw.pitchDegrees, tSec),
            rollDegrees = rollFilter.filter(raw.rollDegrees, tSec),
            faceCenterX = centerXFilter.filter(raw.faceCenterX, tSec),
            faceCenterY = centerYFilter.filter(raw.faceCenterY, tSec),
            faceScale = scaleFilter.filter(raw.faceScale, tSec)
        )
    }

    fun reset() {
        pointXFilters.values.forEach { it.reset() }
        pointYFilters.values.forEach { it.reset() }
        denseXFilters.values.forEach { it.reset() }
        denseYFilters.values.forEach { it.reset() }
        jawFilter.reset()
        leftBlinkFilter.reset()
        rightBlinkFilter.reset()
        smileFilter.reset()
        browFilter.reset()
        yawFilter.reset()
        pitchFilter.reset()
        rollFilter.reset()
        centerXFilter.reset()
        centerYFilter.reset()
        scaleFilter.reset()
    }
}
