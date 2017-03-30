precision mediump float;
const float tau = 6.2831853;
varying vec2 v_TexCoord;
uniform sampler2D s_Texture;
uniform vec4 u_Color;
uniform float u_Time;

void main()
{
    gl_FragColor = texture2D( s_Texture, v_TexCoord ) * u_Color;
}