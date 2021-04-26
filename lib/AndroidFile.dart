import 'package:flutter_httpd/StorageDetail.dart';

class AndroidFile {
  final int id;
  final String title;
  final String displayName;
  final String nativeURL;
  final int size;
  final DateTime dateAdded;
  final DateTime dateModified;

  AndroidFile(this.id, this.title, this.displayName, this.nativeURL, this.size,
      this.dateAdded, this.dateModified);

  factory AndroidFile.fromJson(Map json) {
    return AndroidFile(
        json["id"].toInt(),
        json["title"],
        json["display_name"],
        json["nativeURL"],
        json["size"].toInt(),
        DateTime.fromMillisecondsSinceEpoch(json["date_added"].toInt()),
        DateTime.fromMillisecondsSinceEpoch(json["date_modified"].toInt()));
  }

  @override
  String toString() {
    return [
      '{',
      "id: " + id.toString(),
      "title: " + title,
      "displayName: " + displayName,
      "nativeUrl: " + nativeURL,
      "size: " + StorageDetail.getSize(size),
      "dateAdded: " + dateAdded.toIso8601String(),
      "dateModified: " + dateModified.toIso8601String(),
      '}'
    ].join('\n');
  }
}
