#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// The look of the Rift mode over the finished picture of the world: the edges of the screen sink
// into the colour of the rift, colours split a little there, and when things go badly the picture
// loses colour and the edge beats.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform RiftConfig {
    // rgb: the colour of the edge; a: how much the shadows lean to it (0..1)
    vec4 EdgeColor;
    // how far the dark edge comes in (0: none, 1: strong)
    float Vignette;
    // 0: full colour, 1: grey
    float Desaturate;
    // how much the edge beats (0: still), about once a second
    float Pulse;
    // how far red and blue part at the edge, in screen widths
    float Aberration;
};

// The rifts in sight, four at most. A: both ends of the tear on the screen (0..1), bottom then
// top. B: x how open it is (0: none), y its seed.
layout(std140) uniform RiftTears {
    vec4 Tear0A;
    vec4 Tear0B;
    vec4 Tear1A;
    vec4 Tear1B;
    vec4 Tear2A;
    vec4 Tear2B;
    vec4 Tear3A;
    vec4 Tear3B;
};

layout(location = 0) out vec4 fragColor;

const vec3 Gray = vec3(0.3, 0.59, 0.11);
const vec3 TearRim = vec3(0.80, 0.42, 1.0);
const vec3 TearDeep = vec3(0.10, 0.03, 0.30);
const vec3 TearSky = vec3(0.45, 0.85, 1.0);

float hash1(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// A broken line: straight between random heights, the way a crack runs.
float crack(float x) {
    float i = floor(x);
    return mix(hash1(i), hash1(i + 1.0), fract(x)) - 0.5;
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), f.x), mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(17.0, 9.0);
        a *= 0.5;
    }
    return v;
}

// One tear at this point of the screen. Returns x: how far outside its edge (below zero: inside),
// in lengths of the tear; y: along it (0 bottom, 1 top); z: across it, from its middle line;
// w: its length on the screen. `across` is the direction over it on the screen.
vec4 tearAt(vec4 a, vec4 b, vec2 uv, float aspect, float seconds, out vec2 across) {
    vec2 p0 = a.xy * vec2(aspect, 1.0);
    vec2 axis = a.zw * vec2(aspect, 1.0) - p0;
    float len = max(length(axis), 1.0e-4);
    vec2 dir = axis / len;
    vec2 side = vec2(dir.y, -dir.x);
    across = side / vec2(aspect, 1.0);
    vec2 rel = uv * vec2(aspect, 1.0) - p0;
    float t = dot(rel, dir) / len;
    float s = dot(rel, side) / len;
    float seed = b.y;
    float tc = clamp(t, 0.0, 1.0);
    float line = crack(tc * 6.0 + seed) * 0.16 + crack(tc * 19.0 + seed * 3.0 + floor(seconds * 7.0) * 0.37) * 0.035;
    // The ends are pinned, the middle wanders.
    float body = sin(3.14159265 * tc);
    line *= smoothstep(0.0, 0.25, body);
    float breath = 0.85 + 0.15 * sin(seconds * 2.3 + seed) + 0.06 * sin(seconds * 11.0 + seed * 5.0);
    float width = 0.15 * pow(body, 0.75) * (0.7 + 0.6 * abs(crack(tc * 11.0 + seed * 7.0))) * b.x * breath;
    float beyond = max(max(-t, t - 1.0), 0.0);
    float d = length(vec2(max(abs(s - line) - width, 0.0), beyond));
    if (beyond == 0.0 && abs(s - line) < width) {
        d = abs(s - line) - width;
    }
    return vec4(d, t, s - line, len);
}

// What is seen through a tear: a bright sky of the other side behind slow clouds of the rift.
vec3 through(vec2 q, float seconds, float seed) {
    vec2 flow = q * 3.0 + vec2(seed, seconds * 0.25);
    float clouds = fbm(flow + fbm(flow * 1.7 - seconds * 0.15) * 1.5);
    vec3 c = mix(TearDeep, TearSky, smoothstep(0.25, 0.8, clouds));
    c = mix(c, vec3(1.0), smoothstep(0.62, 0.95, clouds));
    vec2 cell = floor(q * 60.0);
    float star = step(0.985, hash2(cell + seed)) * (0.5 + 0.5 * sin(seconds * 3.0 + hash2(cell) * 40.0));
    return c + star * (1.0 - smoothstep(0.3, 0.6, clouds));
}

void main() {
    vec2 fromCentre = texCoord - 0.5;
    // 0 in the middle, about 1 in the corners, whatever the shape of the window
    float r = length(fromCentre * vec2(1.0, 0.85)) * 1.7;
    float edge = smoothstep(0.38, 1.02, r);

    // GameTime runs 0..1 over a Minecraft day (20 minutes).
    float seconds = GameTime * 1200.0;
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);

    // The rifts first bend the picture around them, towards the tear and a little along it.
    vec4 tearA[4] = vec4[4](Tear0A, Tear1A, Tear2A, Tear3A);
    vec4 tearB[4] = vec4[4](Tear0B, Tear1B, Tear2B, Tear3B);
    vec2 bend = vec2(0.0);
    for (int i = 0; i < 4; i++) {
        if (tearB[i].x <= 0.0) {
            continue;
        }
        vec2 across;
        vec4 k = tearAt(tearA[i], tearB[i], texCoord, aspect, seconds, across);
        float near = exp(-max(k.x, 0.0) * 7.0) * tearB[i].x;
        float wave = sin(k.y * 28.0 - seconds * 5.0 + tearB[i].y) * 0.35 + 1.0;
        bend -= across * sign(k.z) * near * wave * k.w * 0.085;
    }
    vec2 at = texCoord + bend;

    vec2 shift = fromCentre * edge * Aberration + bend * 0.35;
    vec3 color = vec3(
        texture(InSampler, at + shift).r,
        texture(InSampler, at).g,
        texture(InSampler, at - shift).b
    );

    float luma = dot(color, Gray);
    color = mix(color, vec3(luma), Desaturate);
    // Shadows take a breath of the rift's colour, lights stay as they are.
    color += EdgeColor.rgb * (1.0 - smoothstep(0.0, 0.6, luma)) * 0.10 * EdgeColor.a;

    float beat = 0.5 + 0.5 * sin(seconds * 6.2831853 * 1.1);
    float dark = clamp(edge * Vignette * (1.0 + Pulse * (beat - 0.35)), 0.0, 1.0);
    color = mix(color, EdgeColor.rgb * (0.22 + 0.3 * Pulse * beat), dark);

    // Then the tears themselves, over everything: the light of the edge and what is behind it.
    for (int i = 0; i < 4; i++) {
        if (tearB[i].x <= 0.0) {
            continue;
        }
        vec2 across;
        vec4 k = tearAt(tearA[i], tearB[i], texCoord, aspect, seconds, across);
        float open = tearB[i].x;
        float out_ = max(k.x, 0.0);
        float halo = 0.012 / (out_ + 0.012);
        float flicker = 0.85 + 0.15 * sin(seconds * 17.0 + tearB[i].y * 3.0) * sin(seconds * 5.3);
        color += TearRim * halo * halo * 1.6 * open * flicker;
        color += TearRim * exp(-out_ * 4.5) * 0.22 * open;
        // Thin sparks run out of the tear along its length.
        float spark = crack(k.y * 9.0 + floor(seconds * 9.0) * 1.3 + tearB[i].y) * 0.5;
        float thread = smoothstep(0.012, 0.0, abs(k.z - spark * (0.25 + out_))) * exp(-out_ * 9.0) * step(0.0, k.y) * step(k.y, 1.0);
        color += vec3(1.0, 0.9, 1.0) * thread * 0.7 * open * step(0.55, hash1(floor(seconds * 9.0) + float(i)));
        if (k.x < 0.0) {
            float inside = clamp(-k.x / 0.03, 0.0, 1.0);
            vec3 other = through(vec2(k.z, k.y) * vec2(2.0, 1.0), seconds, tearB[i].y);
            vec3 core = mix(vec3(1.0, 0.95, 1.0), other, smoothstep(0.0, 0.6, inside));
            color = mix(color, core, open);
        }
    }

    fragColor = vec4(color, 1.0);
}
