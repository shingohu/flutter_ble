import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_ble/flutter_ble.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> with BleListener {
  StringBuffer sb = StringBuffer();

  @override
  void initState() {
    BleManager.instance.addBleListener(this);

    BleManager.instance.initUUID(
        "Uart",
        "0000FFE0-0000-1000-8000-00805F9B34FB",
        "0000FFE0-0000-1000-8000-00805F9B34FB",
        "0000FFE1-0000-1000-8000-00805F9B34FB",
        "0000FFE1-0000-1000-8000-00805F9B34FB",
        "0000FFE1-0000-1000-8000-00805F9B34FB");

    super.initState();
  }

  @override
  void onBleEnableChange(bool enable) {
    if (mounted) setState(() {});
  }

  @override
  void onBleConnectChange(bool connect) {
    if (mounted) setState(() {});
  }

  @override
  void onBleNotifyData(String hexStr) {
    print('接收到数据' + hexStr);
    sb.write(hexStr);
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    BleManager.instance.removeBleListener(this);
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
              Text("蓝牙状态:${BleManager.instance.isBleOpen}"),
              Text("设备连接状态:${BleManager.instance.isBleConnect}"),
              Text("接收到数据:${sb.toString()}"),
            ],
          ),
        ),
      ),
    );
  }
}
