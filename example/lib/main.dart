import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_httpd/flutter_httpd.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _serverAddress = 'Unknown';

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    var details = await FlutterHttpd.getStorageDetail();
    var storage = details[0];

    String serverAddress;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      serverAddress = await FlutterHttpd.startServer(storage.rootDir, 1234);
    } on PlatformException {
      serverAddress =
          'Failed to start server. Make sure you have read / write access';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _serverAddress = serverAddress;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(child: Text('Running on: $_serverAddress\n')),
      ),
    );
  }
}
