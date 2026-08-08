package asteria.top.client.render

import com.mojang.blaze3d.vertex.BufferBuilder
import net.minecraft.world.phys.Vec3

/**
 * Port of Asteria12111's WorldGeometryEmitter.
 *
 * Emits camera-relative world geometry into a buffer. The original wrote QUADS through a
 * VertexConsumer; this engine's custom pipelines are built with VertexFormat.Mode.TRIANGLES,
 * so every quad is split into two triangles here (v0-v1-v2 / v0-v2-v3) — the emitted surface
 * and vertex colours are identical.
 */
class WorldGeometryEmitter(private val builder: BufferBuilder) {

    fun emitQuad(v0: Vec3, v1: Vec3, v2: Vec3, v3: Vec3, rgbaColor: Int) {
        emitQuad(v0, v1, v2, v3, rgbaColor, rgbaColor, rgbaColor, rgbaColor)
    }

    fun emitQuad(
        v0: Vec3, v1: Vec3, v2: Vec3, v3: Vec3,
        rgbaColor0: Int, rgbaColor1: Int, rgbaColor2: Int, rgbaColor3: Int,
    ) {
        writeColorVertex(v0, rgbaColor0)
        writeColorVertex(v1, rgbaColor1)
        writeColorVertex(v2, rgbaColor2)

        writeColorVertex(v0, rgbaColor0)
        writeColorVertex(v2, rgbaColor2)
        writeColorVertex(v3, rgbaColor3)
    }

    fun emitTriangle(v0: Vec3, v1: Vec3, v2: Vec3, color0: Int, color1: Int, color2: Int) {
        writeColorVertex(v0, color0)
        writeColorVertex(v1, color1)
        writeColorVertex(v2, color2)
    }

    fun emitTexturedQuad(
        v0: Vec3, v1: Vec3, v2: Vec3, v3: Vec3,
        u0: Float, v0Coord: Float,
        u1: Float, v1Coord: Float,
        u2: Float, v2Coord: Float,
        u3: Float, v3Coord: Float,
        rgbaColor: Int,
    ) {
        emitTexturedQuad(
            v0, v1, v2, v3,
            u0, v0Coord, u1, v1Coord, u2, v2Coord, u3, v3Coord,
            rgbaColor, rgbaColor, rgbaColor, rgbaColor,
        )
    }

    fun emitTexturedQuad(
        v0: Vec3, v1: Vec3, v2: Vec3, v3: Vec3,
        u0: Float, v0Coord: Float,
        u1: Float, v1Coord: Float,
        u2: Float, v2Coord: Float,
        u3: Float, v3Coord: Float,
        color0: Int, color1: Int, color2: Int, color3: Int,
    ) {
        writeTexturedVertex(v0, u0, v0Coord, color0)
        writeTexturedVertex(v1, u1, v1Coord, color1)
        writeTexturedVertex(v2, u2, v2Coord, color2)

        writeTexturedVertex(v0, u0, v0Coord, color0)
        writeTexturedVertex(v2, u2, v2Coord, color2)
        writeTexturedVertex(v3, u3, v3Coord, color3)
    }

    private fun writeColorVertex(worldPos: Vec3, rgbaColor: Int) {
        builder.addVertex(worldPos.x.toFloat(), worldPos.y.toFloat(), worldPos.z.toFloat()).setColor(rgbaColor)
    }

    private fun writeTexturedVertex(worldPos: Vec3, u: Float, v: Float, rgbaColor: Int) {
        builder.addVertex(worldPos.x.toFloat(), worldPos.y.toFloat(), worldPos.z.toFloat())
            .setUv(u, v)
            .setColor(rgbaColor)
    }
}
