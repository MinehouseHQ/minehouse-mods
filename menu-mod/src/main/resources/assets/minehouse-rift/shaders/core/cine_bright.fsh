#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minehouse-rift:cine_post.glsl>

// The bright parts of the frame, four times smaller: what the bloom grows from.
uniform sampler2D SceneSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 px = 1.0 / vec2(textureSize(SceneSampler, 0));
    vec3 c = vec3(0.0);
    for (int y = -1; y <= 2; y++) for (int x = -1; x <= 2; x++) {
        c += texture(SceneSampler, texCoord + (vec2(x, y) - 0.5) * px).rgb;
    }
    c /= 16.0;
    c *= Look.x;
    float l = max(c.r, max(c.g, c.b));
    c *= max(l - Frame.w, 0.0) / max(l, 0.0001);
    fragColor = vec4(c, 1.0);
}
