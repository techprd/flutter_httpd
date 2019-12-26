import 'dart:math';

class StorageDetails {
  final String appFilesDir;
  final String rootDir;
  final int availableBytes;
  final int totalBytes;

  int get usedBytes => totalBytes - availableBytes;

  double get usedPercent => (usedBytes) / totalBytes;

  StorageDetails(
      this.appFilesDir, this.rootDir, this.availableBytes, this.totalBytes);

  factory StorageDetails.fromJson(Map json) {
    return StorageDetails(json["path"], json["rootPath"],
        json["availableBytes"], json["totalBytes"]);
  }

  static String getSize(int size) {
    String s = "";
    if (size < 1024) {
      s = size.toString() + " Bytes";
    } else if (size >= 1024 && size < (1024 * 1024)) {
      s = "${size ~/ pow(2, 10)}" + " KB";
    } else if (size >= (1024 * 1024) && size < (1024 * 1024 * 1024)) {
      s = "${size ~/ pow(2, 20)}" + " MB";
    } else if (size >= (1024 * 1024 * 1024) &&
        size < (1024 * 1024 * 1024 * 1024)) {
      s = "${size ~/ pow(2, 30)}" + " GB";
    } else if (size >= (1024 * 1024 * 1024 * 1024)) {
      s = "${size ~/ pow(2, 40)}" + " TB";
    }
    return s;
  }

  @override
  String toString() {
    return [
      '{',
      "appFileDir: " + appFilesDir,
      "rootDir: " + rootDir,
      "available: " + getSize(availableBytes),
      "total: " + getSize(totalBytes),
      "used: " + getSize(usedBytes),
      '}'
    ].join('\n');
  }
}
