import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_httpd/statics.dart';

import 'StorageDetail.dart';

class FlutterHttpd {
  static const MethodChannel _channel = const MethodChannel('flutter_httpd');

  static Future<String> startServer(String path, int port) async {
    final options = {
      "www_root": path,
      "localhost_only": false,
      "port": port,
    };
    final String serverAddress =
        await _channel.invokeMethod(Statics.ACTION_START_SERVER, options);
    return serverAddress;
  }

  static Future<String> stopServer() async {
    return await _channel.invokeMethod(Statics.ACTION_STOP_SERVER, {});
  }

  /// Return a list of of storage infos
  static Future<List<StorageDetail>> getStorageDetail() async {
    List details =
        await _channel.invokeMethod(Statics.ACTION_GET_STORAGE_DETAILS, {});
    List<StorageDetail> storageInfos = details
        .map((storageInfoMap) => StorageDetail.fromJson(storageInfoMap))
        .toList();
    return storageInfos;
  }

  /// Return a list of of media storage infos
  static Future<List<StorageDetail>> getMediaStorageDetail() async {
    List details = await _channel
        .invokeMethod(Statics.ACTION_GET_MEDIA_STORAGE_DETAILS, {});
    List<StorageDetail> storageInfos = details
        .map((storageInfoMap) => StorageDetail.fromJson(storageInfoMap))
        .toList();
    return storageInfos;
  }

  /// returns the url for the running server
  static Future<String> getUrl() async =>
      await _channel.invokeMethod(Statics.ACTION_GET_URL, {});

  /// returns current directory that is served by the app server
  static Future<String> getLocalPath() async =>
      await _channel.invokeMethod(Statics.ACTION_GET_LOCAL_PATH, {});

  static Future<String> getPlatformVersion() async =>
      await _channel.invokeMethod(Statics.ACTION_GET_PLATFORM_VERSION, {});
}
