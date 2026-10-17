#version 150

/* The finished frame, copied aside so the pass can read it while writing over the original */
uniform sampler2D Sampler0;

/* Which effect this pass is (FxOverlay.GRADE ... FxOverlay.EYE) and the size of the frame in pixels */
uniform float Mode;
uniform vec2 Size;

/* The effect's parameters, p0..p23 of FxOverlay in groups of four */
uniform vec4 U0;
uniform vec4 U1;
uniform vec4 U2;
uniform vec4 U3;
uniform vec4 U4;
uniform vec4 U5;

in vec2 texCoord0;

out vec4 fragColor;

float aspect()
{
    return Size.x / max(Size.y, 1.0);
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

float luma(vec3 c)
{
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec2 rotate(vec2 p, float degrees)
{
    float a = radians(degrees);
    float c = cos(a);
    float s = sin(a);

    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

/* 1: lift / gamma / gain, then brightness, contrast, saturation, hue and a tint */
vec3 grade(vec3 c)
{
    vec3 lift = U2.rgb;
    vec3 gamma = max(U3.rgb, vec3(0.01));
    vec3 gain = U4.rgb;

    c = lift * (vec3(1.0) - c) + gain * pow(max(c, vec3(0.0)), vec3(1.0) / gamma);
    c += U1.z;
    c = (c - 0.5) * U1.w + 0.5;
    c = mix(vec3(luma(c)), c, U1.x);

    mat3 toYIQ = mat3(0.299, 0.596, 0.211, 0.587, -0.274, -0.523, 0.114, -0.322, 0.312);
    mat3 toRGB = mat3(1.0, 1.0, 1.0, 0.956, -0.272, -1.106, 0.621, -0.647, 1.703);
    vec3 yiq = toYIQ * c;

    yiq.yz = rotate(yiq.yz, U1.y);
    c = toRGB * yiq;

    return mix(c, U0.rgb, clamp(U0.a, 0.0, 1.0));
}

/* 2: tape - scan lines, torn bands and split colour */
vec3 vhs(vec2 uv)
{
    float i = U0.x;
    float t = U0.y;
    float on = step(0.9, hash(vec2(floor(t * 8.0), 3.0)));
    float band = hash(vec2(floor(uv.y * 40.0), floor(t * 12.0))) - 0.5;
    float off = band * 0.05 * i * on + (noise(vec2(uv.y * 8.0, t * 3.0)) - 0.5) * 0.004 * i;
    vec3 c;

    c.r = scene(uv + vec2(off + 0.003 * i, 0.0)).r;
    c.g = scene(uv + vec2(off, 0.0)).g;
    c.b = scene(uv + vec2(off - 0.003 * i, 0.0)).b;

    float scan = 0.5 + 0.5 * sin(uv.y * Size.y * 3.14159);

    c *= 1.0 - 0.25 * i * (1.0 - scan);
    c += (hash(uv * Size + t) - 0.5) * 0.08 * i;

    return c;
}

/* 3: old film - flicker, warm fade, scratches */
vec3 vintage(vec2 uv)
{
    float i = U0.x;
    float t = U0.y;
    float frame = floor(t * 24.0);
    vec3 c = scene(uv);

    c *= 1.0 + (hash(vec2(frame, 1.0)) - 0.5) * 0.25 * i;

    vec3 sepia = vec3(luma(c)) * vec3(1.15, 1.0, 0.8);

    c = mix(c, sepia, 0.6 * i);

    float x = hash(vec2(frame, 7.0));
    float scratch = (1.0 - smoothstep(0.0, 0.0015, abs(uv.x - x))) * step(0.6, hash(vec2(frame, 9.0)));

    c += scratch * 0.4 * i;
    c += (hash(uv * Size + frame) - 0.5) * 0.06 * i;

    float d = length(uv - 0.5);

    return c * (1.0 - smoothstep(0.4, 0.9, d) * 0.5 * i);
}

/* 4: blur streaking out of the centre */
vec3 radial(vec2 uv)
{
    vec2 d = (uv - 0.5) * 0.08 * U0.x;
    vec3 sum = vec3(0.0);

    for (int k = 0; k < 12; k++)
    {
        sum += scene(uv - d * (float(k) / 11.0));
    }

    return sum / 12.0;
}

/* 5: smudges lit by the bright parts of the frame, and drops bending what is behind them */
vec3 lens(vec2 uv)
{
    float i = U0.x;
    vec2 cells = vec2(18.0, 10.0);
    vec2 g = floor(uv * cells);
    vec2 cell = fract(uv * cells) - 0.5;
    float drop = (1.0 - smoothstep(0.0, 0.35, length(cell))) * step(0.85, hash(g));
    vec3 c = scene(uv + cell * drop * 0.05 * i);

    float dirt = smoothstep(0.35, 0.9, noise(uv * 6.0) * noise(uv * 13.0 + 3.0));

    c += dirt * 0.6 * i * smoothstep(0.4, 1.0, luma(c) + 0.2);
    c += drop * 0.05 * i;

    return c;
}

/* 6: specks and hairs of a worn projector, changing twelve times a second */
vec3 dust(vec2 uv)
{
    float i = U0.x;
    float frame = floor(U0.y * 12.0);
    vec3 c = scene(uv);
    float h = hash(floor(uv * vec2(160.0, 90.0)) + frame);
    float speck = step(1.0 - 0.0035 * i, h);
    float dark = step(1.0 - 0.0035 * i, hash(floor(uv * vec2(160.0, 90.0)) + frame + 17.0));

    c = mix(c, vec3(1.0), speck * 0.8);
    c = mix(c, vec3(0.02), dark * 0.7);

    float hair = (1.0 - smoothstep(0.0, 0.002, abs(uv.x - 0.5 - 0.3 * sin(uv.y * 9.0 + frame)))) * step(0.97, hash(vec2(frame, 21.0)));

    c = mix(c, vec3(0.05), hair * 0.5 * i);

    return c * (1.0 + (hash(vec2(frame, 2.0)) - 0.5) * 0.06 * i);
}

/* 7: a warm glow drifting in from the edge, screened over the frame */
vec3 leak(vec2 uv)
{
    float i = U0.x;
    float t = U0.y;
    vec3 c = scene(uv);
    vec2 centre = vec2(0.95 + 0.08 * sin(t * 0.7), 0.5 + 0.25 * sin(t * 0.4));
    float g = pow(max(0.0, 1.0 - length((uv - centre) * vec2(1.2, 0.9))), 2.0);
    vec3 glow = mix(vec3(1.0, 0.35, 0.1), vec3(1.0, 0.8, 0.3), g) * g * i;

    return vec3(1.0) - (vec3(1.0) - c) * (vec3(1.0) - glow);
}

/* 8: the colour channels pulled apart, along a direction or away from a centre */
vec3 chroma(vec2 uv)
{
    float i = U0.x;
    vec2 centre = U1.zw;
    vec2 dir;
    float mask = 1.0;

    if (U0.z > 0.5)
    {
        float a = radians(U0.y);

        dir = vec2(cos(a), sin(a));
    }
    else
    {
        vec2 d = (uv - centre) * vec2(aspect(), 1.0);

        dir = d / max(length(d), 0.0001);
        mask = smoothstep(U0.w, U0.w + mix(0.6, 0.01, clamp(U1.x, 0.0, 1.0)), length(d));
        dir.x /= aspect();
    }

    float s = i * 0.02 * mask;
    float balance = U1.y;
    vec2 rOff = dir * s * (1.0 + balance);
    vec2 bOff = -dir * s * (1.0 - balance);
    vec2 gOff = dir * s * U2.x;
    vec3 c = vec3(scene(uv + rOff).r, scene(uv + gOff).g, scene(uv + bOff).b);

    if (U2.y > 0.0)
    {
        vec3 half_ = vec3(scene(uv + rOff * 0.5).r, scene(uv + gOff * 0.5).g, scene(uv + bOff * 0.5).b);

        c = mix(c, 0.5 * (c + half_), clamp(U2.y, 0.0, 1.0));
    }

    return c;
}

/* 9: a barrel bulge inside a radius, optionally sharpened */
vec3 fish(vec2 uv)
{
    float k = U0.x * U0.y;
    float radius = max(U0.z, 0.05);
    vec2 p = (uv - 0.5) * 2.0 * vec2(aspect(), 1.0);
    float r = length(p);
    float f2 = (r * r) / (radius * radius);
    float mask = 1.0 - smoothstep(radius * clamp(U0.w, 0.0, 0.99), radius, r);
    vec2 q = p * mix(1.0, 1.0 - k * 0.6 * f2, mask);
    vec2 src = 0.5 + q * 0.5 / vec2(aspect(), 1.0);
    vec3 c = scene(src);

    if (U1.x > 0.0)
    {
        vec2 px = 1.0 / Size;
        vec3 blur = (scene(src + vec2(px.x, 0.0)) + scene(src - vec2(px.x, 0.0)) + scene(src + vec2(0.0, px.y)) + scene(src - vec2(0.0, px.y))) * 0.25;

        c += (c - blur) * U1.x * mask;
    }

    return c;
}

/* 10: warm air rippling the picture */
vec3 heat(vec2 uv)
{
    float strength = U0.x;
    float speed = U0.y;
    float scale = max(U0.z, 0.01);
    float t = U0.w;
    vec2 wave = vec2(
        sin(uv.y * scale * 40.0 + t * speed * 6.0) + noise(uv * scale * 8.0 + t * speed) - 0.5,
        cos(uv.x * scale * 35.0 + t * speed * 5.0)
    );

    return scene(uv + wave * 0.004 * strength);
}

/* 11: the edges of the frame sinking into a colour */
vec3 vignette(vec3 c, vec2 uv)
{
    float d = length((uv - 0.5) * vec2(aspect(), 1.0)) * 1.414;
    float m = smoothstep(U1.x, U1.x + max(U1.y, 0.01), d);

    return mix(c, U0.rgb, clamp(m * U0.a, 0.0, 1.0));
}

/* 12: everything outside a (possibly turned, shifted, zoomed) window covered */
vec3 letterbox(vec3 c, vec2 uv)
{
    float a = aspect();
    vec2 p = (uv - 0.5) * vec2(a, 1.0);

    p -= vec2(U2.y * a, U2.z);
    p = rotate(p, -U1.w);

    float w = U1.x;
    float h = U1.y;
    float zoom = max(U2.x, 0.01);
    float halfW = w * 0.5 * a * zoom;
    float halfH;

    if (h >= 0.0)
    {
        halfH = h * 0.5 * zoom;
    }
    else if (U2.w > 0.0)
    {
        halfH = min(1.0, w * a / U2.w) * 0.5 * zoom;
    }
    else
    {
        halfH = (1.0 - 2.0 * clamp(U3.x, 0.0, 0.49)) * 0.5 * zoom;
    }

    float dq = max(abs(p.x) - halfW, abs(p.y) - halfH);
    float f = max(U1.z * 0.5, 0.0008);
    float m = smoothstep(-f, f, dq);

    return mix(c, U0.rgb, clamp(m * U0.a, 0.0, 1.0));
}

/* 13: white noise film grain. Every pixel of every frame is drawn from its own seed (the window
 * pixel and the frame number), so nothing drifts in any direction: it flickers in place. A little
 * less in the bright parts, like the real thing. */
float hash13(vec3 p)
{
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);

    return fract((p.x + p.y) * p.z);
}

vec3 grain(vec3 c)
{
    float size = max(U0.y, 0.5);
    float frame = mod(floor(U0.z * 24.0), 4096.0);
    float n = hash13(vec3(floor(gl_FragCoord.xy / size), frame)) - 0.5;

    return c + n * U0.x * (1.0 - luma(c) * 0.5);
}

/* 14: lids closing around an elliptical opening */
vec3 eye(vec3 c, vec2 uv)
{
    float a = aspect();
    vec2 q = (uv - 0.5 - vec2(U2.y, U2.z) * 0.5) * vec2(a, 1.0) * 2.0;

    q = rotate(q, -U1.w);

    float open = 1.0 - clamp(U2.w, 0.0, 0.95);
    float rx = max(U1.x * U2.x * a * open, 0.001);
    float ry = max(U1.y * U2.x * open, 0.0005);
    float d = length(q / vec2(rx, ry));
    float f = max(U1.z, 0.003);
    float m = smoothstep(1.0 - f, 1.0 + f, d);

    return mix(c, U0.rgb, clamp(m * U0.a, 0.0, 1.0));
}

void main()
{
    vec2 uv = texCoord0;
    int mode = int(Mode + 0.5);
    vec3 c;

    if (mode == 1) c = grade(scene(uv));
    else if (mode == 2) c = vhs(uv);
    else if (mode == 3) c = vintage(uv);
    else if (mode == 4) c = radial(uv);
    else if (mode == 5) c = lens(uv);
    else if (mode == 6) c = dust(uv);
    else if (mode == 7) c = leak(uv);
    else if (mode == 8) c = chroma(uv);
    else if (mode == 9) c = fish(uv);
    else if (mode == 10) c = heat(uv);
    else if (mode == 11) c = vignette(scene(uv), uv);
    else if (mode == 12) c = letterbox(scene(uv), uv);
    else if (mode == 13) c = grain(scene(uv));
    else if (mode == 14) c = eye(scene(uv), uv);
    else c = scene(uv);

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
