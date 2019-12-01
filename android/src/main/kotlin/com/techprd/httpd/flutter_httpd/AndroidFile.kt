package com.techprd.httpd.flutter_httpd

import android.content.res.AssetManager
import android.util.Log

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Date

class AndroidFile : File {

    private val logTag = "AndroidFile"

    private var filePath = ""

    var assetManager: AssetManager? = null
    var dir: AndroidFile? = null

    companion object {
        private const val serialVersionUID = 1L
    }

    init {
        filePath = this.path

    }

    constructor(name: String) : super(name) {
        this.filePath = this.path
    }

    constructor(dir: AndroidFile, name: String) : super(dir, name) {
        this.dir = dir
        this.filePath = this.path
        assetManager = dir.assetManager
    }

    private val isAsset: Boolean
        get() = (assetManager != null) && (!filePath.startsWith("/"))

    val inputStream: InputStream
        @Throws(IOException::class)
        get() = if (isAsset) {
            assetManager!!.open(filePath)
        } else FileInputStream(this)

    override fun isDirectory(): Boolean {
        if (isAsset) {
            return try {
                val files = assetManager!!.list(filePath)

                // if filePath is a file, no IO exception, so we judge the number of files
                // so when we get a empty folder, it might be a problem.
                files!!.isNotEmpty()

            } catch (e: IOException) {
                false
            }
        }

        return super.isDirectory()
    }

    override fun isFile(): Boolean {
        if (isAsset) {
            return try {
                val inputStream = assetManager!!.open(filePath)
                inputStream.close()
                true
            } catch (e: IOException) {
                false
            }
        }

        return super.isFile()
    }

    override fun exists(): Boolean {
        return if (isAsset) {
            isFile || isDirectory
        } else super.exists()
    }

    override fun canRead(): Boolean {
        return if (isAsset) {
            isFile || isDirectory
        } else super.canRead()

    }

    override fun list(): Array<String>? {
        if (isAsset) {
            try {
                return assetManager!!.list(filePath)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return arrayOf()
        }

        return super.list()
    }

    @Throws(IOException::class)
    override fun getCanonicalPath(): String {
        return if (isAsset) {
            filePath
        } else super.getCanonicalPath()

    }

    override fun getAbsolutePath(): String {
        return if (isAsset) {
            filePath
        } else super.getAbsolutePath()

    }

    override fun lastModified(): Long {
        if (isAsset) {
            val now = Date()
            return now.time - 1000 * 3600 * 24 // 24 hour ago
        }

        return super.lastModified()
    }

    override fun length(): Long {
        if (isAsset) {
            var len: Long = 0
            try {
                val inputStream = assetManager!!.open(filePath)
                len = inputStream.available().toLong()
                inputStream.close()
            } catch (e: IOException) {
                Log.w(logTag, String.format("IOException: %s", e.message))
            }

            return len
        }

        return super.length()
    }
}
