package com.techprd.httpd.flutter_httpd

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class FileLibraryService(private val context: Context) {
    private val photoAlbumProvider: ContentProviderService =
            ContentProviderService(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, QueryType.PHOTO_ALBUM)
    private val photoProvider =
            ContentProviderService(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, QueryType.PHOTO)
    private val videoAlbumProvider =
            ContentProviderService(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, QueryType.VIDEO_ALBUM)
    private val videoProvider =
            ContentProviderService(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, QueryType.VIDEO)
    private val musicAlbumProvider =
            ContentProviderService(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, QueryType.MUSIC_ALBUM)
    private val musicProvider =
            ContentProviderService(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, QueryType.MUSIC)
    private val musicAlbumCoverProvider =
            ContentProviderService(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, QueryType.MUSIC_ALBUM_COVER)
    private val recentFilesProvider =
            ContentProviderService(MediaStore.Files.getContentUri("external"), QueryType.RECENT_FILE)

    val storageUtils: StorageUtils = StorageUtils.getInstance(context)!!

    companion object {
        private var instance: FileLibraryService? = null

        fun getInstance(context: Context): FileLibraryService? {
            if (instance == null) {
                synchronized(FileLibraryService::class.java) {
                    if (instance == null) {
                        instance = FileLibraryService(context)
                    }
                }
            }
            return instance
        }
    }


    @Throws(JSONException::class)
    fun getPhotoAlbums(): JSONObject {
        return photoAlbumProvider.setContext(context)
                .setWhereClause("1) GROUP BY 1,(2")
                .setSortOrder(MediaStore.Images.Media.DATE_TAKEN + " DESC")
                .queryContentProvider()
    }

    @Throws(JSONException::class)
    fun getVideoAlbums(): JSONObject {
        return videoAlbumProvider.setContext(context)
                .setWhereClause("1) GROUP BY 1,(2")
                .setSortOrder(MediaStore.Video.Media.DATE_TAKEN + " DESC")
                .queryContentProvider()
    }

    @Throws(JSONException::class)
    fun getMusicAlbums(): JSONObject {
        return musicAlbumProvider.setContext(context)
                .setWhereClause("1) GROUP BY 1,(2")
                .setSortOrder(MediaStore.Audio.AlbumColumns.ALBUM_ID + " DESC")
                .queryContentProvider()
    }

    @Throws(JSONException::class)
    fun getPhotos(album: String, limit: Int, offset: Int): JSONObject {
        return photoProvider.setContext(context)
                .setWhereClause(MediaStore.Images.Media.BUCKET_DISPLAY_NAME + "=?")
                .setSelectionArgs(arrayOf(album))
                .setSortOrder(MediaStore.Images.Media.DATE_TAKEN + " DESC ")
                .setLimit(limit)
                .setOffset(offset)
                .queryContentProvider()
    }

    @Throws(JSONException::class)
    fun getVideos(album: String, limit: Int, offset: Int): JSONObject {
        return videoProvider.setContext(context)
                .setWhereClause(MediaStore.Video.Media.BUCKET_DISPLAY_NAME + "=?")
                .setSelectionArgs(arrayOf(album))
                .setSortOrder(MediaStore.Video.Media.DATE_TAKEN + " DESC ")
                .setLimit(limit)
                .setOffset(offset)
                .queryContentProvider()
    }

    @Throws(JSONException::class)
    fun getMusics(): JSONObject {
        return musicProvider.setContext(context)
                .setWhereClause(MediaStore.Audio.Media.IS_MUSIC + "!= 0")
                .setSortOrder(MediaStore.Audio.Media.DATE_ADDED + " DESC")
                .queryContentProvider()
    }

    @Throws(JSONException::class)
    fun getMusicAlbumCover(albumId: String): JSONObject {
        if (Build.VERSION.SDK_INT > 29) {
            val albumCover = storageUtils.generateAlbumArtPath(albumId.toLong())
            val obj = JSONObject()
            val arr = JSONArray()
            arr.put(JSONObject().put("album_art", albumCover))
            obj.put("data", arr)
            return obj
        }
        return musicAlbumCoverProvider.setContext(context)
                .setWhereClause(MediaStore.Audio.Albums._ID + " = ?")
                .setSelectionArgs(arrayOf(albumId))
                .queryContentProvider()
    }

    @Throws(JSONException::class)
    fun getRecentFiles(limit: Int, offset: Int): JSONObject {
        return recentFilesProvider.setContext(context)
                .setSortOrder(MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC ")
                .setWhereClause(MediaStore.Files.FileColumns.MIME_TYPE + "!= 'application/octet-stream'")
                .setOffset(offset)
                .setLimit(limit)
                .queryContentProvider()
    }
}