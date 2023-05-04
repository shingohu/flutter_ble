
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

    BLE.initUUID(
        "Uart",
        "0000FFE0-0000-1000-8000-00805F9B34FB",
        "0000FFE0-0000-1000-8000-00805F9B34FB",
        "0000FFE1-0000-1000-8000-00805F9B34FB",
        "0000FFE1-0000-1000-8000-00805F9B34FB",
        "0000FFE1-0000-1000-8000-00805F9B34FB");

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
  void onBLENotifyData(String hexStr) {
    print('接收到数据' + hexStr);
    sb.write(hexStr);
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
            children: <Widget>[
              Text("蓝牙状态:${BLE.isBleOpen}"),
              Text("设备连接状态:${BLE.isBleConnect}"),
              Text("接收到数据:${sb.toString()}"),
            ],
          ),
        ),
      ),
    );
  }
}
