#version 150
in vec2 texCoord;
uniform sampler2D InSampler;
uniform vec2 Direction;
uniform float Radius;
out vec4 fragColor;
void main() {
    vec4 sum = vec4(0.0);
    float total = 0.0;
    for (int i = -4; i <= 4; ++i) {
        float x = float(i) / 4.0;
        float w = exp(-x * x * 2.0);
        sum += texture(InSampler, texCoord + Direction * Radius * x) * w;
        total += w;
    }
    fragColor = sum / total;
}
