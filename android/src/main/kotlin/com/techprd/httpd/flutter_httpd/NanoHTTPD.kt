package com.techprd.httpd.flutter_httpd

import android.content.Context
import android.util.Log
import androidx.annotation.IntegerRes
import com.techprd.httpd.flutter_httpd.Statics.HTTP_BAD_REQUEST
import com.techprd.httpd.flutter_httpd.Statics.HTTP_INTERNAL_ERROR
import com.techprd.httpd.flutter_httpd.Statics.HTTP_NOT_FOUND
import com.techprd.httpd.flutter_httpd.Statics.MIME_JSON
import com.techprd.httpd.flutter_httpd.Statics.HTTP_OK
import com.techprd.httpd.flutter_httpd.Statics.MIME_PLAINTEXT
import com.techprd.httpd.flutter_httpd.Statics.HTTP_FORBIDDEN
import com.techprd.httpd.flutter_httpd.Statics.HTTP_NOT_MODIFIED
import com.techprd.httpd.flutter_httpd.Statics.HTTP_PARTIAL_CONTENT
import com.techprd.httpd.flutter_httpd.Statics.HTTP_RANGE_NOT_SATISFIABLE
import com.techprd.httpd.flutter_httpd.Statics.MIME_HTML
import com.techprd.httpd.flutter_httpd.Statics.HTTP_REDIRECT
import com.techprd.httpd.flutter_httpd.Statics.MIME_DEFAULT_BINARY
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.util.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URLEncoder
import java.text.SimpleDateFormat

/**
 * A simple, tiny, nicely embeddable HTTP 1.0 (partially 1.1) server in Java
 *
 * <p> NanoHTTPD version 1.25,
 * Copyright &copy; 2001,2005-2012 Jarno Elonen (elonen@iki.fi, http://iki.fi/elonen/)
 * and Copyright &copy; 2010 Konstantinos Togias (info@ktogias.gr, http://ktogias.gr)
 *
 * See the end of the source file for distribution license
 * (Modified BSD licence)
 */
open class NanoHTTPD {
    var fileLibraryService: FileLibraryService? = null
    var context: Context? = null
    private val myTcpPort: Int
    private val myServerSocket: ServerSocket
    private val myThread: Thread
    val myRootDir: AndroidFile

    val theBufferSize = 16 * 1024
    var forceDownload = false
    var zipDownload = false

    /**
     * GMT date formatter
     */
    var gmtFrmt: SimpleDateFormat = SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US)

    /**
     * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
     */
    private val theMimeTypes = Hashtable<String, String>()

    init {
        gmtFrmt.timeZone = TimeZone.getTimeZone("GMT")

        val st = StringTokenizer(
                "css		text/css " +
                        "htm		text/html " +
                        "html		text/html " +
                        "xml		text/xml " +
                        "txt		text/plain " +
                        "asc		text/plain " +
                        "gif		image/gif " +
                        "jpg		image/jpeg " +
                        "jpeg		image/jpeg " +
                        "png		image/png " +
                        "mp3		audio/mpeg " +
                        "m3u		audio/mpeg-url " +
                        "mp4		video/mp4 " +
                        "ogv		video/ogg " +
                        "flv		video/x-flv " +
                        "mov		video/quicktime " +
                        "swf		application/x-shockwave-flash " +
                        "js			application/javascript " +
                        "pdf		application/pdf " +
                        "doc		application/msword " +
                        "ogg		application/x-ogg " +
                        "zip		application/octet-stream " +
                        "exe		application/octet-stream " +
                        "class		application/octet-stream ")

        while (st.hasMoreTokens())
            theMimeTypes[st.nextToken()] = st.nextToken()
    }

    // ==================================================
    // Socket & server code
    // ==================================================

    /**
     * Starts a HTTP server to given port.
     *
     *
     * Throws an IOException if the socket is already in use
     */
    @Throws(IOException::class)
    constructor(localAddress: InetSocketAddress, wwwRoot: AndroidFile, context: Context) {
        fileLibraryService = FileLibraryService.getInstance(context)
        this.context = context
        myTcpPort = localAddress.port
        myRootDir = wwwRoot
        myServerSocket = ServerSocket()
        myServerSocket.bind(localAddress)
        myThread = Thread(Runnable {
            try {
                while (true)
                    HTTPSession(this, myServerSocket.accept())
            } catch (ex: SocketException) {
                // socket closed already
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            }
        })
        myThread.isDaemon = true
        myThread.start()
    }

    /**
     * Starts a HTTP server to given port.
     * Throws an IOException if the socket is already in use
     */
    @Throws(IOException::class)
    constructor(port: Int, wwwRoot: AndroidFile, context: Context) {
        fileLibraryService = FileLibraryService.getInstance(context)
        this.context = context
        myTcpPort = port
        this.myRootDir = wwwRoot
        myServerSocket = ServerSocket(myTcpPort)
        myThread = Thread(Runnable {
            try {
                while (true)
                    HTTPSession(this, myServerSocket.accept())
            } catch (ex: SocketException) {
                // socket closed already
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            }
        })
        myThread.isDaemon = true
        myThread.start()
    }

    // ==================================================
    // API parts
    // ==================================================

    /**
     * Override this to customize the server.
     *
     * (By default, this delegates to serveFile() and allows directory listing.)
     *
     * @param uri    Percent-decoded URI without parameters, for example "/index.cgi"
     * @param params  Parsed, percent decoded parameters from URI and, in case of POST, data.
     * @param header Header entries, percent decoded
     * @return HTTP response, see class Response for details
     */
    @Throws(IOException::class)
    fun serve(uri: String, header: Properties, params: Properties): Response {
        if (zipDownload) {
            val options = JSONObject()
            return try {
                options.put("sourceEntry", "$myRootDir$uri")
                options.put("sourcePath", uri)
                options.put("targetPath", "$myRootDir/filetransfer")
                val makeZip = CompressZip(options)
                makeZip.zip()
                zipDownload = false
                serveFile("/FileTransfer.zip", header, myRootDir)

            } catch (e: JSONException) {
                zipDownload = false
                serveFile(uri, header, myRootDir)
            }

        } else return when {
            uri.contains("api") -> serveJson(uri, params)
            uri.contains("dashboard") -> serveFile("/", header, myRootDir)
            else -> serveFile(uri, header, myRootDir)
        }

    }

    private fun serveJson(uri: String, params: Properties): Response {

        var data = ""
        when {
            uri.contains("get-photo-albums") -> try {
                val photoAlbums = fileLibraryService!!.getPhotoAlbums()
                data = photoAlbums.toString()
            } catch (e: JSONException) {
                e.printStackTrace()
                return Response(HTTP_INTERNAL_ERROR, MIME_JSON,
                        "{'error': 500, message: 'Failed to get albums'}")
            }
            uri.contains("get-video-albums") -> try {
                val photoAlbums = fileLibraryService!!.getVideoAlbums()
                data = photoAlbums.toString()
            } catch (e: JSONException) {
                e.printStackTrace()
                return Response(HTTP_INTERNAL_ERROR, MIME_JSON,
                        "{'error': 500, message: 'Failed to get albums'}")
            }
            uri.contains("get-photos") -> try {
                val album = params.getProperty("ALBUM")
                val limit = params.getProperty("LIMIT")
                val offset = params.getProperty("OFFSET")
                if (limit == null || offset == null || album == null ||
                        limit == "" || offset == "" || album == "") {
                    return Response(HTTP_BAD_REQUEST, MIME_JSON,
                            "BAD REQUEST: no LIMIT or OFFSET HEADER presented.")
                }

                val photos = fileLibraryService!!.getPhotos(album,
                        Integer.parseInt(limit), Integer.parseInt(offset))
                data = photos.toString()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            uri.contains("get-videos") -> try {
                val album = params.getProperty("ALBUM")
                val limit = params.getProperty("LIMIT")
                val offset = params.getProperty("OFFSET")
                if (limit == null || offset == null || album == null ||
                        limit == "" || offset == "" || album == "") {
                    return Response(HTTP_BAD_REQUEST, MIME_JSON,
                            "BAD REQUEST: no LIMIT or OFFSET HEADER presented.")
                }

                val videos = fileLibraryService!!.getVideos(album,
                        Integer.parseInt(limit), Integer.parseInt(offset))
                data = videos.toString()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            uri.contains("get-music-albums") -> try {
                val musicAlbums = fileLibraryService!!.getMusicAlbums()
                data = musicAlbums.toString()
            } catch (e: JSONException) {
                e.printStackTrace()
                return Response(HTTP_INTERNAL_ERROR, MIME_JSON,
                        "{'error': 500, message: 'Failed to get albums'}")
            }
            uri.contains("get-musics") -> try {
                val musics = fileLibraryService!!.getMusics()
                data = musics.toString()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            uri.contains("get-music-album-cover") -> {
                val albumId = params.getProperty("ALBUM_ID")
                if (albumId == null || albumId == "") {
                    return Response(HTTP_BAD_REQUEST, MIME_JSON,
                            "BAD REQUEST: no albumId HEADER presented.")
                }
                try {
                    val albumCover = fileLibraryService!!.getMusicAlbumCover(albumId)
                    data = albumCover.toString()
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            uri.contains("get-recent-files") -> {
                val limit = params.getProperty("LIMIT")
                val offset = params.getProperty("OFFSET")
                if (limit == null || limit == "" || offset == null || offset == "") {
                    return Response(HTTP_BAD_REQUEST, MIME_JSON,
                            "BAD REQUEST: no limit HEADER presented.")
                }
                try {
                    val recentFiles = fileLibraryService!!.getRecentFiles(Integer.parseInt(limit), Integer.parseInt(offset))
                    data = recentFiles.toString()
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }
        val res = Response(HTTP_OK, MIME_JSON, data)
        res.addHeader("Access-Control-Allow-Origin", "*")
        res.addHeader("Access-Control-Allow-Headers", "X-Requested-With,content-type")
        return res
    }

    /**
     * Stops the server.
     */
    fun stop() {
        try {
            myServerSocket.close()
            myThread.join()
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        } catch (ioe: InterruptedException) {
            ioe.printStackTrace()
        }

    }

    /**
     * URL-encodes everything between "/"-characters.
     * Encodes spaces as '%20' instead of '+'.
     */
    private fun encodeUri(uri: String): String {
        val newUri = StringBuilder()
        val st = StringTokenizer(uri, "/ ", true)
        while (st.hasMoreTokens()) {
            when (val tok = st.nextToken()) {
                "/" -> newUri.append("/")
                " " -> newUri.append("%20")
                else -> try {
                    newUri.append(URLEncoder.encode(tok, "UTF-8"))
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
            }
        }
        return newUri.toString()
    }

    // ==================================================
    // File server code
    // ==================================================

    /**
     * Serves file from homeDir and its' subdirectories (only).
     * Uses only URI, ignores all headers and HTTP parameters.
     */
    private fun serveFile(path: String, header: Properties, homeDir: AndroidFile): Response {
        var uri = path
        var res: Response? = null

        // Make sure we won't die of an exception later
        if (!homeDir.isDirectory)
            res = Response(HTTP_INTERNAL_ERROR, MIME_PLAINTEXT,
                    "INTERNAL ERRROR: serveFile(): given homeDir:$homeDir is not a directory.")

        if (res == null) {
            // Remove URL arguments
            uri = uri.trim { it <= ' ' }.replace(File.separatorChar, '/')
            if (uri.indexOf('?') >= 0) {
                uri = uri.substring(0, uri.indexOf('?'))
            }

            // Prohibit getting out of current directory
            if (uri.startsWith("..") || uri.endsWith("..") || uri.contains("../"))
                res = Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
                        "FORBIDDEN: Won't serve ../ for security reasons.")
        }
        var f = AndroidFile(homeDir, uri)
        if (res == null && !f.exists())
            res = Response(HTTP_NOT_FOUND, MIME_PLAINTEXT,
                    "Error 404, file not found.")

        // List the directory, if necessary
        if (res == null && f.isDirectory) {
            // Browsers get confused without '/' after the
            // directory, send a redirect.
            if (!uri.endsWith("/")) {
                uri += "/"
                res = Response(HTTP_REDIRECT, MIME_HTML,
                        "<html><body>Redirected: <a href=\"" + uri + "\">" +
                                uri + "</a></body></html>")
                res.addHeader("Location", uri)
                res.addHeader("Access-Control-Allow-Origin", "*")
                res.addHeader("Access-Control-Allow-Headers", "X-Requested-With,content-type")
            }

            if (res == null) {
                // First try index.html and index.htm
                when {
                    AndroidFile(f, "index.html").exists() -> f = AndroidFile(homeDir, "$uri/index.html")
                    AndroidFile(f, "index.htm").exists() -> f = AndroidFile(homeDir, "$uri/index.htm")
                    f.canRead() -> {
                        val files = f.list()
                        val msg = StringBuilder("<html><body><h1>Directory $uri</h1><br/>")

                        if (uri.length > 1) {
                            val u = uri.substring(0, uri.length - 1)
                            val slash = u.lastIndexOf('/')
                            if (slash >= 0 && slash < u.length)
                                msg.append("<b><a href=\"").append(uri.substring(0, slash + 1)).append("\">..</a></b><br/>")
                        }

                        if (files != null) {
                            for (i in files.indices) {
                                val curFile = AndroidFile(f, files[i])
                                val dir = curFile.isDirectory
                                if (dir) {
                                    msg.append("<b>")
                                    files[i] += "/"
                                }

                                msg.append("<a href=\"")
                                        .append(
                                                encodeUri(uri + files[i])).append("\">")
                                        .append(files[i])
                                        .append("</a>")

                                // Show file size
                                if (curFile.isFile) {
                                    val len = curFile.length()
                                    msg.append(" &nbsp;<font size=2>(")
                                    when {
                                        len < 1024 -> msg.append(len).append(" bytes")
                                        len < 1024 * 1024 -> msg.append(len / 1024).append(".").append(len % 1024 / 10 % 100).append(" KB")
                                        else -> msg.append(len / (1024 * 1024)).append(".").append(len % (1024 * 1024) / 10 % 100).append(" MB")
                                    }

                                    msg.append(")</font>")
                                }
                                msg.append("<br/>")
                                if (dir) msg.append("</b>")
                            }
                        }
                        msg.append("</body></html>")
                        res = Response(HTTP_OK, MIME_HTML, msg.toString())
                        res.addHeader("Access-Control-Allow-Origin", "*")
                        res.addHeader("Access-Control-Allow-Headers", "X-Requested-With,content-type")
                    }
                    else -> res = Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
                            "FORBIDDEN: No directory listing.")
                }// No index file, list the directory if it is readable
            }
        }

        try {
            if (res == null) {
                // Get MIME type from file name extension, if possible
                var mime: String? = null
                val dot = f.canonicalPath.lastIndexOf('.')
                if (dot >= 0)
                    mime = theMimeTypes[f.canonicalPath.substring(dot + 1).toLowerCase(Locale.US)]
                if (mime == null || forceDownload)
                    mime = MIME_DEFAULT_BINARY

                // Calculate etag
                val etag = Integer.toHexString((f.absolutePath + f.lastModified() + "" + f.length()).hashCode())

                //System.out.println( String.format("mime: %s, etag: %s", mime, etag));

                // Support (simple) skipping:
                var startFrom: Long = 0
                var endAt: Long = -1
                var range = header.getProperty("range")
                if (!range.isNullOrEmpty()) {
                    if (range.startsWith("bytes=")) {
                        range = range.substring("bytes=".length)
                        val minus = range.indexOf('-')
                        try {
                            if (minus > 0) {
                                val stRange = range.substring(0, minus)
                                if (stRange.isNotEmpty()) {
                                    startFrom = java.lang.Long.parseLong(stRange)
                                }
                                val endRange = range.substring(minus + 1)
                                if (endRange.isNotEmpty()) {
                                    endAt = java.lang.Long.parseLong(endRange)
                                }
                            }
                        } catch (nfe: NumberFormatException) {
                            nfe.printStackTrace()
                        }
                    }
                }

                // Change return code and add Content-Range header when skipping is requested
                val fileLen = f.length()
                //System.out.println( String.format("file length: %d", fileLen));

                when {
                    range != null && startFrom >= 0 -> when {
                        startFrom < fileLen -> {
                            if (endAt < 0)
                                endAt = fileLen - 1
                            var newLen = endAt - startFrom + 1
                            if (newLen < 0) newLen = 0

                            val dataLen = newLen
                            //InputStream fis = new FileInputStream( f ) {
                            //	public int available() throws IOException { return (int)dataLen; }
                            //};
                            val fis = f.inputStream
                            fis.skip(startFrom)

                            res = Response(HTTP_PARTIAL_CONTENT, mime, fis)
                            res.addHeader("Content-Length", "" + dataLen)
                            res.addHeader("Content-Range", "bytes $startFrom-$endAt/$fileLen")
                            res.addHeader("ETag", etag)
                        }
                        else -> {
                            res = Response(HTTP_RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
                            res.addHeader("Content-Range", "bytes 0-0/$fileLen")
                            res.addHeader("ETag", etag)
                        }
                    }
                    else -> when (etag) {
                        header.getProperty("if-none-match") -> res = when {
                            forceDownload -> Response(HTTP_OK, mime, f.inputStream)
                            else -> Response(HTTP_NOT_MODIFIED, mime, "")
                        }
                        else -> {
                            //res = new Response( HTTP_OK, mime, new FileInputStream( f ));
                            res = Response(HTTP_OK, mime, f.inputStream)
                            //mime = MIME_DEFAULT_BINARY;

                            res.addHeader("Content-Length", "" + fileLen)
                            res.addHeader("ETag", etag)
                        }
                    }
                }
            }
        } catch (ioe: IOException) {
            res = Response(HTTP_FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Reading file failed.")
        }
        // Announce that the file server accepts partial content requests
        res!!.addHeader("Accept-Ranges", "bytes")
        res.addHeader("Access-Control-Allow-Origin", "*")
        res.addHeader("Access-Control-Allow-Headers", "X-Requested-With,content-type")
        return res
    }

    /**
     * The distribution licence
     */
    private val LICENCE = "Copyright (C) 2001,2005-2011 by Jarno Elonen <elonen@iki.fi>\n" +
            "and Copyright (C) 2010 by Konstantinos Togias <info@ktogias.gr>\n" +
            "\n" +
            "Redistribution and use in source and binary forms, with or without\n" +
            "modification, are permitted provided that the following conditions\n" +
            "are met:\n" +
            "\n" +
            "Redistributions of source code must retain the above copyright notice,\n" +
            "this list of conditions and the following disclaimer. Redistributions in\n" +
            "binary form must reproduce the above copyright notice, this list of\n" +
            "conditions and the following disclaimer in the documentation and/or other\n" +
            "materials provided with the distribution. The name of the author may not\n" +
            "be used to endorse or promote products derived from this software without\n" +
            "specific prior written permission. \n" +
            " \n" +
            "THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR\n" +
            "IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES\n" +
            "OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.\n" +
            "IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,\n" +
            "INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT\n" +
            "NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,\n" +
            "DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY\n" +
            "THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT\n" +
            "(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE\n" +
            "OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE."

}