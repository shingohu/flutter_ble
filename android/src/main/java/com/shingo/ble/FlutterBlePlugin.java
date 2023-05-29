package com.shingo.ble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;

import java.util.HashMap;
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
public class FlutterBlePlugin implements MethodCallHandler, BleListener, FlutterPlugin, ActivityAware, PluginRegistry.RequestPermissionsResultListener, PluginRegistry.ActivityResultListener {


    Context mContext;


    Activity mActivity;
    MethodChannel mChannel;
    ActivityPluginBinding activityBinding;

    Map<Integer, PermissionCallback> permissionCallbackMap = new HashMap<>();
    Map<Integer, PermissionCallback> locationServiceCallbackMap = new HashMap<>();
    Map<Integer, PermissionCallback> openBluetoothCallbackMap = new HashMap<>();


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
        } else if (method.equals("startScanWithResult")) {
            int scanPeriod = call.argument("scanPeriod");
            startScanAndResult(scanPeriod);
        } else if (method.equals("stopScanWithResult")) {
            stopScanWithResult();
            result.success(true);
        } else if (method.equals("connectById")) {
            String id = call.argument("id");
            connectById(id, new BleManager.ConnectCallback() {
                @Override
                public void onConnected() {
                    result.success(true);
                }

                @Override
                public void onConnectFailed() {
                    result.success(false);
                }
            });

        } else if (method.equals("openBle")) {
            openBluetooth(result);
        } else if (method.equals("openAppSetting")) {
            openAppSettings();
            result.success(true);
        } else if (method.equals("isBLEOpen")) {
            result.success(isBLEOpen());
        } else if (method.equals("MTU")) {
            result.success(BleManager.getInstance().getMTU());
        } else if (method.equals("isConnected")) {
            result.success(BleManager.getInstance().isConnected());
        } else if (method.equals("requestPermission")) {
            requestPermission(result);
        } else if (method.equals("openLocationService")) {
            openLocationService(result);
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
        } else if (method.equals("connectedDeviceInfo")) {
            result.success(BleManager.getInstance().getConnectedInfo());
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
        boolean autoConnect = (boolean) arguments.get("autoConnect");
        BleManager.getInstance().init(mContext, targetDeviceName, advertiseUUID, mainServiceUUID, nofitycharacteristicUUID, writecharacteristicUUID, requestMTU, autoConnect);
    }

    //扫描并连接
    private boolean startScan() {
        return BleManager.getInstance().startScan();
    }

    private void connectById(String id, BleManager.ConnectCallback callback) {
        BleManager.getInstance().connectById(id, callback);
    }

    private void startScanAndResult(int scanPeriod) {
        BleManager.getInstance().startScanWithResult(scanPeriod, result -> UIHandler.of().post(() -> mChannel.invokeMethod("scanResult", result)));
    }

    private void stopScanWithResult() {
        BleManager.getInstance().stopScanWithResult();
    }


    //开启BLE
    private void openBluetooth(Result result) {
        PermissionCallback callback = new PermissionCallback() {
            @Override
            void onPermission(boolean hasPermission) {
                result.success(hasPermission);
                openBluetoothCallbackMap.remove(result.hashCode());
            }
        };
        openBluetoothCallbackMap.put(result.hashCode(), callback);
        if (BleManager.getInstance().isBluetoothOpen()) {
            callback.onPermission(true);
        } else {
            if (mActivity != null) {
                Intent btIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                btIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, mActivity.getPackageName());
                mActivity.startActivityForResult(btIntent, result.hashCode());
            }
        }

    }

    private void openAppSettings() {
        if (mActivity != null) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            this.mActivity.startActivity(intent);

        }
    }


    ///打开定位服务,android 12以下蓝牙权限需要
    private void openLocationService(Result result) {

        PermissionCallback callback = new PermissionCallback() {
            @Override
            void onPermission(boolean hasPermission) {
                result.success(hasPermission);
                locationServiceCallbackMap.remove(result.hashCode());
            }
        };
        locationServiceCallbackMap.put(result.hashCode(), callback);
        if (isGPSEnable()) {
            callback.onPermission(true);
        } else {

            if (mActivity != null) {
                Intent locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                mActivity.startActivityForResult(locationIntent, result.hashCode());
            }
        }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ///android31上面蓝牙搜索连接不需要GPS开启了,所有直接返回true
            //  return true;
        }
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean networkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        boolean gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (networkProvider || gpsProvider) return true;
        return false;
    }

    public boolean checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int p1 = PermissionChecker.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN);
            int p2 = PermissionChecker.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT);
            return p1 == PackageManager.PERMISSION_GRANTED && p2 == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int p = PermissionChecker.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION);
            return p == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }


    public void requestPermission(Result result) {

        PermissionCallback callback = new PermissionCallback() {
            @Override
            void onPermission(boolean hasPermission) {
                result.success(hasPermission);
                permissionCallbackMap.remove(result.hashCode());
            }
        };
        permissionCallbackMap.put(result.hashCode(), callback);


        if (checkBluetoothPermission()) {
            callback.onPermission(true);
        } else {
            if (mActivity != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    //targetSdkVersion 31 更改了蓝牙相关权限
                    ActivityCompat.requestPermissions(mActivity, new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, result.hashCode());
                } else {
                    //targetSdkVersion 28以及以下使用模糊定位权限即可,但是如果是29以及以上要使用精准定位权限,否则无法搜索到蓝牙
                    ActivityCompat.requestPermissions(mActivity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, result.hashCode());
                }
            }
        }


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

    @Override
    public void onAttachedToActivity(ActivityPluginBinding binding) {
        activityBinding = binding;
        mActivity = binding.getActivity();
        binding.addActivityResultListener(this);
        binding.addRequestPermissionsResultListener(this);


    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {

    }

    @Override
    public void onDetachedFromActivity() {
        if (activityBinding != null) {
            activityBinding.removeRequestPermissionsResultListener(this);
            activityBinding.removeActivityResultListener(this);
            activityBinding = null;
            mActivity = null;
        }

    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (permissionCallbackMap.containsKey(requestCode)) {
            if (grantResults.length > 0)
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionCallbackMap.get(requestCode).onPermission(true);
                    return true;
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    permissionCallbackMap.get(requestCode).onPermission(false);
                }
        }
        return false;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (locationServiceCallbackMap.containsKey(requestCode)) {
            locationServiceCallbackMap.get(requestCode).onPermission(isGPSEnable());
            return true;
        }
        if (openBluetoothCallbackMap.containsKey(requestCode)) {
            openBluetoothCallbackMap.get(requestCode).onPermission(isGPSEnable());
            return true;
        }
        return false;
    }


    abstract class PermissionCallback {
        abstract void onPermission(boolean hasPermission);
    }

}
