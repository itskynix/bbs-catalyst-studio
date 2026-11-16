#version 150

/* The post-process effect library (PostEffect): one effect per pass, picked by Mode. Every effect
 * computes its own look of the frame, which is then laid over the original by the intensity. */

/* The finished frame, copied aside so the pass can read it while writing over the original */
uniform sampler2D Sampler0;

/* The world's depth; only read when DepthInfo.z is 1 */
uniform sampler2D Sampler1;

uniform float Mode;
uniform vec2 Size;
/* Seconds into the clip: film time, so a scrub or an export looks the same as the preview */
uniform float Time;
uniform float Intensity;
/* The effect's parameters, in the order PostEffect lists them */
uniform vec4 P;
uniform vec3 Tint;
/* Near plane, far plane, 1 when the depth holds a scene */
uniform vec3 DepthInfo;

in vec2 texCoord0;

out vec4 fragColor;

const float TAU = 6.2831853;

float aspect()
{
    return Size.x / max(Size.y, 1.0);
}

vec2 px()
{
    return 1.0 / max(Size, vec2(1.0));
}

vec3 scene(vec2 uv)
{
    return texture(Sampler0, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

float hash(vec2 p)
{
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p)
{
    vec2 i = floor(p);
    vec2 f = fract(p);

    f = f * f * (3.0 - 2.0 * f);

    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p)
{
    float v = 0.0;
    float a = 0.5;

    for (int i = 0; i < 4; i++)
    {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }

    return v;
}

float luma(vec3 c)
{
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec3 rgb2hsv(vec3 c)
{
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;

    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c)
{
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);

    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

/* Edge strength from the brightness around the pixel, r pixels out */
float sobel(vec2 uv, float r)
{
    vec2 o = px() * r;

    float tl = luma(scene(uv + vec2(-o.x, o.y)));
    float t = luma(scene(uv + vec2(0.0, o.y)));
    float tr = luma(scene(uv + vec2(o.x, o.y)));
    float l = luma(scene(uv + vec2(-o.x, 0.0)));
    float rr = luma(scene(uv + vec2(o.x, 0.0)));
    float bl = luma(scene(uv + vec2(-o.x, -o.y)));
    float b = luma(scene(uv + vec2(0.0, -o.y)));
    float br = luma(scene(uv + vec2(o.x, -o.y)));

    float gx = -tl - 2.0 * l - bl + tr + 2.0 * rr + br;
    float gy = -bl - 2.0 * b - br + tl + 2.0 * t + tr;

    return sqrt(gx * gx + gy * gy);
}

/* Blocks from the camera to what the pixel shows; the far plane for the sky, -1 without depth */
float depthDist(vec2 uv)
{
    if (DepthInfo.z < 0.5)
    {
        return -1.0;
    }

    float d = texture(Sampler1, uv).r;
    float near = max(DepthInfo.x, 0.001);
    float far = max(DepthInfo.y, near + 1.0);

    if (d >= 0.99999)
    {
        return far;
    }

    return 2.0 * near * far / (far + near - (d * 2.0 - 1.0) * (far - near));
}

/* The bright parts of the frame, gathered on two rings around the pixel */
vec3 brightRing(vec2 uv, float threshold, float radius)
{
    vec3 acc = vec3(0.0);

    for (int i = 0; i < 16; i++)
    {
        float a = float(i) * TAU / 16.0;
        vec2 dir = vec2(cos(a), sin(a)) * radius / vec2(aspect(), 1.0);

        acc += max(scene(uv + dir * 0.5) - threshold, 0.0);
        acc += max(scene(uv + dir) - threshold, 0.0);
    }

    return acc / 32.0;
}

/* Distance to the nearest and second nearest cell point of a Voronoi pattern */
vec2 voronoi(vec2 p)
{
    vec2 id = floor(p);
    vec2 f = fract(p);
    float f1 = 8.0;
    float f2 = 8.0;

    for (int j = -1; j <= 1; j++)
    {
        for (int i = -1; i <= 1; i++)
        {
            vec2 g = vec2(float(i), float(j));
            vec2 o = vec2(hash(id + g), hash(id + g + 17.3));
            float d = length(g + o - f);

            if (d < f1)
            {
                f2 = f1;
                f1 = d;
            }
            else if (d < f2)
            {
                f2 = d;
            }
        }
    }

    return vec2(f1, f2);
}

float vignetteMask(vec2 uv, float from, float to)
{
    return smoothstep(from, to, length((uv - 0.5) * vec2(aspect(), 1.0)));
}

/* ---- Cinematic and optics ---- */

vec3 anamorphicFlares(vec2 uv, vec3 base)
{
    vec3 acc = vec3(0.0);
    float total = 0.0;
    float len = P.y * 0.25;

    for (int i = -12; i <= 12; i++)
    {
        float f = float(i) / 12.0;
        float w = 1.0 - abs(f);

        acc += max(scene(uv + vec2(f * len, 0.0)) - P.x, 0.0) * w;
        total += w;
    }

    return base + acc / total * Tint * 6.0;
}

vec3 bleachBypass(vec3 base)
{
    float l = luma(base);
    vec3 blend = mix(2.0 * base * l, 1.0 - 2.0 * (1.0 - base) * (1.0 - l), step(0.5, l));
    vec3 d = mix(blend, vec3(l), 0.35);

    return (d - 0.5) * P.x + 0.5;
}

vec3 hazyLights(vec2 uv, vec3 base)
{
    return base + brightRing(uv, P.x, P.y) * 3.0 + vec3(0.04) * (1.0 - luma(base));
}

vec3 iridescent(vec2 uv, vec3 base)
{
    float l = luma(base);
    vec3 rainbow = 0.5 + 0.5 * cos(TAU * (l * P.x + uv.x * 0.5 + uv.y * 0.3 + Time * P.y) + vec3(0.0, 2.094, 4.188));

    return mix(base, base * rainbow * 1.6 + rainbow * 0.08, 0.7);
}

vec3 screenDrops(vec2 uv)
{
    vec2 offset = vec2(0.0);
    float density = max(P.x, 1.0);

    for (int layer = 0; layer < 2; layer++)
    {
        float fl = float(layer);
        float scale = density * (1.0 + fl * 0.7);
        vec2 g = uv * vec2(aspect(), 1.0) * scale + vec2(0.0, Time * 0.15 * (1.0 + fl));
        vec2 id = floor(g);
        vec2 f = fract(g) - 0.5;
        float h = hash(id + fl * 7.0);

        if (h > 0.55)
        {
            vec2 c = vec2(hash(id + 3.1) - 0.5, hash(id + 5.7) - 0.5) * 0.5;
            vec2 d = f - c;
            float r = 0.18 * P.y * (0.5 + h);
            float m = 1.0 - smoothstep(r * 0.6, r, length(d));

            offset += d * m / scale / vec2(aspect(), 1.0) * 0.8;
        }
    }

    return scene(uv - offset) + vec3(length(offset) * 6.0);
}

vec3 mojoTone(vec3 base)
{
    float l = luma(base);
    vec3 shadows = vec3(-0.05, 0.05, 0.12);
    vec3 highlights = vec3(0.12, 0.04, -0.08);
    vec3 c = base + mix(shadows, highlights, smoothstep(0.2, 0.8, l)) * P.x * 2.0;

    return (c - 0.5) * 1.12 + 0.5;
}

vec3 selectiveColor(vec3 base)
{
    vec3 hsv = rgb2hsv(base);
    vec3 target = rgb2hsv(Tint);
    float dh = abs(hsv.x - target.x);

    dh = min(dh, 1.0 - dh);

    float keep = (1.0 - smoothstep(P.x, P.x + P.y + 0.001, dh)) * smoothstep(0.05, 0.2, hsv.y);

    return mix(vec3(luma(base)), base, keep);
}

vec3 halation(vec2 uv, vec3 base)
{
    return base + brightRing(uv, P.x, P.y) * vec3(1.0, 0.35, 0.15) * 2.5;
}

vec3 dayForNight(vec3 base)
{
    float l = luma(base);
    vec3 c = mix(vec3(l), base, 0.35) * vec3(0.55, 0.7, 1.1);

    return c * (1.0 - P.x * 0.7) + vec3(0.0, 0.01, 0.03);
}

/* ---- Glitch and signal ---- */

vec3 glitch(vec2 uv)
{
    float tick = floor(Time * P.y);
    float band = floor(uv.y * 24.0);
    vec2 u = uv;

    if (hash(vec2(band, tick)) < P.x * 0.6)
    {
        u.x += (hash(vec2(band + 1.3, tick)) - 0.5) * 0.15 * P.x;
    }

    float split = 0.01 * P.x * hash(vec2(tick, 2.0));
    vec3 c = vec3(scene(u + vec2(split, 0.0)).r, scene(u).g, scene(u - vec2(split, 0.0)).b);

    if (hash(floor(uv * vec2(16.0, 9.0)) + tick) > 1.0 - P.x * 0.08)
    {
        c = 1.0 - c;
    }

    return c;
}

vec3 worldGlitch(vec2 uv)
{
    float d = depthDist(uv);
    float key = d < 0.0 ? floor(uv.y * 20.0) : floor(d / max(P.x, 0.1));
    float tick = floor(Time * P.z);
    float r = hash(vec2(key, tick));
    vec2 off = vec2((r - 0.5) * P.y * 2.0, 0.0);
    vec3 c = vec3(scene(uv + off).r, scene(uv).g, scene(uv - off).b);

    return r > 0.85 ? c.gbr : c;
}

vec3 cyberScan(vec2 uv, vec3 base)
{
    float pos = 1.0 - fract(Time * P.x);
    float line = 1.0 - smoothstep(0.0, max(P.y, 0.001), abs(uv.y - pos));
    vec2 g = abs(fract(uv * vec2(aspect(), 1.0) * P.z) - 0.5);
    float grid = P.z > 0.0 ? smoothstep(0.46, 0.5, max(g.x, g.y)) : 0.0;

    return base + Tint * (line * (0.6 + luma(base)) + grid * (0.06 + line * 0.6));
}

vec3 monitor(vec2 uv)
{
    vec2 p = uv * 2.0 - 1.0;

    p *= 1.0 + dot(p, p) * P.x * 0.25;

    vec2 u = p * 0.5 + 0.5;

    if (u.x < 0.0 || u.x > 1.0 || u.y < 0.0 || u.y > 1.0)
    {
        return vec3(0.0);
    }

    vec3 c = scene(u);
    float scan = 0.5 + 0.5 * sin(u.y * Size.y * 3.14159);
    float m = mod(gl_FragCoord.x, 3.0);
    vec3 mask = m < 1.0 ? vec3(1.0, 0.7, 0.7) : (m < 2.0 ? vec3(0.7, 1.0, 0.7) : vec3(0.7, 0.7, 1.0));
    vec2 v = u * (1.0 - u);

    c *= mix(1.0, scan, P.y);
    c *= mix(vec3(1.0), mask, 0.5);

    return c * pow(clamp(v.x * v.y * 16.0, 0.0, 1.0), 0.25) * 1.15;
}

vec3 digitize(vec2 uv)
{
    vec2 grid = vec2(max(P.x, 1.0) * aspect(), max(P.x, 1.0));
    vec2 cell = floor(uv * grid);
    vec3 c = scene((cell + 0.5) / grid);
    float r = hash(cell + floor(Time * 10.0));

    return mix(c, Tint * (0.3 + luma(c)), step(P.y, r));
}

vec3 pixelDissolve(vec2 uv, vec3 base)
{
    vec2 cell = floor(uv * Size / max(P.y, 1.0));

    return mix(base, Tint, step(hash(cell), P.x));
}

vec3 channelSwapper(vec3 base)
{
    int m = int(P.x + 0.5);

    if (m == 0) return base.rbg;
    if (m == 1) return base.grb;
    if (m == 2) return base.gbr;
    if (m == 3) return base.brg;
    if (m == 4) return base.bgr;

    return base;
}

vec3 channelClamper(vec3 base)
{
    vec3 low = vec3(P.x);

    return clamp(base, low, max(low, low + Tint * (P.y - P.x)));
}

vec3 huePanner(vec3 base)
{
    vec3 hsv = rgb2hsv(base);

    hsv.x = fract(hsv.x + (P.x + Time * P.y) / 360.0);

    return hsv2rgb(hsv);
}

vec3 colorize(vec3 base)
{
    return Tint * luma(base) * P.x;
}

/* ---- Artistic and drawing ---- */

vec3 toonShading(vec2 uv, vec3 base)
{
    vec3 hsv = rgb2hsv(base);
    float levels = max(P.x, 2.0);

    hsv.z = floor(hsv.z * levels + 0.5) / levels;

    vec3 c = hsv2rgb(hsv);

    return c * (1.0 - smoothstep(P.y, P.y + 0.1, sobel(uv, 1.0)));
}

vec3 sketch(vec2 uv, vec3 base)
{
    float l = luma(base);
    vec2 q = gl_FragCoord.xy * 0.8 * P.x;
    float hatch1 = step(0.5 + 0.5 * sin(q.x + q.y), 1.0 - l) * (1.0 - l);
    float hatch2 = l < 0.4 ? step(0.5 + 0.5 * sin(q.x - q.y), 1.0 - l) : 0.0;
    float ink = max(clamp(sobel(uv, 1.0) * 3.0, 0.0, 1.0), max(hatch1 * 0.35, hatch2 * 0.4));

    return vec3(0.96, 0.94, 0.9) * (1.0 - ink);
}

vec3 comicHalftone(vec2 uv, vec3 base)
{
    vec2 q = gl_FragCoord.xy / max(P.x, 1.0);
    vec2 r = vec2(q.x * 0.7071 - q.y * 0.7071, q.x * 0.7071 + q.y * 0.7071);
    vec2 cell = fract(r) - 0.5;
    float radius = (1.0 - luma(base)) * 0.7;
    float dots = 1.0 - smoothstep(radius - 0.08, radius, length(cell));
    vec3 c = mix(min(base * 1.6 + 0.1, 1.0), base * 0.25, dots);

    return c * (1.0 - smoothstep(0.25, 0.45, sobel(uv, 1.0)));
}

vec3 drawing(vec2 uv, vec3 base)
{
    float ink = smoothstep(0.08, 0.3, sobel(uv, P.x));
    vec3 paper = mix(vec3(0.97, 0.95, 0.9), base, 0.15);

    return mix(paper, Tint, ink);
}

vec3 kuwahara(vec2 uv)
{
    int r = int(clamp(P.x, 1.0, 6.0) + 0.5);
    vec2 o = px();
    vec3 m0 = vec3(0.0), m1 = vec3(0.0), m2 = vec3(0.0), m3 = vec3(0.0);
    vec3 s0 = vec3(0.0), s1 = vec3(0.0), s2 = vec3(0.0), s3 = vec3(0.0);
    float n = float((r + 1) * (r + 1));

    for (int j = -6; j <= 6; j++)
    {
        for (int i = -6; i <= 6; i++)
        {
            if (abs(i) > r || abs(j) > r)
            {
                continue;
            }

            vec3 c = scene(uv + vec2(float(i), float(j)) * o);

            if (i <= 0 && j <= 0) { m0 += c; s0 += c * c; }
            if (i >= 0 && j <= 0) { m1 += c; s1 += c * c; }
            if (i <= 0 && j >= 0) { m2 += c; s2 += c * c; }
            if (i >= 0 && j >= 0) { m3 += c; s3 += c * c; }
        }
    }

    m0 /= n; m1 /= n; m2 /= n; m3 /= n;

    float v0 = dot(abs(s0 / n - m0 * m0), vec3(1.0));
    float v1 = dot(abs(s1 / n - m1 * m1), vec3(1.0));
    float v2 = dot(abs(s2 / n - m2 * m2), vec3(1.0));
    float v3 = dot(abs(s3 / n - m3 * m3), vec3(1.0));

    vec3 best = m0;
    float low = v0;

    if (v1 < low) { low = v1; best = m1; }
    if (v2 < low) { low = v2; best = m2; }
    if (v3 < low) { best = m3; }

    return best;
}

vec3 emboss(vec2 uv, vec3 base)
{
    vec2 o = px();

    return base + (scene(uv - o) - scene(uv + o)) * P.x;
}

vec3 hardEmboss(vec2 uv)
{
    vec2 o = px() * 1.5;

    return vec3(0.5 + (luma(scene(uv - o)) - luma(scene(uv + o))) * P.x);
}

vec3 edgeDetect(vec2 uv, vec3 base)
{
    float e = smoothstep(P.x, P.x + 0.2, sobel(uv, 1.0));

    return mix(vec3(1.0), base * 1.5, P.y) * e;
}

vec3 neon(vec2 uv, vec3 base)
{
    float e = smoothstep(P.y, P.y + 0.25, sobel(uv, 1.0));
    vec3 hsv = rgb2hsv(base);

    hsv.y = 1.0;
    hsv.z = 1.0;

    return base * 0.15 + hsv2rgb(hsv) * e * P.x;
}

const float BAYER[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);

vec3 dither(vec3 base)
{
    vec2 q = floor(gl_FragCoord.xy / max(P.y, 1.0));
    int index = int(mod(q.x, 4.0)) + int(mod(q.y, 4.0)) * 4;
    float threshold = (BAYER[index] + 0.5) / 16.0;
    float levels = max(P.x - 1.0, 1.0);

    return floor(base * levels + threshold) / levels;
}

vec3 posterize(vec3 base)
{
    float levels = max(P.x, 2.0);

    return floor(base * levels + 0.5) / levels;
}

/* ---- Mosaic and geometric ---- */

vec2 cells(float size)
{
    return Size / max(size, 1.0);
}

vec3 mosaic(vec2 uv)
{
    vec2 cs = cells(P.x);

    return scene((floor(uv * cs) + 0.5) / cs);
}

vec3 circles(vec2 uv)
{
    vec2 cs = cells(P.x);
    vec3 c = scene((floor(uv * cs) + 0.5) / cs);
    float d = length(fract(uv * cs) - 0.5);
    float r = 0.5 * P.y;

    return c * (1.0 - smoothstep(r - 0.05, r, d));
}

vec3 squares(vec2 uv)
{
    vec2 cs = cells(P.x);
    vec3 c = scene((floor(uv * cs) + 0.5) / cs);
    vec2 f = abs(fract(uv * cs) - 0.5);

    return c * step(max(f.x, f.y), 0.5 - P.y * 0.5);
}

vec3 tiles(vec2 uv)
{
    vec2 cs = cells(P.x);
    vec3 c = scene((floor(uv * cs) + 0.5) / cs);
    vec2 f = fract(uv * cs);
    float edge = min(min(f.x, f.y), min(1.0 - f.x, 1.0 - f.y));

    if (edge < P.y)
    {
        return vec3(0.08);
    }

    return c * (1.0 + (f.x - f.y) * 0.15);
}

vec3 kaleidoscope(vec2 uv)
{
    vec2 p = (uv - 0.5) * vec2(aspect(), 1.0);
    float segment = TAU / max(floor(P.x), 1.0);
    float a = mod(atan(p.y, p.x) + radians(P.y), segment);
    float r = length(p);

    a = abs(a - segment * 0.5);

    return scene(vec2(cos(a), sin(a)) * r / vec2(aspect(), 1.0) + 0.5);
}

vec3 pyramid(vec2 uv)
{
    vec2 cs = cells(P.x);
    vec2 id = floor(uv * cs);
    vec2 f = fract(uv * cs) - 0.5;
    float shade;
    vec2 dir;

    if (abs(f.x) > abs(f.y))
    {
        shade = f.x > 0.0 ? 0.8 : 1.15;
        dir = vec2(sign(f.x), 0.0);
    }
    else
    {
        shade = f.y > 0.0 ? 1.2 : 0.75;
        dir = vec2(0.0, sign(f.y));
    }

    vec3 c = scene((id + 0.5 + f * (1.0 - P.y) + dir * 0.25 * P.y) / cs);

    return c * mix(1.0, shade, P.y);
}

vec3 gridCell(vec2 uv)
{
    vec2 p = uv * Size / max(P.x, 1.0);
    vec2 s = vec2(1.0, 1.7320508);
    vec4 hc = floor(vec4(p, p - vec2(0.5, 1.0)) / s.xyxy) + 0.5;
    vec4 h = vec4(p - hc.xy * s, p - (hc.zw + 0.5) * s);
    bool first = dot(h.xy, h.xy) < dot(h.zw, h.zw);
    vec2 center = first ? hc.xy * s : (hc.zw + 0.5) * s;
    vec2 local = first ? h.xy : h.zw;
    vec2 a = abs(local);
    float dist = max(dot(a, normalize(vec2(1.0, 1.7320508))), a.x);
    vec3 c = scene(center * max(P.x, 1.0) / Size);

    return c * (1.0 - smoothstep(0.44, 0.5, dist) * P.y);
}

vec3 wired(vec2 uv, vec3 base)
{
    float line = smoothstep(0.05, 0.25, sobel(uv, 1.0) * P.x);
    vec2 g = abs(fract(uv * vec2(aspect(), 1.0) * P.y) - 0.5);
    float grid = P.y > 0.0 ? smoothstep(0.47, 0.5, max(g.x, g.y)) : 0.0;

    return base * 0.08 + Tint * (line + grid * 0.15);
}

vec3 mirror(vec2 uv)
{
    int m = int(P.x + 0.5);
    vec2 u = uv;

    if (m == 0 || m == 2) u.x = u.x > 0.5 ? 1.0 - u.x : u.x;
    if (m == 1 || m == 2) u.y = u.y < 0.5 ? 1.0 - u.y : u.y;

    return scene(u);
}

/* ---- Atmosphere and nature ---- */

vec3 frost(vec2 uv)
{
    vec2 p = uv * vec2(aspect(), 1.0) * 8.0 * P.y;
    float n = fbm(p);
    float n2 = fbm(p * 3.0 + 5.0);
    float edge = length(uv - 0.5) * 1.4;
    float amount = smoothstep(1.0 - P.x, 1.2 - P.x * 0.6, edge + (n - 0.5) * 0.5);
    vec3 c = scene(uv + (vec2(n, n2) - 0.5) * 0.03 * amount);

    return mix(c, vec3(0.85, 0.92, 1.0) * (0.7 + 0.3 * n2), amount * 0.75);
}

vec3 snow(vec2 uv, vec3 base)
{
    vec3 c = base;

    for (int i = 0; i < 4; i++)
    {
        float fi = float(i);
        vec2 p = uv * vec2(aspect(), 1.0) * (6.0 + fi * 5.0);

        p.y += Time * P.y * (0.6 + fi * 0.25);
        p.x += sin(Time * 0.7 + fi) * 0.3 + sin(p.y * 0.5) * 0.1;

        vec2 id = floor(p);
        vec2 f = fract(p) - 0.5;

        if (hash(id + fi * 13.0) < 0.35 * P.x)
        {
            vec2 o = vec2(hash(id + 1.7), hash(id + 3.3)) - 0.5;

            c += (1.0 - smoothstep(0.0, 0.08 - fi * 0.012, length(f - o * 0.6))) * 0.9;
        }
    }

    return c;
}

vec3 screenFire(vec2 uv, vec3 base)
{
    vec2 p = vec2(uv.x * aspect() * 4.0, uv.y * 3.0 - Time * P.y * 1.5);
    float f = clamp(fbm(p) * 1.4 - uv.y / max(P.x, 0.01), 0.0, 1.0);

    f = pow(f, 1.5);

    return base + (vec3(1.5, 0.5, 0.1) * f + vec3(1.0, 0.9, 0.3) * f * f * f) * 1.2;
}

vec3 scratches(vec2 uv, vec3 base)
{
    float tick = floor(Time * P.y);
    vec3 c = base;

    for (int i = 0; i < 6; i++)
    {
        float fi = float(i);
        float r = hash(vec2(fi, tick));

        if (r < P.x)
        {
            float x = hash(vec2(tick, fi * 3.1));
            float w = 0.0008 + 0.0015 * hash(vec2(fi, tick + 1.0));
            float line = (1.0 - smoothstep(0.0, w, abs(uv.x - x))) * (0.5 + 0.5 * noise(vec2(uv.y * 20.0, tick)));

            c = mix(c, vec3(r > P.x * 0.5 ? 0.95 : 0.05), line * 0.8);
        }
    }

    if (hash(floor(uv * Size / 3.0) + tick) > 0.9995)
    {
        c = vec3(0.05);
    }

    return c;
}

vec3 screenDecals(vec2 uv, vec3 base)
{
    vec2 p = uv * vec2(aspect(), 1.0) * 3.0 * P.y;
    float stain = smoothstep(1.0 - P.x * 0.6, 1.0, fbm(p + 7.0) + 0.35 * fbm(p * 4.0));
    float spots = smoothstep(0.75, 0.8, noise(p * 6.0)) * P.x;
    vec3 c = mix(base, base * Tint * 1.5, stain);

    return mix(c, Tint * 0.3, spots * 0.6);
}

vec3 worldSplash(vec2 uv)
{
    float d = depthDist(uv);
    float w = d < 0.0 ? 1.0 - uv.y : 1.0 - smoothstep(P.x * 0.5, P.x, d);
    vec2 wave = vec2(sin(uv.y * 40.0 * P.y + Time * 6.0 * P.z), cos(uv.x * 35.0 * P.y + Time * 5.0 * P.z)) * 0.008 * w;
    vec3 c = scene(uv + wave);

    c = mix(c, c * vec3(0.75, 0.9, 1.1) + vec3(0.0, 0.03, 0.06), w * 0.6);

    return c + pow(max(0.0, sin(uv.x * 60.0 + Time * 4.0) * sin(uv.y * 50.0 - Time * 3.0)), 12.0) * 0.3 * w;
}

vec3 screenWaves(vec2 uv)
{
    float k = P.y * TAU;
    vec2 off = vec2(sin(uv.y * k + Time * P.z * TAU), sin(uv.x * k * 0.8 + Time * P.z * 5.0)) * P.x;

    return scene(uv + off);
}

vec3 distanceDistortion(vec2 uv)
{
    float d = depthDist(uv);
    float w = d < 0.0 ? smoothstep(0.3, 1.0, uv.y) : smoothstep(P.x, P.x * 3.0 + 1.0, d);
    vec2 off = vec2(noise(uv * vec2(40.0, 30.0) + vec2(0.0, Time * 2.0 * P.z)) - 0.5, noise(uv * vec2(30.0, 40.0) - vec2(Time * 1.7 * P.z, 0.0)) - 0.5);

    return scene(uv + off * P.y * 2.0 * w);
}

vec3 underwater(vec2 uv)
{
    vec2 off = vec2(sin(uv.y * 25.0 + Time * 2.0), cos(uv.x * 20.0 + Time * 1.6)) * P.y;
    vec3 c = scene(uv + off);
    float d = depthDist(uv);
    float fog = d < 0.0 ? 0.4 : clamp(d / 48.0, 0.0, 1.0);
    float caustic = pow(noise(uv * vec2(aspect(), 1.0) * 12.0 + Time * 0.6) * noise(uv * 9.0 - Time * 0.5) * 2.0, 3.0);

    c = mix(c * vec3(0.55, 0.85, 1.0), vec3(0.03, 0.2, 0.3), fog * P.x);

    return c + vec3(0.6, 0.9, 1.0) * caustic * 0.25 * (1.0 - fog);
}

/* ---- Psychological and tension ---- */

vec3 alarm(vec2 uv, vec3 base)
{
    float throb = 0.5 + 0.5 * sin(Time * P.x * TAU);
    float v = vignetteMask(uv, 0.3, 0.9);

    return mix(base, base * 0.5 + Tint * 0.8, v * throb) + Tint * throb * 0.08;
}

vec3 alcohol(vec2 uv)
{
    vec2 sway = vec2(sin(Time * P.y * 1.3), cos(Time * P.y * 0.9)) * P.x;
    vec2 wobble = vec2(sin(uv.y * 6.0 + Time * P.y * 2.0), 0.0) * P.x * 0.4;
    vec3 c = mix(scene(uv + sway), scene(uv - sway), 0.5);

    return mix(c, scene(uv + wobble), 0.3);
}

vec3 drug(vec2 uv)
{
    vec2 off = vec2(sin(uv.y * 10.0 + Time * P.x * 2.0), cos(uv.x * 12.0 + Time * P.x * 1.7)) * P.y;
    vec3 hsv = rgb2hsv(scene(uv + off));

    hsv.x = fract(hsv.x + Time * P.x * 0.1 + length(uv - 0.5) * 0.5);
    hsv.y = min(1.0, hsv.y * 1.5 + 0.2);

    return hsv2rgb(hsv);
}

vec3 infected(vec2 uv, vec3 base)
{
    float n = fbm(uv * vec2(aspect(), 1.0) * 4.0 + Time * 0.05);
    float ridge = pow(1.0 - abs(n * 2.0 - 1.0), 8.0);
    float mask = smoothstep(1.0 - P.x, 1.3 - P.x * 0.5, length(uv - 0.5) * 1.6 + ridge * 0.3);
    float throb = 0.85 + 0.15 * sin(Time * 3.0);
    vec3 c = mix(base, base * 0.6, mask * 0.5);

    return mix(c, Tint * 0.6 * throb, ridge * mask);
}

vec3 haunted(vec2 uv, vec3 base)
{
    float flicker = 1.0 - P.y * 0.4 * step(0.85, hash(vec2(floor(Time * 12.0), 3.0)));
    vec3 c = mix(vec3(luma(base)), base, 0.25) * vec3(0.85, 0.95, 1.05);
    vec3 ghost = scene(uv + vec2(sin(Time * 0.7) * 0.01, 0.005));

    c = mix(c, ghost * vec3(0.7, 0.85, 1.0), 0.25);

    return c * (1.0 - vignetteMask(uv, 0.2, 0.9) * P.x) * flicker;
}

vec3 meltedWorld(vec2 uv)
{
    float n = noise(vec2(uv.x * 20.0, Time * P.y));
    vec2 u = uv + vec2(sin(uv.y * 20.0 + Time) * 0.03 * P.x, P.x * n * (0.3 + (1.0 - uv.y)));

    return scene(u);
}

vec3 screenDamage(vec2 uv, vec3 base)
{
    float n = fbm(uv * 6.0);
    float blood = smoothstep(0.75 - P.x * 0.4, 1.0 - P.x * 0.3, length((uv - 0.5) * vec2(aspect(), 1.0)) + n * 0.25);
    vec2 v = voronoi(uv * vec2(aspect(), 1.0) * 5.0);
    float crack = (1.0 - smoothstep(0.0, 0.04, v.y - v.x)) * step(1.0 - P.x, hash(floor(uv * 3.0)));
    vec3 c = mix(base, base * 0.5 + vec3(0.18), crack * P.x);

    return mix(c, Tint * (0.4 + 0.4 * n), blood * 0.85);
}

vec3 sonar(vec2 uv, vec3 base)
{
    float range = max(P.z, 1.0);
    float d = depthDist(uv);
    float t = mod(Time * P.x, range);

    if (d < 0.0)
    {
        d = length((uv - 0.5) * vec2(aspect(), 1.0)) * range;
    }

    float ring = (1.0 - smoothstep(0.0, max(P.y, 0.01), abs(d - t))) * (1.0 - t / range);

    return base * 0.85 + Tint * ring * 1.2;
}

vec3 speedLines(vec2 uv, vec3 base)
{
    vec2 p = (uv - 0.5) * vec2(aspect(), 1.0);
    float a = atan(p.y, p.x) / TAU * P.x;
    float h = hash(vec2(floor(a), floor(Time * 15.0)));
    float line = step(0.6, h) * smoothstep(1.0 - P.y, 1.0, length(p) * 1.6 + h * 0.2) * (1.0 - abs(fract(a) - 0.5) * 2.0);

    return mix(base, vec3(1.0), clamp(line, 0.0, 1.0) * 0.85);
}

vec3 pulse(vec2 uv)
{
    float phase = fract(Time * P.x);
    float beat = exp(-phase * 10.0) + 0.6 * exp(-max(phase - 0.18, 0.0) * 12.0) * step(0.18, phase);
    vec3 c = scene(0.5 + (uv - 0.5) * (1.0 - beat * P.y));

    return mix(c, c * 0.4 + Tint * 0.3, vignetteMask(uv, 0.3, 1.0) * beat);
}

vec3 nightVision(vec2 uv, vec3 base)
{
    float l = luma(base) * P.y;
    float n = hash(uv * Size + floor(Time * 30.0));
    vec3 c = vec3(0.1, 1.0, 0.3) * (l + (n - 0.5) * P.x);

    c *= 0.9 + 0.1 * sin(uv.y * Size.y * 1.5);

    return c * (1.0 - vignetteMask(uv, 0.35, 0.75));
}

vec3 thermalVision(vec3 base)
{
    float l = clamp((luma(base) - 0.5) * P.x + 0.5, 0.0, 1.0);

    if (l < 0.25) return mix(vec3(0.0, 0.0, 0.15), vec3(0.2, 0.0, 0.6), l / 0.25);
    if (l < 0.5) return mix(vec3(0.2, 0.0, 0.6), vec3(0.9, 0.1, 0.2), (l - 0.25) / 0.25);
    if (l < 0.75) return mix(vec3(0.9, 0.1, 0.2), vec3(1.0, 0.7, 0.0), (l - 0.5) / 0.25);

    return mix(vec3(1.0, 0.7, 0.0), vec3(1.0, 1.0, 0.9), (l - 0.75) / 0.25);
}

vec3 effect(int mode, vec2 uv, vec3 base)
{
    /* Cinematic and optics */
    if (mode == 0) return anamorphicFlares(uv, base);
    if (mode == 1) return bleachBypass(base);
    if (mode == 2) return hazyLights(uv, base);
    if (mode == 3) return iridescent(uv, base);
    if (mode == 4) return screenDrops(uv);
    if (mode == 5) return mojoTone(base);
    if (mode == 6) return selectiveColor(base);
    if (mode == 7) return halation(uv, base);
    if (mode == 8) return dayForNight(base);

    /* Glitch and signal */
    if (mode == 9) return glitch(uv);
    if (mode == 10) return worldGlitch(uv);
    if (mode == 11) return cyberScan(uv, base);
    if (mode == 12) return monitor(uv);
    if (mode == 13) return digitize(uv);
    if (mode == 14) return pixelDissolve(uv, base);
    if (mode == 15) return channelSwapper(base);
    if (mode == 16) return channelClamper(base);
    if (mode == 17) return huePanner(base);
    if (mode == 18) return colorize(base);
    if (mode == 19) return 1.0 - base;

    /* Artistic and drawing */
    if (mode == 20) return toonShading(uv, base);
    if (mode == 21) return sketch(uv, base);
    if (mode == 22) return comicHalftone(uv, base);
    if (mode == 23) return drawing(uv, base);
    if (mode == 24) return kuwahara(uv);
    if (mode == 25) return emboss(uv, base);
    if (mode == 26) return hardEmboss(uv);
    if (mode == 27) return edgeDetect(uv, base);
    if (mode == 28) return neon(uv, base);
    if (mode == 29) return dither(base);
    if (mode == 30) return posterize(base);

    /* Mosaic and geometric */
    if (mode == 31) return mosaic(uv);
    if (mode == 32) return circles(uv);
    if (mode == 33) return squares(uv);
    if (mode == 34) return tiles(uv);
    if (mode == 35) return kaleidoscope(uv);
    if (mode == 36) return pyramid(uv);
    if (mode == 37) return gridCell(uv);
    if (mode == 38) return wired(uv, base);
    if (mode == 39) return mirror(uv);

    /* Atmosphere and nature */
    if (mode == 40) return frost(uv);
    if (mode == 41) return snow(uv, base);
    if (mode == 42) return screenFire(uv, base);
    if (mode == 43) return scratches(uv, base);
    if (mode == 44) return screenDecals(uv, base);
    if (mode == 45) return worldSplash(uv);
    if (mode == 46) return screenWaves(uv);
    if (mode == 47) return distanceDistortion(uv);
    if (mode == 48) return underwater(uv);

    /* Psychological and tension */
    if (mode == 49) return alarm(uv, base);
    if (mode == 50) return alcohol(uv);
    if (mode == 51) return drug(uv);
    if (mode == 52) return infected(uv, base);
    if (mode == 53) return haunted(uv, base);
    if (mode == 54) return meltedWorld(uv);
    if (mode == 55) return screenDamage(uv, base);
    if (mode == 56) return sonar(uv, base);
    if (mode == 57) return speedLines(uv, base);
    if (mode == 58) return pulse(uv);
    if (mode == 59) return nightVision(uv, base);
    if (mode == 60) return thermalVision(base);

    return base;
}

/* ---- Catalyst Studio's composite: a clip drawn on its own (Sampler1, premultiplied) laid over
 * what is under it (Sampler0) with a blend mode: 100 normal, 101 add, 102 multiply, 103 screen,
 * 104 overlay. P is the clip's crop rectangle in uv (x1, y1, x2, y2); Tint.x and Tint.y flip the
 * clip around the point in DepthInfo.xy. ---- */

vec3 blendMode(int mode, vec3 b, vec3 l)
{
    if (mode == 101) return b + l;
    if (mode == 102) return b * l;
    if (mode == 103) return 1.0 - (1.0 - b) * (1.0 - l);
    if (mode == 104) return mix(2.0 * b * l, 1.0 - 2.0 * (1.0 - b) * (1.0 - l), step(0.5, b));

    return l;
}

vec4 composite(int mode, vec2 uv)
{
    vec4 b = texture(Sampler0, uv);
    vec2 at = uv;

    if (Tint.x > 0.5) at.x = 2.0 * DepthInfo.x - at.x;
    if (Tint.y > 0.5) at.y = 2.0 * DepthInfo.y - at.y;

    vec4 l = texture(Sampler1, clamp(at, vec2(0.0), vec2(1.0)));

    if (at.x < 0.0 || at.x > 1.0 || at.y < 0.0 || at.y > 1.0 || uv.x < P.x || uv.x > P.z || uv.y < P.y || uv.y > P.w)
    {
        l = vec4(0.0);
    }

    float a = clamp(l.a * Intensity, 0.0, 1.0);
    vec3 color = l.a > 0.0001 ? l.rgb / l.a : vec3(0.0);

    return vec4(mix(b.rgb, clamp(blendMode(mode, b.rgb, color), 0.0, 1.0), a), max(b.a, a));
}

void main()
{
    vec2 uv = texCoord0;
    int mode = int(Mode + 0.5);

    if (mode >= 100)
    {
        fragColor = composite(mode, uv);

        return;
    }

    vec4 source = texture(Sampler0, uv);
    vec3 base = source.rgb;
    vec3 c = effect(mode, uv, base);

    /* The alpha is kept: a clip drawn on its own stays see-through where it was */
    fragColor = vec4(clamp(mix(base, c, clamp(Intensity, 0.0, 1.0)), 0.0, 1.0), source.a);
}
