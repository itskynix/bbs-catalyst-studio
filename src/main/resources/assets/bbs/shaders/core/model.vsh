#version 150

#moj_import <light.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat3 NormalMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

/* Takes a vertex position to the form's own frame, where the dissolve is measured. */
uniform mat4 DissolveMat;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec4 normal;
out vec3 dissolvePos;
/* The vertex in eye space, where the slice plane is given */
out vec3 slicePos;

/* Same distance the vanilla include used to give up to 1.20.4; 1.21 dropped the model-view form. */
float bbs_fog_distance(mat4 modelViewMat, vec3 pos, int shape)
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

    vertexDistance = bbs_fog_distance(ModelViewMat, IViewRotMat * Position, FogShape);
    vec3 fixNormal = normalize(NormalMat * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, fixNormal, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
    dissolvePos = (DissolveMat * vec4(Position, 1.0)).xyz;
    slicePos = (ModelViewMat * vec4(Position, 1.0)).xyz;
}
