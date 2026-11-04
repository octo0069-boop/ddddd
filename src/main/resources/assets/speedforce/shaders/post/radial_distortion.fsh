#version 150
in vec2 texCoord;
uniform sampler2D InSampler;
uniform float Strength;
uniform float Time;
out vec4 fragColor;
void main() {
    vec2 p = texCoord - 0.5;
    float radius = length(p);
    float ring = sin(radius * 65.0 - Time * 6.0);
    float distortion = ring * exp(-radius * 5.0) * Strength * 0.006;
    vec2 uv = texCoord + normalize(p + vec2(0.00001)) * distortion;
    fragColor = texture(InSampler, uv);
}
