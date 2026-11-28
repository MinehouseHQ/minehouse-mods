#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minehouse-rift:cine_post.glsl>

// Depth of field: what is far from the focus goes soft, as through a macro lens. The distance of each solid
// point is kept in the alpha of the frame; light that adds up (alpha 0) takes the distance of what is behind it.
uniform sampler2D SceneSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float coc(float d) {
    // Aim.w: focus distance, Pass.y: strength (pixels of blur per unit of defocus), Pass.x: largest blur
    if (Aim.w <= 0.0) return 0.0;
    float k = abs(d - Aim.w) / max(d, 0.001);
    return min(k * Pass.y, Pass.x);
}

void main() {
    vec2 size = vec2(textureSize(SceneSampler, 0));
    vec4 c0 = texture(SceneSampler, texCoord);
    float r = coc(c0.a <= 0.0 ? 100000.0 : c0.a);
    if (r < 0.5) {
        fragColor = c0;
        return;
    }
    vec3 sum = c0.rgb;
    float w = 1.0;
    const float GOLDEN = 2.39996323;
    for (int i = 1; i < 40; i++) {
        float fi = float(i);
        float rad = sqrt(fi / 40.0) * r;
        float ang = fi * GOLDEN;
        vec2 o = vec2(cos(ang), sin(ang)) * rad / size;
        vec4 s = texture(SceneSampler, texCoord + o);
        float rs = coc(s.a <= 0.0 ? 100000.0 : s.a);
        // a sharp thing in front does not smear over its neighbours
        float k = smoothstep(rad - 1.5, rad, rs + 1.0);
        sum += s.rgb * k;
        w += k;
    }
    fragColor = vec4(sum / w, c0.a);
}
