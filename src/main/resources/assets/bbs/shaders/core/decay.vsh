#version 150

#moj_import <light.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 NormalMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

/* The vertex to the decay box's own space, and to the space the pattern is read in (the world on a
 * wall, the box on an object) */
uniform mat4 DecayToLocal;
uniform mat4 DecayToPattern;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec2 texCoord0;
out vec3 localPos;
out vec3 patternPos;

float decay_fog_distance(mat4 modelViewMat, vec3 pos, int shape)
{
    if (shape == 0)
    {
        return length((modelViewMat * vec4(pos, 1.0)).xyz);
    }

    float distXZ = length((modelViewMat * vec4(pos.x, 0.0, pos.z, 1.0)).xyz);
    float distY = length((modelViewMat * vec4(0.0, pos.y, 0.0, 1.0)).xyz);

    return max(distXZ, distY);
}

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = decay_fog_distance(ModelViewMat, IViewRotMat * Position, FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(NormalMat * Normal), Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
    localPos = (DecayToLocal * vec4(Position, 1.0)).xyz;
    patternPos = (DecayToPattern * vec4(Position, 1.0)).xyz;
}
