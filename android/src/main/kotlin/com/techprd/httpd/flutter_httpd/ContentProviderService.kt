package com.techprd.httpd.flutter_httpd

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import org.json.JSONException
import org.json.JSONObject
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class ContentProviderService(private val externalContentUri: Uri, private val queryType: QueryType) {

    private var dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    private var context: Context? = null
    private var whereClause = ""
    private var selectionArgs: Array<String>? = null
    private var sortOrder = ""
    private var limit = 0
    private var offset = 0
    private lateinit var storageUtils: StorageUtils

    init {
        dateFormatter.timeZone = TimeZone.getTimeZone("UTC")
    }

    fun setContext(context: Context): ContentProviderService {
        this.context = context
        return this
    }

    fun setWhereClause(whereClause: String): ContentProviderService {
        this.whereClause = whereClause
        return this
    }

    fun setSelectionArgs(selectionArgs: Array<String>): ContentProviderService {
        this.selectionArgs = selectionArgs
        return this
    }

    fun setSortOrder(sortOrder: String): ContentProviderService {
        this.sortOrder = sortOrder
        return this
    }

    fun setLimit(limit: Int): ContentProviderService {
        this.limit = limit
        return this
    }

    fun setOffset(offset: Int): ContentProviderService {
        this.offset = offset
        return this
    }

    fun setStorageUtils(storageUtils: StorageUtils): ContentProviderService {
        this.storageUtils = storageUtils
        return this
    }

    @Throws(JSONException::class)
    fun queryContentProvider(): JSONObject {

        val columns = Utils.getQueryColumnsByType(queryType)
        val columnKeys = Utils.getColumnsKeys(columns)
        val columnValues = Utils.getColumnsValues(columns)
        val cursor = getQueryCursor(columnValues, columns)

        val buffer = JSONArray()
        val output = JSONObject()
        val metadata = JSONObject()
        if (limit > 0) {
            if (cursor!!.move(offset)) {
                do {
                    populateBuffer(columns, columnKeys, cursor, buffer)
                    cursor.moveToNext()
                } while (!cursor.isAfterLast && limit + offset > cursor.position)
                metadata.put("count", cursor.count)
                metadata.put("position", cursor.position)
                cursor.close()
            }
        } else {
            if (cursor!!.moveToFirst()) {
                do {
                    populateBuffer(columns, columnKeys, cursor, buffer)
                } while (cursor.moveToNext())
                metadata.put("count", cursor.count)
                metadata.put("position", cursor.position)
                cursor.close()
            }
        }

        output.put("data", buffer)
        output.put("metadata", metadata)
        return output
    }

    private fun getQueryCursor(columnValues: ArrayList<String>, columns: JSONObject): Cursor? {
        return context!!.contentResolver.query(
                externalContentUri,
                columnValues.toArray(arrayOfNulls<String>(columns.length())),
                whereClause, selectionArgs, sortOrder)
    }

    @Throws(JSONException::class)
    private fun populateBuffer(columns: JSONObject,
                               columnNames: ArrayList<String>,
                               cursor: Cursor,
                               buffer: JSONArray) {
        val item = getContent(columns, columnNames, cursor)
        when {
            item.has(MediaStore.Images.ImageColumns.MINI_THUMB_MAGIC) -> when {
                externalContentUri === MediaStore.Video.Media.EXTERNAL_CONTENT_URI -> {
                    val video = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DATA))
                    getVideoThumbnail(item, video)
                }
                else -> {
                    val imageId = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media._ID))
                    getImageThumbnail(context!!, item, imageId)
                }
            }
            else -> when {
                queryType === QueryType.VIDEO -> {
                    val video = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DATA))
                    getVideoThumbnail(item, video)
                }
                queryType === QueryType.PHOTO -> {
                    val imageId = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME))
                    generateThumbnail(imageId, item)
                }
            }
        }
        buffer.put(item)
    }

    private fun generateThumbnail(imageId: String?, item: JSONObject) {
        val thumbnailFile = File(
                context!!.getExternalFilesDir("thumbnails"), imageId)

        try {

            if (!thumbnailFile.exists()) {
                val thumbnail = ThumbnailUtils.extractThumbnail(
                        BitmapFactory.decodeFile(item.get("nativeURL").toString()),
                        200, 200)
                if (thumbnail != null) {
                    val fos = FileOutputStream(thumbnailFile)
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    fos.flush()
                    fos.close()
                    thumbnail.recycle()
                }
            }

            item.put("thumbnail", thumbnailFile.absolutePath)

        } catch (ioException: IOException) {
            ioException.printStackTrace()
        }
    }

    @Throws(JSONException::class)
    private fun getContent(columns: JSONObject, columnNames: ArrayList<String>, cursor: Cursor): JSONObject {
        val item = JSONObject()
        columnNames.forEach { column ->
            val columnIndex = cursor.getColumnIndex(columns.get(column).toString())

            when {
                column.startsWith("int.") -> {
                    item.put(column.substring(4), cursor.getInt(columnIndex))
                    when {
                        column.substring(4) == "width" && item.getInt("width") == 0 ->
                            Log.e("ContentProviderService", "cursor: $cursor.getInt(columnIndex)")
                    }
                }
                column.startsWith("float.") ->
                    item.put(column.substring(6), cursor.getFloat(columnIndex))
                column.startsWith("date.") -> {
                    val intDate = cursor.getLong(columnIndex)
                    val date = Date(intDate)
                    item.put(column.substring(5), dateFormatter.format(date))
                }
                else -> item.put(column, cursor.getString(columnIndex))
            }
        }

        return item
    }

    @Throws(JSONException::class)
    private fun getImageThumbnail(context: Context, item: JSONObject, imageId: Int) {

        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(
                MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Images.Thumbnails.IMAGE_ID + "=?",
                arrayOf(imageId.toString()), null)
        if (cursor!!.count > 0) {
            cursor.moveToFirst()
            val thumbnail = cursor.getString(0)
            item.put("thumbnail", thumbnail)
            cursor.close()
        }
    }

    @Throws(JSONException::class)
    private fun getVideoThumbnail(item: JSONObject, url: String) {
        val query = hashMapOf<String, Any>()
        query["path"] = url
        query["type"] = "FILE_TYPE.VIDEO"
        val videoThumbnail = this.storageUtils.getThumbnailPath(query)
        item.put("thumbnail", videoThumbnail).toString()
    }
}