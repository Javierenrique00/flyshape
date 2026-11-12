package org.mundocreativo.flyshape.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryTest {

    @Test
    fun everySectionIsClosedCounterClockwiseAndOneChordWide() {
        for (shape in WingShape.entries) {
            val contour = WingGeometry.contour(shape)
            assertTrue(contour.size >= 60, "$shape produced only ${contour.size} points")
            assertTrue(
                contour.all { it.x.isFinite() && it.y.isFinite() },
                "$shape produced non-finite points",
            )

            assertEquals(0f, contour.minOf { it.x }, 0.01f, "$shape leading edge")
            assertEquals(1f, contour.maxOf { it.x }, 0.01f, "$shape trailing edge")
            assertTrue(signedArea(contour) > 0f, "$shape should be counter-clockwise")
        }
    }

    @Test
    fun noPanelIsDegenerate() {
        for (shape in WingShape.entries) {
            val contour = WingGeometry.contour(shape)
            for (index in contour.indices) {
                val a = contour[index]
                val b = contour[(index + 1) % contour.size]
                val length = (b - a).length
                assertTrue(length > 1e-4f, "$shape panel $index is only $length long")
            }
        }
    }

    @Test
    fun boxySectionIsSymmetricAboutTheChordLine() {
        val contour = WingGeometry.contour(WingShape.Boxy)
        val top = contour.maxOf { it.y }
        val bottom = contour.minOf { it.y }

        assertEquals(WingGeometry.BOX_THICKNESS / 2f, top, 1e-3f)
        assertEquals(top, -bottom, 1e-4f)
    }

    @Test
    fun symmetricSectionIsTwelvePercentThick() {
        val contour = WingGeometry.contour(WingShape.Symmetric)
        val thickness = contour.maxOf { it.y } - contour.minOf { it.y }

        // NACA 0012: maximum thickness is 12% of the chord, near the mid chord.
        assertEquals(0.12f, thickness, 0.012f)
    }

    private fun signedArea(points: List<Vec2>): Float {
        var twiceArea = 0f
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            twiceArea += a.x * b.y - b.x * a.y
        }
        return twiceArea * 0.5f
    }
}
