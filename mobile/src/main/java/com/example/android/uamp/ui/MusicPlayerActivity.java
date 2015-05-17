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
package com.example.android.uamp.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.uamp.MusicService;
import com.example.android.uamp.R;
import com.example.android.uamp.utils.LogHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A Fragment that lists all the various browsable queues available
 * from a {@link android.service.media.MediaBrowserService}.
 * <p/>
 * It uses a {@link MediaBrowser} to connect to the {@link com.example.android.uamp.MusicService}.
 * Once connected, the fragment subscribes to get all the children.
 * All {@link MediaBrowser.MediaItem}'s that can be browsed are shown in a ListView.
 */
public class MusicPlayerActivity extends ActionBarActivity {

    private static final String TAG = LogHelper.makeLogTag(MusicPlayerActivity.class);

    private MediaBrowser mMediaBrowser;
    private BrowseAdapter mBrowserAdapter;
    private ImageButton mPlayPause;
    private TextView mTitle;
    private TextView mSubtitle;
    private ImageView mAlbumArt;
    private String mArtUrl;

    private MediaBrowser.ConnectionCallback mConnectionCallback =
            new MediaBrowser.ConnectionCallback() {
                @Override
                public void onConnected() {
                    LogHelper.d(TAG, "onConnected");
                    MusicPlayerActivity.this.onMediaBrowserConnected();
                }
            };

    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private MediaController.Callback mMediaControllerCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            if (metadata == null) {
                return;
            }
            LogHelper.d(TAG, "Received metadata change to media ",
                    metadata.getDescription().getMediaId());
            mBrowserAdapter.notifyDataSetChanged();
            updatePlaybackControlsMetadata(metadata);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            super.onPlaybackStateChanged(state);
            LogHelper.d(TAG, "Received state change: ", state);
            mBrowserAdapter.notifyDataSetChanged();
            updatePlaybackControls(state);
        }
    };

    private MediaBrowser.SubscriptionCallback mSubscriptionCallback =
        new MediaBrowser.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
                try {
                    LogHelper.d(TAG, "fragment onChildrenLoaded, parentId=" + parentId +
                            "  count=" + children.size());
                    mBrowserAdapter.clear();
                    for (MediaBrowser.MediaItem item : children) {
                        mBrowserAdapter.add(item);
                    }
                    mBrowserAdapter.notifyDataSetChanged();
                } catch (Throwable t) {
                    LogHelper.e(TAG, "Error on childrenloaded", t);
                }
            }

            @Override
            public void onError(String id) {
                LogHelper.e(TAG, "browse fragment subscription onError, id=" + id);
                Toast.makeText(MusicPlayerActivity.this, R.string.error_loading_media, Toast.LENGTH_LONG).show();
            }
        };

    public void onMediaItemSelected(MediaBrowser.MediaItem item) {
        LogHelper.d(TAG, "onMediaItemSelected, mediaId=" + item.getMediaId());
        if (item.isPlayable()) {
            getMediaController().getTransportControls().playFromMediaId(item.getMediaId(), null);
        } else if (item.isBrowsable()) {
            LogHelper.w(TAG, "Ignoring MediaItem that is not playable." +
                    "In a real world app, you should navigate into the browsable MediaItem. ",
                    "mediaId=", item.getMediaId());
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "Activity onCreate");
        setContentView(R.layout.activity_player);
        setTitle(getString(R.string.app_name));
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        // Music list configuration:
        mBrowserAdapter = new BrowseAdapter(this);

        ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(mBrowserAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MediaBrowser.MediaItem item = mBrowserAdapter.getItem(position);
                onMediaItemSelected(item);
            }
        });

        // Playback controls configuration:
        mPlayPause = (ImageButton) findViewById(R.id.play_pause);
        mPlayPause.setEnabled(true);
        mPlayPause.setOnClickListener(mPlaybackButtonListener);

        mTitle = (TextView) findViewById(R.id.title);
        mSubtitle = (TextView) findViewById(R.id.artist);
        mAlbumArt = (ImageView) findViewById(R.id.album_art);

        // Connect to the media browser:
        mMediaBrowser = new MediaBrowser(this,
                new ComponentName(this, MusicService.class), mConnectionCallback, null);

    }


    @Override
    public void onStart() {
        super.onStart();
        mMediaBrowser.connect();
    }

    public void onMediaBrowserConnected() {
        mMediaBrowser.subscribe(mMediaBrowser.getRoot(), mSubscriptionCallback);
        MediaController mediaController = new MediaController(this, mMediaBrowser.getSessionToken());
        mediaController.registerCallback(mMediaControllerCallback);
        setMediaController(mediaController);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMediaBrowser != null && mMediaBrowser.isConnected()) {
            mMediaBrowser.unsubscribe(mMediaBrowser.getRoot());
            mMediaBrowser.disconnect();
        }
        if (getMediaController() != null) {
            getMediaController().unregisterCallback(mMediaControllerCallback);
        }
    }

    private void updatePlaybackControls(PlaybackState state) {
        boolean enablePlay = false;
        switch (state.getState()) {
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_STOPPED:
                enablePlay = true;
                break;
            case PlaybackState.STATE_ERROR:
                LogHelper.e(TAG, "error playbackstate: ", state.getErrorMessage());
                Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_LONG).show();
                break;
        }

        if (enablePlay) {
            mPlayPause.setImageDrawable(getDrawable(R.drawable.ic_play_arrow_black_36dp));
        } else {
            mPlayPause.setImageDrawable(getDrawable(R.drawable.ic_pause_black_36dp));
        }
    }

    private void updatePlaybackControlsMetadata(MediaMetadata metadata) {
        mTitle.setText(metadata.getDescription().getTitle());
        mSubtitle.setText(metadata.getDescription().getSubtitle());
        String artUrl = metadata.getDescription().getIconUri().toString();
        if (!TextUtils.equals(artUrl, mArtUrl)) {
            mAlbumArt.setImageURI(Uri.parse(artUrl));
            mArtUrl = artUrl;
        }
    }

    // An adapter for showing the list of browsed MediaItem's
    private static class BrowseAdapter extends ArrayAdapter<MediaBrowser.MediaItem> {

        public BrowseAdapter(Activity context) {
            super(context, R.layout.media_list_item, new ArrayList<MediaBrowser.MediaItem>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MediaBrowser.MediaItem item = getItem(position);
            int state = MediaItemViewHolder.STATE_NONE;
            if (item.isPlayable()) {
                state = MediaItemViewHolder.STATE_PLAYABLE;
                MediaController controller = ((Activity) getContext()).getMediaController();
                if (controller != null && controller.getMetadata() != null) {
                    String currentPlaying = controller.getMetadata().getDescription().getMediaId();
                    String mediaId = item.getDescription().getMediaId();
                    if (currentPlaying != null && currentPlaying.equals(mediaId)) {
                        if (controller.getPlaybackState().getState() ==
                                PlaybackState.STATE_PLAYING) {
                            state = MediaItemViewHolder.STATE_PLAYING;
                        } else if (controller.getPlaybackState().getState() !=
                                PlaybackState.STATE_ERROR) {
                            state = MediaItemViewHolder.STATE_PAUSED;
                        }
                    }
                }
            }
            return MediaItemViewHolder.setupView((Activity) getContext(), convertView, parent,
                item.getDescription(), state);
        }
    }

    private View.OnClickListener mPlaybackButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            PlaybackState stateObj = getMediaController().getPlaybackState();
            final int state = stateObj == null ?
                    PlaybackState.STATE_NONE : stateObj.getState();
            LogHelper.d(TAG, "Button pressed, in state " + state);
            switch (v.getId()) {
                case R.id.play_pause:
                    LogHelper.d(TAG, "Play button pressed, in state " + state);
                    if (state == PlaybackState.STATE_PAUSED ||
                            state == PlaybackState.STATE_STOPPED ||
                            state == PlaybackState.STATE_NONE) {
                        getMediaController().getTransportControls().play();
                    } else if (state == PlaybackState.STATE_PLAYING ||
                            state == PlaybackState.STATE_BUFFERING ||
                            state == PlaybackState.STATE_CONNECTING) {
                        getMediaController().getTransportControls().pause();
                    }
                    break;
            }
        }
    };

}
