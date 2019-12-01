package com.techprd.httpd.flutter_httpd

import android.util.Log
import com.techprd.httpd.flutter_httpd.Statics.HTTP_BAD_REQUEST
import com.techprd.httpd.flutter_httpd.Statics.HTTP_INTERNAL_ERROR
import com.techprd.httpd.flutter_httpd.Statics.MIME_PLAINTEXT
import java.io.*
import java.net.Socket
import java.net.URLDecoder
import java.util.*

/**
 * Handles one session, i.e. parses the HTTP request
 * and returns the response.
 */
class HTTPSession(private val nanoHTTPD: NanoHTTPD, private val mySocket: Socket) : Runnable {

    private val logTag = "HTTPSession: "

    init {
        val t = Thread(this)
        t.isDaemon = true
        t.start()
    }

    override fun run() {
        try {
            val inputStream = mySocket.getInputStream() ?: return

            // Read the first 8192 bytes.
            // The full header should fit in here.
            // Apache's default header limit is 8KB.
            // Do NOT assume that a single read will get the entire header at once!
            val bufSize = 8192
            var buf = ByteArray(bufSize)
            var splitByte = 0
            var rLen = 0
            run {
                var read = inputStream.read(buf, 0, bufSize)
                while (read > 0) {
                    rLen += read
                    splitByte = findHeaderEnd(buf, rLen)
                    if (splitByte > 0)
                        break
                    read = inputStream.read(buf, rLen, bufSize - rLen)
                }
            }

            // Create a BufferedReader for parsing the header.
            val hBis = ByteArrayInputStream(buf, 0, rLen)
            val hin = BufferedReader(InputStreamReader(hBis))
            val pre = Properties()
            val params = Properties()
            val header = Properties()
            val files = Properties()

            // Decode the header into params and header java properties
            decodeHeader(hin, pre, params, header)

            val method = pre.getProperty("method")
            val uri = pre.getProperty("uri")

            var size = calculateSize(header)

            // Write the part of body already read to ByteArrayOutputStream f
            val f = ByteArrayOutputStream()
            if (splitByte < rLen)
                f.write(buf, splitByte, rLen - splitByte)

            // While Firefox sends on the first read all the data fitting
            // our buffer, Chrome and Opera send only the headers even if
            // there is data for the body. We do some magic here to find
            // out whether we have already consumed part of body, if we
            // have reached the end of the data to be sent or we should
            // expect the first byte of the body at the next read.
            if (splitByte < rLen)
                size -= (rLen - splitByte + 1).toLong()
            else if (splitByte == 0 || size == 0x7FFFFFFFFFFFFFFFL)
                size = 0

            // Now read all the body and write it to f
            buf = ByteArray(512)
            while (rLen >= 0 && size > 0) {
                rLen = inputStream.read(buf, 0, 512)
                size -= rLen.toLong()
                if (rLen > 0)
                    f.write(buf, 0, rLen)
            }

            // Get the raw body as a byte []
            val fBuf = f.toByteArray()

            // Create a BufferedReader for easily reading it as string.
            val bin = ByteArrayInputStream(fBuf)
            val bufferedReader = BufferedReader(InputStreamReader(bin))

            // If the method is POST, there may be parameters
            // in data section, too, read it:
            if (method != null && method.equals("POST", ignoreCase = true)) {
                var contentType = ""
                val contentTypeHeader = header.getProperty("content-type")
                var st = StringTokenizer(contentTypeHeader, "; ")
                if (st.hasMoreTokens()) {
                    contentType = st.nextToken()
                }

                if (contentType.equals("multipart/form-data", ignoreCase = true)) {
                    // Handle multipart/form-data
                    if (!st.hasMoreTokens())
                        sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html")
                    val boundaryExp = st.nextToken()
                    st = StringTokenizer(boundaryExp, "=")
                    if (st.countTokens() != 2)
                        sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but boundary syntax error. Usage: GET /example/file.html")
                    st.nextToken()
                    val boundary = st.nextToken()

                    decodeMultipartData(uri, boundary, fBuf, bufferedReader, params, files)
                } else {
                    // Handle application/x-www-form-urlencoded
                    var postLine = StringBuilder()
                    val pBuf = CharArray(512)
                    var read = bufferedReader.read(pBuf)
                    while (read >= 0 && !postLine.toString().endsWith("\r\n")) {
                        postLine.append(String(pBuf, 0, read))
                        read = bufferedReader.read(pBuf)
                    }
                    postLine = StringBuilder(postLine.toString().trim { it <= ' ' })
                    decodeParams(postLine.toString(), params)
                }
            }

            // if (method != null && method.equalsIgnoreCase("PUT"))
            //   files.put("filePath", saveTmpFile(fbuf, 0, f.size()));

            // Ok, now do the serve()
            val r = nanoHTTPD.serve(uri, header, params)
            sendResponse(r.status, r.mimeType, r.header, r.data)
            bufferedReader.close()
            inputStream.close()
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

    private fun calculateSize(header: Properties): Long {
        var size1 = 0x7FFFFFFFFFFFFFFFL
        val contentLength = header.getProperty("content-length")

        if (contentLength != null) {
            try {
                size1 = Integer.parseInt(contentLength).toLong()
            } catch (ex: NumberFormatException) {
                ex.printStackTrace()
            }

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
                uri = decodeUri(uri.substring(0, qmi))
            } else
                uri = decodeUri(uri)

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
    private fun decodeMultipartData(uri: String, boundary: String, fbuf: ByteArray, reader: BufferedReader, parms: Properties, files: Properties) {
        try {
            val bPositions = getBoundaryPositions(fbuf, boundary.toByteArray())
            var boundaryCount = 1
            var mpLine = reader.readLine()
            while (mpLine != null) {
                if (!mpLine.contains(boundary))
                    sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but next chunk does not start with boundary. Usage: GET /example/file.html")
                boundaryCount++
                val item = Properties()
                mpLine = reader.readLine()
                while (mpLine != null && mpLine.trim { it <= ' ' }.isNotEmpty()) {
                    val p = mpLine.indexOf(':')
                    if (p != -1)
                        item[mpLine.substring(0, p).trim { it <= ' ' }.toLowerCase(Locale.US)] =
                                mpLine.substring(p + 1).trim { it <= ' ' }
                    mpLine = reader.readLine()
                }
                if (mpLine != null) {
                    val contentDisposition = item.getProperty("content-disposition")
                    if (contentDisposition == null) {
                        sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but no content-disposition info found. Usage: GET /example/file.html")
                    }
                    val st = StringTokenizer(contentDisposition, ";")
                    val disposition = Properties()
                    while (st.hasMoreTokens()) {
                        val token = st.nextToken()
                        Log.e(logTag, "token: $token")
                        val p = token.indexOf('=')
                        if (p != -1)
                            disposition[token.substring(0, p).trim { it <= ' ' }.toLowerCase(Locale.US)] =
                                    token.substring(p + 1).trim { it <= ' ' }
                    }
                    var pname = disposition.getProperty("name")
                    pname = pname.substring(1, pname.length - 1)

                    var value = StringBuilder()
                    if (item.getProperty("content-type") == null) {
                        while (!mpLine!!.contains(boundary)) {
                            mpLine = reader.readLine()
                            if (mpLine != null) {
                                val d = mpLine.indexOf(boundary)
                                if (d == -1)
                                    value.append(mpLine)
                                else
                                    value.append(mpLine, 0, d - 2)
                            }
                        }
                    } else {
                        if (boundaryCount > bPositions.size)
                            sendError(HTTP_INTERNAL_ERROR, "Error processing request")
                        val offset = stripMultipartHeaders(fbuf, bPositions[boundaryCount - 2])
                        val path = saveTmpFile(uri, pname, fbuf, offset, bPositions[boundaryCount - 1] - offset - 4)
                        files[pname] = path
                        value = StringBuilder(disposition.getProperty("filename"))
                        value = StringBuilder(value.substring(1, value.length - 1))
                        do {
                            mpLine = reader.readLine()
                        } while (mpLine != null && !mpLine.contains(boundary))
                    }
                    parms[pname] = value.toString()
                }
            }
        } catch (ioe: IOException) {
            sendError(HTTP_INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.message)
        }

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
    private fun getBoundaryPositions(b: ByteArray, boundary: ByteArray): IntArray {
        var matchCount = 0
        var matchByte = -1
        val matchBytes = Vector<Int>()
        run {
            var i = 0
            while (i < b.size) {
                if (b[i] == boundary[matchCount]) {
                    if (matchCount == 0)
                        matchByte = i
                    matchCount++
                    if (matchCount == boundary.size) {
                        matchBytes.addElement(matchByte)
                        matchCount = 0
                        matchByte = -1
                    }
                } else {
                    i -= matchCount
                    matchCount = 0
                    matchByte = -1
                }
                i++
            }
        }
        val ret = IntArray(matchBytes.size)
        for (i in ret.indices) {
            ret[i] = matchBytes.elementAt(i) as Int
        }
        return ret
    }

    /**
     * Retrieves the content of a sent file and saves it
     * to a temporary file.
     * The full path to the saved file is returned.
     */
    private fun saveTmpFile(uri: String, filename: String, b: ByteArray, offset: Int, len: Int): String {
        var path = ""
        if (len > 0) {

            try {
                val dir = AndroidFile(nanoHTTPD.myRootDir, uri)
                val temp = File(dir, filename)

                Log.d(logTag, "can dir write: " + dir.absolutePath + " " + dir.canWrite())
                val created = temp.createNewFile()
                if (created) {
                    Log.d(logTag, "  saveTmpFile: writing to file now")
                    val fStream = FileOutputStream(temp)
                    fStream.write(b, offset, len)
                    fStream.close()
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
     * It returns the offset separating multipart file headers
     * from the file's data.
     */
    private fun stripMultipartHeaders(b: ByteArray, offset: Int): Int {
        var i = offset
        while (i < b.size) {
            if (b[i] == '\r'.toByte() && b[++i] == '\n'.toByte() && b[++i] == '\r'.toByte() && b[++i] == '\n'.toByte())
                break
            i++
        }
        return i + 1
    }

    private fun decodeUri(uri: String): String {
        val newUri = StringBuilder()
        val st = StringTokenizer(uri, "/ ", true)
        while (st.hasMoreTokens()) {
            when (val tok = st.nextToken()) {
                "/" -> newUri.append("/")
                "%20" -> newUri.append(" ")
                else -> try {
                    newUri.append(URLDecoder.decode(tok, "UTF-8"))
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
            }
        }
        return newUri.toString()
    }

    /**
     * Decodes the percent encoding scheme. <br></br>
     * For example: "an+example%20string" -> "an example string"
     */
    @Throws(InterruptedException::class)
    private fun decodePercent(str: String): String? {

        try {
            val sb = StringBuilder()
            var i = 0
            while (i < str.length) {
                when (val c = str[i]) {
                    '+' -> sb.append(' ')
                    '%' -> {
                        sb.append(Integer.parseInt(str.substring(i + 1, i + 3), 16).toChar())
                        i += 2
                    }
                    else -> sb.append(c)
                }
                i++
            }
            return sb.toString()
        } catch (e: Exception) {
            sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Bad percent-encoding.")
            return null
        }

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