package com.theprobyte.cozybeach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;

/**
 * The live wallpaper. A WebView can't draw straight onto a wallpaper, so it lives in a window on a
 * private virtual display whose output IS the wallpaper's surface. The WebView then renders with the
 * GPU as usual (WebGL included), and every frame lands on the home and lock screen.
 */
public class BeachWallpaperService extends WallpaperService {
    private static final String TAG = "CozyBeach";

    @Override
    public Engine onCreateEngine() {
        return new BeachEngine();
    }

    /** The screen's fastest refresh rate (120 Hz on a Pixel 10 Pro XL). */
    private float maxRefreshRate() {
        float hz = 60f;
        try {
            Display d = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            for (Display.Mode m : d.getSupportedModes()) hz = Math.max(hz, m.getRefreshRate());
        } catch (RuntimeException ignored) { }
        return hz;
    }

    /**
     * The private display the page draws into. On Android 14+ it is asked to run at the screen's full
     * refresh rate (otherwise virtual displays tick at 60). Done by reflection so it builds against
     * any SDK and quietly falls back to the plain version.
     */
    private VirtualDisplay createDisplay(DisplayManager dm, int w, int h, int dpi, Surface surface) {
        float hz = maxRefreshRate();
        if (Build.VERSION.SDK_INT >= 34 && hz > 61f) {
            try {
                Class<?> builder = Class.forName("android.hardware.display.VirtualDisplayConfig$Builder");
                Object b = builder.getConstructor(String.class, int.class, int.class, int.class).newInstance("CozyBeach", w, h, dpi);
                builder.getMethod("setSurface", Surface.class).invoke(b, surface);
                builder.getMethod("setRequestedRefreshRate", float.class).invoke(b, hz);
                Object config = builder.getMethod("build").invoke(b);
                for (java.lang.reflect.Method m : DisplayManager.class.getMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (m.getName().equals("createVirtualDisplay") && p.length == 3 && p[0].getName().equals("android.hardware.display.VirtualDisplayConfig")) {
                        Object vd = m.invoke(dm, config, null, null);
                        if (vd instanceof VirtualDisplay) return (VirtualDisplay) vd;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                Log.w(TAG, "High refresh rate display not available, using 60 Hz", e);
            }
        }
        // Flags 0 = a private display only this app can put windows on.
        return dm.createVirtualDisplay("CozyBeach", w, h, dpi, surface, 0);
    }

    private class BeachEngine extends Engine {
        private VirtualDisplay display;
        private Context windowContext;
        private WebView web;
        private long lastTouch;
        private NowPlaying nowPlaying;
        private Tilt tilt;
        private final Handler main = new Handler(Looper.getMainLooper());
        private float frameRate = 60f;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(true);
            setOffsetNotificationsEnabled(false);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            int dpi = getResources().getDisplayMetrics().densityDpi;
            if (display != null) {
                display.setSurface(holder.getSurface());
                display.resize(width, height, dpi);
                applyFrameRate();
                return;
            }
            try {
                DisplayManager dm = getSystemService(DisplayManager.class);
                display = createDisplay(dm, width, height, dpi, holder.getSurface());
                Context displayContext = createDisplayContext(display.getDisplay());
                windowContext = displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION, null);
                web = BeachWeb.create(windowContext, () -> BeachWeb.sendTrack(web, nowPlaying.current()));
                nowPlaying = new NowPlaying(BeachWallpaperService.this, (json) -> BeachWeb.sendTrack(web, json));
                tilt = new Tilt(BeachWallpaperService.this, (x, y) -> BeachWeb.sendTilt(web, x, y));
                BeachWeb.bridge(web, (fps) -> main.post(() -> { frameRate = fps; applyFrameRate(); }));
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION,
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.OPAQUE);
                windowContext.getSystemService(WindowManager.class).addView(web, lp);
                web.loadUrl(BeachWeb.PAGE + "?wallpaper&app");
                if (isVisible()) startExtras();
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not start the wallpaper", e);
                cleanUp();
                showError(holder, e);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            if (display != null) display.setSurface(null);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (web == null) return;
            // Out of sight (an app on top, screen off): stop drawing to save battery.
            if (visible) { web.onResume(); startExtras(); } else { web.onPause(); stopExtras(); }
            web.evaluateJavascript("window.__wallpaperVisible && window.__wallpaperVisible(" + visible + ")", null);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            if (web == null) return;
            lastTouch = SystemClock.uptimeMillis();
            web.dispatchTouchEvent(event);
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            // Some home screens only report a tap as a command: turn it into a touch, unless the
            // touch already arrived on its own.
            if (web != null && android.app.WallpaperManager.COMMAND_TAP.equals(action)
                    && SystemClock.uptimeMillis() - lastTouch > 400) {
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0);
                web.dispatchTouchEvent(down);
                web.dispatchTouchEvent(up);
                down.recycle();
                up.recycle();
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        @Override
        public void onDestroy() {
            cleanUp();
            super.onDestroy();
        }

        /** Ask Android to run the screen at the page's frame rate (e.g. 120 Hz), so it isn't held at 60. */
        private void applyFrameRate() {
            try {
                Surface s = getSurfaceHolder().getSurface();
                if (s != null && s.isValid()) s.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            } catch (RuntimeException ignored) { }
        }

        /** Song info and tilt only while the wallpaper can be seen (saves battery). */
        private void startExtras() {
            if (nowPlaying != null) nowPlaying.start();
            if (tilt != null) tilt.start();
        }

        private void stopExtras() {
            if (nowPlaying != null) nowPlaying.stop();
            if (tilt != null) tilt.stop();
        }

        private void cleanUp() {
            stopExtras();
            if (web != null) {
                try {
                    windowContext.getSystemService(WindowManager.class).removeViewImmediate(web);
                } catch (RuntimeException ignored) {
                }
                web.destroy();
                web = null;
            }
            if (display != null) {
                display.release();
                display = null;
            }
        }

        /** If something goes wrong, say so on the wallpaper instead of showing black. */
        private void showError(SurfaceHolder holder, Exception e) {
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c == null) return;
                c.drawColor(Color.rgb(11, 22, 48));
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.WHITE);
                p.setTextSize(c.getWidth() / 24f);
                float y = c.getHeight() * 0.4f;
                c.drawText("Cozy Beach couldn't start:", c.getWidth() * 0.06f, y, p);
                p.setTextSize(c.getWidth() / 34f);
                String msg = String.valueOf(e);
                int per = 40;
                for (int i = 0; i < msg.length() && i < per * 8; i += per) {
                    y += p.getTextSize() * 1.4f;
                    c.drawText(msg.substring(i, Math.min(msg.length(), i + per)), c.getWidth() * 0.06f, y, p);
                }
            } catch (RuntimeException ignored) {
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
        }
    }
}
