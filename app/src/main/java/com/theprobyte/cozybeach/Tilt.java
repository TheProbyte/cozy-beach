package com.theprobyte.cozybeach;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/**
 * Phone tilt for the camera parallax. Uses the low-power gravity sensor and measures tilt relative
 * to how you've been holding the phone for the last few seconds, so any comfortable angle is "centre".
 * Reports x (left/right) and y (towards/away) from −1 to 1, at most ~20 times a second.
 */
final class Tilt implements SensorEventListener {
    interface Listener { void onTilt(float x, float y); }

    private static final float RANGE = 0.3f;        // about 17° of tilt = full effect
    private static final float SETTLE_SECONDS = 4f; // how quickly a new holding angle becomes centre

    private final SensorManager sensors;
    private final Sensor gravity;
    private final Listener listener;
    private boolean running, primed;
    private float baseX, baseZ, sentX, sentY;
    private long lastNanos, lastSent;

    Tilt(Context context, Listener listener) {
        this.listener = listener;
        sensors = context.getSystemService(SensorManager.class);
        Sensor g = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        gravity = g != null ? g : (sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
    }

    void start() {
        if (running || gravity == null) return;
        running = true;
        primed = false;
        sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI);
    }

    void stop() {
        if (!running) return;
        running = false;
        sensors.unregisterListener(this);
        listener.onTilt(0, 0);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float gx = e.values[0] / 9.81f, gz = e.values[2] / 9.81f;
        if (!primed) { baseX = gx; baseZ = gz; primed = true; lastNanos = e.timestamp; }
        float dt = Math.min(0.5f, (e.timestamp - lastNanos) / 1e9f);
        lastNanos = e.timestamp;
        float k = 1f - (float) Math.exp(-dt / SETTLE_SECONDS);
        baseX += (gx - baseX) * k;
        baseZ += (gz - baseZ) * k;
        float x = clamp((gx - baseX) / RANGE), y = clamp((gz - baseZ) / RANGE);
        long now = SystemClock.uptimeMillis();
        if (now - lastSent < 50 || (Math.abs(x - sentX) < 0.01f && Math.abs(y - sentY) < 0.01f)) return;
        lastSent = now; sentX = x; sentY = y;
        listener.onTilt(x, y);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private static float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }
}
