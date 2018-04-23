#version 300 es
in vec4 vertexPosition;
in vec2 vertexTexCoord;
uniform mat4 projectionMatrix;

out vec2 texCoord;

void main(){
        gl_Position = projectionMatrix * vertexPosition;
        texCoord = vertexTexCoord;
}
