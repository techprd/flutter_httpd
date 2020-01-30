package com.techprd.httpd.flutter_httpd

import java.util.HashMap

data class StorageDetail(
        val name: String, val dir: String, val rootDir: String?,
        val size: Long,
        val totalBytes: Long, val availableBytes: Long) {
    fun toHashMap(): HashMap<String, Any?> {
        return hashMapOf(
                "name" to name,
                "path" to dir,
                "rootPath" to rootDir,
                "size" to size,
                "availableBytes" to availableBytes,
                "totalBytes" to totalBytes
        )
    }
}