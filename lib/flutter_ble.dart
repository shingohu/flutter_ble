import 'dart:async';

import 'package:flutter/services.dart';

class BleManager {
  static const MethodChannel _channel = const MethodChannel('flutter_ble');
  static BleManager _instance;

  factory BleManager() => _getInstance();

  static BleManager get instance => _getInstance();

  static BleManager _getInstance() {
    if (_instance == null) {
      _instance = BleManager._();
    }
    return _instance;
  }

  BleManager._() {
    _bleListener();
  }

  bool isBleOpen = false; //蓝牙是否打开
  bool isBleConnect = false; //是否连接设备

  void _bleListener() {
    _channel.setMethodCallHandler((call) async {
      if (call.method == "bleEnable") {
        isBleOpen = call.arguments;
      }
      if (call.method == "bleConnect") {
        isBleConnect = call.arguments;
      }
      return true;
    });
  }

  void initUUID(String targetDeviceName, String advertiseUUID,
      String mainServiceUUID, String characteristicUUID) {
    _channel.invokeMethod("initUUID", {
      "targetDeviceName": targetDeviceName,
      "advertiseUUID": advertiseUUID,
      "mainServiceUUID": mainServiceUUID,
      "characteristicUUID": characteristicUUID,
    });
  }

  void scanAndConnect() {
    _channel.invokeMethod("startScan");
  }

  void openBle() {
    _channel.invokeMethod("openBle");
  }
}
