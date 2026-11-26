#version 330
#extension GL_ARB_separate_shader_objects : require

// Lmg Client colour grade: used by the photo mode filters and the grey menu background.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform GradeConfig {
    vec3 Tint;        // multiplied in: warm / cool
    float Saturation; // 1 = as is, 0 = black and white
    vec3 Shadows;     // added to the dark parts (split toning)
    float Contrast;   // 1 = as is
    vec3 Highlights;  // added to the bright parts
    float Fade;       // lifts the blacks, like old film
    float Brightness; // 1 = as is
};

layout(location = 0) out vec4 fragColor;

const vec3 Luma = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float l = dot(c, Luma);
    c = mix(vec3(l), c, Saturation);
    c *= Tint;
    c = (c - 0.5) * Contrast + 0.5;
    float lum = clamp(dot(c, Luma), 0.0, 1.0);
    c += Shadows * (1.0 - lum) + Highlights * lum;
    c = Fade + c * (1.0 - Fade);
    c *= Brightness;
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
