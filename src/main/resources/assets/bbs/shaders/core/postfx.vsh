#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec2 texCoord0;

/* A full-screen pass: the quad is given in clip space, so no matrices are involved */
void main()
{
    gl_Position = vec4(Position.xy, 0.0, 1.0);

    texCoord0 = UV0;
}
