package com.shingohu.flutter_ble;

import android.app.Activity;
import android.content.pm.PackageManager;

import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.view.FlutterNativeView;

/**
 * FlutterBlePlugin
 */
public class FlutterBlePlugin implements MethodCallHandler, BleListener, PluginRegistry.RequestPermissionsResultListener,PluginRegistry.ViewDestroyListener {
    Activity mActivity;
    final MethodChannel channel;
    final UIHandler uiHandler = new UIHandler();

    FlutterBlePlugin(Registrar registrar) {
        channel = new MethodChannel(registrar.messenger(), "flutter_ble");
        channel.setMethodCallHandler(this);
        mActivity = registrar.activity();
        registrar.addRequestPermissionsResultListener(this);
        registrar.addViewDestroyListener(this);
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
            openBle();
            result.success(true);
            return;
        }

        if (method.equals("write")) {
            String hexData = (String) call.arguments;
            write(hexData, new BleWriteListener() {
                @Override
                public void onWriteSuccess() {
                    result.success(true);
                }

                @Override
                public void onWriteFailed() {
                    result.success(false);
                }
            });

        }

    }


    private void initUUID(Map<String, String> arguments) {
        String targetDeviceName = arguments.get("targetDeviceName");
        String advertiseUUID = arguments.get("advertiseUUID");
        String mainServiceUUID = arguments.get("mainServiceUUID");
        String readcharacteristicUUID = arguments.get("readcharacteristicUUID");
        String nofitycharacteristicUUID = arguments.get("notifycharacteristicUUID");
        String writecharacteristicUUID = arguments.get("writecharacteristicUUID");
        BleManager.getInstance().init(mActivity, targetDeviceName, advertiseUUID, mainServiceUUID, readcharacteristicUUID, nofitycharacteristicUUID, writecharacteristicUUID);
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


    private void write(String hexData, BleWriteListener listener) {
        BleManager.getInstance().write(hexData, listener);
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
        uiHandler.post(() -> channel.invokeMethod("notify", hexStr));

    }

    @Override
    public boolean onRequestPermissionsResult(int i, String[] strings, int[] grantResults) {
        if (i == 2018) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onViewDestroy(FlutterNativeView flutterNativeView) {

        return false;
    }
}
