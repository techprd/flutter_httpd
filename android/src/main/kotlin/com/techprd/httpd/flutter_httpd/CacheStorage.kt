package com.techprd.httpd.flutter_httpd

import android.content.Context
import android.content.SharedPreferences

class CacheStorage(context: Context) {

    private val storageName = "TechPrdFileTransfer"
    private var preferences: SharedPreferences = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)

    companion object {
        private var instance: CacheStorage? = null

        fun getInstance(context: Context): CacheStorage? {
            if (instance == null) {
                synchronized(CacheStorage::class.java) {
                    if (instance == null) {
                        instance = CacheStorage(context)
                    }
                }
            }
            return instance
        }
    }

    fun storeThumbnailPath(filePath: String, thumbnailPath: String) {
        val editor = preferences.edit().putString(filePath, thumbnailPath)
        editor.apply()
        editor.commit()
    }

    fun getThumbnailPath(filePath: String): String? {
        return preferences.getString(filePath, null)
    }
}