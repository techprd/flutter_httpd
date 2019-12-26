package com.techprd.httpd.flutter_httpd

import android.content.Context
import android.os.StatFs


class StorageUtils {

    fun getExternalStorageAvailableData(context: Context): ArrayList<Any> {
        val appsDir = context.getExternalFilesDirs(null)
        val extRootPaths = arrayListOf<Any>()
        for (file in appsDir) {
            val path = file.absolutePath
            val statFs = StatFs(path)
            val totalBytes = statFs.totalBytes
            val availableBytes = statFs.freeBytes
            val storageData = hashMapOf<String, Any?>()
            try {
                val rootPath = file?.parentFile?.parentFile?.parentFile?.parentFile?.absolutePath
                storageData["rootPath"] = rootPath
            } catch (e: Exception) {
            }

            storageData["path"] = path
            storageData["totalBytes"] = totalBytes
            storageData["availableBytes"] = availableBytes
            extRootPaths.add(storageData)
        }
        return extRootPaths
    }


}