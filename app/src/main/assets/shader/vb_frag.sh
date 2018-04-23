#version 300 es
precision mediump float;
in vec2 texCoord;
uniform sampler2D texSampler2D;
out vec4 fragColor;

void main (){
    fragColor = texture(texSampler2D, texCoord);
}