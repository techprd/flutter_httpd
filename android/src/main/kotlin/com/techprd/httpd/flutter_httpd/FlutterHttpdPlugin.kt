package com.techprd.httpd.flutter_httpd

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.WindowManager
import com.google.gson.Gson
import com.techprd.httpd.flutter_httpd.di.DaggerAppComponent
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.FileFilter
import java.io.IOException
import java.net.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class FlutterHttpdPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    /**
     * Common tag used for logging statements.
     */
    private val logTag = "FlutterHttpdPlugin"

    private var port = 8888
    private var localhostOnly = false
    private var localPath = ""
    private val webServers = ArrayList<WebServer>()
    private var url = ""
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var activity: Activity
    private lateinit var storageUtils: StorageUtils
    private lateinit var fileService: FileLibraryService

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "flutter_httpd")
        channel.setMethodCallHandler(this)
        context = binding.applicationContext
        val appComponent = DaggerAppComponent.factory().create(context)
        fileService = appComponent.fileService()
        storageUtils = appComponent.storageUtils()
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        val inputs = call.arguments<HashMap<String, Any>>()

        when (call.method) {
            Statics.ACTION_GET_PLATFORM_VERSION -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            Statics.ACTION_GET_STORAGE_DETAILS -> {
                val storage = storageUtils.getExternalStorageDetails()
                result.success(storage)
            }
            Statics.ACTION_GET_MEDIA_STORAGE_DETAILS -> {
                val storage = storageUtils.getMediaStorageDetails()
                result.success(storage)
            }
            Statics.ACTION_START_SERVER -> {
                Log.d(logTag, "$inputs")
                startServer(inputs, result)
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            Statics.ACTION_STOP_SERVER -> {
                stopServer(result)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            Statics.ACTION_GET_URL -> {
                result.success(this.url)
            }
            Statics.ACTION_GET_LOCAL_PATH -> {
                result.success(this.localPath)
            }
            Statics.ACTION_GET_THUMBNAIL_PATH -> {
                val thumbnailPath = storageUtils.getThumbnailPath(inputs)
                result.success(thumbnailPath)
            }
            Statics.ACTION_GET_RECENT_FILES -> {
                val limit = inputs["limit"] as Int
                val offset = inputs["offset"] as Int
                val data = fileService.getRecentFiles(limit, offset)
                val dataHashmap = Gson().fromJson(data.toString(), HashMap::class.java)
                result.success(dataHashmap)
            }
            else -> {
                Log.d(logTag, String.format("Invalid action passed: %s", call.method))
                result.notImplemented()
            }
        }
    }

    private fun startServer(options: HashMap<String, Any>, result: Result) {
        Log.d(logTag, "starting server...")

        val wwwRoot = options[Statics.OPT_WWW_ROOT] as String
        port = options[Statics.OPT_PORT] as Int
        localhostOnly = options[Statics.OPT_LOCALHOST_ONLY] as Boolean

        if (wwwRoot.startsWith("/")) {
            //localPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            localPath = wwwRoot
        } else {
            //localPath = "file:///android_asset/flutter_asset";
            val loader = FlutterInjector.instance().flutterLoader()
            localPath = loader.getLookupKeyForAsset(wwwRoot)
        }

        val errMsg = runServer()

        if (errMsg.isNotEmpty()) {
            result.error("1", errMsg, errMsg)
        } else {
            url = when {
                localhostOnly -> {
                    "http://127.0.0.1:$port"
                }
                else -> {
                    "http://" + getLocalIpAddress() + ":" + port
                }
            }
            result.success(url)
        }
    }

    private fun runServer(): String {
        var errMsg = ""
        try {
            val f = AndroidFile(localPath)
            var sdCardRootDir: AndroidFile? = null
            f.assetManager = context.assets

            val storage = storageUtils.getExternalStorageDetails()
            val sdCardDetails = storage.find { it["name"] == "SDCard" }
            if (sdCardDetails != null) {
                sdCardRootDir = AndroidFile(sdCardDetails["rootPath"].toString())
            }

            val server: WebServer
            if (localhostOnly) {
                val localAddress = InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port)
                server = WebServer(fileService, localAddress, f, sdCardRootDir, context)
                webServers.add(server)
            } else {
                server = WebServer(fileService, port, f, sdCardRootDir, context)
                webServers.add(server)
            }
        } catch (e: IOException) {
            errMsg = String.format("IO Exception: %s", e.message)
            Log.w(logTag, errMsg)
        }

        return errMsg
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            interfaces.forEach { inf ->
                inf.inetAddresses
                        .asSequence()
                        .filter { !it.isLoopbackAddress && it is Inet4Address }
                        .map { it.hostAddress.toUpperCase(Locale.US) }
                        .forEach {
                            if (inf.displayName.startsWith("wlan")) {
                                Log.w(logTag, "local IP: $it")
                                return it
                            }
                        }
            }
        } catch (ex: SocketException) {
            Log.e(logTag, ex.toString())
        }

        return "127.0.0.1"
    }

    private fun stopServer(result: Result) {
        Log.d(logTag, "stopServer")
        if (webServers.size > 0) {
            for (webServer in webServers) {
                webServer.stop()
            }
            webServers.clear()
        }
        url = ""
        localPath = ""
        cleanCacheDir()
        result.success("File Server has stopped")
    }

    private fun cleanCacheDir() {
        context.externalCacheDir?.listFiles(FileFilter {
            it.extension == "tmp"
        })?.map {
            try {
                it.delete()
            } catch (ex: IOException) {
                Log.e(logTag, "Failed to delete the cache file: ${it.name}")
            }
        }
    }
}
