#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// The look of the Rift mode over the finished picture of the world: the edges of the screen sink
// into the colour of the rift, colours split a little there, and when things go badly the picture
// loses colour and the edge beats.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

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
// top. B: x how open it is (0: none), y its seed, z and w the depth of its bottom and its top as
// the depth buffer holds it: what stands nearer hides the tear.
layout(std140) uniform RiftTears {
    vec4 Tear0A;
    vec4 Tear0B;
    vec4 Tear1A;
    vec4 Tear1B;
    vec4 Tear2A;
    vec4 Tear2B;
    vec4 Tear3A;
    vec4 Tear3B;
    // x: how much of the screen the field of a restart has taken (0..1)
    vec4 Screen;
    // the view the world was drawn with, the sway of walking in it
    mat4 View;
};

layout(location = 0) out vec4 fragColor;

const vec3 Gray = vec3(0.3, 0.59, 0.11);
const vec3 RiftRim = vec3(0.72, 0.95, 1.0);
const vec3 RiftGlow = vec3(0.16, 0.55, 0.95);
const vec3 RiftFar = vec3(0.42, 0.22, 0.85);
const vec3 RiftCloud = vec3(0.25, 0.75, 0.95);
const vec3 RiftPale = vec3(0.74, 0.94, 0.96);

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

// Where this point of the screen lies about something of a rift that hangs `offset` blocks from
// its middle: in halves of the rift's height, as seen at that depth. `radius` is that half on the
// screen and `nearness` grows as the thing comes nearer. A rift never takes more than most of the
// screen, however near the viewer stands, so nothing of it can smear over the whole picture.
// a: the middle from the viewer in blocks, and half the height. b.w: the zoom of the view.
vec2 about(vec4 a, vec4 b, vec3 offset, vec2 uv, float aspect, out float radius, out float nearness) {
    vec4 clip = View * vec4(a.xyz + offset, 1.0);
    float w = max(clip.w, 0.2);
    nearness = 1.0 / w;
    radius = clamp(a.w * b.w / w * 0.5, 1.0e-4, 0.6);
    return (uv - (clip.xy / w * 0.5 + 0.5)) * vec2(aspect, 1.0) / radius;
}

// 1 where nothing of the world stands before the rift at this point of the screen, 0 where it is hidden.
float shown(vec4 b, vec2 uv) {
    // The game keeps depth turned over: nearer is greater, about (near plane / distance).
    return b.z >= texture(DepthSampler, uv).r * 0.97 ? 1.0 : 0.0;
}

vec2 hash22(vec2 p) {
    return fract(sin(vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)))) * 43758.5453);
}

// Broken glass: the piece this point falls in. Returns how far it is from the piece's straight
// edge; `middle` is the piece's own point and `id` its number.
float piece(vec2 q, out vec2 middle, out float id) {
    vec2 cell = floor(q);
    vec2 f = q - cell;
    vec2 nearest = vec2(0.0);
    vec2 to = vec2(0.0);
    float first = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            vec2 r = g + 0.12 + 0.76 * hash22(cell + g) - f;
            float d = dot(r, r);
            if (d < first) {
                first = d;
                to = r;
                nearest = g;
            }
        }
    }
    middle = q + to;
    id = hash2(cell + nearest + 3.7);
    float edge = 8.0;
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            vec2 g = nearest + vec2(float(x), float(y));
            vec2 r = g + 0.12 + 0.76 * hash22(cell + g) - f;
            vec2 between = r - to;
            if (dot(between, between) > 1.0e-5) {
                edge = min(edge, dot(0.5 * (to + r), normalize(between)));
            }
        }
    }
    return edge;
}

// The first screen of the MineHouse site: near-black, a faint arc of light over the horizon and
// slow waves of small dots on the ground below it.
vec3 field(vec2 uv, float aspect, float seconds) {
    vec3 c = vec3(0.035, 0.035, 0.043);
    float x = (uv.x - 0.5) * aspect;
    // The horizon bends down to the sides, as a wide lens would show it.
    float horizon = 0.30 - x * x * 0.035;
    float arc = exp(-abs(uv.y - horizon) * 55.0) * 0.10 + exp(-abs(uv.y - horizon) * 9.0) * 0.035;
    c += vec3(0.85, 0.87, 1.0) * arc * exp(-x * x * 0.9);
    // The ground: four ranges of low hills, the far ones first; each is lit along its crest and
    // falls into the dark below it. All of it is seen through one even grid of dots.
    float lit = 0.0;
    for (int k = 0; k < 4; k++) {
        float near = float(k) / 3.0;
        float wide = mix(2.6, 1.3, near);
        float ridge = mix(0.23, 0.10, near)
            + (fbm(vec2(x * wide + float(k) * 7.3 + seconds * 0.012 * (1.0 + near), float(k) * 3.1 + seconds * 0.02)) - 0.45) * mix(0.16, 0.28, near);
        float under = ridge - uv.y;
        if (under > 0.0) {
            lit = exp(-under * mix(22.0, 9.0, near)) * mix(0.55, 1.0, near) * smoothstep(0.0, 0.004, under);
        }
    }
    vec2 cell = fract(vec2(x, uv.y) * 150.0) - 0.5;
    float dot_ = 1.0 - smoothstep(0.22, 0.34, length(cell));
    c += vec3(0.95) * dot_ * lit * exp(-x * x * 0.35);
    return c;
}

void main() {
    vec2 fromCentre = texCoord - 0.5;
    // 0 in the middle, about 1 in the corners, whatever the shape of the window
    float r = length(fromCentre * vec2(1.0, 0.85)) * 1.7;
    float edge = smoothstep(0.38, 1.02, r);

    // GameTime runs 0..1 over a Minecraft day (20 minutes).
    float seconds = GameTime * 1200.0;
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);

    // The rifts first draw the picture a little towards their middle.
    vec4 tearA[4] = vec4[4](Tear0A, Tear1A, Tear2A, Tear3A);
    vec4 tearB[4] = vec4[4](Tear0B, Tear1B, Tear2B, Tear3B);
    vec2 bend = vec2(0.0);
    for (int i = 0; i < 4; i++) {
        if (tearB[i].x <= 0.0) {
            continue;
        }
        float radius;
        float nearness;
        vec2 p = about(tearA[i], tearB[i], vec3(0.0), texCoord, aspect, radius, nearness);
        float d = length(p);
        bend -= p / max(d, 0.05) * exp(-d * 1.8) * 0.018 * radius * min(tearB[i].x, 1.0) * shown(tearB[i], texCoord) / vec2(aspect, 1.0);
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

    // A restart (its last seconds, and the wait for the server to come back): the picture gives
    // way to the field of the MineHouse site.
    float gone = clamp(Screen.x, 0.0, 1.0);
    if (gone > 0.0) {
        color = mix(color, field(texCoord, aspect, seconds), gone);
    }

    // Then the rifts themselves: the air there is broken like glass. Pieces of it hang about a
    // bright middle, the world shows through them out of place, light and rays come out between.
    for (int i = 0; i < 4; i++) {
        if (tearB[i].x <= 0.0) {
            continue;
        }
        float open = min(tearB[i].x, 1.0) * (1.0 - gone) * shown(tearB[i], texCoord);
        if (open <= 0.0) {
            continue;
        }
        float radius;
        float nearness;
        vec2 p = about(tearA[i], tearB[i], vec3(0.0), texCoord, aspect, radius, nearness);
        float d = length(p);
        if (d > 2.6) {
            continue;
        }
        float seed = tearB[i].y;
        float breath = seconds * 0.5 + seed;

        // A ragged cloud of blue light behind everything.
        // It hangs half the rift's height behind the middle, so it slides behind the shards.
        float tall = tearA[i].w;
        float r2;
        float n2;
        vec2 back = about(tearA[i], tearB[i], normalize(tearA[i].xyz) * tall * 0.5, texCoord, aspect, r2, n2);
        float mist = fbm(back * 1.5 + vec2(seed, seconds * 0.05));
        float cloud = smoothstep(1.55, 0.55, length(back) + (mist - 0.5) * 1.1);
        vec3 fog = mix(vec3(0.03, 0.22, 0.50), vec3(0.08, 0.60, 0.78), mist) + RiftCloud * exp(-d * 2.2) * 0.8;
        color = mix(color, fog, cloud * 0.9 * open);
        float angle = atan(p.y, p.x);
        float rays = pow(max(sin(angle * 4.0 + seed + seconds * 0.05), 0.0), 30.0) + 0.6 * pow(max(sin(angle * 7.0 - seconds * 0.04 + seed * 2.0), 0.0), 50.0);
        color += RiftCloud * rays * exp(-d * 1.1) * 0.55 * open;

        // The shards: a few great ones and many splinters, all pointing at the middle and
        // drifting out of it. The nearest one found at a point lies on top.
        float edge = -1.0;
        float nearest = 0.0;
        float along = 0.0;
        float which = 0.0;
        vec2 inside = vec2(0.0);
        for (int n = 0; n < 12; n++) {
            float id = float(n) + seed * 13.0;
            float h1 = hash1(id * 1.7 + 0.3);
            float h2 = hash1(id * 3.1 + 5.0);
            float h3 = hash1(id * 5.3 + 9.0);
            int kind = n - (n / 4) * 4;
            float start;
            float len;
            float wide;
            if (kind == 0) {
                start = 0.10;
                len = 0.85 + 0.35 * h1;
                wide = 0.24 + 0.09 * h2;
            } else if (kind == 2) {
                start = 0.16 + 0.10 * h1;
                len = 0.30 + 0.25 * h2;
                wide = 0.07 + 0.04 * h3;
            } else {
                start = 0.14 + 0.35 * h1;
                len = 0.22 + 0.50 * h2;
                wide = 0.014 + 0.022 * h3;
            }
            start += 0.03 + 0.03 * sin(breath + id * 2.0);
            // Each shard hangs at its own place in the air, a little off the middle.
            vec3 off = (vec3(hash1(id * 7.1 + 1.0), hash1(id * 9.7 + 2.0), hash1(id * 11.3 + 3.0)) - 0.5) * vec3(0.55, 0.25, 0.55) * tall;
            float close;
            vec2 ps = about(tearA[i], tearB[i], off, texCoord, aspect, r2, close);
            float turn = (float(n) + 0.5 + (h3 - 0.5) * 0.6) / 12.0 * 6.2831853 + seed + 0.03 * sin(breath * 0.7 + id);
            vec2 dir = vec2(cos(turn), sin(turn));
            float u = dot(ps, dir);
            float v = dot(ps, vec2(-dir.y, dir.x));
            float t = (u - start) / len;
            if (t <= 0.0 || t > 1.3) {
                continue;
            }
            // Pointed at the middle, widest two thirds out, broken off askew at the end.
            float w = wide * min(t / 0.5, 1.0) * (1.0 - 0.2 * smoothstep(0.6, 1.0, t));
            float notch = wide * 0.16 * (floor(hash1(floor(u * 9.0 + id) + id) * 3.0) - 1.0) * smoothstep(0.2, 0.5, t);
            float left = w * (0.6 + 0.8 * h1) + v + notch;
            float right = w * (0.6 + 0.8 * h2) - v - notch * 0.6;
            float end = (start + len + (h3 - 0.5) * 2.2 * v + wide * 0.35 * sin(v / wide * 4.0 + id) - u) * 0.8;
            float e = min(min(left, right), end);
            if (e > 0.0 && close > nearest) {
                nearest = close;
                edge = e;
                along = t;
                which = id;
                inside = vec2(u, v);
            }
        }
        if (edge > 0.0) {
            // Slate-blue glass with the world dimly behind it, pale at the edges, cracked.
            vec3 glass = mix(vec3(0.20, 0.22, 0.40), vec3(0.32, 0.40, 0.58), fbm(inside * 3.0 + which));
            glass = mix(glass, texture(InSampler, at).rgb, 0.12);
            glass += RiftPale * exp(-d * 5.0) * 0.45;
            vec2 m2;
            float id2;
            float vein = piece(inside * vec2(2.2, 3.4) + which * 3.0 + 0.25 * fbm(inside * 6.0), m2, id2);
            glass += RiftPale * (1.0 - smoothstep(0.0, 0.02, vein)) * 0.30 * smoothstep(0.0, 0.04, edge);
            glass += RiftPale * (hash1(id2 * 31.0) - 0.5) * 0.09;
            glass = mix(glass, RiftPale, (1.0 - smoothstep(0.0, 0.014, edge)) * 0.9);
            glass += RiftPale * (1.0 - smoothstep(0.0, 0.07, edge)) * 0.10;
            color = mix(color, glass, 0.93 * open);
        }

        // The middle burns over everything, with a flare across it, and a few sparks hang about.
        float core = 0.022 / (d * d + 0.022);
        float flare = 0.004 / (p.y * p.y + 0.004) * exp(-abs(p.x) * 2.2);
        color += (vec3(0.85, 1.0, 1.0) * core * (1.2 + 0.12 * sin(seconds * 3.1 + seed)) + RiftCloud * flare * 0.5) * open;
        vec2 front = about(tearA[i], tearB[i], -normalize(tearA[i].xyz) * tall * 0.4, texCoord, aspect, r2, n2);
        vec2 grain = floor(front * 14.0);
        vec2 dot2 = fract(front * 14.0) - 0.5;
        float spark = step(0.975, hash2(grain + seed)) * (0.5 + 0.5 * sin(seconds * 2.0 + hash2(grain) * 40.0));
        color += RiftPale * spark * exp(-dot(dot2, dot2) * 60.0) * cloud * open;
    }

    fragColor = vec4(color, 1.0);
}
