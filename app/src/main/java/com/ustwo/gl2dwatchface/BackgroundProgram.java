package com.ustwo.gl2dwatchface;

import android.content.Context;
import android.opengl.GLES20;
import android.support.annotation.NonNull;

import com.ustwo.glbitmapcanvas.programs.GLProgram;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class BackgroundProgram extends GLProgram {
    private int mMatrixLoc = -1;
    private int mColorLoc = -1;;
    private int mTimeLoc = -1;
    private int mHourAngleLoc = -1;
    private int mMinAngleLoc = -1;
    private final GregorianCalendar mCalendar = new GregorianCalendar();
    private static final float PI = (float)Math.PI;

    private static final double TWO_PI = Math.PI*2.;

    private float mTime = 0.0f;

    BackgroundProgram(@NonNull Context context, @NonNull String vertexAssetFilename, @NonNull String fragmentAssetFilename) {
        super(context, vertexAssetFilename, fragmentAssetFilename);
    }

    @Override
    public void glOnDidLink() {
        mMatrixLoc = GLES20.glGetUniformLocation(getHandle(), "u_MVPMatrix");
        mColorLoc = GLES20.glGetUniformLocation(getHandle(), "u_Color");
        mTimeLoc = GLES20.glGetUniformLocation(getHandle(), "u_Time");
        mHourAngleLoc = GLES20.glGetUniformLocation(getHandle(), "u_HourRads");
        mMinAngleLoc = GLES20.glGetUniformLocation(getHandle(), "u_MinRads");
    }

    @Override
    public void glOnDraw(float[] mvpMatrix, float[] color) {
        GLES20.glUniform4f(mColorLoc, color[0], color[1], color[2], color[3]);
        GLES20.glUniformMatrix4fv(mMatrixLoc, 1, false, mvpMatrix, 0);

        mCalendar.setTimeInMillis(System.currentTimeMillis());
        mCalendar.setTimeZone(TimeZone.getDefault());

        // Minutes represented on scale between last hour (0) and next hour (1)
        float minutesNormalized = mCalendar.get(Calendar.MINUTE) / 60f;

        float hourDeg = glAngleFromNormalizedTime((mCalendar.get(Calendar.HOUR) + minutesNormalized) / 12f);
        float minDeg = glAngleFromNormalizedTime(minutesNormalized);

        GLES20.glUniform1f(mHourAngleLoc, hourDeg);
        GLES20.glUniform1f(mMinAngleLoc, minDeg);

        // Wrap time every 2*PI
        mTime = (float)((mTime + 0.02) % TWO_PI);
        GLES20.glUniform1f(mTimeLoc, mTime);
    }

    private float glAngleFromNormalizedTime(float normalizedTime) {
        return (normalizedTime*2f*PI + PI*0.5f);
    }
}