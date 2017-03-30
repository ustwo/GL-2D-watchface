/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ustwo.gl2dwatchface;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;

import com.ustwo.glbitmapcanvas.GLBitmapObject;
import com.ustwo.glbitmapcanvas.GLBitmapRenderer;
import com.ustwo.glbitmapcanvas.GLWatchFace;
import com.ustwo.glbitmapcanvas.programs.GLProgram;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
public class ExampleFace extends GLWatchFace {
    private SimpleDateFormat mTimeFormat12 = new SimpleDateFormat("hh:mm", Locale.getDefault());
    private SimpleDateFormat mTimeFormat24 = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private Paint mTimePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    public GLEngine onCreateGLEngine() {
        return new ExampleGLEngine();
    }

    private class ExampleGLEngine extends GLWatchFace.GLEngine {
        GLBitmapObject mBackgroundObject = null;
        GLBitmapObject mTimeObject = null;
        @Override
        protected long getInteractiveModeUpdateRate() {
            return 0;
        }

        @Override
        public void onCreate() {
            super.onCreate();
            mTimePaint.setStrokeWidth(2.0f);
            mTimePaint.setColor(0xFFFFFFFF);
            mTimePaint.setTextAlign(Paint.Align.CENTER);
            mTimePaint.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }

        @Override
        public void onRendererReady(GLBitmapRenderer renderer) {
            // This object doesn't need a bitmap or texture, it's drawn entirely in the fragment shader
            mBackgroundObject = renderer.createBitmapObject(renderer.getSurfaceWidth(), renderer.getSurfaceHeight(), false);
            // Create a custom program for this object. This program object is responsible for passing
            // any parameters to the GLSL program.
            GLProgram backgroundProgram = new BackgroundProgram(ExampleFace.this, "circle_vert.glsl", "circle_frag.glsl");
            mBackgroundObject.attachGLProgram(backgroundProgram);

            mTimePaint.setTextSize(60f * renderer.getSurfaceWidth()/320f);
            // Allocate an object for us to draw time into (by default, a Bitmap and associated
            // Canvas are created). We'll draw to it when time changes.
            mTimeObject = renderer.createBitmapObject(renderer.getSurfaceWidth()/2, renderer.getSurfaceHeight()/2);
            // Position the object in the center of the screen (we use the object's center as the
            // anchor point, and position it at the surface center.
            mTimeObject.transformTo(0f, 1.0f, 1.0f, renderer.getSurfaceWidth()/2, renderer.getSurfaceHeight()/2, mTimeObject.getBitmapWidth()/2, mTimeObject.getBitmapHeight()/2);

            updateTime(getTimeFormat().format(getLatestTime().getTime()));
        }

        @Override
        protected void onTimeChanged(GregorianCalendar oldTime, GregorianCalendar newTime) {
            super.onTimeChanged(oldTime, newTime);

            // Draw into the time bitmap and then "invalidate" this object (causing its texture data
            // to be substituted with our new bitmap data, we should do this sparingly).

            DateFormat format = getTimeFormat();
            String newTimeString = format.format(newTime.getTime());
            String oldTimeString = format.format(oldTime.getTime());

            // Since we only draw hours and minutes, we are satisfied with only redrawing when either
            // of those changes (i.e. we don't care about changes to seconds, milliseconds, etc)
            if(!newTimeString.equals(oldTimeString)) {
                updateTime(newTimeString);
            }
        }

        private DateFormat getTimeFormat() {
            return is24HourFormat() ? mTimeFormat24 : mTimeFormat12;
        }

        private void updateTime(String timeString) {
            Canvas canvas = mTimeObject.getCanvas();
            // Clear canvas
            canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
            // Draw time to canvas

            // We want to center the text vertically. Because we position text at the baseline, we
            // must then figure out how much distance there is from text center to its baseline.
            float pixelsFromBaselineToCenterOfText = ((mTimePaint.descent() + mTimePaint.ascent()) / 2);
            canvas.drawText(timeString,  mTimeObject.getBitmapWidth()/2, mTimeObject.getBitmapHeight()/2 - pixelsFromBaselineToCenterOfText, mTimePaint);
            mGLBitmapRenderer.invalidateBitmapObject(mTimeObject);
        }
        @Override
        public void onRendererDestroyed(GLBitmapRenderer renderer) {

        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if(mBackgroundObject != null) {
                mBackgroundObject.setVisible(!inAmbientMode);
            }
        }
    }
}
