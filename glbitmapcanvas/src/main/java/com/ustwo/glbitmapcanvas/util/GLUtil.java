package com.ustwo.glbitmapcanvas.util;

import android.opengl.GLES20;
import android.util.Log;

public class GLUtil {
    public static void checkGLError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e("glbitmapcanvas", op + ": glError " + error);
        }
    }
}
