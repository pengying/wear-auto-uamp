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

import com.example.android.uamp.MusicLibrary;
import com.example.android.uamp.PlaybackManager;
import com.example.android.uamp.R;

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

    private PlaybackManager mPlaybackManager;
    private BrowseAdapter mBrowserAdapter;
    private ImageButton mPlayPause;
    private TextView mTitle;
    private TextView mSubtitle;
    private ImageView mAlbumArt;
    private ViewGroup mPlaybackControls;
    private String mArtUrl;

    private MediaMetadata mCurrentMetadata;
    private PlaybackState mCurrentState;

    private MediaBrowser mMediaBrowser;

    private MediaBrowser.ConnectionCallback mConnectionCallback =
            new MediaBrowser.ConnectionCallback() {
                @Override
                public void onConnected() {
                    mMediaBrowser.subscribe(mMediaBrowser.getRoot(), mSubscriptionCallback);
                    MediaController mediaController = new MediaController(
                            MusicPlayerActivity.this, mMediaBrowser.getSessionToken());
                    mediaController.registerCallback(mMediaControllerCallback);
                    setMediaController(mediaController);
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
            updatePlaybackControlsMetadata(metadata);
            mBrowserAdapter.notifyDataSetChanged();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            super.onPlaybackStateChanged(state);
            updatePlaybackControls(state);
            mBrowserAdapter.notifyDataSetChanged();
        }
    };

    private MediaBrowser.SubscriptionCallback mSubscriptionCallback =
        new MediaBrowser.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
                mBrowserAdapter.clear();
                mBrowserAdapter.addAll(children);
                mBrowserAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String id) {
                Toast.makeText(MusicPlayerActivity.this, R.string.error_loading_media, Toast.LENGTH_LONG).show();
            }
        };

    public void onMediaItemSelected(MediaBrowser.MediaItem item) {
        if (item.isPlayable()) {
            mPlaybackManager.play(item.getMediaId());
            updatePlaybackControlsMetadata(MusicLibrary.getMetadata(item.getMediaId()));
//            getMediaController().getTransportControls().playFromMediaId(item.getMediaId(), null);
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        setTitle(getString(R.string.app_name));
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        // Music list configuration:
        mPlaybackManager = new PlaybackManager(this);
        mPlaybackManager.setCallback(new PlaybackManager.Callback() {
            @Override
            public void onPlaybackStatusChanged(PlaybackState state) {
                mBrowserAdapter.notifyDataSetChanged();
                updatePlaybackControls(state);
            }
        });

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
        mPlaybackControls = (ViewGroup) findViewById(R.id.playback_controls);
        mPlayPause = (ImageButton) findViewById(R.id.play_pause);
        mPlayPause.setEnabled(true);
        mPlayPause.setOnClickListener(mPlaybackButtonListener);

        mTitle = (TextView) findViewById(R.id.title);
        mSubtitle = (TextView) findViewById(R.id.artist);
        mAlbumArt = (ImageView) findViewById(R.id.album_art);

//        // Connect to the media browser:
//        mMediaBrowser = new MediaBrowser(this,
//                new ComponentName(this, MusicService.class), mConnectionCallback, null);

    }


    @Override
    public void onStart() {
        super.onStart();
//        mMediaBrowser.connect();
        mBrowserAdapter.clear();
        mBrowserAdapter.addAll(MusicLibrary.getMediaItems());
        mBrowserAdapter.notifyDataSetChanged();

    }

    @Override
    public void onStop() {
        super.onStop();
        mPlaybackManager.stop();
//        if (mMediaBrowser != null && mMediaBrowser.isConnected()) {
//            mMediaBrowser.unsubscribe(mMediaBrowser.getRoot());
//            mMediaBrowser.disconnect();
//        }
//        if (getMediaController() != null) {
//            getMediaController().unregisterCallback(mMediaControllerCallback);
//        }
    }

    private void updatePlaybackControls(PlaybackState state) {
        mCurrentState = state;
        boolean enablePlay = false;
        switch (state.getState()) {
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_STOPPED:
                mPlaybackControls.setVisibility(View.VISIBLE);
                enablePlay = true;
                break;
            case PlaybackState.STATE_ERROR:
                Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_LONG).show();
                break;
            case PlaybackState.STATE_NONE:
                mPlaybackControls.setVisibility(View.GONE);
                break;
        }

        if (enablePlay) {
            mPlayPause.setImageDrawable(getDrawable(R.drawable.ic_play_arrow_black_36dp));
        } else {
            mPlayPause.setImageDrawable(getDrawable(R.drawable.ic_pause_black_36dp));
        }
    }

    private void updatePlaybackControlsMetadata(MediaMetadata metadata) {
        mCurrentMetadata = metadata;
        mTitle.setText(metadata.getDescription().getTitle());
        mSubtitle.setText(metadata.getDescription().getSubtitle());
        String artUrl = metadata.getDescription().getIconUri().toString();
        if (!TextUtils.equals(artUrl, mArtUrl)) {
            mAlbumArt.setImageURI(Uri.parse(artUrl));
            mArtUrl = artUrl;
        }
        mBrowserAdapter.notifyDataSetChanged();
    }

    // An adapter for showing the list of browsed MediaItem's
    private class BrowseAdapter extends ArrayAdapter<MediaBrowser.MediaItem> {

        public BrowseAdapter(Activity context) {
            super(context, R.layout.media_list_item, new ArrayList<MediaBrowser.MediaItem>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MediaBrowser.MediaItem item = getItem(position);
            int itemState = MediaItemViewHolder.STATE_NONE;
            if (item.isPlayable()) {
                String itemMediaId = item.getDescription().getMediaId();
                int playbackState = PlaybackState.STATE_NONE;
                if (mCurrentState != null) {
                    playbackState = mCurrentState.getState();
                }
                if (mCurrentMetadata != null &&
                        itemMediaId.equals(mCurrentMetadata.getDescription().getMediaId())) {
                    if (playbackState == PlaybackState.STATE_PLAYING ||
                        playbackState == PlaybackState.STATE_BUFFERING) {
                        itemState = MediaItemViewHolder.STATE_PLAYING;
                    } else if (playbackState != PlaybackState.STATE_ERROR) {
                        itemState = MediaItemViewHolder.STATE_PAUSED;
                    }
                }
            }
            return MediaItemViewHolder.setupView((Activity) getContext(), convertView, parent,
                item.getDescription(), itemState);
        }
    }

    private View.OnClickListener mPlaybackButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
//            PlaybackState stateObj = getMediaController().getPlaybackState();
            final int state = mCurrentState == null ?
                    PlaybackState.STATE_NONE : mCurrentState.getState();
            switch (v.getId()) {
                case R.id.play_pause:
                    if (state == PlaybackState.STATE_PAUSED ||
                            state == PlaybackState.STATE_STOPPED ||
                            state == PlaybackState.STATE_NONE) {

                        if (mCurrentMetadata == null) {
                            String mediaId = MusicLibrary.getMediaItems().get(0).getMediaId();
                            mCurrentMetadata = MusicLibrary.getMetadata(mediaId);
                            updatePlaybackControlsMetadata(mCurrentMetadata);
                        }
                        mPlaybackManager.play(mCurrentMetadata.getDescription().getMediaId());
//                        getMediaController().getTransportControls().play();
                    } else if (state == PlaybackState.STATE_PLAYING ||
                            state == PlaybackState.STATE_BUFFERING ||
                            state == PlaybackState.STATE_CONNECTING) {
                        mPlaybackManager.pause();
//                        getMediaController().getTransportControls().pause();
                    }
                    break;
            }
        }
    };

}
