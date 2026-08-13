#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D InSampler;

layout(std140) uniform HandsData {
    vec4 resolutionPadding;
    vec4 gradientStart;
    vec4 animationOptions;
    vec4 gradientEnd;
    vec4 surfaceOptions;
    vec4 glowOptions;
    vec4 extra;
};

#define TAU 6.28318530718

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float nebulaNoise(vec2 p) {
    float result = 0.0;
    float amplitude = 0.64;
    // Two broad octaves retain the cloudy Space look. The removed third
    // octave was mostly invisible on pixel-art items but cost four hashes for
    // every covered framebuffer pixel.
    for (int i = 0; i < 2; ++i) {
        result += valueNoise(p) * amplitude;
        p = p * 2.03 + vec2(7.7, 4.1);
        amplitude *= 0.42;
    }
    return result;
}

// Low-frequency domain warp. Keeping all octaves below the size of a hand
// prevents the old effect from breaking into large, flat colour holes.
vec2 smoothFlow(vec2 p, float time) {
    float x = nebulaNoise(p * 0.72 + vec2(time * 0.055, -time * 0.038));
    float y = nebulaNoise(p * 0.67 + vec2(8.7, 3.1) + vec2(-time * 0.043, time * 0.052));
    return vec2(x, y) - 0.5;
}

float starLayer(vec2 uv, float cells, float seed, float time, float density) {
    vec2 grid = uv * cells;
    vec2 id = floor(grid);
    vec2 local = fract(grid) - 0.5;
    float random = hash21(id + seed);
    vec2 offset = vec2(hash21(id + seed + 19.17), hash21(id + seed + 73.41)) - 0.5;
    float radius = mix(0.025, 0.085, clamp(density / 2.5, 0.0, 1.0));
    float star = 1.0 - smoothstep(0.0, radius, length(local - offset * 0.72));
    float occurrence = step(1.0 - clamp(density, 0.0, 2.5) * 0.19, random);
    float twinkle = 0.58 + 0.42 * sin(time * (1.7 + random * 2.8) + random * 31.0);
    return star * occurrence * twinkle;
}

void main() {
    vec4 source = texture(InSampler, texCoord);
    if (source.a <= 0.001) discard;

    float time = animationOptions.x;
    float speed = animationOptions.y;
    float fillAlpha = animationOptions.z;
    float keepShading = surfaceOptions.x;
    float shadingStrength = surfaceOptions.y;
    float flowStrength = surfaceOptions.z;
    float shaderMode = extra.x;

    float aspect = resolutionPadding.x / max(resolutionPadding.y, 1.0);
    vec2 screenUv = (texCoord - 0.5) * vec2(aspect, 1.0);
    // The normal gradient used six full value-noise evaluations per pixel in
    // the first port. These intersecting sine fields are equally smooth but
    // dramatically cheaper; expensive FBM remains exclusive to Space mode.
    vec2 flow = vec2(
        sin(screenUv.y * 4.15 + screenUv.x * 1.20 - time * speed * 0.21),
        sin(screenUv.x * 3.10 - screenUv.y * 1.65 + time * speed * 0.17)
    ) * 0.5;
    float verticalPhase = texCoord.y * (0.72 + flowStrength * 0.075)
                        + flow.x * (0.30 + flowStrength * 0.08)
                        + flow.y * 0.16
                        - time * speed * 0.115;
    float primaryWave = 0.5 - 0.5 * cos(verticalPhase * TAU);
    float secondaryWave = 0.5 + 0.5 * sin((screenUv.x * 0.42 + screenUv.y * 0.28
                              + flow.y * 0.34 + time * speed * 0.047) * TAU);
    float blendFactor = smoothstep(0.08, 0.92, mix(primaryWave, secondaryWave, 0.24));
    vec3 clientGradient = mix(gradientStart.rgb, gradientEnd.rgb, blendFactor);

    float sourceLight = dot(source.rgb, vec3(0.299, 0.587, 0.114));
    // Preserve model volume without reproducing its individual dark pixels.
    // This broad remap is the main fix for the visible blocky "pits".
    float shadedLight = mix(0.84, 1.08, smoothstep(0.04, 0.96, sourceLight));
    float shade = mix(1.0, shadedLight, keepShading * shadingStrength);
    vec3 colour;
    if (shaderMode > 0.5) {
        float spaceSpeed = max(extra.y, 0.01);
        float density = max(extra.z, 0.0);
        float nebulaPower = max(extra.w, 0.0);
        vec2 spaceUv = (texCoord - 0.5) * vec2(aspect, 1.0);
        vec2 drift = vec2(time * spaceSpeed * 0.018, -time * spaceSpeed * 0.012);
        float cloudA = nebulaNoise(spaceUv * 3.4 + drift);
        float cloudB = nebulaNoise(spaceUv * 5.2 - drift * 1.7 + vec2(13.2, 4.7));
        float cloud = smoothstep(0.24, 0.90, cloudA * 0.68 + cloudB * 0.42);
        vec3 deepSpace = vec3(0.006, 0.009, 0.035);
        vec3 nebulaColor = mix(gradientStart.rgb, gradientEnd.rgb, smoothstep(0.18, 0.82, cloudB));
        colour = mix(deepSpace, nebulaColor, cloud * clamp(nebulaPower, 0.0, 2.0) * 0.78);

        float stars = starLayer(spaceUv + drift * 0.35, 29.0, 3.0, time * spaceSpeed, density);
        stars += starLayer(spaceUv - drift * 0.22, 53.0, 17.0, time * spaceSpeed * 1.3, density * 0.72) * 0.72;
        vec3 starTint = mix(vec3(0.68, 0.82, 1.0), vec3(1.0, 0.78, 0.96), cloudB);
        colour += starTint * stars * 1.65;
        colour *= mix(1.0, shade, 0.62);
    } else {
        colour = clientGradient * shade;
    }

    // Merge the captured vanilla surface and the authored fill here. The old
    // renderer copied the capture to the main target first and then drew this
    // effect, costing an extra full-screen pass every frame.
    float effectAmount = clamp(fillAlpha, 0.0, 1.0);
    vec3 combined = mix(source.rgb, colour, effectAmount);
    fragColor = vec4(combined, source.a);
}
