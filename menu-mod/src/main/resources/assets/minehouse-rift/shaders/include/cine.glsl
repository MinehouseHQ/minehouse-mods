#ifndef MINEHOUSE_CINE_GLSL
#define MINEHOUSE_CINE_GLSL

// One frame of a client-side film: where the camera is, when it is, where the light comes from, the fog.
layout(std140) uniform CineInfo {
    mat4 View;      // world to view
    vec4 CamTime;   // xyz: camera position in the world, w: seconds of the shot
    vec4 LightDir;  // xyz: towards the key light, in view space; w: how strong the light from behind is
    vec4 FogInfo;   // rgb: fog colour, a: density
    vec4 Extra;     // x: seconds of the whole film, y: focus distance, z: depth of field, w: unused
};

#endif
