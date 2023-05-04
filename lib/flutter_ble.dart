import 'dart:async';

import 'package:flutter/services.dart';

late _BLEManager BLE = _BLEManager._();

class _BLEManager {
  static const MethodChannel _channel = const MethodChannel('flutter_ble');

  _BLEManager._() {
    _bleListener();
  }

  List<BLEListener> _bleListeners = [];

  void addListener(BLEListener bleListener) {
    if (!_bleListeners.contains(bleListener)) {
      _bleListeners.add(bleListener);
    }
  }

  void removeListener(BLEListener bleListener) {
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
      if (call.method == "onBleWriteError") {
        _notifyBleWriteError();
      }
      return true;
    });
  }

  void _notifyBleEnableChange(bool enable) {
    for (BLEListener listener in _bleListeners) {
      listener.onBLEEnableChange(enable);
    }
  }

  void _notifyBleConnectChange(bool connect) {
    for (BLEListener listener in _bleListeners) {
      listener.onBLEConnectChange(connect);
    }
  }

  void _notifyBleNotifyData(String hexStr) {
    for (BLEListener listener in _bleListeners) {
      listener.onBLENotifyData(hexStr);
    }
  }

  void _notifyBleWriteError() {
    for (BLEListener listener in _bleListeners) {
      listener.onBLEWriteError();
    }
  }

  ///初始化需要连接的设备的UUID等信息
  Future<void> initUUID(
      String targetDeviceName,
      String advertiseUUID,
      String mainServiceUUID,
      String readcharacteristicUUID,
      String nofitycharacteristicUUID,
      String writecharacteristicUUID) async {
    return _channel.invokeMethod("initUUID", {
      "targetDeviceName": targetDeviceName,
      "advertiseUUID": advertiseUUID,
      "mainServiceUUID": mainServiceUUID,
      "readcharacteristicUUID": readcharacteristicUUID,
      "notifycharacteristicUUID": nofitycharacteristicUUID,
      "writecharacteristicUUID": writecharacteristicUUID,
    });
  }

  ///初始化需要连接的设备的UUID等信息
  Future<void> init({
    String name,
    String advertiseUUID,
    String readUUID,
    String notifyUUID,
    String writeUUID,
  }) async {
    return _channel.invokeMethod("initUUID", {
      "targetDeviceName": targetDeviceName,
      "advertiseUUID": advertiseUUID,
      "mainServiceUUID": mainServiceUUID,
      "readcharacteristicUUID": readcharacteristicUUID,
      "notifycharacteristicUUID": nofitycharacteristicUUID,
      "writecharacteristicUUID": writecharacteristicUUID,
    });
  }

  ///扫描并且连接设备
  Future<void> startScan() async {
    return _channel.invokeMethod("startScan");
  }

  ///强制打开蓝牙(Android Only)
  /// IOS 跳转到设置界面
  void openBLESetting() {
    _channel.invokeMethod("openBle");
  }

  ///蓝牙是否开启
  Future<bool> isOpen() async {
    return await _channel.invokeMethod<bool>("isOpen") ?? false;
  }

  ///停止扫描
  Future<void> stopScan() async {
    return _channel.invokeMethod("stopScan");
  }

  ///断开连接
  Future<void> disconnect() {
    return _channel.invokeMethod("disconnect");
  }

  ///断开并重新连接
  void disAndReConnect() {
    _channel.invokeMethod("disAndReConnect");
  }

  ///写入数据,数据需要编码成16进制字符串
  Future<bool> write(String hexStr) async {
    bool result = await _channel.invokeMethod<bool>("write", hexStr) ?? false;
    return result;
  }
}

abstract class BLEListener {
  void onBLEEnableChange(bool enable) {}

  void onBLEConnectChange(bool connect) {}

  void onBLENotifyData(String hexStr) {}

  void onBLEWriteError() {}
}
