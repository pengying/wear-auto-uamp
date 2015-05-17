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

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MusicLibrary {

    private static final String SONGS_BASE_DIR = "songs";

    public static String getRoot() {
        return "";
    }

    private static final HashMap<String, MediaMetadata> music = new HashMap<>();
    private static final HashMap<String, Integer> albums = new HashMap<>();
    static {
        createMediaMetadata("Jazz_In_Paris", "Jazz in Paris",
                "Media Right Productions", "Jazz & Blues", "Jazz", 103,
                R.drawable.album_jazz_blues);
        createMediaMetadata("The_Coldest_Shoulder",
                "The Coldest Shoulder", "The 126ers", "Youtube Audio Library Rock 2", "Rock", 160,
                R.drawable.album_youtube_audio_library_rock_2);
    }

    public static AssetFileDescriptor getSongUri(Context ctx, String mediaId) {
        AssetManager assetManager = ctx.getAssets();
        AssetFileDescriptor fd = null;
        try {
            fd = assetManager.openFd(SONGS_BASE_DIR + File.separator + mediaId + ".mp3");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fd;
    }

    private static String getAlbumArtUri(int albumArtResId) {
        return "android.resource://" + BuildConfig.APPLICATION_ID + "/" + albumArtResId;
    }

    public static int getAlbumArtRes(String mediaId) {
        return albums.containsKey(mediaId) ? albums.get(mediaId) : 0;
    }

    public static List<MediaBrowser.MediaItem> getMediaItems() {
        List<MediaBrowser.MediaItem> result = new ArrayList<>();
        for (MediaMetadata metadata: music.values()) {
            result.add(new MediaBrowser.MediaItem(metadata.getDescription(),
                    MediaBrowser.MediaItem.FLAG_PLAYABLE));
        }
        return result;
    }

    public static MediaMetadata getMetadata(String mediaId) {
        return music.get(mediaId);
    }

    public static List<MediaSession.QueueItem> createQueue() {
        List<MediaSession.QueueItem> result = new ArrayList<>();
        for (MediaMetadata metadata: music.values()) {
            int queueId = metadata.getDescription().getMediaId().hashCode();
            result.add(new MediaSession.QueueItem(metadata.getDescription(), queueId));
        }
        return result;
    }

    private static void createMediaMetadata(String mediaId, String title, String artist, String album, String genre, long duration, int albumArtResId) {
        music.put(mediaId,
                new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, mediaId)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                .putString(MediaMetadata.METADATA_KEY_GENRE, genre)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, getAlbumArtUri(albumArtResId))
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, getAlbumArtUri(albumArtResId))
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .build());
        albums.put(mediaId, albumArtResId);
    }

}