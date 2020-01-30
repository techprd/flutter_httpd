package com.techprd.httpd.flutter_httpd

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.*


class StorageUtils(val context: Context) {

    private var rootDirectory: String? = null

    init {
        context.getExternalFilesDirs(null).forEach { file ->
            rootDirectory = file?.parentFile?.parentFile?.parentFile?.parentFile?.absolutePath
        }
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
                Log.e("StorageDetails", "Failed to get storage details")
            }
        }

        return storageDetails.map { it.toHashMap() }
    }

    fun getMediaStorageDetails(): List<HashMap<String, Any?>> {
        return when {
            !rootDirectory.isNullOrEmpty() -> {
                arrayListOf(
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
            }
            else -> {
                arrayListOf()
            }
        }
    }

    private fun buildStorageDetail(file: File): StorageDetail {

        val statFs = StatFs(file.path)
        val size: Long = FileUtils.sizeOfDirectory(file)
        return StorageDetail(
                file.name,
                file.absolutePath,
                file.parent,
                size,
                statFs.totalBytes,
                statFs.freeBytes
        )
    }

}