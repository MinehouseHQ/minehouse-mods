#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minehouse-rift:cine_post.glsl>

// One direction of a wide soft blur.
uniform sampler2D SceneSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 step = Pass.xy / vec2(textureSize(SceneSampler, 0));
    vec3 c = vec3(0.0);
    float w = 0.0;
    for (int i = -12; i <= 12; i++) {
        float k = exp(-float(i * i) / 50.0);
        c += texture(SceneSampler, texCoord + step * float(i)).rgb * k;
        w += k;
    }
    fragColor = vec4(c / w, 1.0);
}
