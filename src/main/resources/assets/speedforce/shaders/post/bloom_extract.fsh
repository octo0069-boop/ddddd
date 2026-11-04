#version 150
in vec2 texCoord;
uniform sampler2D InSampler;
uniform float Threshold;
out vec4 fragColor;
void main() {
    vec4 c = texture(InSampler, texCoord);
    float l = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
    float contribution = max(l - Threshold, 0.0) / max(l, 0.0001);
    fragColor = vec4(c.rgb * contribution, 1.0);
}
