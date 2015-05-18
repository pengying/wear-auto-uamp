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

 import android.content.Intent;
 import android.media.browse.MediaBrowser.MediaItem;
 import android.media.session.MediaSession;
 import android.media.session.PlaybackState;
 import android.os.Bundle;
 import android.os.Handler;
 import android.os.Message;
 import android.service.media.MediaBrowserService;

 import com.example.android.uamp.utils.LogHelper;

 import java.lang.ref.WeakReference;
 import java.util.List;

public class MusicService extends MediaBrowserService implements PlaybackManager.Callback {

    private static final String TAG = LogHelper.makeLogTag(MusicService.class);

    // Delay stopSelf by using a handler.
    private static final int STOP_DELAY = 30000;

    private MediaSession mSession;
    private MediaNotificationManager mMediaNotificationManager;
    // Indicates whether the service was started.
    private boolean mServiceStarted;
    private DelayedStopHandler mDelayedStopHandler = new DelayedStopHandler(this);
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
     * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
     */
    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        // Reset the delay handler to enqueue a message to stop the service if
        // nothing is playing.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
        return START_STICKY;
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

        mDelayedStopHandler.removeCallbacksAndMessages(null);
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
            setActive(true);
            mSession.setMetadata(MusicLibrary.getMetadata(mediaId));
            mPlayback.play(mediaId);
        }

        @Override
        public void onPause() {
            setActive(false);
        }

        @Override
        public void onStop() {
            stopPlaying();
        }

        @Override
        public void onSkipToNext() {
            setActive(true);
        }

        @Override
        public void onSkipToPrevious() {
            setActive(true);
        }
    }

    private void setActive(boolean active) {
        if (active) {
            mDelayedStopHandler.removeCallbacksAndMessages(null);
            if (!mServiceStarted) {
                LogHelper.v(TAG, "Starting service");
                // The MusicService needs to keep running even after the calling MediaBrowser
                // is disconnected. Call startService(Intent) and then stopSelf(..) when we no longer
                // need to play media.
                startService(new Intent(getApplicationContext(), MusicService.class));
                mServiceStarted = true;
            }

            if (!mSession.isActive()) {
                mSession.setActive(true);
            }
        } else {
            mPlayback.pause();
            // reset the delayed stop handler.
            mDelayedStopHandler.removeCallbacksAndMessages(null);
            mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
        }
    }

    /**
     * Handle a request to stop music
     */
    private void stopPlaying() {
        mPlayback.stop(true);
        // reset the delayed stop handler.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);

        // service is no longer necessary. Will be started again if needed.
        stopSelf();
        mServiceStarted = false;
    }

    /**
     * Implementation of the PlaybackManager.Callback interface
     */
    @Override
    public void onCompletion() {
        stopPlaying();
    }

    @Override
    public void onPlaybackStatusChanged(PlaybackState state) {
        mSession.setPlaybackState(state);

        if (state.getState() == PlaybackState.STATE_PLAYING ||
                state.getState() == PlaybackState.STATE_PAUSED) {
            mMediaNotificationManager.startNotification();
        }
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private static class DelayedStopHandler extends Handler {
        private final WeakReference<MusicService> mWeakReference;

        private DelayedStopHandler(MusicService service) {
            mWeakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MusicService service = mWeakReference.get();
            if (service != null && service.mPlayback != null) {
                if (service.mPlayback.isPlaying()) {
                    LogHelper.d(TAG, "Ignoring delayed stop since the media player is in use.");
                    return;
                }
                LogHelper.d(TAG, "Stopping service with delay handler.");
                service.stopSelf();
                service.mServiceStarted = false;
            }
        }
    }
}
