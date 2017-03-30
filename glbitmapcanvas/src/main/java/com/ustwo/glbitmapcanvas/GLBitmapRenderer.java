package com.ustwo.glbitmapcanvas;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import com.ustwo.glbitmapcanvas.programs.GLProgram;
import com.ustwo.glbitmapcanvas.programs.StandardGLProgram;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Manages and handles rendering of {@link GLBitmapObject}s.
 */
public class GLBitmapRenderer {
    private static final String TAG = GLBitmapRenderer.class.getSimpleName();

    /**
     * Holds buffer positions in application memory space. Don't use local/transient variable to store.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private FloatBuffer mBufferPositions;

    /**
     * Holds buffer indices in application memory space. Don't use local variables to store.
     */
    private ShortBuffer mBufferIndices;

    /**
     * Holds buffer UV coordinates in application memory space. Don't use local variables to store.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private FloatBuffer mBufferUVCoords;

    /**
     * System float size
     */
    public static final int FLOAT_SIZE_BYTES = 4;


    private int mLastTextureRef = 0;

    /**
     * System short int size
     */
    public static final int SHORT_SIZE_BYTES = 2;

    private final float[] mVPMatrix = new float[16];
    private final float[] mMVPMatrix = new float[16];
    private final long mGLThreadId;
    private final int[] mTextures = new int[1];

    private GLProgram mStandardProgram = new StandardGLProgram();

    private List<GLBitmapObject> mBitmapObjects = new ArrayList<>(3);

    private final List<Runnable> mEventQueue = new ArrayList<>();

    private static float[] mTextureUVCoords = new float[] {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
    };

    private static short[] mVertexIndices = new short[] {
            0, 1,
            2, 0,
            2, 3
    };

    private static float[] mVertexPositions = new float[] {
            0f, 1f, 0f,
            0f, 0f, 0f,
            1f, 0f, 0f,
            1f, 1f, 0f,
    };
    private int mSurfaceHeight;
    private int mSurfaceWidth;
    private boolean mIsFinishing = false;

    public long getGLThreadId() {
        return mGLThreadId;
    }

    /**
     * Determine if calling thread is the thread with which this object was created
     * @return True if calling thread is one with which this object was created, false otherwise
     */
    public boolean isThisGLThread() {
        return Thread.currentThread().getId() == mGLThreadId;
    }

    /**
     * Create renderer with given surface (usually window) size
     * The calling thread's GL context will be associated with this object, and cannot be changed.
     * @param width The surface width
     * @param height The surface height
     */
    public GLBitmapRenderer(int width, int height) {
        mGLThreadId = Thread.currentThread().getId();

        float[] viewMatrix = new float[16];
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Allocate buffers
        mBufferUVCoords = ByteBuffer.allocateDirect(mTextureUVCoords.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mBufferUVCoords.put(mTextureUVCoords);
        mBufferUVCoords.position(0);

        mBufferPositions = ByteBuffer.allocateDirect(mVertexPositions.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mBufferPositions.put(mVertexPositions);
        mBufferPositions.position(0);

        mBufferIndices = ByteBuffer.allocateDirect(mVertexIndices.length * SHORT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asShortBuffer();
        mBufferIndices.put(mVertexIndices);
        mBufferIndices.position(0);

        mStandardProgram.glCreateCompileLink();

        int handle = mStandardProgram.getHandle();
        GLES20.glUseProgram(handle);

        int positionLoc = GLES20.glGetAttribLocation(handle, "a_Position");
        GLES20.glEnableVertexAttribArray(positionLoc);
        GLES20.glVertexAttribPointer(positionLoc, 3, GLES20.GL_FLOAT, false, 0, mBufferPositions);

        int texCoordLoc = GLES20.glGetAttribLocation(handle, "a_TexCoord");
        GLES20.glEnableVertexAttribArray(texCoordLoc);
        GLES20.glVertexAttribPointer(texCoordLoc, 2, GLES20.GL_FLOAT, false, 0, mBufferUVCoords);

        int texSampleLoc = GLES20.glGetUniformLocation(handle, "s_Texture");
        GLES20.glUniform1i(texSampleLoc, 0);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        mSurfaceWidth = width;
        mSurfaceHeight = height;

        float[] projectionMatrix = new float[16];
        Matrix.orthoM(projectionMatrix, 0, 0, mSurfaceWidth, 0, mSurfaceHeight, -1, 1);
        Matrix.multiplyMM(mVPMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

        GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
    }

    /**
     * Queue work to be run on the GL thread at next frame.
     * @param r The {@link Runnable} containing the work
     */
    public void queue(Runnable r) {
        if (r == null) {
            throw new IllegalArgumentException("r must not be null");
        }

        Log.d(TAG, String.format("queue (%s)", Thread.currentThread().getName()));
        synchronized(mEventQueue) {
            mEventQueue.add(r);
        }
    }

    /**
     * Create a new {@link GLBitmapObject} of specified size.
     * Order will be automatically set to current count of {@link GLBitmapObject}s being managed
     * @param width The object's width
     * @param height The object's height
     * @return The created object
     */
    public GLBitmapObject createBitmapObject(int width, int height) {
        return createBitmapObject(width, height, null);
    }

    /**
     * Create a new {@link GLBitmapObject} of specified size, with specified order
     * @param width The object's width
     * @param height The object's height
     * @param order The order. Higher order means this object will be drawn after previous object.
     * @return The created object
     */
    public GLBitmapObject createBitmapObject(int width, int height, Integer order) {
        return createBitmapObject(width, height, order, true, true);
    }

    /**
     * Create a new {@link GLBitmapObject} of specified size, optionally allocating a {@link Bitmap}.
     * @param width The object's width
     * @param height The object's height
     * @param allocateBitmap Whether a {@link Bitmap} should be automatically allocated for this object.
     * @return The created object
     */
    public GLBitmapObject createBitmapObject(int width, int height, boolean allocateBitmap) {
        return createBitmapObject(width, height, null, allocateBitmap, true);
    }

    /**
     * Create a new {@link GLBitmapObject} of specified size, optionally allocating a {@link Bitmap}.
     * @param width The object's width
     * @param height The object's height
     * @param allocateBitmap Whether a {@link Bitmap} should be automatically allocated for this object.
     * @param renderWithGL Whether this object should be rendered with GL. If this is false, no GL
     *                     instructions will be made by this object.
     * @return The created object
     */
    public GLBitmapObject createBitmapObject(int width, int height, Integer order, boolean allocateBitmap, boolean renderWithGL) {
        if(renderWithGL) {
            checkGLThread();
        }

        if(order == null) {
            order = mBitmapObjects.size();
        }

        if(renderWithGL) {
            // Get reference to texture
            GLES20.glGenTextures(1, mTextures, 0);

            // Select & bind texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);

            Log.d(TAG, String.format("createBitmapObject: %d (%d x %d)", mTextures[0], width, height));

            // Change filters here if needed
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        }

        GLBitmapObject bitmapObject = new GLBitmapObject(mTextures[0], width, height, mSurfaceWidth, mSurfaceHeight, order, allocateBitmap);
        bitmapObject.setRenderWithGL(renderWithGL);

        mBitmapObjects.add(bitmapObject);
        invalidateSortOrder();
        return bitmapObject;
    }

    private void checkGLThread() {
        if(!isThisGLThread()) {
            throw new IllegalThreadStateException(String.format("Method must be called from GL thread (ID %d)", mGLThreadId));
        }
    }


    /**
     * Re-sorts the {@link GLBitmapObject}s. Can be called in case the order changes somehow?
     * Must be done on the thread that created this object to ensure the GL context is the same.
     */
    public void invalidateSortOrder() {
        checkGLThread();
        Collections.sort(mBitmapObjects, mBitmapObjectComparator);
    }

    private Comparator<GLBitmapObject> mBitmapObjectComparator = new GLBitmapObjectComparator();

    /**
     * Frees all resources, deletes all textures. Should be called when GL context is shutting down
     */
    public void onDestroy() {
        checkGLThread();
        Log.d(TAG, String.format("onDestroy (%s)", Thread.currentThread().getName()));
        for (GLBitmapObject bitmapObject : mBitmapObjects) {
            if(bitmapObject.isRenderWithGL()) {
                GLES20.glDeleteTextures(1, new int[]{bitmapObject.getTextureRef()}, 0);
            }
            bitmapObject.onDestroy();
        }
        mBitmapObjects.clear();
        mIsFinishing = true;
    }

    /**
     * The height of the surface this renderer was configured with
     * @return The height of the surface, in pixels
     */
    public int getSurfaceHeight() {
        return mSurfaceHeight;
    }

    /**
     * The width of the surface this renderer was configured with
     * @return The width of the surface, in pixels
     */
    public int getSurfaceWidth() {
        return mSurfaceWidth;
    }

    private GLBitmapObject getBitmapObject(int index) {
        if(index >= 0 && index < mBitmapObjects.size()) {
            return mBitmapObjects.get(index);
        }
        return null;
    }

    /**
     * Instruct GLES to immediately copy the pixels in the {@link Bitmap} owned by this object
     * to the associated GL texture.
     * Must be done on the thread that created this object to ensure the GL context is the same.
     * @param bitmapObject The object containing the pixels and the texture reference.
     */
    public void invalidateBitmapObject(GLBitmapObject bitmapObject) {
        invalidateBitmapObject(bitmapObject, bitmapObject.getBitmap());
    }

    /**
     * Instruct GLES to immediately copy the pixels in the specified {@link Bitmap}
     * to the associated GL texture.
     * Must be done on the thread that created this object to ensure the GL context is the same.
     * @param bitmapObject The object containing the pixels and the texture reference.
     * @param newBitmap The bitmap containing pixels to copy
     */
    public void invalidateBitmapObject(GLBitmapObject bitmapObject, Bitmap newBitmap) {
        checkGLThread();
        if (bitmapObject != null && bitmapObject.isRenderWithGL() && newBitmap != null) {

            if(bitmapObject.didPushTexture() &&
                    (newBitmap.getWidth() > bitmapObject.getBitmapWidth() || newBitmap.getHeight() > bitmapObject.getBitmapHeight())) {
                throw new IllegalArgumentException(String.format("The new bitmap must have the same or smaller dimensions, and same configuration as the original one used during creation. Old: %d x %d New: %d x %d", bitmapObject.getBitmapWidth(), bitmapObject.getBitmapHeight(), newBitmap.getWidth(), newBitmap.getHeight()));
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapObject.getTextureRef());
            if(!bitmapObject.didPushTexture()) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, newBitmap, 0);
                bitmapObject.onTexturePushed();
            }
            else {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, newBitmap);
            }
        }
        else {
            Log.w(TAG, "Trying to push null bitmap to vram");
        }
    }

    /**
     * Clean up the specified {@link GLBitmapObject}.
     * This does not need to be called if {@link #onDestroy()} is called, as it destroys all objects managed.
     * Must be done on the thread that created this object to ensure the GL context is the same.
     * @param bitmapObject The object to destroy.
     */
    public void destroyBitmapObject(GLBitmapObject bitmapObject) {
        checkGLThread();
        if (bitmapObject != null && mBitmapObjects.contains(bitmapObject)) {
            Log.d(TAG, String.format("destroyBitmapObject: %d (%s)", bitmapObject.getTextureRef(), Thread.currentThread().getName()));
            if(bitmapObject.isRenderWithGL()) {
                GLES20.glDeleteTextures(1, new int[]{bitmapObject.getTextureRef()}, 0);
            }
            bitmapObject.onDestroy();
            mBitmapObjects.remove(bitmapObject);
        }
    }

    private void executeQueuedEvents() {
        synchronized (mEventQueue) {
            if(mEventQueue.size() > 0) {
                for (Runnable event : mEventQueue) {
                    event.run();
                }
                mEventQueue.clear();
            }
        }
    }

    /**
     * Draws all managed {@link GLBitmapObject}s
     * This method uses the calling thread's GL context
     */
    public void draw() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        executeQueuedEvents();
        if(isFinishing()) {
            return;
        }

        if(mBitmapObjects.size() == 0) {
            // Nothing to draw
            return;
        }

        float[] color;
        GLProgram currentProgram = null;

        for(GLBitmapObject bitmapObject : mBitmapObjects) {
            if(!bitmapObject.isVisible()) {
                continue;
            }

            if(!bitmapObject.isRenderWithGL()) {
                continue;
            }

            GLProgram newProgram = (bitmapObject.getGLProgram() == null) ? mStandardProgram : bitmapObject.getGLProgram();

            if(currentProgram == null || currentProgram != newProgram) {
                GLES20.glUseProgram(newProgram.getHandle());
                currentProgram = newProgram;
            }

            Matrix.multiplyMM(mMVPMatrix, 0, mVPMatrix, 0, bitmapObject.getModelMatrix(), 0);

            int textureRef = bitmapObject.getTextureRef();

            if(textureRef != mLastTextureRef) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureRef);
            }

            color = bitmapObject.getColor();
            currentProgram.glOnDraw(mMVPMatrix, color);

            if(bitmapObject.isVisible()) {
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, mVertexIndices.length,
                        GLES20.GL_UNSIGNED_SHORT, mBufferIndices);
            }

            mLastTextureRef = textureRef;
        }
    }

    private boolean isFinishing() {
        return mIsFinishing;
    }

    private static class GLBitmapObjectComparator implements Comparator<GLBitmapObject> {
        @Override
        public int compare(GLBitmapObject lhs, GLBitmapObject rhs) {
            return lhs.getOrder() < rhs.getOrder() ? -1 : lhs.getOrder().equals(rhs.getOrder()) ? 0 : 1;
        }
    }
}