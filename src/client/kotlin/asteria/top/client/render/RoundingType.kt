package asteria.top.client.render

/**
 * Enum for corner rounding types used in rendering shaders.
 * Each type has a corresponding GLSL implementation.
 */
enum class RoundingType(val displayName: String, val glslId: Int) {
    /**
     * Current/Standard rounding using the rounded box distance algorithm.
     * Creates smooth, uniform circular corners.
     */
    CURRENT("Current", 0) {
        override val description: String = "Standard circular corner rounding"
    },

    /**
     * Squircle rounding using the superellipse/lnorm-based algorithm.
     * Creates smooth corners that transition between circular and square shapes.
     */
    SQUIRCLE("Squircle", 1) {
        override val description: String = "Smooth superellipse-based corner rounding"
    };

    abstract val description: String
}
