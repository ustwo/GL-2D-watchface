package com.ustwo.glbitmapcanvas;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLDisplay;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.Gles2WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class GLWatchFace extends Gles2WatchFaceService {
    private static final String TAG = GLWatchFace.class.getSimpleName();

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public final Engine onCreateEngine() {
        return onCreateGLEngine();
    }

    public abstract GLWatchFace.GLEngine onCreateGLEngine();

    protected abstract class GLEngine extends Gles2WatchFaceService.Engine {
        private ScheduledExecutorService mScheduledTimeUpdaterPool = Executors.newSingleThreadScheduledExecutor();
        private ScheduledFuture<?> mScheduledTimeUpdater;
        private boolean mShouldDrawContinuously = false;
        private final GregorianCalendar mPreviousTime = new GregorianCalendar();
        private final GregorianCalendar mLatestTime = new GregorianCalendar();
        private boolean mIs24HourFormat = false;
        private boolean mIsAmbient = false;
        private boolean mLowBitAmbient = false;
        private boolean mBurnInProtection = false;
        private Boolean mIsRound = null;
        protected Rect mFaceRect = new Rect();
        protected Rect mFaceInsets = new Rect();

        private ContentObserver mFormatChangeObserver;
        protected GLBitmapRenderer mGLBitmapRenderer;
        protected Handler mGLThreadHandler = new Handler();

        private BroadcastReceiver mDateTimeChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateTimeAndInvalidate();
            }
        };

        private BroadcastReceiver mLocaleReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onLocaleChanged();
            }
        };

        /**
         * Returns the width of the watch face.
         *
         * @return value of the watch face {@link android.graphics.Rect}s width
         */
        public final int getWidth() {
            return mFaceRect.width();
        }

        /**
         * Returns the height of the watch face.
         *
         * @return value of the watch face {@link android.graphics.Rect}s height
         */
        public final int getHeight() {
            return mFaceRect.height();
        }

        /**
         * Returns watch face shape if known
         *
         * @return true if the watch is round, false if not round, and null if unknown
         */
        @Nullable
        public final Boolean isRound() {
            return mIsRound;
        }

        /**
         * Returns true if user preference is set to 24-hour format.
         *
         * @return true if 24 hour time format is selected, false otherwise.
         */
        public final boolean is24HourFormat() {
            return DateFormat.is24HourFormat(GLWatchFace.this);
        }

        /**
         * Gets the latest {@link GregorianCalendar} that was updated the last time
         * onTimeChanged was called.
         *
         * @return latest {@link GregorianCalendar}
         */
        public final GregorianCalendar getLatestTime() {
            return mLatestTime;
        }


        /**
         * Override to provide a custom {@link android.support.wearable.watchface.WatchFaceStyle} for the
         * watch face.
         *
         * @return {@link android.support.wearable.watchface.WatchFaceStyle} for watch face.
         */
        protected WatchFaceStyle getWatchFaceStyle() {
            return null;
        }

        /**
         * Returns the interactive-mode update rate in millis.
         * This will tell the {@link GLWatchFace} base class the period to call
         * {@link #onTimeChanged(GregorianCalendar, GregorianCalendar)}.
         * <br><br>DEFAULT={@link android.text.format.DateUtils#MINUTE_IN_MILLIS}
         *
         * @return number of millis to wait before calling onTimeChanged and glOnDraw.
         */
        protected abstract long getInteractiveModeUpdateRate();

        //================================================================================
        //    SERVICE/SYSTEM LIFECYCLE
        //================================================================================

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(GLWatchFace.this)
                    .setStatusBarGravity(Gravity.RIGHT | Gravity.TOP)
                    .build());

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_DATE_CHANGED);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            registerReceiver(mDateTimeChangedReceiver, filter);

            IntentFilter localeFilter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
            registerReceiver(mLocaleReceiver, localeFilter);

            if (mFormatChangeObserver == null) {
                mFormatChangeObserver = new TimeFormatObserver(new Handler());
                getContentResolver().registerContentObserver(
                        Settings.System.getUriFor(Settings.System.TIME_12_24), true, mFormatChangeObserver);
            }

            onCreate();
        }

        @Override
        public void onDestroy() {
            cancelTimeUpdater();
            mScheduledTimeUpdater = null;
            mScheduledTimeUpdaterPool.shutdown();
            mScheduledTimeUpdaterPool = null;

            unregisterReceiver(mDateTimeChangedReceiver);
            mDateTimeChangedReceiver = null;

            unregisterReceiver(mLocaleReceiver);
            mLocaleReceiver = null;

            if (mFormatChangeObserver != null) {
                getContentResolver().unregisterContentObserver(
                        mFormatChangeObserver);
                mFormatChangeObserver = null;
            }

            if(mGLBitmapRenderer != null) {
                Log.d(TAG, "onRendererDestroyed");
                onRendererDestroyed(mGLBitmapRenderer);
                mGLBitmapRenderer.onDestroy();
                mGLBitmapRenderer = null;
            }

            mInvalidateRunnable = null;
            mTimeUpdater = null;

            super.onDestroy();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            Log.d(TAG, "WatchFace.onApplyWindowInsets: " + "isRound=" + Boolean.toString(insets.isRound()));

            WatchFaceStyle watchFaceStyle = getWatchFaceStyle();
            if(watchFaceStyle != null) {
                setWatchFaceStyle(watchFaceStyle);
            }

            mFaceInsets = new Rect();
            mFaceInsets.set(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            mIsRound = insets.isRound();

            // fire onLayout if onApplyWindowInsets is occurring after GLBitmapRenderer was initialized
            if(mGLBitmapRenderer != null) {
                // Queue to guarantee this occurs after the renderer surface is ready (on draw)
                mGLBitmapRenderer.queue(new Runnable() {
                    @Override
                    public void run() {
                        onLayout(mIsRound, mFaceRect, mFaceInsets);
                        checkTimeUpdater();
                    }
                });
            }
        }

        //================================================================================
        //    GL/RENDERING LIFECYCLE
        //================================================================================

        @Override
        public final void onGlContextCreated() {
            Log.d(TAG, "onGlContextCreated");
            super.onGlContextCreated();
        }

        @Override
        @CallSuper
        public final void onGlSurfaceCreated(int width, int height) {
            Log.d(TAG, "onGlSurfaceCreated: " + width + " x " + height);
            super.onGlSurfaceCreated(width, height);
            mFaceRect.set(0, 0, width, height);

            // only create once for now until we handle destruction properly
            if(mGLBitmapRenderer == null && width > 0 && height > 0) {
                mGLBitmapRenderer = new GLBitmapRenderer(width, height);
                Log.d(TAG, "onRendererReady");
                onRendererReady(mGLBitmapRenderer);

                // fire onLayout if onApplyWindowInsets already occurred
                if(mIsRound != null) {
                    onLayout(mIsRound, mFaceRect, mFaceInsets);
                }
                updateTimeAndInvalidate();
            }
        }

        private void updateTimeAndInvalidate() {
            mPreviousTime.setTimeInMillis(mLatestTime.getTimeInMillis());
            long now = System.currentTimeMillis();
            mLatestTime.setTimeInMillis(now);
            mLatestTime.setTimeZone(TimeZone.getDefault());

            onTimeChanged(mPreviousTime, mLatestTime);

            boolean is24Hour = DateFormat.is24HourFormat(GLWatchFace.this);
            if (is24Hour != mIs24HourFormat) {
                mIs24HourFormat = is24Hour;
                on24HourFormatChanged(mIs24HourFormat);
            }

            postInvalidate();
        }

        @Override
        @CallSuper
        public void onDraw() {
            if(mGLBitmapRenderer != null) {
                mGLBitmapRenderer.draw();
            }
            if (!mIsAmbient && isVisible() && mShouldDrawContinuously) {
                updateTimeAndInvalidate();
            }
        }

        //================================================================================
        //    SYSTEM EVENT HANDLERS
        //================================================================================

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            Log.v(TAG, "WatchFace.onTimeTick");

            if(mGLBitmapRenderer != null && !isTimeUpdaterRunning()) {
                updateTimeAndInvalidate();
            }
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            Log.d(TAG, "WatchFace.onPropertiesChanged: " + "LowBit=" + Boolean.toString(mLowBitAmbient) +
                    ", BurnIn=" + Boolean.toString(mBurnInProtection));
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.v(TAG, "WatchFace.onAmbientModeChanged: " + Boolean.toString(inAmbientMode) + " (" + Thread.currentThread().getName() + ")");

            if (mIsAmbient != inAmbientMode) {
                mIsAmbient = inAmbientMode;

                if(mGLBitmapRenderer != null) {
                    updateTimeAndInvalidate();
                    checkTimeUpdater();
                }
            }
        }


        @Override
        public void onVisibilityChanged(boolean visible) {
            // need to call super, otherwise onPropertiesChanged will not be called
            super.onVisibilityChanged(visible);
            Log.v(TAG, "WatchFace.onVisibilityChanged: " + visible);

            if (visible) {
                updateTimeAndInvalidate();
            }
            checkTimeUpdater();
        }

        @Override
        public EGLConfig chooseEglConfig(EGLDisplay display) {
            int[] eglAttribList = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
            };

            EGLConfig[] configs = new EGLConfig[1];

            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(display, eglAttribList, 0, configs, 0, configs.length,
                    numConfigs, 0)) {
                Log.w(TAG, "unable to find EGLConfig");
                // TODO: Improve initialization, maybe add fallback EGL configs
                throw new RuntimeException("Unable to find desired ES2 EGL config");
            }

            return configs[0];
        }

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            onCardPeek(rect);

            updateTimeAndInvalidate();
        }

        //================================================================================
        //    ABSTRACTED EVENTS
        //================================================================================

        /**
         * Called when the "Use 24-hour format" user setting is modified.
         *
         * @param is24HourFormat
         */
        protected void on24HourFormatChanged(boolean is24HourFormat) { }

        /**
         * Called when the device locale is changed.
         */
        protected void onLocaleChanged() { }

        /**
         * Override to perform view and logic updates. This will be called once per minute
         * ({@link #onTimeTick()}) in
         * ambient modes and once per {@link #getInteractiveModeUpdateRate()} in
         * interactive mode. This is also called when the date,
         * time, and/or time zone (ACTION_DATE_CHANGED, ACTION_TIME_CHANGED, and ACTION_TIMEZONE_CHANGED
         * intents, respectively) is changed on the watch.
         *
         * @param oldTime {@link GregorianCalendar} last time this method was called.
         * @param newTime updated {@link GregorianCalendar}
         */
        protected void onTimeChanged(GregorianCalendar oldTime, GregorianCalendar newTime) { }

        /**
         * Override to be informed of card peek events.
         *
         * @param rect size of the peeking card.
         */
        protected void onCardPeek(Rect rect) {
            Log.v(TAG, "WatchFace.onCardPeek: " + rect);
        }

        // CONT'D... ABSTRACTED LIFECYCLE EVENTS
        //================================================================================*****

        public abstract void onRendererReady(GLBitmapRenderer renderer);

        public abstract void onRendererDestroyed(GLBitmapRenderer renderer);

        protected void onLayout(boolean isRound, Rect screenBounds, Rect screenInsets) {

        }

        public void onCreate() { }


        //================================================================================
        //    TIME UPDATER
        //================================================================================

        /**
         * Change the interactive mode update rate for the
         * duration of the current interactive mode. The mode will change back to one specified by
         * {@link #getInteractiveModeUpdateRate()} once the watch returns to interactive mode.
         * May be useful for creating animations when a higher-than-normal update rate is desired for a
         * short period of time. If the update rate is 0, the face will redraw as fast as it can.
         * This will tell the {@link GLWatchFace} base class the period to call
         * {@link #onTimeChanged(GregorianCalendar, GregorianCalendar)} and
         * {@link #onDraw()}.
         *
         * @param updateRateMillis The new update rate, expressed in milliseconds between updates
         * @param delayUntilWholeSecond Whether the first update should start on a whole second (i.e. when milliseconds are 0)
         */
        public void startPresentingWithUpdateRate(long updateRateMillis, boolean delayUntilWholeSecond) {
            checkTimeUpdater(updateRateMillis, delayUntilWholeSecond);
        }

        private void checkTimeUpdater() {
            checkTimeUpdater(getInteractiveModeUpdateRate(), true);
        }

        private void checkTimeUpdater(long updateRate, boolean delayStart) {
            cancelTimeUpdater();
            // Note that when we're ambient or invisible, we rely on timeTick to update instead of a scheduled future
            if (!mIsAmbient && isVisible()) {
                if(updateRate == 0) {
                    mShouldDrawContinuously = true;

                    updateTimeAndInvalidate();
                } else {
                    mShouldDrawContinuously = false;

                    // start updater on next second (millis = 0) when delayed start is requested
                    long initialDelay = (delayStart ? DateUtils.SECOND_IN_MILLIS - (System.currentTimeMillis() % 1000) : 0);
                    mScheduledTimeUpdater = mScheduledTimeUpdaterPool.scheduleAtFixedRate(mTimeUpdater,
                            initialDelay, updateRate, TimeUnit.MILLISECONDS);
                }
            }
        }

        private void cancelTimeUpdater() {
            if(mScheduledTimeUpdater != null) {
                mScheduledTimeUpdater.cancel(true);
            }
        }

        private boolean isTimeUpdaterRunning() {
            return (mScheduledTimeUpdater != null && !mScheduledTimeUpdater.isCancelled());
        }

        private Runnable mInvalidateRunnable = new Runnable() {
            @Override
            public void run() {
                onInteractiveTimeTick();
                updateTimeAndInvalidate();
            }
        };

        /**
         * Called immediately before a time is updated in interactive mode
         */
        protected void onInteractiveTimeTick() {

        }

        private Runnable mTimeUpdater = new Runnable() {
            @Override
            public void run() {
                mGLThreadHandler.post(mInvalidateRunnable);
            }
        };

        //================================================================================
        //    INTERFACE IMPLEMENTATIONS
        //================================================================================

        private class TimeFormatObserver extends ContentObserver {
            public TimeFormatObserver(Handler handler) {
                super(handler);
            }

            @Override
            public void onChange(boolean selfChange) {
                updateTimeAndInvalidate();
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                updateTimeAndInvalidate();
            }
        }
    }
}