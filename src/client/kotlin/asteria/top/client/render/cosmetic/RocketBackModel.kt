package asteria.top.client.render.cosmetic

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeDeformation
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.client.model.geom.builders.PartDefinition
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import java.io.InputStreamReader
import java.util.function.Function
import kotlin.math.PI
import kotlin.math.sin

/** Bedrock geometry adapter for the rocket cosmetic supplied with Delta Client. */
class RocketBackModel private constructor(root: ModelPart) : Model<AvatarRenderState>(
    root,
    Function { texture -> RenderTypes.entityTranslucent(texture) },
) {
    private val wings = root.getChild("wings")
    private val wingRight = wings.getChild("wing_right")
    private val wingLeft = wings.getChild("wing_left")
    private val energyRight = wingRight.getChild("energy")
    private val energyLeft = wingLeft.getChild("energy2")
    private val flameRight = wingRight.getChild("flame_right")
    private val flameLeft = wingLeft.getChild("flame_left")

    override fun setupAnim(state: AvatarRenderState) {
        super.setupAnim(state)
        resetPose()

        val seconds = state.ageInTicks / 20.0f
        val vibration = seconds * (PI.toFloat() * 12.0f)
        wings.x += sin(vibration) * 0.25f
        wings.y += sin(vibration + PI.toFloat() * 0.5f) * 0.25f

        energyRight.yRot -= seconds * PI.toFloat() * 2.0f
        energyLeft.yRot += seconds * PI.toFloat() * 2.0f
        flameRight.yRot -= seconds * PI.toFloat() * 6.0f
        flameLeft.yRot += seconds * PI.toFloat() * 6.0f

        val flameScale = 0.9f + (sin(vibration) + 1.0f) * 0.1f
        flameRight.xScale = flameScale
        flameRight.yScale = flameScale
        flameRight.zScale = flameScale
        flameLeft.xScale = flameScale
        flameLeft.yScale = flameScale
        flameLeft.zScale = flameScale
    }

    companion object {
        private const val GEOMETRY_RESOURCE = "/assets/asteria/cosmetics/rocket/rocket.geo.json"

        fun create(): RocketBackModel = RocketBackModel(bakeBedrockGeometry())

        private fun bakeBedrockGeometry(): ModelPart {
            val stream = RocketBackModel::class.java.getResourceAsStream(GEOMETRY_RESOURCE)
                ?: error("Missing rocket geometry: $GEOMETRY_RESOURCE")
            val document = InputStreamReader(stream, Charsets.UTF_8).use(JsonParser::parseReader).asJsonObject
            val geometry = document.getAsJsonArray("minecraft:geometry")[0].asJsonObject
            val bones = geometry.getAsJsonArray("bones")
            val textureWidth = geometry.getAsJsonObject("description").get("texture_width").asInt
            val textureHeight = geometry.getAsJsonObject("description").get("texture_height").asInt

            val mesh = MeshDefinition()
            val definitions = mutableMapOf<String, PartDefinition>()
            val pivots = mutableMapOf<String, FloatArray>()

            for (boneElement in bones) {
                val bone = boneElement.asJsonObject
                val name = bone.get("name").asString
                val parentName = bone.get("parent")?.asString
                val parent = parentName?.let(definitions::get) ?: mesh.root
                val pivot = bone.getAsJsonArray("pivot").floats(3)
                val parentPivot = parentName?.let(pivots::get) ?: floatArrayOf(0.0f, 24.0f, 0.0f)
                val definition = parent.addOrReplaceChild(
                    name,
                    CubeListBuilder.create(),
                    PartPose.offset(
                        pivot[0] - parentPivot[0],
                        parentPivot[1] - pivot[1],
                        pivot[2] - parentPivot[2],
                    ),
                )
                definitions[name] = definition
                pivots[name] = pivot

                bone.getAsJsonArray("cubes")?.forEachIndexed { index, cubeElement ->
                    addCube(definition, pivot, cubeElement.asJsonObject, index)
                }
            }

            return mesh.root.bake(textureWidth, textureHeight)
        }

        private fun addCube(parent: PartDefinition, bonePivot: FloatArray, cube: JsonObject, index: Int) {
            val origin = cube.getAsJsonArray("origin").floats(3)
            val size = cube.getAsJsonArray("size").floats(3)
            val uv = cube.getAsJsonArray("uv").floats(2)
            val pivot = cube.getAsJsonArray("pivot")?.floats(3) ?: bonePivot
            val rotation = cube.getAsJsonArray("rotation")?.floats(3) ?: floatArrayOf(0.0f, 0.0f, 0.0f)
            val inflate = cube.get("inflate")?.asFloat ?: 0.0f
            val builder = CubeListBuilder.create()
                .texOffs(uv[0].toInt(), uv[1].toInt())
                .mirror(cube.get("mirror")?.asBoolean ?: false)
                .addBox(
                    origin[0] - pivot[0],
                    pivot[1] - origin[1] - size[1],
                    origin[2] - pivot[2],
                    size[0],
                    size[1],
                    size[2],
                    CubeDeformation(inflate),
                )

            parent.addOrReplaceChild(
                "cube_$index",
                builder,
                PartPose.offsetAndRotation(
                    pivot[0] - bonePivot[0],
                    bonePivot[1] - pivot[1],
                    pivot[2] - bonePivot[2],
                    -Math.toRadians(rotation[0].toDouble()).toFloat(),
                    -Math.toRadians(rotation[1].toDouble()).toFloat(),
                    Math.toRadians(rotation[2].toDouble()).toFloat(),
                ),
            )
        }

        private fun JsonArray.floats(expectedSize: Int): FloatArray =
            FloatArray(expectedSize) { index -> get(index).asFloat }
    }
}
