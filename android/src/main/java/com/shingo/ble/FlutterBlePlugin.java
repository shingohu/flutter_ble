package com.shingo.ble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;

import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlutterBlePlugin
 */
public class FlutterBlePlugin implements MethodCallHandler, BleListener, FlutterPlugin {

    Context mContext;

    MethodChannel mChannel;


    private void initPlugin(BinaryMessenger messenger, Context context) {
        mChannel = new MethodChannel(messenger, "flutter_ble");
        mChannel.setMethodCallHandler(this);
        mContext = context;
        addBleListener();
    }


    @Override
    public void onMethodCall(MethodCall call, final Result result) {
        String method = call.method;
        if (method.equals("initUUID")) {
            initUUID((Map<String, Object>) call.arguments);
            result.success(true);
        } else if (method.equals("startScan")) {
            result.success(startScan());
        } else if (method.equals("stopScan")) {
            BleManager.getInstance().setStopScan();
            result.success(true);
        } else if (method.equals("openBle")) {
            result.success(openBle());
        } else if (method.equals("isBLEOpen")) {
            result.success(isBLEOpen());
        } else if (method.equals("MTU")) {
            result.success(BleManager.getInstance().getMTU());
        } else if (method.equals("isConnected")) {
            result.success(BleManager.getInstance().isConnected());
        } else if (method.equals("write")) {
            byte[] bytes = (byte[]) call.arguments;
            final Result tempResult = result;
            write(bytes, new BleWriteListener() {
                @Override
                public void onWriteSuccess() {
                    try {
                        tempResult.success(true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onWriteFailed() {
                    try {
                        tempResult.success(false);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        } else if (method.equals("isGPSEnable")) {
            result.success(isGPSEnable());
        } else if (method.equals("disconnect")) {
            BleManager.getInstance().setDisconnect();
            result.success(true);
        } else {
            result.notImplemented();
        }

    }


    private void initUUID(Map<String, Object> arguments) {
        String targetDeviceName = (String) arguments.get("deviceName");
        String advertiseUUID = (String) arguments.get("advertiseUUID");
        String mainServiceUUID = (String) arguments.get("mainServiceUUID");
        String nofitycharacteristicUUID = (String) arguments.get("notifycharacteristicUUID");
        String writecharacteristicUUID = (String) arguments.get("writecharacteristicUUID");
        int requestMTU = arguments.containsKey("requestMTU") ? (int) arguments.get("requestMTU") : 20;
        BleManager.getInstance().init(mContext, targetDeviceName, advertiseUUID, mainServiceUUID, nofitycharacteristicUUID, writecharacteristicUUID, requestMTU);
        mChannel.invokeMethod("bleEnable", BleManager.getInstance().isBluetoothOpen());
    }

    //扫描并连接
    private boolean startScan() {
        return BleManager.getInstance().startScan();
    }

    //强制开启BLE
    private boolean openBle() {
        return BleManager.getInstance().openBluetooth();
    }

    private boolean isBLEOpen() {
        return BleManager.getInstance().isBluetoothOpen();
    }


    private boolean isGPSEnable() {
        if (mContext != null) {
            return isGPSEnable(mContext);
        }
        return false;
    }


    //GPS是否开启
    public static final boolean isGPSEnable(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean networkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        boolean gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (networkProvider || gpsProvider) return true;
        return false;
    }


    private synchronized void write(byte[] bytes, BleWriteListener listener) {
        BleManager.getInstance().write(bytes, listener);
    }


    private void addBleListener() {
        BleManager.getInstance().addBleListener(this);
    }


    @Override
    public void onBleEnableChange(boolean enable) {

        if (mChannel != null) {
            UIHandler.of().post(() -> mChannel.invokeMethod("bleEnable", enable));
        }
    }

    @Override
    public void onBleConnectChange(boolean connect) {
        if (mChannel != null) {
            UIHandler.of().post(() -> mChannel.invokeMethod("bleConnect", connect));
        }
    }

    @Override
    public void onBleNotifyData(byte[] bytes) {
        if (mChannel != null) {
            UIHandler.of().post(() -> mChannel.invokeMethod("notify", bytes));
        }
    }


    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        initPlugin(binding.getBinaryMessenger(), binding.getApplicationContext());
    }

    @Override
    public void onDetachedFromEngine(FlutterPluginBinding binding) {
        mContext = null;
        mChannel.setMethodCallHandler(null);
        mChannel = null;
        BleManager.getInstance().removeBleListener(this);
    }


}
