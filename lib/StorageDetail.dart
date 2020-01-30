import 'dart:math';

class StorageDetail {
  final String name;
  final String appFilesDir;
  final String rootDir;
  final int size;
  final int availableBytes;
  final int totalBytes;

  StorageDetail(this.name, this.appFilesDir, this.rootDir, this.size,
      this.availableBytes, this.totalBytes);

  factory StorageDetail.fromJson(Map json) {
    return StorageDetail(json["name"], json["path"], json["rootPath"],
        json["size"], json["availableBytes"], json["totalBytes"]);
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
      "name: " + name,
      "appFileDir: " + appFilesDir,
      "rootDir: " + rootDir,
      "size: " + getSize(size),
      "available: " + getSize(availableBytes),
      "total: " + getSize(totalBytes),
      '}'
    ].join('\n');
  }
}
