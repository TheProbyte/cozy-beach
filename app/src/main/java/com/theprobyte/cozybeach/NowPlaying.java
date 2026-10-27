package com.theprobyte.cozybeach;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Watches Android's media sessions (Spotify, YouTube Music, ...) and reports the playing song as
 * Lively-style JSON ({"Title": ..., "Artist": ...}), or null when nothing is playing.
 * Needs notification access (see MediaListener); without it, it quietly reports nothing.
 */
final class NowPlaying {
    interface Listener { void onTrack(String json); }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ComponentName component;
    private final List<MediaController> controllers = new ArrayList<>();
    private MediaSessionManager sessions;
    private String last = "";
    private boolean running;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::watch;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { report(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { report(); }
        @Override public void onSessionDestroyed() { report(); }
    };

    NowPlaying(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.component = new ComponentName(this.context, MediaListener.class);
    }

    static boolean hasAccess(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(new ComponentName(context, MediaListener.class));
    }

    void start() {
        if (running) return;
        running = true;
        try {
            sessions = context.getSystemService(MediaSessionManager.class);
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, component, main);
            watch(sessions.getActiveSessions(component));
        } catch (SecurityException noAccess) {
            sessions = null;          // no notification access yet: no sign, everything else works
        }
    }

    void stop() {
        if (!running) return;
        running = false;
        if (sessions != null) {
            try { sessions.removeOnActiveSessionsChangedListener(sessionsChanged); } catch (RuntimeException ignored) { }
        }
        for (MediaController c : controllers) c.unregisterCallback(callback);
        controllers.clear();
    }

    /** The latest report, so a freshly loaded page can be told straight away. */
    String current() { return last.isEmpty() ? null : last; }

    private void watch(List<MediaController> list) {
        for (MediaController c : controllers) c.unregisterCallback(callback);
        controllers.clear();
        if (list != null) for (MediaController c : list) { c.registerCallback(callback, main); controllers.add(c); }
        report();
    }

    private void report() {
        String json = "";
        for (MediaController c : controllers) {
            PlaybackState state = c.getPlaybackState();
            MediaMetadata meta = c.getMetadata();
            if (state == null || meta == null || state.getState() != PlaybackState.STATE_PLAYING) continue;
            String title = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null) artist = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (title == null && artist == null) continue;
            try {
                json = new JSONObject().put("Title", title == null ? "" : title).put("Artist", artist == null ? "" : artist).toString();
            } catch (JSONException ignored) { }
            break;
        }
        if (json.equals(last)) return;
        last = json;
        listener.onTrack(current());
    }
}
