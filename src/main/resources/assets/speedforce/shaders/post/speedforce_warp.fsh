#version 150
in vec2 texCoord;
uniform sampler2D InSampler;
uniform vec2 ScreenSize;
uniform float Speed;
uniform float Time;
out vec4 fragColor;
void main() {
    vec2 uv = texCoord;
    vec2 center = uv - vec2(0.5);
    float r = length(center);
    vec2 dir = normalize(center + vec2(0.0001));
    float strength = clamp(Speed, 0.0, 1.0) * smoothstep(0.12, 0.72, r);
    vec2 warped = uv + dir * strength * 0.012 * sin(Time * 2.0 + r * 18.0);
    vec2 blur = dir * strength * 0.006;
    vec4 c = texture(InSampler, warped) * 0.4;
    c += texture(InSampler, warped + blur) * 0.3;
    c += texture(InSampler, warped - blur) * 0.3;
    float vignette = 1.0 - smoothstep(0.35, 0.78, r) * strength * 0.42;
    fragColor = vec4(c.rgb * vignette, c.a);
}
