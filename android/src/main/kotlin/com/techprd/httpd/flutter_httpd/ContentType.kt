package com.techprd.httpd.flutter_httpd

import java.util.regex.Pattern


/*
 * #%L
 * NanoHttpd-Core
 * %%
 * Copyright (C) 2012 - 2016 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

class ContentType(contentTypeHeader: String?) {
    var contentType: String? = null
    private var encoding: String? = null
    var boundary: String? = null

    private fun getDetailFromContentHeader(contentTypeHeader: String?, pattern: Pattern, defaultValue: String?, group: Int): String? {
        val matcher = pattern.matcher(contentTypeHeader)
        return if (matcher.find()) matcher.group(group) else defaultValue
    }

    fun getEncoding(): String {
        return encoding ?: UTF_ENCODING
    }

    val isMultipart: Boolean
        get() = MULTIPART_FORM_DATA_HEADER.equals(contentType, ignoreCase = true)

    companion object {
        private const val UTF_ENCODING = "UTF-8"
        private const val MULTIPART_FORM_DATA_HEADER = "multipart/form-data"
        private const val CONTENT_REGEX = "[ |\t]*([^/^ ^;^,]+/[^ ^;^,]+)"
        private val MIME_PATTERN = Pattern.compile(CONTENT_REGEX, Pattern.CASE_INSENSITIVE)
        private const val CHARSET_REGEX = "[ |\t]*(charset)[ |\t]*=[ |\t]*['|\"]?([^\"^'^;^,]*)['|\"]?"
        private val CHARSET_PATTERN = Pattern.compile(CHARSET_REGEX, Pattern.CASE_INSENSITIVE)
        private const val BOUNDARY_REGEX = "[ |\t]*(boundary)[ |\t]*=[ |\t]*['|\"]?([^\"^'^;^,]*)['|\"]?"
        private val BOUNDARY_PATTERN = Pattern.compile(BOUNDARY_REGEX, Pattern.CASE_INSENSITIVE)
    }

    init {
        when {
            contentTypeHeader != null -> {
                contentType = getDetailFromContentHeader(contentTypeHeader, MIME_PATTERN, "", 1)
                encoding = getDetailFromContentHeader(contentTypeHeader, CHARSET_PATTERN, null, 2)
            }
            else -> {
                contentType = ""
                encoding = UTF_ENCODING
            }
        }
        boundary = when {
            MULTIPART_FORM_DATA_HEADER.equals(contentType, ignoreCase = true) -> {
                getDetailFromContentHeader(contentTypeHeader, BOUNDARY_PATTERN, null, 2)
            }
            else -> {
                null
            }
        }
    }
}
