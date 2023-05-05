import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_ble/flutter_ble.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> with BLEListener {
  StringBuffer sb = StringBuffer();

  @override
  void initState() {
    BLE.addListener(this);

    BLE.init(
        name: "Uart",
        advertiseUUID: "0000FFE0-0000-1000-8000-00805F9B34FB",
        mainServiceUUID: "0000FFE0-0000-1000-8000-00805F9B34FB",
        notifyUUID: "0000FFE1-0000-1000-8000-00805F9B34FB",
        writeUUID: "0000FFE1-0000-1000-8000-00805F9B34FB",
        requestMTU: 20);

    super.initState();
  }

  @override
  void onBLEEnableChange(bool enable) {
    if (mounted) setState(() {});
  }

  @override
  void onBLEConnectChange(bool connect) {
    if (mounted) setState(() {});
  }

  @override
  void onBLENotifyData(Uint8List bytes) {
    print('接收到数据' + bytes.toString());
    sb.write(bytes.toString());
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    BLE.removeListener(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Ble app'),
        ),
        body: Center(
          child: Column(
            children: <Widget>[],
          ),
        ),
      ),
    );
  }
}
