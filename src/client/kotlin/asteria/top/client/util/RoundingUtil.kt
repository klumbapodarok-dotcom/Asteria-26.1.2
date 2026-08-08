package asteria.top.client.util

import asteria.top.client.math.RoundingRule
import asteria.top.client.render.RoundingType

object RoundingUtil {
    const val CORNER_RADIUS = 30.0f
    const val SQUIRCLE_POWER = 5.0f
    val ROUNDING_RULE = RoundingRule.TOWARD_ZERO
    
    /**
     * Default rounding type for UI elements.
     * Can be changed based on user preferences or performance considerations.
     */
    var currentRoundingType: RoundingType = RoundingType.CURRENT
    
    /**
     * Get the GLSL ID for the current rounding type.
     * Used when passing rounding type to shaders.
     */
    fun getRoundingTypeId(): Int {
        return currentRoundingType.glslId
    }
    
    /**
     * Set the rounding type for all subsequent rendering operations.
     */
    fun setRoundingType(type: RoundingType) {
        currentRoundingType = type
    }
}
