/**
 This work is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported
 license (CC BY-NC-SA 3.0). https://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_US

 This work is derived from shader named "Shiny Circle", created by "phil" on www.shadertoy.com licensed
 by default under CC BY-NC-SA 3.0. https://www.shadertoy.com/view/ltBXRc
**/
precision mediump float;
#define PI 3.141592654
varying vec2 v_TexCoord;
uniform sampler2D s_Texture;
uniform vec4 u_Color;
uniform float u_Time;
uniform float u_HourRads;
uniform float u_MinRads;

mat2 rotate2d(float angle){
    return mat2(cos(angle),-sin(angle),
                sin(angle),cos(angle));
}

float variation(vec2 v1, vec2 v2, float strength, float speed) {
	return sin(
        dot(v1, v2) * strength + u_Time * speed
    ) / 100.0;
}

vec3 paintCircle (vec2 uv, vec2 center, float rad, float width) {

    vec2 diff = center-uv;
    float len = length(diff);
    diff = normalize(diff);

    float t = sin(u_Time) * 0.5 + 0.5;
    len += variation(diff, vec2(0.0, 1.0), 3.0 + 2.0 * t, 1.0);
    len -= variation(diff, vec2(1.0, 0.0), 3.0 + 1.0 * t, 1.0);

    float circle = smoothstep(rad-width, rad, len) - smoothstep(rad, rad+width, len);
    return vec3(circle);
}

void main()
{
	vec2 uv = v_TexCoord;

    vec3 color;
    float radius = 0.34;
    vec2 center = vec2(0.5);

    //paint color circle
    color = paintCircle(uv, center, radius, 0.1);

    vec2 diff = center-uv;
    float len = length(diff);
    diff /= len;

    float theta = atan(diff.y, diff.x);
    float angleDelta = atan(sin(theta-u_HourRads), cos(theta-u_HourRads));
    float highlight = exp(-(angleDelta*angleDelta)/0.07)/(1./0.7) * smoothstep(0.1, 0.5, len);
    color += highlight;

    angleDelta = atan(sin(theta-u_MinRads), cos(theta-u_MinRads));
    highlight = exp(-(angleDelta*angleDelta)/0.03)/(1./0.7) * smoothstep(0.1, 0.5, len)*0.8;
    color += highlight;
    color += 0.35;

    //color with gradient
    vec2 v = rotate2d(u_Time) * uv;
    color *= vec3(v.x, v.y, 0.7-v.y*v.x);

    //paint white circle
    color += paintCircle(uv, center, radius, 0.01);
	gl_FragColor = vec4(color, 1.0);
}
