package com.theprobyte.cozybeach;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * Opening the app: the beach full screen (tap it for the ⚙ settings, which the wallpaper shares),
 * a "Set as wallpaper" button, and, until it's allowed, a button to let the sign show your music.
 */
public class MainActivity extends Activity {
    private WebView web;
    private NowPlaying nowPlaying;
    private Tilt tilt;
    private Button musicButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1630);

        web = BeachWeb.create(this, () -> BeachWeb.sendTrack(web, nowPlaying.current()));
        nowPlaying = new NowPlaying(this, (json) -> BeachWeb.sendTrack(web, json));
        tilt = new Tilt(this, (x, y) -> BeachWeb.sendTilt(web, x, y));
        BeachWeb.bridge(web, (fps) -> runOnUiThread(() -> {
            // let the screen run as fast as the page wants (up to 120 Hz)
            android.view.WindowManager.LayoutParams a = getWindow().getAttributes();
            a.preferredRefreshRate = fps;
            getWindow().setAttributes(a);
        }));
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setGravity(Gravity.CENTER_HORIZONTAL);
        musicButton = pill("♪  Show my music on the sign");
        musicButton.setOnClickListener(v -> explainMusicAccess());
        Button set = pill(getString(R.string.set_wallpaper));
        set.setOnClickListener(v -> setAsWallpaper());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(52));
        blp.topMargin = dp(10);
        buttons.addView(musicButton, blp);
        buttons.addView(set, new LinearLayout.LayoutParams(blp));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.addView(buttons, lp);

        // Keep the buttons above the gesture bar.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            lp.bottomMargin = insets.getInsets(WindowInsets.Type.systemBars()).bottom + dp(24);
            buttons.setLayoutParams(lp);
            return insets;
        });

        setContentView(root);
        web.loadUrl(BeachWeb.PAGE + "?app");
    }

    private Button pill(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC1E2A4A);
        bg.setStroke(dp(1), 0x66FFFFFF);
        bg.setCornerRadius(dp(26));
        b.setBackground(bg);
        b.setPadding(dp(26), 0, dp(26), 0);
        b.setStateListAnimator(null);
        return b;
    }

    private void explainMusicAccess() {
        new AlertDialog.Builder(this)
                .setTitle("Show your music on the sign")
                .setMessage("When music plays (Spotify, YouTube Music…), Bo holds up a sign with the song and everyone nods along.\n\n"
                        + "Android only shares the song name with apps that have \"notification access\". Cozy Beach uses it only to read the title and artist of what's playing. It doesn't read your notifications, and nothing leaves your phone.\n\n"
                        + "1. Tap Open settings and switch on Cozy Beach.\n"
                        + "2. If Android says it's a restricted setting: tap App info, then ⋮ (top right) → Allow restricted settings, and try step 1 again.")
                .setPositiveButton("Open settings", (d, w) -> openListenerSettings())
                .setNeutralButton("App info", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))))
                .setNegativeButton("Not now", null)
                .show();
    }

    private void openListenerSettings() {
        Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(this, MediaListener.class).flattenToString());
        try {
            startActivity(detail);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    private void setAsWallpaper() {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, BeachWallpaperService.class));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "Open Settings → Wallpaper and pick Cozy Beach under live wallpapers.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        musicButton.setVisibility(NowPlaying.hasAccess(this) ? View.GONE : View.VISIBLE);
        nowPlaying.start();
        tilt.start();
    }

    @Override
    protected void onPause() {
        nowPlaying.stop();
        tilt.stop();
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
