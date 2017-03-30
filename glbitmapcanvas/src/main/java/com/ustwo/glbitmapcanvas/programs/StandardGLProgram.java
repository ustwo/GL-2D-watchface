package com.ustwo.glbitmapcanvas.programs;

import android.opengl.GLES20;

public final class StandardGLProgram extends GLProgram {
    private static String VERTEX =
            "uniform mat4 u_MVPMatrix;" +
                    "attribute vec4 a_Position;" +
                    "attribute vec2 a_TexCoord;" +
                    "varying vec2 v_TexCoord;" +
                    "void main() {" +
                    "  gl_Position = u_MVPMatrix * a_Position;" +
                    "  v_TexCoord = a_TexCoord;" +
                    "}";
    private static String FRAGMENT =
            "precision mediump float;" +
                    "varying vec2 v_TexCoord;" +
                    "uniform sampler2D s_Texture;" +
                    "uniform vec4 u_Color;" +
                    "void main() {" +
                    "   gl_FragColor = texture2D( s_Texture, v_TexCoord ) * u_Color;" +
                    "}";

    private int mMatrixLoc = -1;
    private int mColorLoc = -1;

    public StandardGLProgram() {
        super(VERTEX, FRAGMENT);
    }

    public int getColorLoc() {
        return mColorLoc;
    }

    public int getMatrixLoc() {
        return mMatrixLoc;
    }

    @Override
    public void glOnDidLink() {
        mMatrixLoc = GLES20.glGetUniformLocation(getHandle(), "u_MVPMatrix");
        mColorLoc = GLES20.glGetUniformLocation(getHandle(), "u_Color");
    }

    @Override
    public void glOnDraw(float[] mvpMatrix, float[] color) {
        GLES20.glUniform4f(mColorLoc, color[0], color[1], color[2], color[3]);
        GLES20.glUniformMatrix4fv(mMatrixLoc, 1, false, mvpMatrix, 0);
    }
}
