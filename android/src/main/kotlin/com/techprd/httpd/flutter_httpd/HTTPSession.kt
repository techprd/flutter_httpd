package com.techprd.httpd.flutter_httpd

import android.util.Log
import com.techprd.httpd.flutter_httpd.Statics.HTTP_BAD_REQUEST
import com.techprd.httpd.flutter_httpd.Statics.HTTP_INTERNAL_ERROR
import com.techprd.httpd.flutter_httpd.Statics.MIME_PLAINTEXT
import java.io.*
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.min


/**
 * Handles one session, i.e. parses the HTTP request
 * and returns the response.
 */
class HTTPSession(private val nanoHTTPD: NanoHTTPD, private val mySocket: Socket) : Runnable {

    private val logTag = "HTTPSession: "
    private lateinit var inputStream: BufferedInputStream

    companion object {
        const val BUFFER_SIZE = 8192
        const val MEMORY_STORE_LIMIT = 20000000
        const val MAX_HEADER_SIZE = 1024
        const val REQUEST_BUFFER_LEN = 1024 * 16
        private const val CONTENT_DISPOSITION_REGEX = "([ |\t]*Content-Disposition[ |\t]*:)(.*)"
        val CONTENT_DISPOSITION_PATTERN: Pattern = Pattern.compile(CONTENT_DISPOSITION_REGEX, Pattern.CASE_INSENSITIVE)
        private const val CONTENT_DISPOSITION_ATTRIBUTE_REGEX = "[ |\t]*([a-zA-Z]*)[ |\t]*=[ |\t]*['|\"]([^\"^']*)['|\"]"
        val CONTENT_DISPOSITION_ATTRIBUTE_PATTERN: Pattern = Pattern.compile(CONTENT_DISPOSITION_ATTRIBUTE_REGEX)
        private const val CONTENT_TYPE_REGEX = "([ |\t]*content-type[ |\t]*:)(.*)"
        val CONTENT_TYPE_PATTERN: Pattern = Pattern.compile(CONTENT_TYPE_REGEX, Pattern.CASE_INSENSITIVE)
    }

    init {
        val t = Thread(this)
        t.isDaemon = true
        t.start()
    }

    override fun run() {
        try {
            val inStream = mySocket.getInputStream() ?: return
            inputStream = BufferedInputStream(inStream, BUFFER_SIZE)

            // Read the first 8192 bytes.
            // The full header should fit in here.
            // Apache's default header limit is 8KB.
            // Do NOT assume that a single read will get the entire header at once!
            val buf = ByteArray(BUFFER_SIZE)
            var splitByte = 0
            var rLen = 0

            var read: Int
            inputStream.mark(BUFFER_SIZE)

            read = try {
                inputStream.read(buf, 0, BUFFER_SIZE)
            } catch (e: IOException) {
                inputStream.close()
                inStream.close()
                throw SocketException("NanoHttpd Shutdown")
            }
            if (read == -1) {
                // socket was been closed
                inputStream.close()
                inStream.close()
                throw SocketException("NanoHttpd Shutdown")
            }

            while (read > 0) {
                rLen += read
                splitByte = findHeaderEnd(buf, rLen)
                if (splitByte > 0) {
                    break
                }
                read = this.inputStream.read(buf, rLen, BUFFER_SIZE - rLen)
            }

            if (splitByte < rLen) {
                this.inputStream.reset()
                this.inputStream.skip(splitByte.toLong())
            }

            // Create a BufferedReader for parsing the header.
            val hin = BufferedReader(InputStreamReader(ByteArrayInputStream(buf, 0, rLen)))
            val pre = Properties()
            val params = Properties()
            val header = Properties()

            // Decode the header into params and header java properties
            decodeHeader(hin, pre, params, header)

            val method = pre.getProperty("method")
            val uri = pre.getProperty("uri")

            parseBody(header, splitByte, rLen, method, uri, params)

            // if (method != null && method.equalsIgnoreCase("PUT"))
            //   files.put("filePath", saveTmpFile(fbuf, 0, f.size()));

            // Ok, now do the serve()
            if (method !== null) {
                val r = nanoHTTPD.serve(uri, header, params)
                sendResponse(r.status, r.mimeType, r.header, r.data)
            }
        } catch (ioe: IOException) {
            try {
                sendError(HTTP_INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.message)
            } catch (t: Throwable) {
                t.printStackTrace()
            }

        } catch (ie: InterruptedException) {
            // Thrown by sendError, ignore and exit the thread.
        }
    }

    private fun parseBody(header: Properties, splitByte: Int, rLen: Int,
                          method: String?, uri: String, params: Properties) {

        var randomAccessFile: RandomAccessFile? = null

        try {
            var rLen1 = rLen
            var size = calculateSize(header, splitByte, rLen)

            var byteArrayOut: ByteArrayOutputStream? = null
            val requestDataOutput: DataOutput?

            // Store the request in memory or a file, depending on size
            if (size < MEMORY_STORE_LIMIT) {
                byteArrayOut = ByteArrayOutputStream()
                requestDataOutput = DataOutputStream(byteArrayOut)
            } else {
                randomAccessFile = getTmpBucket()
                requestDataOutput = randomAccessFile
            }

            val buf = ByteArray(REQUEST_BUFFER_LEN)

            while (rLen1 >= 0 && size > 0) {
                rLen1 = inputStream.read(buf, 0, min(size, REQUEST_BUFFER_LEN))
                size -= rLen1
                if (rLen1 > 0) {
                    requestDataOutput.write(buf, 0, rLen1)
                }
            }

            val fBuf: ByteBuffer?
            if (byteArrayOut != null) {
                fBuf = ByteBuffer.wrap(byteArrayOut.toByteArray(), 0, byteArrayOut.size())
            } else {
                fBuf = randomAccessFile!!.channel.map(FileChannel.MapMode.READ_ONLY, 0, randomAccessFile.length())
                randomAccessFile.seek(0)
            }

            // If the method is POST, there may be parameters
            // in data section, too, read it:
            if (method != null && method.equals("POST", ignoreCase = true)) {
                val contentType = ContentType(header.getProperty("content-type"))

                if (contentType.isMultipart) {
                    // Handle multipart/form-data
                    val boundary = contentType.boundary
                    if (boundary == null)
                        sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html")

                    decodeMultipartData(uri, contentType, fBuf!!, params)
                } else {
                    // Handle application/x-www-form-urlencoded
                    val postBytes = ByteArray(fBuf!!.remaining())
                    fBuf.get(postBytes)
                    val postLine = String(postBytes, Charset.forName(contentType.getEncoding())).trim()
                    // Handle application/x-www-form-urlencoded
                    if ("application/x-www-form-urlencoded".equals(contentType.contentType, ignoreCase = true)) {
                        decodeParams(postLine, params)
                    }
                }
            }
        } finally {
            randomAccessFile?.close()
        }
    }

    private fun getTmpBucket(): RandomAccessFile {
        try {
            val fileName = File.createTempFile("HTTPSession", null)
            val file = File(nanoHTTPD.context?.externalCacheDir, fileName.name)
            file.createNewFile()
            return RandomAccessFile(file, "rw")
        } catch (ex: Exception) {
            throw Error(ex)
        }
    }

    private fun calculateSize(header: Properties, splitByte: Int, rLen: Int): Int {
        var size1 = 0
        val contentLength = header.getProperty("content-length")

        if (!contentLength.isNullOrEmpty()) {
            try {
                size1 = Integer.parseInt(contentLength)
            } catch (ex: NumberFormatException) {
                ex.printStackTrace()
            }

        } else if (splitByte < rLen) {
            return rLen - splitByte
        }
        return size1
    }

    /**
     * Decodes the sent headers and loads the data into
     * java Properties' key - value pairs
     */
    @Throws(InterruptedException::class)
    private fun decodeHeader(reader: BufferedReader, pre: Properties, params: Properties, header: Properties) {
        try {
            // Read the request line
            val inLine = reader.readLine() ?: return
            val st = StringTokenizer(inLine)
            if (!st.hasMoreTokens())
                sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html")

            val method = st.nextToken()
            pre["method"] = method

            if (!st.hasMoreTokens())
                sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html")

            var uri = st.nextToken()

            // Decode parameters from the URI
            val qmi = uri.indexOf('?')
            if (qmi >= 0) {
                nanoHTTPD.forceDownload = uri.endsWith("forcedownload")
                nanoHTTPD.zipDownload = uri.endsWith("zipdownload")
                decodeParams(uri.substring(qmi + 1), params)
                uri = decodePercent(uri.substring(0, qmi))
            } else
                uri = decodePercent(uri)

            // If there's another token, it's protocol version,
            // followed by HTTP headers. Ignore version but parse headers.
            // NOTE: this now forces header names lowercase since they are
            // case insensitive and vary by client.
            if (st.hasMoreTokens()) {
                var line = reader.readLine()
                while (line!!.trim { it <= ' ' }.isNotEmpty()) {
                    val p = line.indexOf(':')
                    if (p >= 0)
                        header[line.substring(0, p).trim { it <= ' ' }.toLowerCase(Locale.US)] =
                                line.substring(p + 1).trim { it <= ' ' }
                    line = reader.readLine()
                }
            }

            pre["uri"] = uri
        } catch (ioe: IOException) {
            sendError(HTTP_INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.message)
        }

    }

    /**
     * Decodes the Multipart Body data and put it
     * into java Properties' key - value pairs.
     */
    @Throws(InterruptedException::class)
    private fun decodeMultipartData(uri: String, contentType: ContentType, fBuf: ByteBuffer, params: Properties) {
        var pCount = 0
        try {
            val boundaryIndexes = getBoundaryPositions(fBuf, contentType.boundary!!.toByteArray())
            if (boundaryIndexes.size < 2) {
                return sendError(HTTP_BAD_REQUEST,
                        "BAD REQUEST: Content type is multipart/form-data but contains less than two boundary strings.")
            }
            val partHeaderBuff = ByteArray(MAX_HEADER_SIZE)
            for (boundaryIdx in 0 until boundaryIndexes.size - 1) {
                fBuf.position(boundaryIndexes[boundaryIdx])
                val len = if (fBuf.remaining() < MAX_HEADER_SIZE) fBuf.remaining() else MAX_HEADER_SIZE
                fBuf[partHeaderBuff, 0, len]
                val bufferedReader = BufferedReader(InputStreamReader(ByteArrayInputStream(partHeaderBuff, 0, len), Charset.forName(contentType.getEncoding())), len)
                var headerLines = 0
                // First line is boundary string
                var mpLine = bufferedReader.readLine()
                headerLines++
                if (mpLine == null || !mpLine.contains(contentType.boundary!!)) {
                    return sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but chunk does not start with boundary.")
                }
                var partName: String? = null
                var fileName: String? = null
                var partContentType: String? = null
                // Parse the reset of the header lines
                mpLine = bufferedReader.readLine()
                headerLines++
                while (mpLine != null && mpLine.trim { it <= ' ' }.isNotEmpty()) {
                    var matcher: Matcher = CONTENT_DISPOSITION_PATTERN.matcher(mpLine)
                    if (matcher.matches()) {
                        val attributeString: String = matcher.group(2)
                        matcher = CONTENT_DISPOSITION_ATTRIBUTE_PATTERN.matcher(attributeString)
                        while (matcher.find()) {
                            val key: String = matcher.group(1)
                            if ("name".equals(key, ignoreCase = true)) {
                                partName = matcher.group(2)
                            } else if ("filename".equals(key, ignoreCase = true)) {
                                fileName = matcher.group(2)
                                // add these two line to support multiple
                                // files uploaded using the same field Id
                                if (fileName.isNotEmpty()) {
                                    if (pCount > 0) partName += pCount++.toString() else pCount++
                                }
                            }
                        }
                    }
                    matcher = CONTENT_TYPE_PATTERN.matcher(mpLine)
                    if (matcher.matches()) {
                        partContentType = matcher.group(2).trim()
                    }
                    mpLine = bufferedReader.readLine()
                    headerLines++
                }

                var partHeaderLength = 0
                while (headerLines-- > 0) {
                    partHeaderLength = skipOverNewLine(partHeaderBuff, partHeaderLength)
                }

                // Read the part data
                if (partHeaderLength >= len - 4) {
                    sendError(HTTP_INTERNAL_ERROR, "Multipart header size exceeds MAX_HEADER_SIZE.")
                }
                val partDataStart = boundaryIndexes[boundaryIdx] + partHeaderLength
                val partDataEnd = boundaryIndexes[boundaryIdx + 1] - 4
                fBuf.position(partDataStart)
                var values = params[partName] as ArrayList<String>?
                if (values == null) {
                    values = arrayListOf("")
                    params[partName] = values
                }
                Log.d(logTag, "contentType: $partContentType")
                if (partContentType == null) { // Read the part into a string
                    val dataBytes = ByteArray(partDataEnd - partDataStart)
                    fBuf.get(dataBytes)
                    values.add(String(dataBytes, Charset.forName(contentType.getEncoding())))
                } else { // Read it into a file
                    saveTmpFile(uri, fBuf, partDataStart, partDataEnd - partDataStart, fileName!!)
                    values.add(fileName)
                }
            }
        } catch (ioe: IOException) {
            sendError(HTTP_INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.message)
        }
    }

    private fun skipOverNewLine(partHeaderBuff: ByteArray, index: Int): Int {
        var index1 = index
        while (partHeaderBuff[index1] != '\n'.toByte()) {
            index1++
        }
        return ++index1
    }

    /**
     * Find byte index separating header from body.
     * It must be the last byte of the first two sequential new lines.
     */
    private fun findHeaderEnd(buf: ByteArray, rLen: Int): Int {
        var splitByte = 0
        while (splitByte + 3 < rLen) {
            if (buf[splitByte] == '\r'.toByte() && buf[splitByte + 1] == '\n'.toByte() && buf[splitByte + 2] == '\r'.toByte() && buf[splitByte + 3] == '\n'.toByte())
                return splitByte + 4
            splitByte++
        }
        return 0
    }

    /**
     * Find the byte positions where multipart boundaries start.
     */
    private fun getBoundaryPositions(b: ByteBuffer, boundary: ByteArray): IntArray {
        var res = IntArray(0)
        if (b.remaining() < boundary.size) {
            return res
        }

        var searchWindowPos = 0
        val searchWindow = ByteArray(4 * 1024 + boundary.size)

        val firstFill = if (b.remaining() < searchWindow.size) b.remaining() else searchWindow.size
        b.get(searchWindow, 0, firstFill)
        var newBytes: Int = firstFill - boundary.size

        do { // Search the search_window
            for (j in 0 until newBytes) {
                for (i in boundary.indices) {
                    if (searchWindow[j + i] != boundary[i]) break
                    if (i == boundary.size - 1) { // Match found, add it to results
                        val newRes = IntArray(res.size + 1)
                        System.arraycopy(res, 0, newRes, 0, res.size)
                        newRes[res.size] = searchWindowPos + j
                        res = newRes
                    }
                }
            }
            searchWindowPos += newBytes
            // Copy the end of the buffer to the start
            System.arraycopy(searchWindow, searchWindow.size - boundary.size, searchWindow, 0, boundary.size)
            // Refill search_window
            newBytes = searchWindow.size - boundary.size
            newBytes = if (b.remaining() < newBytes) b.remaining() else newBytes
            b.get(searchWindow, boundary.size, newBytes)
        } while (newBytes > 0)
        return res
    }

    /**
     * Retrieves the content of a sent file and saves it
     * to a temporary file.
     * The full path to the saved file is returned.
     */
    private fun saveTmpFile(uri: String, b: ByteBuffer, offset: Int, len: Int, filename: String): String {
        val fileUri = uri
        var path = ""
        if (len > 0) {
            try {
                var dir = AndroidFile(nanoHTTPD.myRootDir, fileUri)
                if(fileUri.startsWith("/SDCard") && nanoHTTPD.sdCardRootDir != null) {
                   dir = AndroidFile(nanoHTTPD.sdCardRootDir!!, fileUri.removePrefix("/SDCard"))
                }
                val temp = File(dir, filename)

                Log.d(logTag, "can dir write: " + dir.path + " " + dir.canWrite())
                if (!temp.exists()) {
                    temp.createNewFile()
                }

                if (temp.exists()) {
                    Log.d(logTag, " RandomAccessFile writing with offset: $offset and length: $len")
                    try {
                        val src = b.duplicate()
                        val fileOutputStream = FileOutputStream(temp)
                        val dest = fileOutputStream.channel
                        src.position(offset).limit(offset + len)
                        dest.write(src.slice())
                    } catch (ex: IOException) {
                        ex.printStackTrace()
                    }
                    path = temp.absolutePath
                } else {
                    Log.e(logTag, " Failed to create file")
                }

            } catch (e: Exception) { // Catch exception if any
                Log.e(logTag, "Error: " + e.message)
            }

        }
        return path
    }

    /**
     * Decodes the percent encoding scheme. <br></br>
     * For example: "an+example%20string" -> "an example string"
     */
    @Throws(InterruptedException::class)
    private fun decodePercent(str: String): String? {
        var decoded: String? = null
        try {
            decoded = URLDecoder.decode(str, "UTF8")
        } catch (e: UnsupportedEncodingException) {
            sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Bad percent-encoding.")
        }

        return decoded
    }

    /**
     * Decodes parameters in percent-encoded URI-format
     * ( e.g. "name=Jack%20Daniels&pass=Single%20Malt" ) and
     * adds them to given Properties. NOTE: this doesn't support multiple
     * identical keys due to the simplicity of Properties -- if you need multiples,
     * you might want to replace the Properties with a Hashtable of Vectors or such.
     */
    @Throws(InterruptedException::class)
    private fun decodeParams(params: String?, p: Properties) {
        if (params == null)
            return

        val st = StringTokenizer(params, "&")
        while (st.hasMoreTokens()) {
            val e = st.nextToken()
            val sep = e.indexOf('=')
            if (sep >= 0)
                p[decodePercent(e.substring(0, sep))!!.trim { it <= ' ' }] = decodePercent(e.substring(sep + 1))
        }
    }

    /**
     * Returns an error message as a HTTP response and
     * throws InterruptedException to stop further request processing.
     */
    @Throws(InterruptedException::class)
    private fun sendError(status: String, msg: String) {
        sendResponse(status, MIME_PLAINTEXT, null, ByteArrayInputStream(msg.toByteArray()))
        throw InterruptedException()
    }

    /**
     * Sends given response to the socket.
     */
    private fun sendResponse(status: String?, mime: String?, header: Properties?, data: InputStream?) {
        try {
            if (status == null)
                throw Error("sendResponse(): Status can't be null.")

            val out = mySocket.getOutputStream()
            val pw = PrintWriter(out)
            pw.print("HTTP/1.0 $status \r\n")

            if (mime != null)
                pw.print("Content-Type: $mime\r\n")

            if (header?.getProperty("Date") == null)
                pw.print("Date: " + nanoHTTPD.gmtFrmt.format(Date()) + "\r\n")

            if (header != null) {
                val e = header.keys()
                while (e.hasMoreElements()) {
                    val key = e.nextElement() as String
                    val value = header.getProperty(key)
                    pw.print("$key: $value\r\n")
                }
            }

            pw.print("\r\n")
            pw.flush()

            if (data != null) {
                var pending = data.available()    // This is to support partial sends, see serveFile()
                val buff = ByteArray(nanoHTTPD.theBufferSize)
                while (pending > 0) {
                    val read = data.read(buff, 0,
                            if (pending > nanoHTTPD.theBufferSize) nanoHTTPD.theBufferSize else pending
                    )
                    if (read <= 0) break
                    out.write(buff, 0, read)
                    pending -= read
                }
            }
            out.flush()
            out.close()
            data?.close()
        } catch (ioe: IOException) {
            // Couldn't write? No can do.
            try {
                mySocket.close()
            } catch (t: Throwable) {
                t.printStackTrace()
            }

        }

    }
}