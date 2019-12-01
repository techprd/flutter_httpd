package com.techprd.httpd.flutter_httpd

import android.content.Context
import java.io.IOException
import java.net.InetSocketAddress

class WebServer : NanoHTTPD {

    @Throws(IOException::class)
    constructor(localAddress: InetSocketAddress, wwwRoot: AndroidFile, context: Context) :
            super(localAddress, wwwRoot, context) {
    }

    @Throws(IOException::class)
    constructor(port: Int, wwwRoot: AndroidFile, context: Context) :
            super(port, wwwRoot, context) {
    }
}
