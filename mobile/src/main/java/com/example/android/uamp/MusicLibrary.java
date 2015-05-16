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
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicLibrary {

    private static final String SONGS_BASE_DIR = "songs";
    private static final String ALBUM_ARTS_BASE_DIR = "album_arts";
    private static final String ALBUM_ART_THUMBNAIL_SUFFIX = "_thumbnail";

    public static String getRoot() {
        return "";
    }

    public static final String getSongFilename(String mediaId) {
        return SONGS_BASE_DIR + mediaId;
    }

    public static final String getSongUri(Context ctx, String mediaId) {
        return "android.resource://" + ctx.getPackageName()+"/assets/" + SONGS_BASE_DIR + mediaId;
    }

    private static final String getAlbumArttUri(String album, boolean thumbnail) {
        album = album.replaceAll("[^\\w]+", "_").toLowerCase();
        return "file:///android_asset/" + ALBUM_ARTS_BASE_DIR + File.separator
                + album + (thumbnail ? ALBUM_ART_THUMBNAIL_SUFFIX : "") + ".png";
    }

    public static List<MediaBrowser.MediaItem> getMusic(Context ctx, String parentMediaId) {
        List<MediaBrowser.MediaItem> result = new ArrayList<>();
        try {
            String[] files = ctx.getAssets().list(SONGS_BASE_DIR + parentMediaId);
            for (String file : files) {
                String mediaId = parentMediaId + File.separator + file;

                if (file.endsWith(".mp3")) {
                    MediaDescription media = createMediaDescription(ctx, mediaId);
                    result.add(new MediaBrowser.MediaItem(media,
                            MediaBrowser.MediaItem.FLAG_PLAYABLE));
                } else {
                    MediaDescription media = new MediaDescription.Builder()
                            .setMediaId(mediaId)
                            .setTitle(file)
                            .build();
                    result.add(new MediaBrowser.MediaItem(media,
                            MediaBrowser.MediaItem.FLAG_BROWSABLE));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static List<MediaSession.QueueItem> getAllSongs(Context ctx) {
        return getAllSongs(ctx, getRoot());
    }

    public static List<MediaSession.QueueItem> getAllSongs(Context ctx, String dir) {
        List<MediaSession.QueueItem> result = new ArrayList<>();
        getAllSongsImpl(ctx, result, dir);
        return result;
    }

    private static void getAllSongsImpl(Context ctx, List<MediaSession.QueueItem> result,
                                       String dir) {
        try {
            String[] files = ctx.getAssets().list(SONGS_BASE_DIR + dir);
            for (String file : files) {
                String mediaId = dir + File.separator + file;

                if (file.endsWith(".mp3")) {
                    result.add(new MediaSession.QueueItem(createMediaDescription(ctx, mediaId),
                            result.size() + 1));
                } else {
                    getAllSongsImpl(ctx, result, mediaId);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static MediaDescription createMediaDescription(Context ctx, String mediaId) {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();

        try (AssetFileDescriptor fd = ctx.getAssets().openFd(getSongFilename(mediaId))) {
            metadataRetriever.setDataSource(fd.getFileDescriptor());
            String title = metadataRetriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = metadataRetriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = metadataRetriever

                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            MediaDescription media = new MediaDescription.Builder()
                    .setMediaId(mediaId)
                    .setTitle(title)
                    .setSubtitle(artist)
                    .setIconUri(Uri.parse(getAlbumArttUri(album, true)))
                    .build();
            return media;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static MediaMetadata getMediaMetadata(Context ctx, String mediaId) {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(getSongUri(ctx, mediaId));
        String duration = metadataRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        String title = metadataRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        String artist = metadataRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        String album = metadataRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        String genre = metadataRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);

        return new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, mediaId)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, Long.parseLong(duration))
                .putString(MediaMetadata.METADATA_KEY_GENRE, genre)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, getAlbumArttUri(album, true))
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .build();
    }

}
