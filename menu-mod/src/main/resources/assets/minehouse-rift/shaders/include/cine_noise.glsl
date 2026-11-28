#ifndef MINEHOUSE_CINE_NOISE_GLSL
#define MINEHOUSE_CINE_NOISE_GLSL

float cHash2(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float cHash3(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }

float cNoise2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(cHash2(i), cHash2(i + vec2(1, 0)), f.x), mix(cHash2(i + vec2(0, 1)), cHash2(i + vec2(1, 1)), f.x), f.y);
}

float cNoise3(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(cHash3(i), cHash3(i + vec3(1, 0, 0)), f.x), mix(cHash3(i + vec3(0, 1, 0)), cHash3(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(cHash3(i + vec3(0, 0, 1)), cHash3(i + vec3(1, 0, 1)), f.x), mix(cHash3(i + vec3(0, 1, 1)), cHash3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float cFbm3(vec3 p, int octaves) {
    float a = 0.5, s = 0.0;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        s += a * cNoise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

float cFbm2(vec2 p, int octaves) {
    float a = 0.5, s = 0.0;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 7; i++) {
        if (i >= octaves) break;
        s += a * cNoise2(p);
        p = m * p;
        a *= 0.5;
    }
    return s;
}

float cRidge3(vec3 p, int octaves) {
    float a = 0.5, s = 0.0;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        float n = 1.0 - abs(2.0 * cNoise3(p) - 1.0);
        s += a * n * n;
        p = p * 2.07 + vec3(3.1, 1.3, 7.7);
        a *= 0.5;
    }
    return s;
}

// distance to the walls of 3D cells (foam, honeycomb)
float cCells3(vec3 x) {
    vec3 n = floor(x), f = fract(x);
    float F1 = 8.0, F2 = 8.0;
    for (int k = -1; k <= 1; k++) for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
        vec3 g = vec3(i, j, k);
        vec3 o = vec3(cHash3(n + g), cHash3(n + g + 11.3), cHash3(n + g + 27.1)) * 0.8 + 0.1;
        vec3 r = g + o - f;
        float d = dot(r, r);
        if (d < F1) { F2 = F1; F1 = d; } else if (d < F2) { F2 = d; }
    }
    return sqrt(F2) - sqrt(F1);
}

#endif
