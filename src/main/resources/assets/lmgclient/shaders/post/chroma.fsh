#version 330
#extension GL_ARB_separate_shader_objects : require

// Lmg Client: colour fringes towards the edges of the screen, like a cheap camera lens.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ChromaConfig {
    float Amount;   // how far red and blue slide apart at the very edge, in pixels
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 fromCentre = texCoord - 0.5;
    // nothing in the middle, growing towards the corners
    float edge = dot(fromCentre, fromCentre) * 2.0;
    vec2 shift = fromCentre * edge * Amount / InSize * 2.0;
    float r = texture(InSampler, texCoord + shift).r;
    float g = texture(InSampler, texCoord).g;
    float b = texture(InSampler, texCoord - shift).b;
    fragColor = vec4(r, g, b, 1.0);
}
