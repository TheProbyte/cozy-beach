package com.theprobyte.cozybeach;

import android.service.notification.NotificationListenerService;

/**
 * Android only shares "what's playing" (media sessions) with apps that have notification access.
 * This service exists just to hold that access. It reads no notifications; NowPlaying uses the
 * access to read the song title and artist of whatever is playing, and nothing leaves the phone.
 */
public class MediaListener extends NotificationListenerService {
}
