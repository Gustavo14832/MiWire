#version 450
#extension GL_GOOGLE_include_directive : require

// Full range YCbCr 4:2:0 in separate Y, Cb and Cr planes, as PyroWave decodes it
layout(set = 0, binding = 0) uniform sampler2D uPlanes[3];

#include "video_common.glsl"

layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 outColor;

vec3 samplePlanar(vec2 rawUv) {
    vec2 uv = clamp(rawUv, pc.uvClamp.xy, pc.uvClamp.zw);
    float y = texture(uPlanes[0], uv).r;

    // The host sites chroma with the left luma sample of each pair, not between them
    vec2 chromaUv = uv + vec2(pc.ycbcr.w, 0.0);
    float cb = texture(uPlanes[1], chromaUv).r - pc.ycbcr.z;
    float cr = texture(uPlanes[2], chromaUv).r - pc.ycbcr.z;

    float kr = pc.ycbcr.x;
    float kb = pc.ycbcr.y;
    float r = y + 2.0 * (1.0 - kr) * cr;
    float b = y + 2.0 * (1.0 - kb) * cb;
    float g = (y - kr * r - kb * b) / (1.0 - kr - kb);
    return vec3(r, g, b);
}

void main() {
    vec3 color = samplePlanar(vUv);
    if (pc.texel.z > 0.0) {
        vec2 dx = vec2(pc.texel.x, 0.0);
        vec2 dy = vec2(0.0, pc.texel.y);
        vec3 b = samplePlanar(vUv - dy);
        vec3 d = samplePlanar(vUv - dx);
        vec3 f = samplePlanar(vUv + dx);
        vec3 h = samplePlanar(vUv + dy);
        color = applyRcas(color, b, d, f, h, pc.texel.z);
    }
    outColor = outputColor(color);
}
