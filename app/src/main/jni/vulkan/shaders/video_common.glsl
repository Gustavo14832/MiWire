// Shared by the fragment shaders: the push constants, and turning the video's non-linear R'G'B'
// into the output's.

// Keep in sync with PushConstants in vulkan_renderer.cpp
layout(push_constant) uniform PushConstants {
    vec4 uvRect;
    vec4 uvClamp;
    vec4 params;   // x = dither amplitude, y = frame counter, z = output mode, w = content peak nits
    vec4 params2;  // x = SDR reference white nits, y = quarter turns clockwise to pre-rotate by
    vec4 ycbcr;    // Planar video: x = Kr, y = Kb, z = chroma midpoint, w = chroma U offset
    vec4 texel;    // x = 1/bufferWidth, y = 1/bufferHeight, z = RCAS sharpen peak (0 = off), w = reserved
} pc;

const float OUTPUT_PASSTHROUGH = 0.0;
const float OUTPUT_PQ_TO_SDR = 1.0;

// AMD FidelityFX Robust Contrast-Adaptive Sharpening (5-tap cross) with anti-ringing clamp
// and a subtle noise gate so compression grain is not amplified.
vec3 applyRcas(vec3 e, vec3 b, vec3 d, vec3 f, vec3 h, float peak) {
    vec3 ec = clamp(e, 0.0, 1.0);
    vec3 bc = clamp(b, 0.0, 1.0);
    vec3 dc = clamp(d, 0.0, 1.0);
    vec3 fc = clamp(f, 0.0, 1.0);
    vec3 hc = clamp(h, 0.0, 1.0);

    vec3 mn4 = min(min(bc, dc), min(fc, hc));
    vec3 mx4 = max(max(bc, dc), max(fc, hc));
    vec3 mn = min(mn4, ec);
    vec3 mx = max(mx4, ec);

    vec3 ampMin = mn / max(4.0 * mx4, vec3(1e-4));
    vec3 ampMax = (1.0 - mx) / max(4.0 * (1.0 - mn4), vec3(1e-4));
    vec3 amp = min(ampMin, ampMax);
    float maxAmp = min(min(amp.r, amp.g), amp.b);

    float contrast = dot(mx - mn, vec3(0.2126, 0.7152, 0.0722));
    float noiseGate = smoothstep(0.008, 0.03, contrast);

    float w = -min(peak, maxAmp) * noiseGate;
    return clamp((ec + w * (bc + dc + fc + hc)) / (1.0 + 4.0 * w), 0.0, 1.0);
}

// Interleaved gradient noise (Jimenez 2014). Its energy sits at high spatial frequencies
// like blue noise, so the dither reads as fine grain rather than blotches.
float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

// SMPTE ST 2084 EOTF, returning linear light where 1.0 is 10000 nits
vec3 pqToLinear(vec3 e) {
    const float m1 = 0.1593017578125;
    const float m2 = 78.84375;
    const float c1 = 0.8359375;
    const float c2 = 18.8515625;
    const float c3 = 18.6875;
    vec3 ep = pow(max(e, 0.0), vec3(1.0 / m2));
    return pow(max(ep - c1, 0.0) / (c2 - c3 * ep), vec3(1.0 / m1));
}

// HDR10 content on a surface that cannot take it: map to SDR BT.709
vec3 pqToSdr(vec3 e) {
    float sdrWhite = pc.params2.x;
    vec3 lin = pqToLinear(e) * (10000.0 / sdrWhite);

    // BT.2020 to BT.709 primaries (column-major)
    lin = mat3(1.6605, -0.1246, -0.0182,
              -0.5876,  1.1329, -0.1006,
              -0.0728, -0.0083,  1.1187) * lin;
    lin = max(lin, 0.0);

    // Extended Reinhard on luminance, with the content peak mapped to SDR white
    float white = max(pc.params.w / sdrWhite, 1.0);
    float l = dot(lin, vec3(0.2126, 0.7152, 0.0722));
    if (l > 0.0) {
        float lt = l * (1.0 + l / (white * white)) / (1.0 + l);
        lin *= lt / l;
    }

    return pow(clamp(lin, 0.0, 1.0), vec3(1.0 / 2.2));
}

vec4 outputColor(vec3 color) {
    if (pc.params.z == OUTPUT_PQ_TO_SDR) {
        color = pqToSdr(color);
    }

    float amplitude = pc.params.x;
    if (amplitude > 0.0) {
        // Triangular dither of +/- 1 LSB from two decorrelated noise samples, moved each
        // frame so the pattern averages out over time instead of sitting still.
        vec2 p = gl_FragCoord.xy + 5.588238 * mod(pc.params.y, 64.0);
        float n = ign(p) + ign(p + vec2(47.0, 17.0)) - 1.0;
        color += n * amplitude;
    }

    return vec4(color, 1.0);
}
