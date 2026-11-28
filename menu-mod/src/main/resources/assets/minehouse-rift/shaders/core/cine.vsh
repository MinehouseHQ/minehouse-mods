#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minehouse-rift:cine.glsl>

// A mesh of the film, drawn once or many times (one per instance). TextureMat carries the material:
// [0] = (material, a, b, c), [1], [2] = more of it, [3].w > 0.5 = a sprite that always faces the camera.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Normal;
layout(location = 2) in vec2 UV0;
layout(location = 3) in vec4 IPos;   // xyz: where, w: size
layout(location = 4) in vec4 IRot;   // a quaternion
layout(location = 5) in vec4 IScale; // xyz: stretch, w: spare
layout(location = 6) in vec4 IData;  // anything the material wants: brightness, seed...

layout(location = 0) out vec3 vObj;
layout(location = 1) out vec3 vNormal;
layout(location = 2) out vec2 vUv;
layout(location = 3) out vec4 vData;
layout(location = 4) out vec3 vView;
layout(location = 5) out vec3 vLocal;
layout(location = 6) out vec3 vLocalNormal;
layout(location = 7) out vec3 vSize;  // how big the copy is along its own axes (blocks tile their texture by it)

vec3 qrot(vec4 q, vec3 v) {
    return v + 2.0 * cross(q.xyz, cross(q.xyz, v) + q.w * v);
}

void main() {
    vLocal = Position;
    vLocalNormal = Normal;
    vUv = UV0;
    vData = IData;
    vSize = IScale.xyz * IPos.w;
    if (TextureMat[3].w > 0.5) {
        vec4 c = ModelViewMat * vec4(IPos.xyz, 1.0);
        float ca = cos(IScale.w), sa = sin(IScale.w);
        vec2 off = Position.xy * IScale.xy * IPos.w * length(ModelViewMat[0].xyz);
        vec3 vp = c.xyz + vec3(ca * off.x - sa * off.y, sa * off.x + ca * off.y, 0.0);
        vView = vp;
        vObj = IPos.xyz;
        vNormal = vec3(0.0, 0.0, 1.0);
        gl_Position = ProjMat * vec4(vp, 1.0);
        return;
    }
    vec3 p = qrot(IRot, Position * IScale.xyz * IPos.w) + IPos.xyz;
    vec3 n = qrot(IRot, Normal / max(IScale.xyz, vec3(0.0001)));
    vec4 v = ModelViewMat * vec4(p, 1.0);
    vObj = p;
    vView = v.xyz;
    vNormal = normalize(mat3(ModelViewMat) * n);
    gl_Position = ProjMat * v;
}
