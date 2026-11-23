package com.example.metrognome.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where a stage carousel fling lands ([stageFlingTarget]): steps, coasts and edges. */
class StageFlingTargetTest {
    private val step = 1000f   // px/s below which a release is a step
    private val pages = 10

    private fun land(at: Float, v: Float, coast: Float = 0f) = stageFlingTarget(at, v, coast, step, pages)

    @Test fun aSlowDragPastAThirdStepsForward() = assertEquals(3, land(2.4f, 200f))
    @Test fun aSlowDragShortOfAThirdReturns() = assertEquals(2, land(2.3f, 200f))
    @Test fun aSlowDragBackPastAThirdStepsBack() = assertEquals(2, land(2.6f, -200f))
    @Test fun aSlowDragBackShortOfAThirdReturns() = assertEquals(3, land(2.7f, -200f))
    @Test fun noSpeedSettlesOnTheNearest() = assertEquals(3, land(2.6f, 0f))

    @Test fun aFlickCoastsSeveralPages() = assertEquals(6, land(2.2f, 5000f, coast = 3.6f))
    @Test fun aFlickBackCoastsSeveralPages() = assertEquals(1, land(4.8f, -5000f, coast = -3.9f))
    @Test fun aWeakFlickStillReachesTheNextPage() = assertEquals(3, land(2.05f, 1500f, coast = 0.1f))
    @Test fun aWeakFlickBackStillReachesThePreviousPage() = assertEquals(1, land(2f, -1500f, coast = -0.1f))
    @Test fun aFlickStopsAtTheLastPage() = assertEquals(9, land(7f, 9000f, coast = 12f))
    @Test fun aFlickBackStopsAtTheFirstPage() = assertEquals(0, land(2f, -9000f, coast = -12f))
}
