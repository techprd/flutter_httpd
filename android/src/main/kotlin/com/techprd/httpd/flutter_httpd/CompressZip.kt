package com.techprd.httpd.flutter_httpd

import android.util.Log

import org.json.JSONObject

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CompressZip(options: JSONObject) {

    private val sourceEntry: String = options.optString("sourceEntry")
    private val targetPath = options.optString("targetPath")
    private val sourcePath = options.optString("sourcePath")
    private val sourceName = options.optString("sourceName")
    private val targetName = if (options.optString("name").isEmpty()) sourceName else options.optString("name")

    /**
     * Public access to the main class function
     *
     * @return true if none exception occurs
     */
    fun zip(): Boolean {
        try {
            this.makeZip(targetPath + this.targetName + ".zip", this.sourceEntry)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }

    @Throws(Exception::class)
    private fun makeZip(zipFileName: String, dir: String) {
        val dirObj = File(dir)
        val out = ZipOutputStream(FileOutputStream(zipFileName))
        Log.d("CompressZip Log: ", "Making Zip : $zipFileName")
        if (dirObj.isDirectory) {
            this.addDir(dirObj, out)
        } else {
            this.addFile(dirObj, out)
        }
        out.close()
    }

    /**
     * A convenient method to add the elements in a folder, just call the file zip function when is needit
     *
     * @param dirObj Path to folder (In file object)
     * @param out    Output stream in construction
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun addDir(dirObj: File, out: ZipOutputStream) {
        val files = dirObj.listFiles()

        for (file in files) {
            if (file.isDirectory) {
                addDir(file, out)
                continue
            } else {
                this.addFile(file, out)
            }
        }
    }

    /**
     * Add the file to the zip archive
     *
     * @param toZip Path to file (In file object)
     * @param out    Output stream in construction
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun addFile(toZip: File, out: ZipOutputStream) {
        val bufferSize = 2048
        val tmpBuf = ByteArray(bufferSize)

        val inputStream = FileInputStream(toZip.absolutePath)
        Log.d("CompressZip Log: ", " Adding To Archive: " + toZip.absolutePath)
        val zipEntryPath = toZip.absolutePath.replace(this.sourcePath, "")
        out.putNextEntry(ZipEntry(zipEntryPath))
        var len: Int = inputStream.read(tmpBuf)
        while (len > 0) {
            out.write(tmpBuf, 0, len)
            len = inputStream.read(tmpBuf)
        }
        out.closeEntry()
        inputStream.close()
    }
}


