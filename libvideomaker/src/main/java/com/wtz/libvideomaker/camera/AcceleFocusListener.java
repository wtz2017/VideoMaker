package com.wtz.libvideomaker.camera;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.Calendar;

public class AcceleFocusListener implements SensorEventListener {

    private static AcceleFocusListener mInstance;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private OnFocusListener mCameraFocusListener;

    public static final int STATUS_NONE = 0;
    public static final int STATUS_STATIC = 1;
    public static final int STATUS_MOVE = 2;
    private int mStatus = STATUS_NONE;
    private int mX, mY, mZ;

    boolean enableListen = false;
    boolean canFocus = false;
    boolean isFocusing = false;

    private static final double MOVE_THRESHOLD = 1.0;
    private static final int STATIC_DELAY_MILLISEC = 300;
    private long mStartStaticStamp = 0;

    public static AcceleFocusListener getInstance(Context context) {
        if (mInstance == null) {
            synchronized (AcceleFocusListener.class) {
                if (mInstance == null) {
                    mInstance = new AcceleFocusListener(context);
                }
            }
        }
        return mInstance;
    }

    private AcceleFocusListener(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Activity.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    public void setCameraFocusListener(OnFocusListener listener) {
        this.mCameraFocusListener = listener;
    }

    public void start() {
        resetParams();
        isFocusing = false;
        enableListen = true;
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void stop() {
        mSensorManager.unregisterListener(this, mSensor);
        enableListen = false;
        isFocusing = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == null) {
            return;
        }

        if (isFocusing) {
            resetParams();
            return;
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            int x = (int) event.values[0];
            int y = (int) event.values[1];
            int z = (int) event.values[2];
            long stamp = Calendar.getInstance().getTimeInMillis();
            if (mStatus != STATUS_NONE) {
                int px = Math.abs(mX - x);
                int py = Math.abs(mY - y);
                int pz = Math.abs(mZ - z);
                double value = Math.sqrt(px * px + py * py + pz * pz);

                if (value > MOVE_THRESHOLD) {
                    mStatus = STATUS_MOVE;
                } else {
                    if (mStatus == STATUS_MOVE) {
                        // 之前在移动，现在停了下来，记下开始静止的时间戳
                        mStartStaticStamp = stamp;
                        canFocus = true;
                    }

                    if (canFocus && stamp - mStartStaticStamp > STATIC_DELAY_MILLISEC && !isFocusing) {
                        // 静止一段时间后再对焦
                        canFocus = false;
                        if (mCameraFocusListener != null) {
                            mCameraFocusListener.onFocus();
                        }
                    }

                    mStatus = STATUS_STATIC;
                }
            } else {
                mStartStaticStamp = stamp;
                mStatus = STATUS_STATIC;
            }

            mX = x;
            mY = y;
            mZ = z;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void resetParams() {
        mStatus = STATUS_NONE;
        canFocus = false;
        mX = 0;
        mY = 0;
        mZ = 0;
    }

    /**
     * 对焦是否被锁定
     */
    public boolean isFocusLocked() {
        return enableListen && isFocusing;
    }

    /**
     * 锁定对焦
     */
    public void lockFocus() {
        isFocusing = true;
    }

    /**
     * 解锁对焦
     */
    public void unlockFocus() {
        isFocusing = false;
    }

    public interface OnFocusListener {
        /**
         * 相机可以对焦了
         */
        void onFocus();
    }

}
