#version 300 es
precision mediump float;
uniform sampler2D sTexture;//纹理内容数据

in vec2 vTextureCoord;
out vec4 fragColor;
void main()
{
   //给此片元颜色值
   fragColor =texture(sTexture, vTextureCoord);
}