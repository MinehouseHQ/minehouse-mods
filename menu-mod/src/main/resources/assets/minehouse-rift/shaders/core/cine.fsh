#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minehouse-rift:cine.glsl>
#include <minehouse-rift:cine_noise.glsl>

// The materials of the film. Everything is lit and coloured in plain grey-blue light; the steel grade, the bloom
// and the grain come after, over the whole frame.

layout(location = 0) in vec3 vObj;
layout(location = 1) in vec3 vNormal;
layout(location = 2) in vec2 vUv;
layout(location = 3) in vec4 vData;
layout(location = 4) in vec3 vView;
layout(location = 5) in vec3 vLocal;
layout(location = 6) in vec3 vLocalNormal;
layout(location = 7) in vec3 vSize;

layout(location = 0) out vec4 fragColor;

uniform sampler2D Atlas;

const int LIT = 0, EMISSIVE = 1, SEM = 2, GLOW = 3, PLANET = 4, STAR = 5, NERVE = 6, GLASS = 7, CLOUD = 8,
    ATMOS = 9, DUST = 10, PLASMA = 11, SILHOUETTE = 12, FOAM = 13, BUBBLE = 14, BLOCK = 15, PIXEL = 16;

float time() { return CamTime.w; }

vec3 fogged(vec3 c) {
    float d = length(vView);
    float f = 1.0 - exp(-d * FogInfo.a);
    return mix(c, FogInfo.rgb, f);
}

// a surface seen through an electron microscope: frosted, hairy, lit from the edges
vec3 sem(vec3 n, vec3 v, float scale, float hair, float baseLit) {
    vec3 l = normalize(LightDir.xyz);
    float ndl = dot(n, l);
    float wrap = clamp((ndl + 0.4) / 1.4, 0.0, 1.0);
    float rim = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 2.2);
    float fuzz = cFbm3(vObj * scale, 4);
    float fine = cNoise3(vObj * scale * 9.0);
    float pores = smoothstep(0.08, 0.0, cCells3(vObj * scale * 1.7)) * 0.35;
    float body = baseLit * (0.18 + 0.82 * wrap) * (0.55 + 0.6 * fuzz) * (1.0 - pores);
    float glow = rim * (0.65 + hair * (fine * 1.6 - 0.3)) * (0.6 + LightDir.w);
    return ColorModulator.rgb * (body + glow);
}

// a face of a unit box (-0.5..0.5) the way the game lays a block texture on it, in texture repeats along the face
// (sz: how many blocks the box is along each axis); .z is the game's face shade
vec3 blockFace(vec3 q, vec3 ln, vec3 sz) {
    if (abs(ln.y) > 0.5) return vec3((q.x + 0.5) * sz.x, (ln.y > 0.0 ? q.z + 0.5 : 0.5 - q.z) * sz.z, ln.y > 0.0 ? 1.0 : 0.5);
    if (abs(ln.x) > 0.5) return vec3((ln.x > 0.0 ? 0.5 - q.z : q.z + 0.5) * sz.z, (0.5 - q.y) * sz.y, 0.6);
    return vec3((ln.z > 0.0 ? q.x + 0.5 : 0.5 - q.x) * sz.x, (0.5 - q.y) * sz.y, 0.8);
}

// how the game lights a block face, with some of the film's key light (p0.z: how much)
float blockLight(vec3 n, float shade, float key) {
    float dif = clamp(dot(n, normalize(LightDir.xyz)), 0.0, 1.0);
    return shade * mix(1.0, 0.3 + 0.9 * dif, key);
}

void main() {
    int mat = int(TextureMat[0].x + 0.5);
    vec4 p0 = TextureMat[0];
    vec4 p1 = TextureMat[1];
    vec3 n = normalize(vNormal);
    vec3 v = normalize(-vView);
    if (dot(n, v) < 0.0) n = -n;
    float t = time();
    vec3 c = vec3(0.0);
    float alpha = 1.0;

    if (mat == LIT) {
        float dif = clamp(dot(n, normalize(LightDir.xyz)), 0.0, 1.0);
        float rim = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 3.0);
        c = ColorModulator.rgb * (0.08 + 0.92 * dif) + rim * p0.y;
        c = fogged(c);
    } else if (mat == EMISSIVE) {
        c = ColorModulator.rgb * p0.y * (vData.x > 0.0 ? vData.x : 1.0);
    } else if (mat == SEM) {
        c = sem(n, v, p0.y, p0.z, p0.w) * vData.x;
        // deep inside a pipe the light dies (p1.x: how far down along -z, in mesh units)
        if (p1.x > 0.0) c *= mix(1.0, 0.03, smoothstep(0.0, p1.x, -vLocal.z));
        c = fogged(c);
    } else if (mat == FOAM) {
        // a bubble skin: thin walls catch the light, the faces stay clear
        float cell = cCells3(vObj * p0.y);
        float wall = smoothstep(0.12, 0.0, cell);
        float rim = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 2.0);
        c = ColorModulator.rgb * (wall * (0.6 + 0.6 * cNoise3(vObj * p0.y * 6.0)) + rim * 0.5 + 0.04) * vData.x;
        c = fogged(c);
    } else if (mat == GLOW) {
        float r = length(vUv * 2.0 - 1.0);
        float core = exp(-r * r * p0.y);
        float halo = exp(-r * p0.z) * p0.w;
        c = ColorModulator.rgb * (core + halo) * max(vData.x, 0.0) * smoothstep(1.0, 0.85, r);
    } else if (mat == PLANET) {
        vec3 d = normalize(vLocal);
        float land = cFbm3(d * 2.2 + p1.x, 6);
        float cloud = cFbm3(d * 4.0 + vec3(t * 0.01, 0.0, 0.0) + 31.0, 5);
        float dif = clamp(dot(n, normalize(LightDir.xyz)), 0.0, 1.0);
        float dayLit = smoothstep(-0.05, 0.35, dot(n, normalize(LightDir.xyz)));
        vec3 albedo = mix(vec3(0.12, 0.16, 0.2), vec3(0.42, 0.45, 0.47), smoothstep(0.48, 0.62, land));
        albedo = mix(albedo, vec3(0.9), smoothstep(0.55, 0.78, cloud) * 0.85);
        c = albedo * dif * ColorModulator.rgb * 1.6;
        // the lights of cities on the night side
        float lights = smoothstep(0.62, 0.75, cFbm3(d * 26.0, 3)) * smoothstep(0.5, 0.6, land) * (1.0 - dayLit);
        c += vec3(0.7, 0.8, 0.9) * lights * p0.y;
        float rim = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 4.0);
        c += vec3(0.5, 0.65, 0.8) * rim * dayLit * p0.z;
    } else if (mat == STAR) {
        vec3 d = normalize(vLocal);
        float gran = cFbm3(d * p0.z + vec3(0.0, t * 0.05, 0.0), 5);
        float cells = smoothstep(0.0, 0.25, cCells3(d * p0.z * 2.0 + t * 0.02));
        float limb = pow(clamp(dot(n, v), 0.0, 1.0), 0.35);
        c = ColorModulator.rgb * p0.y * limb * (0.75 + 0.35 * gran) * (0.85 + 0.15 * cells);
    } else if (mat == NERVE) {
        float flick = 0.75 + 0.25 * sin(t * 6.0 + vData.y * 40.0 + vUv.x * 30.0);
        float pulse = smoothstep(0.85, 1.0, sin(vUv.x * 12.0 - t * 3.0 + vData.y * 9.0)) * 0.8;
        float side = pow(clamp(dot(n, v), 0.0, 1.0), 1.5);
        c = ColorModulator.rgb * p0.y * max(vData.x, 0.05) * (flick + pulse) * (0.35 + 0.65 * side);
    } else if (mat == GLASS) {
        // a slab of glass: dark inside, its edges and faces catch streaks of light
        vec3 q = abs(vLocal);
        float edge = max(max(min(q.x, q.y), min(q.y, q.z)), min(q.x, q.z));
        float e = smoothstep(0.42, 0.5, edge);
        float streak = pow(cNoise2(vec2(dot(vLocal, vec3(1.0, 0.3, 0.7)) * 20.0, 3.0 + vData.y * 7.0)), 6.0);
        float fres = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 2.0);
        c = ColorModulator.rgb * (0.05 + fres * 0.5 + e * 1.4 + streak * 1.2) * max(vData.x, 0.05) * p0.y;
    } else if (mat == CLOUD) {
        vec2 q = vUv * 2.0 - 1.0;
        float r = length(q);
        float n2 = cFbm2(q * 2.2 + vData.y * 13.0 + vec2(t * 0.03, 0.0), 5);
        float body = smoothstep(1.0, 0.1, r + 0.55 * (n2 - 0.5));
        c = ColorModulator.rgb * body * n2 * p0.y * max(vData.x, 0.0);
    } else if (mat == ATMOS) {
        float rim = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), p0.z);
        float lit = clamp(dot(n, normalize(LightDir.xyz)) + 0.3, 0.0, 1.0);
        c = ColorModulator.rgb * rim * lit * p0.y;
    } else if (mat == DUST) {
        float r = length(vUv * 2.0 - 1.0);
        float s = exp(-r * r * 6.0) * smoothstep(1.0, 0.7, r);
        c = ColorModulator.rgb * s * vData.x * p0.y;
    } else if (mat == PLASMA) {
        // feathers of light: stretched noise along the strip
        float along = vUv.x, across = vUv.y * 2.0 - 1.0;
        float f = cFbm2(vec2(along * p0.z + t * 0.2, across * 3.0 + vData.y * 10.0), 6);
        float strands = pow(1.0 - abs(2.0 * cNoise2(vec2(along * p0.z * 3.0, across * 9.0 + vData.y * 3.0)) - 1.0), 6.0);
        float fall = smoothstep(1.0, 0.2, abs(across)) * smoothstep(0.0, 0.15, along) * smoothstep(1.0, 0.7, along);
        c = ColorModulator.rgb * (f * 0.6 + strands * 1.4) * fall * p0.y * max(vData.x, 0.0);
    } else if (mat == BUBBLE) {
        // a thin skin of a bubble or a cell: its edge glows, its face has faint cells and a lit spot
        float nv = clamp(dot(n, v), 0.0, 1.0);
        float rim = pow(1.0 - nv, p0.z);
        float skin = cFbm3(vLocal * 3.0 + vData.y * 7.0, 4);
        float spot = pow(clamp(dot(reflect(-v, n), normalize(LightDir.xyz)), 0.0, 1.0), 12.0);
        c = ColorModulator.rgb * (rim * (0.6 + 0.8 * skin) + 0.06 * skin + spot * 0.6) * p0.y * vData.x;
    } else if (mat == BLOCK) {
        // a block of the game: its texture from the atlas (the rectangle per instance, or the top and bottom
        // of the material), cut where the texture is clear (leaves, glass); p0.y: glow, p0.w: grass-green top
        // TextureMat[3].x > 0: the texture repeats once per block of the box's size (a whole chunk in one box)
        vec3 sz = TextureMat[3].x > 0.5 ? max(floor(vSize / TextureMat[3].x + 0.5), vec3(1.0)) : vec3(1.0);
        vec3 f = blockFace(vLocal, vLocalNormal, sz);
        vec4 rect = vData;
        if (vLocalNormal.y > 0.5 && p1.z > p1.x) rect = p1;
        if (vLocalNormal.y < -0.5 && TextureMat[2].z > TextureMat[2].x) rect = TextureMat[2];
        vec2 span = rect.zw - rect.xy;
        vec2 uv = rect.xy + span * clamp(fract(f.xy), 0.002, 0.998);
        vec4 tex = textureGrad(Atlas, uv, dFdx(f.xy) * span, dFdy(f.xy) * span);
        if (tex.a < 0.1) discard;
        vec3 albedo = tex.rgb * ColorModulator.rgb;
        if (p0.w > 0.5 && vLocalNormal.y > 0.5) albedo *= vec3(0.55, 0.78, 0.36);
        // only the bright pixels glow: the berries of a vine, the flame of a torch, the cracks of glowstone
        float hot = smoothstep(0.45, 0.85, max(tex.r, max(tex.g, tex.b)));
        c = albedo * blockLight(n, f.z, p0.z) + tex.rgb * p0.y * hot;
        c = fogged(c);
    } else if (mat == PIXEL) {
        // one pixel of a texture as a cube: its colour in the instance, a = how much it glows
        vec3 f = blockFace(vLocal, vLocalNormal, vec3(1.0));
        c = vData.rgb * ColorModulator.rgb * blockLight(n, f.z, p0.z) + vData.rgb * vData.a * p0.y;
        c = fogged(c);
    } else if (mat == SILHOUETTE) {
        float rim = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 5.0);
        float back = clamp(dot(n, normalize(LightDir.xyz)), 0.0, 1.0);
        float tex = cFbm3(normalize(vLocal) * 4.0, 4);
        c = ColorModulator.rgb * (0.015 + 0.04 * tex + rim * back * p0.y);
    }
    // solid things keep how far they are in alpha (for the depth of field); light adds nothing there
    fragColor = vec4(max(c, vec3(0.0)), TextureMat[3].z > 0.5 ? 0.0 : length(vView));
}
