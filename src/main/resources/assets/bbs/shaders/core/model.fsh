#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform int PassMode;

/* Dissolve: x = progress (0 is off), y = width of the burning edge, z = mode (0 linear, 1 noise,
 * 2 luma), w = noise frequency. The burn colour is rgb; the range is the form's lowest point and
 * its height, in the frame DissolveMat leads to. */
uniform vec4 DissolveParams;
uniform vec4 DissolveBurn;
uniform vec2 DissolveRange;
/* A form sliced in 3D keeps what lies on the positive side of this plane, in eye space. (0, 0, 0, 1)
 * keeps everything. */
uniform vec4 SlicePlane;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec4 normal;
in vec3 dissolvePos;
in vec3 slicePos;

out vec4 fragColor;

float hash31(vec3 p)
{
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);

    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 x)
{
    vec3 i = floor(x);
    vec3 f = fract(x);

    f = f * f * (3.0 - 2.0 * f);

    return mix(
        mix(mix(hash31(i), hash31(i + vec3(1.0, 0.0, 0.0)), f.x), mix(hash31(i + vec3(0.0, 1.0, 0.0)), hash31(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
        mix(mix(hash31(i + vec3(0.0, 0.0, 1.0)), hash31(i + vec3(1.0, 0.0, 1.0)), f.x), mix(hash31(i + vec3(0.0, 1.0, 1.0)), hash31(i + vec3(1.0, 1.0, 1.0)), f.x), f.y),
        f.z
    );
}

float fbm3(vec3 p)
{
    return vnoise(p) * 0.55 + vnoise(p * 2.1 + 17.0) * 0.3 + vnoise(p * 4.3 + 3.0) * 0.15;
}

void main()
{
    if (dot(vec4(slicePos, 1.0), SlicePlane) < 0.0)
    {
        discard;
    }

    vec4 color = texture(Sampler0, texCoord0);

    if (color.a < 0.1)
    {
        discard;
    }

    bool burning = false;

    if (DissolveParams.x > 0.0)
    {
        float value;

        if (DissolveParams.z > 1.5)
        {
            float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));

            /* The brightest burns first; noise keeps a flat colour from going all at once */
            value = (1.0 - luma) * 0.7 + fbm3(dissolvePos * DissolveParams.w) * 0.3;
        }
        else if (DissolveParams.z > 0.5)
        {
            value = (fbm3(dissolvePos * DissolveParams.w) - 0.2) / 0.6;
        }
        else
        {
            /* From the top down */
            value = 1.0 - (dissolvePos.y - DissolveRange.x) / max(DissolveRange.y, 0.0001);
        }

        value = clamp(value, 0.0, 0.999);

        float threshold = DissolveParams.x * (1.0 + DissolveParams.y);

        if (value < threshold - DissolveParams.y)
        {
            discard;
        }

        burning = value < threshold;
    }

    color *= vertexColor * ColorModulator;

    /* Two-pass translucency: pass 1 keeps only opaque texels (they write depth), pass 2 —
     * deferred, sorted far-to-near, no depth write — keeps only the semi-transparent ones.
     * The test uses final alpha, so form/bone color alpha counts too. PassMode 0 = single pass. */
    if (PassMode == 1 && color.a < 0.999)
    {
        discard;
    }

    if (PassMode == 2 && color.a >= 0.999)
    {
        discard;
    }

    if (burning)
    {
        /* The edge glows: its own colour, untouched by light and overlay */
        color.rgb = DissolveBurn.rgb;
    }
    else
    {
        color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
        color *= lightMapColor;
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
