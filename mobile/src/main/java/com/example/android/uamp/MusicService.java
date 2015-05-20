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

package com.example.android.uamp;

 import android.media.browse.MediaBrowser.MediaItem;
 import android.media.session.MediaSession;
 import android.media.session.PlaybackState;
 import android.os.Bundle;
 import android.service.media.MediaBrowserService;

 import com.example.android.uamp.utils.LogHelper;

 import java.util.List;

public class MusicService extends MediaBrowserService implements PlaybackManager.Callback {

    private static final String TAG = LogHelper.makeLogTag(MusicService.class);

    private MediaSession mSession;
    private MediaNotificationManager mMediaNotificationManager;
    private PlaybackManager mPlayback;

    /*
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        LogHelper.d(TAG, "onCreate");

        // Start a new MediaSession
        mSession = new MediaSession(this, "MusicService");
        setSessionToken(mSession.getSessionToken());
        mSession.setCallback(new MediaSessionCallback());
        mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mPlayback = new PlaybackManager(this);
        mPlayback.setCallback(this);

        mMediaNotificationManager = new MediaNotificationManager(this);
    }

    /**
     * (non-Javadoc)
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        LogHelper.d(TAG, "onDestroy");
        // Service is being killed, so make sure we release our resources
        stopPlaying();

        // Always release the MediaSession to clean up resources
        // and notify associated MediaController(s).
        mSession.release();
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot(MusicLibrary.getRoot(), null);
    }

    @Override
    public void onLoadChildren(final String parentMediaId, final Result<List<MediaItem>> result) {
        result.sendResult(MusicLibrary.getMediaItems());
    }

    private final class MediaSessionCallback extends MediaSession.Callback {
        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            mSession.setActive(true);
            mSession.setMetadata(MusicLibrary.getMetadata(mediaId));
            mPlayback.play(mediaId);
        }

        @Override
        public void onPlay() {
            if (mPlayback.getCurrentMediaId() != null) {
                mSession.setActive(true);
                mPlayback.play(mPlayback.getCurrentMediaId());
            }
        }

        @Override
        public void onPause() {
            mPlayback.pause();
        }

        @Override
        public void onStop() {
            stopPlaying();
        }

    }

    /**
     * Handle a request to stop music
     */
    private void stopPlaying() {
        mPlayback.stop();
        stopSelf();
    }

    @Override
    public void onPlaybackStatusChanged(PlaybackState state) {
        mSession.setPlaybackState(state);
        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_PAUSED:
                mMediaNotificationManager.startNotification();
                break;
            case PlaybackState.STATE_STOPPED:
                stopPlaying();
                break;
        }
    }

}
