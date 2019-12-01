package com.techprd.httpd.flutter_httpd

import org.json.JSONObject

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class DecompressZip(opts: JSONObject) {

    private val sourceEntry = opts.optString("sourceEntry")
    private val targetPath = opts.optString("targetPath")

    fun unZip(): Boolean {
        var result = false
        try {
            result = this.doUnZip(this.targetPath)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return result
    }

    /**
     * Extracts a zip file to a given path
     *
     * @param actualTargetPath Path to un-zip
     * @throws IOException
     */
    @Throws(IOException::class)
    fun doUnZip(actualTargetPath: String): Boolean {
        val target = File(actualTargetPath)
        if (!target.exists()) {
            target.mkdir()
        }

        val zipFl = ZipInputStream(FileInputStream(this.sourceEntry))
        var entry: ZipEntry? = zipFl.nextEntry

        while (entry != null) {
            val filePath = actualTargetPath + File.separator + entry.name
            if (entry.isDirectory) {
                val path = File(filePath)
                path.mkdir()
            } else {
                extractFile(zipFl, filePath)
            }
            zipFl.closeEntry()
            entry = zipFl.nextEntry
        }
        zipFl.close()
        return true
    }

    /**
     * Extracts a file
     *
     * @param zipFl
     * @param filePath
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun extractFile(zipFl: ZipInputStream, filePath: String) {
        val buffer = BufferedOutputStream(FileOutputStream(filePath))
        val bufferSize = 2048
        val bytesIn = ByteArray(bufferSize)
        var read = zipFl.read(bytesIn)
        while (read != -1) {
            buffer.write(bytesIn, 0, read)
            read = zipFl.read(bytesIn)
        }
        buffer.close()
    }
}
