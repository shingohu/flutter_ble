#import "FlutterBlePlugin.h"
#import <flutter_ble/flutter_ble-Swift.h>

@implementation FlutterBlePlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterBlePlugin registerWithRegistrar:registrar];
}
@end
