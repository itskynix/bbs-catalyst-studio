#version 150

#moj_import <fog.glsl>

/* The decay, per pixel. The same maths as DecayPattern.java, with the same integer hash, so the
 * CPU atlas used under a shader pack draws the same pattern: a change here goes there too. */

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

/* x = progress, y = edge softness, z = pattern scale, w = depth inset */
uniform vec4 DecayParams;
/* Half sizes of the box and the height of its centre */
uniform vec4 DecayBox;
/* rgb and the form's opacity */
uniform vec4 DecayPrimary;
/* rgb and whether pixels outside the box are cut (1) */
uniform vec4 DecayEdge;
uniform int DecayPreset;
uniform int DecaySeed;
/* The form colour the decay is drawn under: its tint and fade, an illusion copy fainter */
uniform vec4 DecayTint;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;
in vec3 localPos;
in vec3 patternPos;

out vec4 fragColor;

uint decayHash(ivec3 c, int seed)
{
    uint h = uint(seed) * 374761393u + uint(c.x) * 668265263u + uint(c.y) * 1274126177u + uint(c.z) * 1911520717u;

    h = (h ^ (h >> 13u)) * 1274126177u;
    h ^= h >> 16u;

    return h;
}

float decayRnd(uint h)
{
    return float(h & 0xFFFFFFu) / 16777216.0;
}

float decaySmooth(float t)
{
    t = clamp(t, 0.0, 1.0);

    return t * t * (3.0 - 2.0 * t);
}

float decaySmoothstep(float a, float b, float x)
{
    return decaySmooth((x - a) / (b - a));
}

float decayNoise(vec3 p, int seed)
{
    vec3 fl = floor(p);
    ivec3 i = ivec3(fl);
    vec3 f = p - fl;

    f = f * f * (3.0 - 2.0 * f);

    float c000 = decayRnd(decayHash(i, seed));
    float c100 = decayRnd(decayHash(i + ivec3(1, 0, 0), seed));
    float c010 = decayRnd(decayHash(i + ivec3(0, 1, 0), seed));
    float c110 = decayRnd(decayHash(i + ivec3(1, 1, 0), seed));
    float c001 = decayRnd(decayHash(i + ivec3(0, 0, 1), seed));
    float c101 = decayRnd(decayHash(i + ivec3(1, 0, 1), seed));
    float c011 = decayRnd(decayHash(i + ivec3(0, 1, 1), seed));
    float c111 = decayRnd(decayHash(i + ivec3(1, 1, 1), seed));

    float y0 = mix(mix(c000, c100, f.x), mix(c010, c110, f.x), f.y);
    float y1 = mix(mix(c001, c101, f.x), mix(c011, c111, f.x), f.y);

    return mix(y0, y1, f.z);
}

float decayFbm(vec3 p, int seed)
{
    float v = decayNoise(p, seed) * 0.5 + decayNoise(p * 2.03, seed + 17) * 0.3 + decayNoise(p * 4.1, seed + 41) * 0.2;

    return clamp((v - 0.5) * 1.7 + 0.5, 0.0, 1.0);
}

/* F2 - F1 of a 3D cellular noise */
float decayVoronoiEdge(vec3 p, int seed)
{
    vec3 fl = floor(p);
    ivec3 i = ivec3(fl);
    float f1 = 8.0;
    float f2 = 8.0;

    for (int dx = -1; dx <= 1; dx++)
    {
        for (int dy = -1; dy <= 1; dy++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                ivec3 c = i + ivec3(dx, dy, dz);
                vec3 feature = vec3(c) + vec3(decayRnd(decayHash(c, seed)), decayRnd(decayHash(c, seed + 101)), decayRnd(decayHash(c, seed + 202)));
                float d = length(feature - p);

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
    }

    return f2 - f1;
}

void main()
{
    float hy = max(DecayBox.y, 1e-4);
    vec3 extent = max(DecayBox.xyz, vec3(1e-4));
    float ly = localPos.y - DecayBox.w;

    if (DecayEdge.w > 0.5 && (abs(localPos.x) > extent.x || abs(ly) > extent.y || abs(localPos.z) > extent.z))
    {
        discard;
    }

    float progress = DecayParams.x;
    float soft = max(DecayParams.y, 0.01);
    float s = max(DecayParams.z, 0.2);
    float inset = DecayParams.w;

    if (progress <= 0.0005)
    {
        discard;
    }

    float radial = min(1.0, length(vec3(localPos.x, ly, localPos.z) / extent) / 1.7320508);
    float y01 = clamp(ly / (2.0 * hy) + 0.5, 0.0, 1.0);
    vec3 p = patternPos;
    int seed = DecaySeed;
    vec3 rgb;
    float alpha;

    if (DecayPreset == 0)
    {
        float warp = 0.35 / s * 2.0;
        vec3 q = p * s * 1.3;
        vec3 w = p + (vec3(decayNoise(q, seed + 61), decayNoise(q, seed + 62), decayNoise(q, seed + 63)) - 0.5) * warp;
        float edge = decayVoronoiEdge(w * s, seed) / s;
        float branchDist = decayVoronoiEdge(w * s * 2.6, seed + 313) / (s * 2.6);
        float time = clamp(radial * 0.7 + decayFbm(p * s * 0.8, seed + 7) * 0.3, 0.0, 1.0);
        float gate = decaySmooth((progress * (1.0 + soft) - time) / soft);

        if (gate <= 0.002)
        {
            discard;
        }

        float width = 0.03 + 0.03 * progress;
        float core = (1.0 - decaySmoothstep(width * 0.5, width, edge)) * gate;
        float branchGate = decaySmoothstep(time * 0.5 + 0.3, time * 0.5 + 0.5, progress);
        float branch = (1.0 - decaySmoothstep(width * 0.25, width * 0.55, branchDist)) * branchGate * gate;

        core = max(core, branch * 0.85);

        float haloWidth = width * (2.4 + inset * 18.0);
        float halo = (1.0 - decaySmoothstep(width, haloWidth, edge)) * gate * (0.32 + inset * 2.0);

        alpha = min(1.0, core + halo * (1.0 - core));

        if (alpha <= 0.002)
        {
            discard;
        }

        float dark = max(0.0, 1.0 - inset * 4.0);
        float haloDark = max(0.2, 1.0 - inset * 2.5);

        rgb = (DecayPrimary.rgb * dark * core + DecayEdge.rgb * haloDark * halo * (1.0 - core)) / alpha;
    }
    else
    {
        float value;
        float time;
        float shade;

        if (DecayPreset == 1)
        {
            value = decayFbm(p * s * 1.1, seed) * 0.78 + decayNoise(p * s * 6.0, seed + 5) * 0.22;
            time = (1.0 - value) * 0.7 + radial * 0.3;
            shade = decayNoise(p * s * 9.0, seed + 9);
        }
        else if (DecayPreset == 2)
        {
            value = decayFbm(p * s * 0.9, seed) * 0.65 + decayNoise(p * s * 3.5, seed + 5) * 0.35;
            value += (decayRnd(decayHash(ivec3(floor(p * 16.0)), seed + 3)) - 0.5) * 0.14;
            time = (1.0 - value) * 0.6 + radial * 0.2 + y01 * 0.2;
            shade = decayNoise(p * s * 7.0, seed + 9);
        }
        else if (DecayPreset == 3)
        {
            value = decayFbm(vec3(p.x * s * 1.5, p.y * s * 0.32, p.z * s * 1.5), seed);
            time = (1.0 - value) * 0.65 + radial * 0.15 + (1.0 - y01) * 0.2;
            shade = decayNoise(vec3(p.x * s * 4.0, p.y * s * 1.2, p.z * s * 4.0), seed + 9);
        }
        else
        {
            value = decayFbm(p * s, seed);
            time = (1.0 - value) * 0.7 + radial * 0.3;
            shade = 0.5;
        }

        time = clamp(time, 0.0, 1.0);

        /* The reach passes every decay time at progress 1, so the whole volume is covered */
        float u = (progress * (1.0 + 2.2 * soft) - time) / soft;

        alpha = decaySmooth(u);

        if (alpha <= 0.002)
        {
            discard;
        }

        float body = decaySmoothstep(0.3, 2.2, u);

        if (DecayPreset == 4)
        {
            rgb = vec3(0.8 + body * 0.2);
        }
        else
        {
            rgb = min(vec3(2.0), mix(DecayEdge.rgb, DecayPrimary.rgb, body) * (0.8 + shade * 0.4));
        }
    }

    vec4 color = texture(Sampler0, texCoord0) * vec4(rgb, alpha * DecayPrimary.a);

    color *= vertexColor * ColorModulator * DecayTint;
    color *= lightMapColor;

    if (color.a < 0.004)
    {
        discard;
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
