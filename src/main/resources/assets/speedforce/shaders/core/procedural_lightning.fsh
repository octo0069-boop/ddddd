#version 150
in vec2 uv;
in float arcNoise;
uniform float Theme;
uniform float Time;
out vec4 fragColor;
void main() {
    float filament = smoothstep(0.42, 0.50, 1.0 - abs(uv.x - 0.5 + arcNoise * 0.035));
    float branch = smoothstep(0.46, 0.49, 1.0 - abs(fract(uv.y * 5.0 + arcNoise * 0.1) - 0.5));
    vec3 gold = vec3(1.0, 0.72, 0.16);
    vec3 red = vec3(0.95, 0.045, 0.12);
    vec3 c = mix(gold, red, clamp(Theme, 0.0, 1.0));
    float flicker = 0.82 + 0.18 * sin(Time * 44.0 + uv.y * 36.0);
    float alpha = max(filament, branch * 0.65) * flicker;
    fragColor = vec4(c * (1.0 + filament * 1.8), alpha);
}
