package com.ustwo.glbitmapcanvas.programs;

import android.content.Context;
import android.opengl.GLES20;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Container for set of GLSL vertex and fragment shaders.
 *
 * Subclasses can handle
 */
public abstract class GLProgram {
    private String mVertex = null;
    private String mFragment = null;
    private int mHandle = 0;
    private boolean mIsLinked = false;

    public int getHandle() {
        return mHandle;
    }

    public boolean isLoaded() {
        return mHandle != 0;
    }

    @NonNull
    public String getFragment() {
        return mFragment;
    }

    @NonNull
    public String getVertex() {
        return mVertex;
    }

    /**
     * Construct program object with shader program defined as strings
     * @param vertex The vertex program
     * @param fragment The fragment program
     */
    public GLProgram(@NonNull String vertex, @NonNull String fragment) {
        mVertex = vertex;
        mFragment = fragment;
    }

    /**
     * Construct program object with shader program defined as asset filenames. The vertex and fragment
     * shaders should be placed into the /assets folder.
     * @param context The context, used to read assets
     * @param vertexAssetFilename The filename of the vertex shader, relative to the /assets/ folder (e.g. "myvert.glsl" for /assets/myvert.glsl)
     * @param fragmentAssetFilename The filename of the fragment shader, relative to the /assets/ folder (e.g. "myfrag.glsl" for /assets/myfrag.glsl)
     */
    public GLProgram(@NonNull Context context, @NonNull String vertexAssetFilename, @NonNull String fragmentAssetFilename) {
        try {
            mVertex = readStringAsset(context, vertexAssetFilename);
            mFragment = readStringAsset(context, fragmentAssetFilename);
        } catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Create, compile, and link this program, on the calling thread's GL context.
     */
    public final void glCreateCompileLink() {
        if(mIsLinked) {
            return;
        }
        mHandle = GLES20.glCreateProgram();
        GLES20.glAttachShader(mHandle, glCompile(GLES20.GL_VERTEX_SHADER, mVertex));
        GLES20.glAttachShader(mHandle, glCompile(GLES20.GL_FRAGMENT_SHADER, mFragment));
        GLES20.glLinkProgram(mHandle);

        mIsLinked = true;
        glOnDidLink();
    }

    /**
     * Called once after this program has been linked.
     */
    public abstract void glOnDidLink();

    /**
     * Called for each frame of the draw loop.
     * @param mvpMatrix The model-view-projection matrix of the {@link com.ustwo.glbitmapcanvas.GLBitmapObject}
     *                  owning this program.
     * @param color The color of the {@link com.ustwo.glbitmapcanvas.GLBitmapObject} owning this program.
     */
    public abstract void glOnDraw(float[] mvpMatrix, float[] color);

    /**
     * Delete the current program, using calling thread's GL context.
     */
    public void glDelete() {
        if(mHandle == 0) {
            throw new IllegalStateException("Attempting to delete program that hasn't been created");
        }
        GLES20.glDeleteProgram(mHandle);
        mIsLinked = false;
    }

    private static int glCompile(int type, @NonNull String shaderCode){
        int shader = GLES20.glCreateShader(type);

        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("GLProgram", "Compilation " + GLES20.glGetShaderInfoLog(shader));
            return 0;
        }
        return shader;
    }

    /**
     * Reads an asset file into a string, returning the resulting string
     * @param context The asset's context
     * @param asset The name of the asset
     * @return The string if successful
     */
    public static String readStringAsset(Context context, String asset) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        InputStream inputStream = context.getAssets().open(asset);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        String line = bufferedReader.readLine();
        while (line != null) {
            stringBuilder.append(line);
            stringBuilder.append('\n');
            line = bufferedReader.readLine();
        }
        return stringBuilder.toString();
    }
}
