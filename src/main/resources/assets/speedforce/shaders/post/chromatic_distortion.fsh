#version 150
in vec2 texCoord;
uniform sampler2D InSampler;
uniform float Amount;
out vec4 fragColor;
void main() {
    vec2 p = texCoord - 0.5;
    float r = length(p);
    vec2 offset = normalize(p + vec2(0.00001)) * Amount * r * r;
    vec4 c;
    c.r = texture(InSampler, texCoord + offset).r;
    c.g = texture(InSampler, texCoord).g;
    c.b = texture(InSampler, texCoord - offset).b;
    c.a = texture(InSampler, texCoord).a;
    fragColor = c;
}
