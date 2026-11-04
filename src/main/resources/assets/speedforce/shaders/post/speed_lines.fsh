#version 150
in vec2 texCoord;
uniform sampler2D InSampler;
uniform float Time;
uniform float Intensity;
out vec4 fragColor;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
void main() {
    vec2 p = texCoord - 0.5;
    float a = atan(p.y, p.x);
    float lines = pow(max(0.0, sin(a * 31.0 + Time * 1.5)), 22.0);
    float radial = smoothstep(0.18, 0.72, length(p));
    float noise = hash(floor(texCoord * 180.0 + Time * 3.0));
    vec3 streak = vec3(0.95, 0.66, 0.18) * lines * radial * noise * clamp(Intensity, 0.0, 1.0);
    vec4 base = texture(InSampler, texCoord);
    fragColor = vec4(base.rgb + streak, base.a);
}
