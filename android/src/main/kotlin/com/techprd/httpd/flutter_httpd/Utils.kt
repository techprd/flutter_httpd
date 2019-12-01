package com.techprd.httpd.flutter_httpd

import org.json.JSONException
import org.json.JSONObject
import android.provider.MediaStore


class Utils {

    companion object {
        @Throws(JSONException::class)
        fun getQueryColumnsByType(type: QueryType): JSONObject {
            return when (type) {
                QueryType.PHOTO_ALBUM -> getPhotoAlbumColumns()
                QueryType.PHOTO -> getPhotoColumns()
                QueryType.VIDEO_ALBUM -> getVideoAlbumColumns()
                QueryType.VIDEO -> getVideoColumns()
                QueryType.MUSIC_ALBUM_COVER -> getMusicAlbumCoverColumns()
                QueryType.MUSIC_ALBUM -> getMusicAlbumColumns()
                QueryType.MUSIC -> getMusicColumns()
            }
        }

        fun getColumnsKeys(columns: JSONObject): ArrayList<String> {
            val columnKeys = arrayListOf<String>()
            val iteratorFields = columns.keys()
            while (iteratorFields.hasNext()) {
                val column = iteratorFields.next()
                columnKeys.add(column)
            }

            return columnKeys
        }

        @Throws(JSONException::class)
        fun getColumnsValues(columns: JSONObject): ArrayList<String> {
            val columnValues = arrayListOf<String>()
            val iteratorFields = columns.keys()
            while (iteratorFields.hasNext()) {
                val column = iteratorFields.next()
                columnValues.add("" + columns.getString(column))
            }
            return columnValues
        }

        @Throws(JSONException::class)
        private fun getPhotoAlbumColumns(): JSONObject {
            return object : JSONObject() {
                init {
                    put("id", MediaStore.Images.ImageColumns.BUCKET_ID)
                    put("title", MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                }
            }
        }

        @Throws(JSONException::class)
        private fun getVideoAlbumColumns(): JSONObject {
            return object : JSONObject() {
                init {
                    put("id", MediaStore.Video.VideoColumns.BUCKET_ID)
                    put("title", MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME)
                }
            }
        }

        @Throws(JSONException::class)
        private fun getMusicAlbumColumns(): JSONObject {
            return object : JSONObject() {
                init {
                    put("id", MediaStore.Audio.AlbumColumns.ALBUM_ID)
                    put("album", MediaStore.Audio.AlbumColumns.ALBUM)
                    put("artist", MediaStore.Audio.AlbumColumns.ARTIST)
                }
            }
        }

        @Throws(JSONException::class)
        private fun getPhotoColumns(): JSONObject {
            return object : JSONObject() {
                init {
                    put("int.id", MediaStore.Images.Media._ID)
                    put("fileName", MediaStore.Images.ImageColumns.DISPLAY_NAME)
                    put(MediaStore.Images.ImageColumns.MINI_THUMB_MAGIC, MediaStore.Images.ImageColumns.MINI_THUMB_MAGIC)
                    put("int.width", MediaStore.Images.ImageColumns.WIDTH)
                    put("int.height", MediaStore.Images.ImageColumns.HEIGHT)
                    put("albumId", MediaStore.Images.ImageColumns.BUCKET_ID)
                    put("date.creationDate", MediaStore.Images.ImageColumns.DATE_TAKEN)
                    put("float.latitude", MediaStore.Images.ImageColumns.LATITUDE)
                    put("float.longitude", MediaStore.Images.ImageColumns.LONGITUDE)
                    put("nativeURL", MediaStore.MediaColumns.DATA) // will not be returned to javascript
                }
            }
        }

        @Throws(JSONException::class)
        private fun getVideoColumns(): JSONObject {
            return object : JSONObject() {
                init {
                    put("int.id", MediaStore.Video.Media._ID)
                    put("fileName", MediaStore.Video.VideoColumns.DISPLAY_NAME)
                    put(MediaStore.Images.ImageColumns.MINI_THUMB_MAGIC, MediaStore.Images.ImageColumns.MINI_THUMB_MAGIC)
                    put("int.width", MediaStore.Video.VideoColumns.WIDTH)
                    put("int.height", MediaStore.Video.VideoColumns.HEIGHT)
                    put("albumId", MediaStore.Video.VideoColumns.BUCKET_ID)
                    put("date.creationDate", MediaStore.Video.VideoColumns.DATE_TAKEN)
                    put("float.latitude", MediaStore.Video.VideoColumns.LATITUDE)
                    put("float.longitude", MediaStore.Video.VideoColumns.LONGITUDE)
                    put("nativeURL", MediaStore.MediaColumns.DATA)
                }
            }
        }

        @Throws(JSONException::class)
        private fun getMusicColumns(): JSONObject {
            return object : JSONObject() {
                init {
                    put("int.id", MediaStore.Audio.Media._ID)
                    put("artist", MediaStore.Audio.Media.ARTIST)
                    put("album", MediaStore.Audio.Media.ALBUM)
                    put("title", MediaStore.Audio.Media.TITLE)
                    put("albumId", MediaStore.Audio.Media.ALBUM_ID)
                    put("int.duration", MediaStore.Audio.Media.DURATION)
                    put("nativeURL", MediaStore.MediaColumns.DATA)
                    put("int.date_added", MediaStore.Audio.Media.DATE_ADDED)
                    put("display_name", MediaStore.Audio.Media.DISPLAY_NAME)
                }
            }
        }

        @Throws(JSONException::class)
        private fun getMusicAlbumCoverColumns(): JSONObject {
            return object : JSONObject() {
                init {
                    put("album_art", MediaStore.Audio.Albums.ALBUM_ART)
                    put("album_id", MediaStore.Audio.Albums._ID)
                }
            }
        }
    }

}