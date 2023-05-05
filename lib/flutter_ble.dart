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

  void _bleListener() {
    _channel.setMethodCallHandler((call) async {
      if (call.method == "bleEnable") {
        _notifyBleEnableChange(call.arguments);
      }
      if (call.method == "bleConnect") {
        _notifyBleConnectChange(call.arguments);
      }
      if (call.method == "notify") {
        _notifyBleNotifyData(call.arguments);
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

  void _notifyBleNotifyData(Uint8List bytes) {
    for (BLEListener listener in _bleListeners) {
      listener.onBLENotifyData(bytes);
    }
  }

  ///初始化需要连接的设备的UUID等信息
  Future<void> init(
      {String? name,
      required String advertiseUUID,
      required String mainServiceUUID,
      required String notifyUUID,
      required String writeUUID,
      int requestMTU = 20}) async {
    return _channel.invokeMethod("initUUID", {
      "deviceName": name ?? "",
      "advertiseUUID": advertiseUUID,
      "mainServiceUUID": mainServiceUUID,
      "notifycharacteristicUUID": notifyUUID,
      "writecharacteristicUUID": writeUUID,
      "requestMTU": requestMTU,
    });
  }

  ///扫描并且连接设备
  Future<bool> startScan() async {
    return await _channel.invokeMethod("startScan");
  }

  ///停止扫描
  Future<void> stopScan() async {
    return _channel.invokeMethod("stopScan");
  }

  ///强制打开蓝牙(Android Only)
  /// IOS 跳转到设置界面
  Future<void> openSetting() async {
    await _channel.invokeMethod("openBle");
  }

  ///蓝牙是否开启
  Future<bool> isOpen() async {
    return await _channel.invokeMethod<bool>("isBLEOpen") ?? false;
  }

  ///是否已经连接上
  Future<bool> isConnected() async {
    return await _channel.invokeMethod<bool>("isConnected") ?? false;
  }

  ///获取MTU大小
  Future<int> MTU() async {
    return await _channel.invokeMethod<int>("mtu") ?? 20;
  }

  ///断开连接
  Future<void> disconnect() {
    return _channel.invokeMethod("disconnect");
  }

  ///GPS是否开启,android低于31以下版本蓝牙权限的时候需要
  Future<bool> isGPSEnable() async {
    return await _channel.invokeMethod("isGPSEnable");
  }

  ///写入数据
  Future<bool> write(Uint8List bytes) async {
    bool result = await _channel.invokeMethod<bool>("write", bytes) ?? false;
    return result;
  }
}

abstract class BLEListener {
  void onBLEEnableChange(bool enable) {}

  void onBLEConnectChange(bool connect) {}

  void onBLENotifyData(Uint8List bytes) {}
}
