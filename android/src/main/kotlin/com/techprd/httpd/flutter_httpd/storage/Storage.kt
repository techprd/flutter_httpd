package com.techprd.httpd.flutter_httpd.storage

interface Storage {
    fun setString(key: String, value: String)
    fun getString(key: String): String
}
