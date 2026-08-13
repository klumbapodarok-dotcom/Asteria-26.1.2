#version 150

layout(std140) uniform GlowData {
    vec4 resolutionRadius;
    vec4 colorIntensity;
    vec4 turbulenceOptions;
};

uniform sampler2D InputSampler;
out vec4 fragColor;

// Half-resolution input needs fewer taps for the same perceived smoothness.
const int BLUR_HALF_TAPS = 8;

void main() {
    vec2 resolution = resolutionRadius.xy;
    float blurRadius = max(resolutionRadius.z, 1.0);
    vec2 uv = gl_FragCoord.xy / resolution;
    float sigma = max(blurRadius * 0.48, 1.0);
    vec4 accumulated = vec4(0.0);
    float weightSum = 0.0;

    for (int i = -BLUR_HALF_TAPS; i <= BLUR_HALF_TAPS; ++i) {
        float normalizedOffset = float(i) / float(BLUR_HALF_TAPS);
        // Denser sampling around the silhouette removes the separated bands
        // that appeared with a large configured radius.
        float offsetPx = sign(normalizedOffset) * pow(abs(normalizedOffset), 1.18) * blurRadius;
        float weight = exp(-0.5 * offsetPx * offsetPx / (sigma * sigma));
        accumulated += texture(InputSampler, uv + vec2(offsetPx / resolution.x, 0.0)) * weight;
        weightSum += weight;
    }
    fragColor = accumulated / max(weightSum, 0.0001);
}
