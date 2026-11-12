package org.mundocreativo.flyshape.sim

import kotlin.random.Random

/**
 * The smoke of the tunnel: a fixed pool of tracers advected by the flow field.
 *
 * State lives in plain `FloatArray`s and is mutated in place, so stepping the
 * whole field every frame allocates nothing.
 */
class ParticleField(val count: Int = 240) {

    private val positionX = FloatArray(count)
    private val positionY = FloatArray(count)
    private val velocityX = FloatArray(count)
    private val velocityY = FloatArray(count)
    private val random = Random(0x5EED)
    private val sample = FloatArray(2)

    fun x(index: Int): Float = positionX[index]

    fun y(index: Int): Float = positionY[index]

    /** World-space velocity, already scaled by the wind speed. */
    fun vx(index: Int): Float = velocityX[index]

    fun vy(index: Int): Float = velocityY[index]

    /** Fills the whole tunnel so the first frame is already populated. */
    fun seed(solution: FlowSolution) {
        for (index in 0 until count) {
            var x = TunnelDomain.minX + random.nextFloat() * TunnelDomain.WIDTH
            var y = TunnelDomain.minY + random.nextFloat() * TunnelDomain.HEIGHT
            if (solution.isInsideBody(x, y)) {
                y += TunnelDomain.HEIGHT * 0.35f
                if (y > TunnelDomain.maxY) y -= TunnelDomain.HEIGHT * 0.7f
                x = TunnelDomain.minX + 0.02f
            }
            positionX[index] = x
            positionY[index] = y
            velocityX[index] = solution.windDirection.x
            velocityY[index] = solution.windDirection.y
        }
    }

    /** Advects every tracer by [dt] seconds of wall-clock time. */
    fun advance(dt: Float, windSpeed: Float, solution: FlowSolution) {
        if (dt <= 0f || windSpeed <= 0f) return
        val scale = windSpeed * WORLD_SPEED_PER_METRE

        for (index in 0 until count) {
            solution.velocityInto(positionX[index], positionY[index], sample)
            val vx = sample[0] * scale
            val vy = sample[1] * scale
            val nextX = positionX[index] + vx * dt
            val nextY = positionY[index] + vy * dt

            if (!TunnelDomain.contains(nextX, nextY) || solution.isInsideBody(nextX, nextY)) {
                respawnAtInlet(solution, index)
            } else {
                positionX[index] = nextX
                positionY[index] = nextY
                velocityX[index] = vx
                velocityY[index] = vy
            }
        }
    }

    /** Puts a tracer back on the upwind boundary, spread across the airstream. */
    private fun respawnAtInlet(solution: FlowSolution, index: Int) {
        val wind = solution.windDirection
        val perpendicular = wind.perpendicular()
        val reach = TunnelDomain.WIDTH * 0.42f
        val baseX = TunnelDomain.center.x - wind.x * reach
        val baseY = TunnelDomain.center.y - wind.y * reach
        val spread = TunnelDomain.HEIGHT * 0.95f
        val offset = (random.nextFloat() - 0.5f) * spread

        var x = (baseX + perpendicular.x * offset).coerceIn(TunnelDomain.minX, TunnelDomain.maxX)
        var y = (baseY + perpendicular.y * offset).coerceIn(TunnelDomain.minY, TunnelDomain.maxY)
        if (solution.isInsideBody(x, y)) {
            x = TunnelDomain.minX + 0.02f
            y = TunnelDomain.center.y
        }

        positionX[index] = x
        positionY[index] = y
        velocityX[index] = wind.x
        velocityY[index] = wind.y
    }

    private companion object {
        /**
         * Metres per second of real wind converted into chord lengths per second
         * of screen motion. Tuned so 25 m/s reads as a brisk tunnel.
         */
        const val WORLD_SPEED_PER_METRE = 0.035f
    }
}
