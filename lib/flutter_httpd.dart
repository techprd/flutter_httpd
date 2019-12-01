import 'dart:async';

import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

class FlutterHttpd {
  static const MethodChannel _channel = const MethodChannel('flutter_httpd');

  static Future<String> get platformVersion async {
    var externalPath = await getExternalStorageDirectory();
    final options = {
      "www_root": externalPath.path,
      "localhost_only": false,
      "port": 1234,
    };
    final String version = await _channel.invokeMethod('startServer', options);
    return version;
  }
}
