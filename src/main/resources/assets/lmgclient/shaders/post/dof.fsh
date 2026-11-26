#version 330
#extension GL_ARB_separate_shader_objects : require

// Lmg Client: depth of field for photo mode. Whatever is in the middle of the screen stays sharp;
// things nearer or further than it blur, the more so the further they are from it. With Tilt on,
// it fakes a tilt-shift "miniature" look instead: sharp band across the middle, soft top and bottom.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DepthSize;
};

layout(std140) uniform DofConfig {
    float Strength;  // how quickly things go soft away from the focus
    float Radius;    // the biggest blur, in pixels
    float Tilt;      // 1 = tilt-shift instead of depth
};

layout(location = 0) out vec4 fragColor;

const float NEAR = 0.05;
const float FAR = 1024.0;
const float GOLDEN = 2.39996323;

// The game stores depth "reversed" (1 = right in front of you, 0 = the sky), which keeps
// far things precise. Turn it back into a distance in blocks.
float linearDepth(vec2 uv) {
    float d = texture(DepthSampler, uv).r;
    return NEAR * FAR / (NEAR + d * (FAR - NEAR));
}

float blurAmount(vec2 uv, float focus) {
    if (Tilt > 0.5) {
        float band = abs(uv.y - 0.5);
        return smoothstep(0.12, 0.42, band);
    }
    // how far from the focus, measured the way a lens does (1/distance)
    float d = linearDepth(uv);
    return clamp(abs(1.0 / focus - 1.0 / d) * Strength, 0.0, 1.0);
}

void main() {
    // focus on the nearest thing right in the middle (a person standing there, not the hill behind them)
    float focus = linearDepth(vec2(0.5, 0.5));
    focus = min(focus, linearDepth(vec2(0.485, 0.5)));
    focus = min(focus, linearDepth(vec2(0.515, 0.5)));
    focus = min(focus, linearDepth(vec2(0.5, 0.47)));
    focus = min(focus, linearDepth(vec2(0.5, 0.53)));
    focus = max(focus, 0.3);

    float amount = blurAmount(texCoord, focus);
    vec3 sharp = texture(InSampler, texCoord).rgb;
    if (amount < 0.02) {
        fragColor = vec4(sharp, 1.0);
        return;
    }
    vec2 px = 1.0 / InSize;
    float radius = Radius * amount;
    vec3 sum = sharp;
    float weight = 1.0;
    // a disc of samples (golden-angle spiral), skipping ones that are sharper than us in front
    for (int i = 1; i < 32; i++) {
        float r = sqrt(float(i) / 32.0) * radius;
        float a = float(i) * GOLDEN;
        vec2 uv = texCoord + vec2(cos(a), sin(a)) * r * px;
        float w = Tilt > 0.5 ? 1.0 : clamp(blurAmount(uv, focus) * 1.5, 0.15, 1.0);
        sum += texture(InSampler, uv).rgb * w;
        weight += w;
    }
    vec3 c = sum / weight;
    if (Tilt > 0.5) {
        // miniatures look extra colourful
        float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
        c = mix(vec3(l), c, 1.25);
    }
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
