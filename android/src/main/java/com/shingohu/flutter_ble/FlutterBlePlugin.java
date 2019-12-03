package com.shingohu.flutter_ble;

import android.content.Context;

import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlutterBlePlugin
 */
public class FlutterBlePlugin implements MethodCallHandler, BleListener {
    Context mContext;
    final MethodChannel channel;

    FlutterBlePlugin(Registrar registrar) {
        channel = new MethodChannel(registrar.messenger(), "flutter_ble");
        channel.setMethodCallHandler(this);
        mContext = registrar.context();
        addBleListener();

    }


    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        new FlutterBlePlugin(registrar);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        String method = call.method;
        if (method.equals("initUUID")) {
            initUUID((Map<String, String>) call.arguments);
            result.success(true);
            return;
        }

        if (method.equals("startScan")) {
            startScan();
            result.success(true);
            return;
        }

        if (method.equals("openBle")) {

        }

    }


    private void initUUID(Map<String, String> arguments) {
        String targetDeviceName = arguments.get("targetDeviceName");
        String advertiseUUID = arguments.get("advertiseUUID");
        String mainServiceUUID = arguments.get("mainServiceUUID");
        String characteristicUUID = arguments.get("characteristicUUID");
        BleManager.getInstance().init(mContext, targetDeviceName, advertiseUUID, mainServiceUUID, characteristicUUID);
        channel.invokeMethod("bleEnable", BleManager.getInstance().isBluetoothOpen());
    }

    //扫描并连接
    private void startScan() {
        BleManager.getInstance().startScan();
    }

    //强制开启BLE
    private void openBle() {
        BleManager.getInstance().openBluetooth();
    }


    private void addBleListener() {
        BleManager.getInstance().addBleListener(this);
    }


    @Override
    public void onBleEnableChange(boolean enable) {
        channel.invokeMethod("bleEnable", enable);
    }

    @Override
    public void onBleConnectChange(boolean connect) {
        channel.invokeMethod("bleConnect", connect);
    }

    @Override
    public void onBleNotifyData(String hexStr) {

    }
}
