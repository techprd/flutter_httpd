package com.techprd.httpd.flutter_httpd

object Statics {
    const val MIME_PLAINTEXT = "text/plain"
    const val MIME_HTML = "text/html"
    const val MIME_JSON = "application/json"
    const val MIME_DEFAULT_BINARY = "application/octet-stream"
    const val MIME_XML = "text/xml"
    const val HTTP_OK = "200 OK"
    const val HTTP_PARTIAL_CONTENT = "206 Partial Content"
    const val HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable"
    const val HTTP_REDIRECT = "301 Moved Permanently"
    const val HTTP_NOT_MODIFIED = "304 Not Modified"
    const val HTTP_FORBIDDEN = "403 Forbidden"
    const val HTTP_NOT_FOUND = "404 Not Found"
    const val HTTP_BAD_REQUEST = "400 Bad Request"
    const val HTTP_INTERNAL_ERROR = "500 Internal Server Error"
    const val HTTP_NOT_IMPLEMENTED = "501 Not Implemented"

    const val ACTION_START_SERVER = "startServer"
    const val ACTION_STOP_SERVER = "stopServer"
    const val ACTION_GET_URL = "getURL"
    const val ACTION_GET_LOCAL_PATH = "getLocalPath"
    const val ACTION_GET_PLATFORM_VERSION = "getPlatformVersion"
    const val ACTION_GET_STORAGE_DETAILS = "getStorageDetails"

    const val OPT_WWW_ROOT = "www_root"
    const val OPT_PORT = "port"
    const val OPT_LOCALHOST_ONLY = "localhost_only"
}