package com.techprd.httpd.flutter_httpd

import com.techprd.httpd.flutter_httpd.Statics.HTTP_OK
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.util.*


/**
 * HTTP response.
 * Return one of these from serve().
 */
class Response {

    /**
     * HTTP status code after processing, e.g. "200 OK", HTTP_OK
     */
    var status: String

    /**
     * MIME type of content, e.g. "text/html"
     */
    var mimeType: String

    /**
     * Data of the response, may be null.
     */
    lateinit var data: InputStream

    /**
     * Headers for the HTTP response. Use addHeader()
     * to add lines.
     */
    var header = Properties()

    /**
     * Default constructor: response = HTTP_OK, data = mime = 'null'
     */
    init {
        this.status = HTTP_OK
    }

    /**
     * Basic constructor.
     */
    constructor(status: String, mimeType: String, data: InputStream) {
        this.status = status
        this.mimeType = mimeType
        this.data = data
    }

    /**
     * Convenience method that makes an InputStream out of
     * given text.
     */
    constructor(status: String, mimeType: String, txt: String) {
        this.status = status
        this.mimeType = mimeType
        try {
            this.data = ByteArrayInputStream(txt.toByteArray(charset("UTF-8")))
        } catch (uee: UnsupportedEncodingException) {
            uee.printStackTrace()
        }

    }

    /**
     * Adds given line to the header.
     */
    fun addHeader(name: String, value: String) {
        header[name] = value
    }
}