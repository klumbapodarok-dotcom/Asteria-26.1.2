#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// Splash/lingering potion area of effect. The quad is built exactly one potion radius wide,
// so the boundary sits at the quad's edge. Only that boundary is drawn — a thin outline ring,
// alpha-matched to the impact ball's own shading (~0.9) rather than a thick opaque band.
void main() {
    vec2 p = (texCoord0 - 0.5) * 2.0;
    float dist = length(p);
    if (dist > 1.0) {
        discard;
    }

    float ring = pow(clamp(1.0 - abs(dist - 0.97) / 0.022, 0.0, 1.0), 1.5);
    if (ring <= 0.002) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, ring * 0.9 * vertexColor.a) * ColorModulator;
}
