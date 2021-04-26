package com.techprd.httpd.flutter_httpd

import android.content.Context
import java.io.IOException
import java.net.InetSocketAddress

class WebServer : NanoHTTPD {

    @Throws(IOException::class)
    constructor(fileLibraryService: FileLibraryService, localAddress: InetSocketAddress, wwwRoot: AndroidFile, sdCardRootDir: AndroidFile?, context: Context) :
            super(fileLibraryService, localAddress, wwwRoot, sdCardRootDir, context) {
    }

    @Throws(IOException::class)
    constructor(fileLibraryService: FileLibraryService, port: Int, wwwRoot: AndroidFile, sdCardRootDir: AndroidFile?, context: Context) :
            super(fileLibraryService, port, wwwRoot, sdCardRootDir, context) {
    }
}
