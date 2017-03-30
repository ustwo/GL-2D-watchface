package com.ustwo.glbitmapcanvas;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.Matrix;
import android.support.annotation.CallSuper;

import com.ustwo.glbitmapcanvas.programs.GLProgram;

public class GLBitmapObject {
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private boolean mIsVisible = true;
    private Integer mOrder;
    private final int mTextureRef;
    private final float[] mModelMatrix = new float[16];
    private GLProgram mGLProgram = null;
    private TransformState mTransformState = null;
    private TransformState mPostTransformState = null;
    private boolean mRenderWithGL = true;
    private boolean mDidPushTexture = false;
    private float[] mColor = new float[]{1f, 1f, 1f, 1f};

    public Integer getOrder() {
        return mOrder;
    }

    public float[] getColor() {
        return mColor;
    }

    public boolean didPushTexture() {
        return mDidPushTexture;
    }

    public void onTexturePushed() {
        mDidPushTexture = true;
    }

    public GLProgram getGLProgram() {
        return mGLProgram;
    }

    public void attachGLProgram(GLProgram program) {
        mGLProgram = program;
        if(program != null) {
            program.glCreateCompileLink();
        }
    }

    /**
     * Whether this bitmap object should have a corresponding OpenGL texture. If false, this object
     * will only hold a Bitmap, and will not carry out any OpenGL operations. Default is true.
     *
     * @return True if OpenGL should be used to render this object, false otherwise
     */
    public boolean isRenderWithGL() {
        return mRenderWithGL;
    }

    /**
     * Set whether this bitmap object should have a corresponding OpenGL texture. If false, this object
     * will only hold a Bitmap, and will not carry out any OpenGL operations
     *
     * @param renderWithGL True if OpenGL should be used to render this object (this is default), false otherwise
     */
    public void setRenderWithGL(boolean renderWithGL) {
        mRenderWithGL = renderWithGL;
    }

    /**
     * Set the alpha part of the color by which all fragment colors are multiplied
     *
     * @param alpha The alpha, in range 0.0 - 1.0
     */
    public void setAlpha(float alpha) {
        mColor[3] = alpha;
    }

    /**
     * Set the color by which all fragment colors are multiplied
     *
     * @param r Red component
     * @param g Green component
     * @param b Blue component
     * @param a Alpha component
     */
    public void setColor(float r, float g, float b, float a) {
        mColor[0] = r;
        mColor[1] = g;
        mColor[2] = b;
        mColor[3] = a;
    }

    public TransformState getTransformState() {
        return mTransformState;
    }

    public TransformState getPostTransformState() {
        return mPostTransformState;
    }

    public float[] getModelMatrix() {
        return mModelMatrix;
    }

    public int getTextureRef() {
        return mTextureRef;
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public void setVisible(boolean visible) {
        mIsVisible = visible;
    }

    public void transformTo(float angleDegrees, float scaleX, float scaleY, float centerPositionX, float centerPositionY) {
        float newWidth = mBitmapWidth * scaleX;
        float newHeight = mBitmapHeight * scaleY;
        transformTo(angleDegrees, scaleX, scaleY, centerPositionX, centerPositionY, newWidth * 0.5f, newHeight * 0.5f);
    }

    public void transformTo(float angleDegrees, float scaleX, float scaleY, float anchorPositionX, float anchorPositionY, float anchorX, float anchorY) {
        if (mTransformState == null) {
            mTransformState = new TransformState();
        }
        mTransformState.set(angleDegrees, anchorPositionX, anchorPositionY, scaleX, scaleY, anchorX, anchorY);
        Matrix.setIdentityM(mModelMatrix, 0);
        float newWidth = mBitmapWidth * scaleX;
        float newHeight = mBitmapHeight * scaleY;
        float canvasPositionY = (mSurfaceHeight - anchorPositionY);
        Matrix.translateM(mModelMatrix, 0, anchorPositionX, canvasPositionY, 0f);
        Matrix.rotateM(mModelMatrix, 0, -angleDegrees, 0f, 0f, 1f);
        Matrix.translateM(mModelMatrix, 0, -anchorX, -anchorY, 0f);
        Matrix.scaleM(mModelMatrix, 0, newWidth, newHeight, 0f);
    }

    public GLBitmapObject(int textureRef, int bitmapWidth, int bitmapHeight, int surfaceWidth, int surfaceHeight, Integer order, boolean allocateBitmap) {

        mBitmapWidth = bitmapWidth;
        mBitmapHeight = bitmapHeight;

        if (allocateBitmap) {
            mBitmap = Bitmap.createBitmap(mBitmapWidth, mBitmapHeight, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
            mBitmap.eraseColor(0x00000000);
        }

        mTextureRef = textureRef;
        mSurfaceWidth = surfaceWidth;
        mSurfaceHeight = surfaceHeight;
        mOrder = order;

        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.scaleM(mModelMatrix, 0, mBitmapWidth, mBitmapHeight, 0f);
    }


    protected Bitmap mBitmap;
    protected Canvas mCanvas = null;

    protected final int mBitmapHeight;
    protected final int mBitmapWidth;

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public Canvas getCanvas() {
        return mCanvas;
    }

    public int getBitmapHeight() {
        return mBitmapHeight;
    }

    public int getBitmapWidth() {
        return mBitmapWidth;
    }


    @CallSuper
    public void releaseBitmap() {
        if (mBitmap != null) {
            mBitmap.recycle();
        }
        mBitmap = null;
        mCanvas = null;
    }

    @CallSuper
    public void onDestroy() {
        releaseBitmap();
    }

    @CallSuper
    public void clear() {
        if (mBitmap != null) {
            mBitmap.eraseColor(0x00000000);
        }
    }

    public void allocateBitmap() {
        if (mBitmap == null || mBitmap.isRecycled() || !mBitmap.isMutable()) {
            mBitmap = Bitmap.createBitmap(mBitmapWidth, mBitmapHeight, Bitmap.Config.ARGB_8888);
        }

        if (mCanvas == null) {
            mCanvas = new Canvas(mBitmap);
        } else {
            mCanvas.setBitmap(mBitmap);
        }
    }

    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;

        if (bitmap != null) {
            if (mCanvas == null) {
                mCanvas = new Canvas(mBitmap);
            } else {
                mCanvas.setBitmap(mBitmap);
            }
        }
    }

    public static class TransformState {
        private float mAngleDegrees = 0f;
        private float mTranslationX = 0f;
        private float mTranslationY = 0f;
        private float mScaleX = 1f;
        private float mScaleY = 1f;
        private float mAnchorX = 0f;
        private float mAnchorY = 0f;

        public TransformState() {
        }

        public TransformState(float angleDegrees, float translationX, float translationY, float scaleX, float scaleY) {
            mAngleDegrees = angleDegrees;
            mTranslationX = translationX;
            mTranslationY = translationY;
            mScaleX = scaleX;
            mScaleY = scaleY;
        }

        public void set(float angleDegrees, float centerPositionX, float centerPositionY, float scaleX, float scaleY) {
            set(angleDegrees, centerPositionX, centerPositionY, scaleX, scaleY, 0f, 0f);
        }

        public void set(float angleDegrees, float centerPositionX, float centerPositionY, float scaleX, float scaleY, float anchorX, float anchorY) {
            mAngleDegrees = angleDegrees;
            mTranslationX = centerPositionX;
            mTranslationY = centerPositionY;
            mScaleX = scaleX;
            mScaleY = scaleY;
            mAnchorX = anchorX;
            mAnchorY = anchorY;
        }

        public float getAngleDegrees() {
            return mAngleDegrees;
        }

        public void setAngleDegrees(float angleDegrees) {
            mAngleDegrees = angleDegrees;
        }

        public float getTranslationX() {
            return mTranslationX;
        }

        public void setTranslationX(float translationX) {
            mTranslationX = translationX;
        }

        public float getTranslationY() {
            return mTranslationY;
        }

        public void setTranslationY(float translationY) {
            mTranslationY = translationY;
        }

        public float getScaleX() {
            return mScaleX;
        }

        public void setScaleX(float scaleX) {
            mScaleX = scaleX;
        }

        public float getScaleY() {
            return mScaleY;
        }

        public void setScaleY(float scaleY) {
            mScaleY = scaleY;
        }

        public float getAnchorX() {
            return mAnchorX;
        }

        public float getAnchorY() {
            return mAnchorY;
        }
    }
}