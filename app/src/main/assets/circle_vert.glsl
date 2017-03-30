uniform mat4 u_MVPMatrix;
attribute vec4 a_Position;
attribute vec2 a_TexCoord;
varying vec2 v_TexCoord;

void main() {
    gl_Position = u_MVPMatrix * a_Position;
    v_TexCoord = a_TexCoord;
}