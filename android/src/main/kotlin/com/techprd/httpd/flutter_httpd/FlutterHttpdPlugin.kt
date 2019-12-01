package com.techprd.httpd.flutter_httpd

import android.content.Context
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import org.json.JSONArray
import java.io.IOException
import java.net.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class FlutterHttpdPlugin(private val context: Context) : MethodCallHandler {

    /**
     * Common tag used for logging statements.
     */
    private val logTag = "FlutterHttpdPlugin"

    private var port = 8888
    private var localhostOnly = false

    private var localPath = ""
    private val webServers = ArrayList<WebServer>()
    private var url = ""

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "flutter_httpd")
            val context = registrar.context()
            channel.setMethodCallHandler(FlutterHttpdPlugin(context))
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        val inputs = call.arguments as HashMap<String, Any>
        when {
            call.method == "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
                Log.d(logTag, "$inputs")
            }
            Statics.ACTION_START_SERVER == call.method -> {
                Log.d(logTag, "$inputs")
                startServer(inputs, result)

            }
            Statics.ACTION_STOP_SERVER == call.method -> {
                stopServer(inputs, result)
            }
            Statics.ACTION_GET_URL == call.method -> {
                Log.d(logTag, "getURL")
                result.success(this.url)
            }
            Statics.ACTION_GET_LOCAL_PATH == call.method -> {
                Log.d(logTag, "getLocalPath")
                result.success(this.localPath)
            }
            else -> {
                Log.d(logTag, String.format("Invalid action passed: %s", call.method))
                result.notImplemented()
            }
        }
    }

    private fun startServer(options: HashMap<String, Any>, result: Result) {
        Log.d(logTag, "startServer")

        val wwwRoot = options[Statics.OPT_WWW_ROOT] as String
        port = options[Statics.OPT_PORT] as Int
        localhostOnly = options[Statics.OPT_LOCALHOST_ONLY] as Boolean

        if (wwwRoot.startsWith("/")) {
            //localPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            localPath = wwwRoot
        } else {
            //localPath = "file:///android_asset/www";
            localPath = "www"
            if (wwwRoot.isNotEmpty()) {
                localPath += "/"
                localPath += wwwRoot
            }
        }

        val errMsg = runServer()

        if (errMsg.isNotEmpty()) {
            result.error("1", errMsg, errMsg)
        } else {
            url = if (localhostOnly) {
                "http://127.0.0.1:$port"
            } else {
                "http://" + getLocalIpAddress() + ":" + port
            }
            result.success(url)
        }
    }

    private fun runServer(): String {
        var errMsg = ""
        try {
            val f = AndroidFile(localPath)

            f.assetManager = context.resources.assets

            val server: WebServer
            if (localhostOnly) {
                val localAddress = InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port)
                server = WebServer(localAddress, f, context)
                webServers.add(server)
            } else {
                server = WebServer(port, f, context)
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

    private fun stopServer(inputs: HashMap<String, Any>, result: Result) {
        Log.d(logTag, "stopServer")

//        cordova.getActivity().runOnUiThread(Runnable {
//            __stopServer()
//            url = ""
//            localPath = ""
//            callbackContext.success()
//        })

    }

    private fun __stopServer() {
        if (webServers.size > 0) {
            for (webServer in webServers) {
                webServer.stop()
            }
            webServers.clear()
        }
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking Flag indicating if multitasking is turned on for app
     */
    fun onPause(multitasking: Boolean) {
        //if(! multitasking) __stopServer();
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking Flag indicating if multitasking is turned on for app
     */
    fun onResume(multitasking: Boolean) {
        //if(! multitasking) __startServer();
    }

    /**
     * The final call you receive before your activity is destroyed.
     */
    fun onDestroy() {
        __stopServer()
    }
}
