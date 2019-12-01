#import "FlutterHttpdPlugin.h"
#import <flutter_httpd/flutter_httpd-Swift.h>

@implementation FlutterHttpdPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterHttpdPlugin registerWithRegistrar:registrar];
}
@end
