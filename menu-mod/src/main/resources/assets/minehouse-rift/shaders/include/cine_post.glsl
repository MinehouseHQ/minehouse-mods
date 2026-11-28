#ifndef MINEHOUSE_CINE_POST_GLSL
#define MINEHOUSE_CINE_POST_GLSL

layout(std140) uniform CinePost {
    vec4 Look;   // x: exposure, y: bloom, z: grain, w: vignette
    vec4 Pass;   // xy: blur step (texels), z: seconds, w: white (0..1) over everything
    vec4 Frame;  // x: letterbox aspect (0 = none), y: black (0..1), z: zoom blur towards the middle, w: threshold
    vec4 Aim;    // xy: centre of the zoom blur (0..1), z: lift of the blacks, w: focus distance (0 = no depth of field)
    vec4 Tint;   // x: how much of the real colours shows through the grade (0 = all steel)
};

#endif
