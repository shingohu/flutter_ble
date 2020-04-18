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

  List<BleListener> _bleListeners = [];

  void addBleListener(BleListener bleListener) {
    if (!_bleListeners.contains(bleListener)) {
      _bleListeners.add(bleListener);
    }
  }

  void removeBleListener(BleListener bleListener) {
    if (_bleListeners.contains(bleListener)) {
      _bleListeners.remove(bleListener);
    }
  }

  bool isBleOpen = false; //蓝牙是否打开
  bool isBleConnect = false; //是否连接蓝牙设备

  void _bleListener() {
    _channel.setMethodCallHandler((call) async {
      if (call.method == "bleEnable") {
        isBleOpen = call.arguments;
        _notifyBleEnableChange(isBleOpen);
      }
      if (call.method == "bleConnect") {
        isBleConnect = call.arguments;
        _notifyBleConnectChange(isBleConnect);
      }
      if (call.method == "notify") {
        _notifyBleNotifyData(call.arguments);
      }
      return true;
    });
  }

  void _notifyBleEnableChange(bool enable) {
    for (BleListener listener in _bleListeners) {
      listener.onBleEnableChange(enable);
    }
  }

  void _notifyBleConnectChange(bool connect) {
    for (BleListener listener in _bleListeners) {
      listener.onBleConnectChange(connect);
    }
  }

  void _notifyBleNotifyData(String hexStr) {
    for (BleListener listener in _bleListeners) {
      listener.onBleNotifyData(hexStr);
    }
  }

  ///初始化需要连接的设备的UUID等信息
  void initUUID(
      String targetDeviceName,
      String advertiseUUID,
      String mainServiceUUID,
      String readcharacteristicUUID,
      String nofitycharacteristicUUID,
      String writecharacteristicUUID) {
    _channel.invokeMethod("initUUID", {
      "targetDeviceName": targetDeviceName,
      "advertiseUUID": advertiseUUID,
      "mainServiceUUID": mainServiceUUID,
      "readcharacteristicUUID": readcharacteristicUUID,
      "notifycharacteristicUUID": nofitycharacteristicUUID,
      "writecharacteristicUUID": writecharacteristicUUID,
    });
  }

  ///扫描并且连接设备
  void scanAndConnect() {
    _channel.invokeMethod("startScan");
  }

  ///强制打开蓝牙(Android Only)
  /// IOS 跳转到设置界面
  void openBle() {
    _channel.invokeMethod("openBle");
  }

  ///写入数据,数据需要编码成16进制字符串
  Future<bool> write(String hexStr) {
    return _channel.invokeMethod("write", hexStr);
  }
}

abstract class BleListener {
  void onBleEnableChange(bool enable) {}

  void onBleConnectChange(bool connect) {}

  void onBleNotifyData(String hexStr) {}
}
