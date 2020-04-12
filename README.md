# flutter_httpd

Running a Web Server on Android for flutter

## Getting Started

Make sure you have handled READ & WRITE permissions on your app before using this plugin

### How to use:

```

    var details = await FlutterHttpd.getStorageDetails();
    var storage = details[0];

    String serverAddress;
    try {
      serverAddress = await FlutterHttpd.startServer(storage.rootDir, 1234);
    } on PlatformException {
      serverAddress =
          'Failed to start server. Make sure you have read / write access';
    }

```

##### Please refer to example app  for a complete example
