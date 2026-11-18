#version 450
#extension GL_GOOGLE_include_directive : require

// The sampler carries an immutable VkSamplerYcbcrConversion, so sampling returns
// non-linear R'G'B' in the stream's own color encoding.
layout(set = 0, binding = 0) uniform sampler2D uVideo;

#include "video_common.glsl"

layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 outColor;

void main() {
    vec2 uv = clamp(vUv, pc.uvClamp.xy, pc.uvClamp.zw);
    vec3 color = texture(uVideo, uv).rgb;
    if (pc.texel.z > 0.0) {
        vec2 dx = vec2(pc.texel.x, 0.0);
        vec2 dy = vec2(0.0, pc.texel.y);
        vec3 b = texture(uVideo, clamp(vUv - dy, pc.uvClamp.xy, pc.uvClamp.zw)).rgb;
        vec3 d = texture(uVideo, clamp(vUv - dx, pc.uvClamp.xy, pc.uvClamp.zw)).rgb;
        vec3 f = texture(uVideo, clamp(vUv + dx, pc.uvClamp.xy, pc.uvClamp.zw)).rgb;
        vec3 h = texture(uVideo, clamp(vUv + dy, pc.uvClamp.xy, pc.uvClamp.zw)).rgb;
        color = applyRcas(color, b, d, f, h, pc.texel.z);
    }
    outColor = outputColor(color);
}
