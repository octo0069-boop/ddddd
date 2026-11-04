#version 150
in vec3 Position;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Time;
out vec2 uv;
out float arcNoise;
void main() {
    uv = UV0;
    arcNoise = sin(Position.x * 17.0 + Position.y * 11.0 + Time * 9.0);
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
