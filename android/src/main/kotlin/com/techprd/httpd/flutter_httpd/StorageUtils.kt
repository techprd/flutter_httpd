package com.techprd.httpd.flutter_httpd

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException


class StorageUtils(private val context: Context) {

    private var rootDirectory: String? = null
    private val cachedStorage: CacheStorage

    init {
        context.getExternalFilesDirs(null).forEach { file ->
            rootDirectory = file?.parentFile?.parentFile?.parentFile?.parentFile?.absolutePath
        }
        cachedStorage = CacheStorage.getInstance(context)!!
    }

    fun getExternalStorageDetails(): List<HashMap<String, Any?>> {
        val storageDetails = arrayListOf<StorageDetail>()
        for ((index, file) in context.getExternalFilesDirs(null).withIndex()) {
            val statFs = StatFs(file.path)
            try {
                val rootPath = file?.parentFile?.parentFile?.parentFile?.parentFile?.absolutePath
                val dirName = when (index) {
                    0 -> "Root"
                    1 -> "SD Card"
                    else -> file.name
                }
                val storageData =
                        StorageDetail(dirName,
                                file.absolutePath, rootPath, 0, statFs.totalBytes, statFs.freeBytes)
                storageDetails.add(storageData)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("StorageDetails", "Failed to get storage details")
            }
        }

        return storageDetails.map { it.toHashMap() }
    }

    fun getMediaStorageDetails(): List<HashMap<String, Any?>> {
        when {
            !rootDirectory.isNullOrEmpty() -> {
                try {
                    return arrayListOf(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath,
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath,
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath,
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
                    ).filter {
                        val file = File(it)
                        file.exists()
                    }.map {
                        buildStorageDetail(File(it)).toHashMap()
                    }
                } catch (ex: Exception) {
                    Log.e("FileTransfer", "Failed to get a list of common android dirs")
                    return arrayListOf()
                }
            }
            else -> {
                return arrayListOf()
            }
        }
    }

    private fun buildStorageDetail(file: File): StorageDetail {
        val statFs = StatFs(file.path)
        try {
            val size: Long = FileUtils.sizeOfDirectory(file)
            return StorageDetail(
                    file.name,
                    file.absolutePath,
                    file.parent,
                    size,
                    statFs.totalBytes,
                    statFs.freeBytes
            )
        } catch (ex: NoSuchMethodError) {
            return StorageDetail(
                    file.name,
                    file.absolutePath,
                    file.parent,
                    0,
                    statFs.totalBytes,
                    statFs.freeBytes
            )
        }
    }

    fun getThumbnailPath(inputs: HashMap<String, Any>): String {
        val filePath = inputs["path"] as String
        val file = File(filePath)
        var thumbnailPath = cachedStorage.getThumbnailPath(filePath).orEmpty()
        if (thumbnailPath.isNotEmpty()) {
            return thumbnailPath
        }
        when (inputs["type"] as String) {
            "FILE_TYPE.VIDEO" -> {
                val videoId = getVideoId(filePath)
                thumbnailPath = if (videoId != null) {
                    getVideoThumbnailByVideoId(videoId.toString()).run {
                        if (this.isNullOrEmpty()) {
                            generateVideoThumbnail(file.nameWithoutExtension, filePath).orEmpty()
                        } else {
                            this
                        }
                    }
                } else {
                    generateVideoThumbnail(file.nameWithoutExtension, filePath).orEmpty()
                }
                cachedStorage.storeThumbnailPath(filePath, thumbnailPath)
            }
            "FILE_TYPE.AUDIO" -> {
                val audioId = getAudioAlbumId(filePath)
                if (audioId != null) {
                    thumbnailPath = generateAlbumArtPath(audioId).orEmpty()
                }
                cachedStorage.storeThumbnailPath(filePath, thumbnailPath)
            }
            "FILE_TYPE.IMAGE" -> {
                if (filePath.contains("/files/thumbnails/")) {
                    return filePath
                } else if (filePath.contains(".thumbnails")) {
                    return filePath
                }
                val imageId = getImageId(filePath)
                thumbnailPath = if (imageId != null) {
                    getImageThumbnailByImageId(imageId.toString()).run {
                        if (this.isNullOrEmpty()) {
                            generateImageThumbnail(file.nameWithoutExtension, filePath).orEmpty()
                        } else {
                            this
                        }
                    }
                } else {
                    generateImageThumbnail(file.nameWithoutExtension, filePath).orEmpty()
                }
                cachedStorage.storeThumbnailPath(filePath, thumbnailPath)
            }
        }
        return thumbnailPath
    }

    fun getVideoThumbnailByVideoId(videoID: String): String? {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        val cursor = context.contentResolver.query(
                MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Video.Thumbnails.VIDEO_ID + "=?",
                arrayOf(videoID), null)
        if (cursor!!.count > 0) {
            cursor.moveToFirst()
            val thumbnail = cursor.getString(0)
            cursor.close()
            return thumbnail
        }
        return null
    }

    fun generateVideoThumbnail(name: String, path: String): String? {

        val thumbnailFile = File(
                context.getExternalFilesDir("thumbnails"), "$name.jpg")
        try {
            if (!thumbnailFile.exists()) {
                val thumbnail = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
                if (thumbnail != null) {
                    val fos = FileOutputStream(thumbnailFile)
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                    fos.flush()
                    fos.close()
                    thumbnail.recycle()
                }
            }
            return thumbnailFile.absolutePath
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            return null
        }
    }

    fun getImageThumbnailByImageId(imageId: String): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(
                MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Images.Thumbnails.IMAGE_ID + "=?",
                arrayOf(imageId), null)
        if (cursor!!.count > 0) {
            cursor.moveToFirst()
            val thumbnail = cursor.getString(0)
            cursor.close()
            return thumbnail
        }
        return null
    }

    fun getVideoId(filePath: String): Long? {
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Video.Media.DATA + "=?",
                arrayOf(filePath), null)
        if (cursor!!.count > 0) {
            cursor.moveToFirst()
            val videoID = cursor.getLong(0)
            cursor.close()
            return videoID
        }
        return null
    }

    fun getImageId(filePath: String): Long? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Images.Media.DATA + "=?",
                arrayOf(filePath), null)
        if (cursor!!.count > 0) {
            cursor.moveToFirst()
            val imageId = cursor.getLong(0)
            cursor.close()
            return imageId
        }
        return null
    }

    fun getAudioAlbumId(filePath: String): Long? {
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
        val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Audio.Media.DATA + "=?",
                arrayOf(filePath), null)
        if (cursor!!.count > 0) {
            cursor.moveToFirst()
            val albumId = cursor.getLong(0)
            cursor.close()
            return albumId
        }
        return null
    }

    fun getAlbumArt(albumId: Long): Bitmap? {
        val sArtworkUri = Uri
                .parse("content://media/external/audio/albumart")
        val albumArtUri = ContentUris.withAppendedId(sArtworkUri, albumId)
        try {
            val uri = MediaStore.Images.Media.getBitmap(
                    context.contentResolver, albumArtUri)
            return Bitmap.createScaledBitmap(uri, 200, 200, true)
        } catch (exception: FileNotFoundException) {
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun generateAlbumArtPath(albumId: Long): String? {
        val parent = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File(parent, "${albumId}.jpg")
        if (!file.exists()) {
            val albumArt = getAlbumArt(albumId) ?: return null
            try {
                file.createNewFile()
                FileOutputStream(file).use { out ->
                    albumArt.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return file.absolutePath
    }

    fun generateImageThumbnail(name: String, path: String): String? {
        val thumbnailFile = File(
                context.getExternalFilesDir("thumbnails"), "$name.jpg")
        try {
            if (!thumbnailFile.exists()) {
                val thumbnail = ThumbnailUtils.extractThumbnail(
                        BitmapFactory.decodeFile(path),
                        200, 200)
                if (thumbnail != null) {
                    val fos = FileOutputStream(thumbnailFile)
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    fos.flush()
                    fos.close()
                    thumbnail.recycle()
                }
            }
            return thumbnailFile.absolutePath
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            return null
        }
    }
}