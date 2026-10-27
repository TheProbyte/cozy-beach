package com.theprobyte.cozybeach;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * Opening the app: the beach full screen (tap it for the ⚙ settings, which the wallpaper shares)
 * and a "Set as wallpaper" button that opens Android's wallpaper preview.
 */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1630);

        web = BeachWeb.create(this);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        Button set = new Button(this);
        set.setText(R.string.set_wallpaper);
        set.setAllCaps(false);
        set.setTextColor(Color.WHITE);
        set.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC1E2A4A);
        bg.setStroke(dp(1), 0x66FFFFFF);
        bg.setCornerRadius(dp(28));
        set.setBackground(bg);
        set.setPadding(dp(28), dp(12), dp(28), dp(12));
        set.setOnClickListener(v -> setAsWallpaper());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(56), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.addView(set, lp);

        // Keep the button above the gesture bar.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
            lp.bottomMargin = bottom + dp(28);
            set.setLayoutParams(lp);
            return insets;
        });

        setContentView(root);
        web.loadUrl(BeachWeb.PAGE);
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
    }

    @Override
    protected void onPause() {
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
