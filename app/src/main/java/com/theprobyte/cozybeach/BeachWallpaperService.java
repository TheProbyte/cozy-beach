package com.theprobyte.cozybeach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.MotionEvent;
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

    private class BeachEngine extends Engine {
        private VirtualDisplay display;
        private Context windowContext;
        private WebView web;
        private long lastTouch;

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
                return;
            }
            try {
                DisplayManager dm = getSystemService(DisplayManager.class);
                // Flags 0 = a private display only this app can put windows on.
                display = dm.createVirtualDisplay("CozyBeach", width, height, dpi, holder.getSurface(), 0);
                Context displayContext = createDisplayContext(display.getDisplay());
                windowContext = displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION, null);
                web = BeachWeb.create(windowContext);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION,
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.OPAQUE);
                windowContext.getSystemService(WindowManager.class).addView(web, lp);
                web.loadUrl(BeachWeb.PAGE + "?wallpaper");
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
            if (visible) web.onResume(); else web.onPause();
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

        private void cleanUp() {
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
