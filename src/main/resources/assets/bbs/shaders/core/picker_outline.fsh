#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform int Target;
uniform vec4 HighlightColor;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const int R = 3;

/* Highest gizmo handle id (Gizmo.STENCIL_MAX): form / actor ids begin right after it */
const int GIZMO_MAX = 19;

int idAt(ivec2 p, ivec2 size)
{
    if (p.x < 0 || p.y < 0 || p.x >= size.x || p.y >= size.y)
    {
        return -1;
    }

    vec4 c = texelFetch(Sampler0, p, 0);

    if (c.a < 1.0)
    {
        return 0;
    }

    return int(round(c.r * 255.0)) | (int(round(c.g * 255.0)) << 8) | (int(round(c.b * 255.0)) << 16);
}

void main()
{
    ivec2 size = textureSize(Sampler0, 0);
    ivec2 p = ivec2(texCoord0 * vec2(size));

    int ownId = idAt(p, size);

    /* Only the rim around the target: its own pixels stay untouched */
    if (ownId == Target)
    {
        discard;
    }

    /* Gizmo handles are drawn over the actor in the id buffer, so they punch holes in its
     * mask; the rim must not follow those holes, or the gizmo gets painted in the actor colour */
    if (Target > GIZMO_MAX && ownId >= 1 && ownId <= GIZMO_MAX)
    {
        discard;
    }

    bool near = false;

    for (int dx = -R; dx <= R; dx++)
    {
        for (int dy = -R; dy <= R; dy++)
        {
            if (dx * dx + dy * dy <= R * R && idAt(p + ivec2(dx, dy), size) == Target)
            {
                near = true;
            }
        }
    }

    if (!near)
    {
        discard;
    }

    fragColor = HighlightColor * ColorModulator;
}