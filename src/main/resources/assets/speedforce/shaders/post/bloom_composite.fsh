#version 150
in vec2 texCoord;
uniform sampler2D InSampler;
uniform sampler2D BloomSampler;
uniform float BloomStrength;
out vec4 fragColor;
void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec3 glow = texture(BloomSampler, texCoord).rgb;
    fragColor = vec4(scene.rgb + glow * clamp(BloomStrength, 0.0, 2.0), scene.a);
}
