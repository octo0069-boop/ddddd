#version 150
in vec4 vertexColor;
uniform float GameTime;
uniform float Theme;
out vec4 fragColor;
void main() {
    float pulse = 0.82 + 0.18 * sin(GameTime * 32.0 + gl_FragCoord.x * 0.04);
    vec3 positive = vec3(1.0, 0.67, 0.12);
    vec3 negative = vec3(0.92, 0.04, 0.10);
    vec3 color = mix(positive, negative, clamp(Theme, 0.0, 1.0));
    float edge = smoothstep(0.0, 0.22, vertexColor.a);
    fragColor = vec4(color * vertexColor.rgb * pulse, edge * vertexColor.a);
}
