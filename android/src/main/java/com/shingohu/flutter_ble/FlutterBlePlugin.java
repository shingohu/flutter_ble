package com.shingohu.flutter_ble;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;

import java.util.Map;

import cn.com.heaton.blelibrary.ble.utils.ByteUtils;
import io.flutter.Log;
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
public class FlutterBlePlugin implements MethodCallHandler, BleListener, FlutterPlugin, ActivityAware, PluginRegistry.RequestPermissionsResultListener {

    public static final int REQUEST_PERMISSION_LOCATION = 2018;
    Context mContext;
    Activity mActivity;
    MethodChannel mChannel;
    ActivityPluginBinding activityBinding;
    final UIHandler uiHandler = new UIHandler();


    private void initPlugin(BinaryMessenger messenger, Context context) {
        mChannel = new MethodChannel(messenger, "flutter_ble");
        mChannel.setMethodCallHandler(this);
        mContext = context;
        addBleListener();
    }


    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        FlutterBlePlugin plugin = new FlutterBlePlugin();
        plugin.initPlugin(registrar.messenger(), registrar.context());
        plugin.mActivity = registrar.activity();
        registrar.addRequestPermissionsResultListener(plugin);
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
                    uiHandler.post(() -> result.success(true));

                }

                @Override
                public void onWriteFailed() {
                    uiHandler.post(() -> result.success(false));
                }
            });
            return;
        }

        if (method.equals("isGPSEnable")) {
            result.success(isGPSEnable());
            return;
        }

        if (method.equals("checkLocationPermission")) {
            result.success(checkLocationPermission());
            return;
        }

    }


    private void initUUID(Map<String, String> arguments) {
        String targetDeviceName = arguments.get("targetDeviceName");
        String advertiseUUID = arguments.get("advertiseUUID");
        String mainServiceUUID = arguments.get("mainServiceUUID");
        String readcharacteristicUUID = arguments.get("readcharacteristicUUID");
        String nofitycharacteristicUUID = arguments.get("notifycharacteristicUUID");
        String writecharacteristicUUID = arguments.get("writecharacteristicUUID");
        BleManager.getInstance().init(mContext, targetDeviceName, advertiseUUID, mainServiceUUID, readcharacteristicUUID, nofitycharacteristicUUID, writecharacteristicUUID);
        mChannel.invokeMethod("bleEnable", BleManager.getInstance().isBluetoothOpen());
    }

    //扫描并连接
    private void startScan() {
        BleManager.getInstance().startScan();
    }

    //强制开启BLE
    private void openBle() {
        BleManager.getInstance().openBluetooth();
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

    public boolean checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int p = PermissionChecker.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION);
            return p == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public boolean shouldShowRequestPermissionRationale() {
        if (mActivity != null) {
            return ActivityCompat.shouldShowRequestPermissionRationale(mActivity, Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        return false;

    }

    private synchronized void write(String hexData, BleWriteListener listener) {
        BleManager.getInstance().write(hexData, listener);
       // testData(hexData);
    }

    private void testData(String hexData){

        byte[] data = ByteUtils.hexStr2Bytes(hexData);

        int index = 0;
        int length = data.length;
        int availableLength = length;
        int packLength = 20;
        while (index < length) {
            int onePackLength = packLength;

            onePackLength = (availableLength >= packLength ? packLength : availableLength);

            byte[] txBuffer = new byte[onePackLength];
            for (int i = 0; i < onePackLength; i++) {
                if (index < length) {
                    txBuffer[i] = data[index++];
                }
            }
            availableLength -= onePackLength;
            Log.e("BLE","分包发送数据"+ByteUtils.bytes2HexStr(txBuffer));
        }
    }


    private void addBleListener() {
        BleManager.getInstance().addBleListener(this);
    }


    @Override
    public void onBleEnableChange(boolean enable) {

        if (mChannel != null) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mChannel.invokeMethod("bleEnable", enable);
                }
            });

        }
    }

    @Override
    public void onBleConnectChange(boolean connect) {
        if (mChannel != null) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mChannel.invokeMethod("bleConnect", connect);
                }
            });

        }
    }

    @Override
    public void onBleNotifyData(String hexStr) {
        if (mChannel != null) {
            uiHandler.post(() -> mChannel.invokeMethod("notify", hexStr));
        }
    }

    @Override
    public void requestLocationPermission() {
        if (mActivity != null) {
            ActivityCompat.requestPermissions(mActivity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_LOCATION);
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
            activityBinding = null;
            mActivity = null;
        }
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults.length > 0)
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startScan();
                    return true;
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    //在用户已经拒绝授权的情况下，如果shouldShowRequestPermissionRationale返回false则
                    // 可以推断出用户选择了“不在提示”选项，在这种情况下需要引导用户至设置页手动授权
                    if (shouldShowRequestPermissionRationale()) {
                        ///用户拒绝了 并且不在提示
                        ///这里应该返回到Flutter端,让Flutter端弹出提示框
                        //todo
                    }
                }
        }
        return false;
    }
}
