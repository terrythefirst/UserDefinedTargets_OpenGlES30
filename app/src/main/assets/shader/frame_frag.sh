#version 300 es
precision mediump float;
in vec2 texCoord;
uniform sampler2D texSampler2D;
uniform vec4 keyColor;

out vec4 fragColor;

void main(){
    vec4 texColor = texture(texSampler2D, texCoord);
    fragColor = keyColor * texColor;
}
