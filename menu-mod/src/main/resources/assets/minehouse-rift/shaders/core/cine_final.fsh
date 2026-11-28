#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minehouse-rift:cine_post.glsl>

// The frame as the player sees it: exposure, bloom, a pull towards the middle at the cuts, one cold steel-blue
// grade over everything (after the 3 Body Problem titles), grain, vignette and the black bars of a wide film.
uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

vec3 scene(vec2 uv) {
    return texture(SceneSampler, uv).rgb * Look.x + texture(BloomSampler, uv).rgb * Look.y;
}

void main() {
    vec2 uv = texCoord;
    vec3 c;
    if (Frame.z > 0.001) {
        vec2 d = (Aim.xy - uv);
        c = vec3(0.0);
        float jitter = hash(uv * 813.0 + Pass.z);
        for (int i = 0; i < 16; i++) {
            float k = (float(i) + jitter) / 16.0;
            c += scene(uv + d * k * Frame.z);
        }
        c /= 16.0;
    } else {
        c = scene(uv);
    }
    // the grade: brightness into a ramp from deep navy through steel to a cold white
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    l = l / (1.0 + 0.35 * l);
    l = max(l + Aim.z, 0.0);
    vec3 deep = vec3(0.016, 0.024, 0.032);
    vec3 steel = vec3(0.32, 0.43, 0.52);
    vec3 pale = vec3(0.88, 0.93, 0.96);
    vec3 g = l < 0.45 ? mix(deep, steel, smoothstep(0.0, 0.45, l) * 0.9 + 0.1 * l / 0.45) : mix(steel, pale, smoothstep(0.45, 1.05, l));
    g *= smoothstep(0.0, 0.08, l) * 0.9 + 0.1;
    vec3 real = c / (1.0 + 0.35 * l);
    g = mix(g, c / (1.0 + c), 0.08);
    // the colours of the blocks, let through the steel by as much as the shot wants (kept at the grade's brightness)
    if (Tint.x > 0.0) {
        float gl = dot(g, vec3(0.2126, 0.7152, 0.0722));
        float rl = max(dot(real, vec3(0.2126, 0.7152, 0.0722)), 1e-4);
        g = mix(g, real * (gl / rl), Tint.x);
    }
    // grain
    float n = hash(floor(uv * vec2(textureSize(SceneSampler, 0))) + fract(Pass.z * 13.7) * 91.0) - 0.5;
    g += n * Look.z * (0.6 + 0.4 * l);
    // vignette
    vec2 q = uv - 0.5;
    q.x *= 1.3;
    g *= 1.0 - Look.w * smoothstep(0.25, 0.75, length(q));
    g = mix(g, vec3(0.0), Frame.y);
    g = mix(g, vec3(0.86, 0.87, 0.88), Pass.w);
    // the black bars
    if (Frame.x > 0.0) {
        vec2 size = vec2(textureSize(SceneSampler, 0));
        float barH = 0.5 * (1.0 - (size.x / Frame.x) / size.y);
        if (uv.y < barH || uv.y > 1.0 - barH) g = vec3(0.0);
    }
    fragColor = vec4(g, 1.0);
}
