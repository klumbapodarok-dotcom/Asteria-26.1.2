#version 150

layout(std140) uniform GlowData {
    vec4 resolutionRadius;
    vec4 colorIntensity;
    vec4 turbulenceOptions;
};

uniform sampler2D InputSampler;
uniform sampler2D OriginalSampler;
out vec4 fragColor;

const int BLUR_HALF_TAPS = 8;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    vec2 resolution = resolutionRadius.xy;
    float radius = max(resolutionRadius.z, 1.0);
    vec3 glowColor = colorIntensity.rgb;
    float intensity = colorIntensity.a;
    float time = turbulenceOptions.x * max(turbulenceOptions.z, 0.05);
    float power = max(turbulenceOptions.y, 0.0);
    // Ceiling follows the setting range (0..9). The old 2.5 cap made every
    // value above ~3 look identical, which is why turbulence read as static.
    float strength = clamp(power / 1.2, 0.0, 7.5);
    vec2 uv = gl_FragCoord.xy / resolution;

    float n1 = noise(uv * vec2(3.2, 4.8) + vec2(time * 0.16, -time * 0.21));
    float n2 = noise(uv * vec2(7.5, 9.0) + vec2(-time * 0.29, time * 0.24));
    float n3 = noise(uv * vec2(15.0, 12.0) + vec2(time * 0.41, time * 0.33));
    float turbulence = n1 * 0.56 + n2 * 0.31 + n3 * 0.13;
    // Organic breathing instead of the former 0.12..1.92 radius jumps. Those
    // jumps produced hard white bulbs followed by obvious missing sections.
    float widthField = smoothstep(0.18, 0.86, turbulence);
    float turbulentWidth = mix(0.84, 1.20, widthField);
    float widthWave = max(0.72, 1.0 + (turbulentWidth - 1.0) * strength);
    vec2 flow = vec2(n2 - 0.5, n3 - 0.5);
    vec2 warpedUv = uv + flow * (radius * 0.105 * strength) / resolution;
    float sway = sin(time * 0.82 + uv.y * 8.0 + n1 * 4.0)
               + sin(time * 1.17 - uv.y * 5.0 + n2 * 3.0) * 0.45;

    float blurRadius = radius * widthWave;
    float sigma = max(blurRadius * 0.48, 1.0);
    vec4 blurred = vec4(0.0);
    float weightSum = 0.0;
    for (int i = -BLUR_HALF_TAPS; i <= BLUR_HALF_TAPS; ++i) {
        float normalizedOffset = float(i) / float(BLUR_HALF_TAPS);
        float offsetPx = sign(normalizedOffset) * pow(abs(normalizedOffset), 1.18) * blurRadius;
        float edge = abs(normalizedOffset);
        float flameShiftX = sway * power * radius * 0.055 * edge;
        float flameShiftY = (n2 - 0.5) * power * radius * 0.085 * edge;
        float weight = exp(-0.5 * offsetPx * offsetPx / (sigma * sigma));
        blurred += texture(InputSampler, warpedUv + vec2(flameShiftX, offsetPx + flameShiftY) / resolution) * weight;
        weightSum += weight;
    }
    blurred /= max(weightSum, 0.0001);

    float origA = texture(OriginalSampler, uv).a;
    // Continuous falloff: no threshold cliffs and therefore no neon-white
    // cutout around the block silhouette.
    float softBloom = pow(clamp(blurred.a, 0.0, 1.0), 0.82);
    // Glow must live outside the captured hand/item. The previous 18% inner
    // bleed exposed the low-frequency noise as clipped oval islands inside
    // opaque sword pixels. A soft alpha gate keeps antialiased boundaries
    // clean while making every solid interior pixel exactly glow-free.
    float outerOnly = 1.0 - smoothstep(0.01, 0.22, origA);
    float cloud = smoothstep(0.20, 0.84, turbulence);
    float filamentNoise = fract(n3 * 0.73 + n2 * 0.51 + n1 * 0.29 + time * 0.035 + flow.x * 0.17);
    float filaments = pow(smoothstep(0.38, 0.86, filamentNoise), 1.45);
    float slowBreath = 0.92 + 0.08 * sin(time * 0.31 + uv.y * 2.4 + n1 * 1.8);
    // Turbulence now only gives the halo a restrained, continuous breath;
    // it can no longer carve visible cloudy shapes into the result.
    float targetDensity = 0.94 + cloud * 0.075 + filaments * 0.035;
    float turbulentDensity = max(0.78, 1.0 + (targetDensity - 1.0) * strength);
    float alpha = min(softBloom * outerOnly * intensity * turbulentDensity * slowBreath, 0.86);
    if (alpha <= 0.002) { fragColor = vec4(0.0); return; }

    float whiteCore = smoothstep(0.42, 0.96, blurred.a)
                    * (0.035 + (cloud * 0.045 + filaments * 0.025) * strength);
    vec3 coolEdge = mix(glowColor, vec3(0.78, 0.90, 1.0), 0.08 + n2 * 0.035);
    vec3 color = mix(coolEdge, vec3(1.0), clamp(whiteCore, 0.0, 0.14));
    fragColor = vec4(color * alpha, alpha);
}
