#version 330
#extension GL_ARB_separate_shader_objects : require

// Lmg Client motion blur: mixes this frame with the ones before it.

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform BlurAmount {
    float Strength;   // 0 = none, closer to 1 = longer trails
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 now = texture(InSampler, texCoord).rgb;
    vec3 before = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(now, before, Strength), 1.0);
}
