#version 300 es
in vec4 vertexPosition;
in vec2 vertexTexCoord;

out vec2 texCoord;
uniform mat4 modelViewProjectionMatrix;

void main(){
    gl_Position = modelViewProjectionMatrix * vertexPosition;
    texCoord = vertexTexCoord;
}
