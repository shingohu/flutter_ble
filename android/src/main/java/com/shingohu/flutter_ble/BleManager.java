package com.shingohu.flutter_ble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import cn.com.heaton.blelibrary.ble.Ble;
import cn.com.heaton.blelibrary.ble.BleFactory;
import cn.com.heaton.blelibrary.ble.callback.BleConnectCallback;
import cn.com.heaton.blelibrary.ble.callback.BleNotiftCallback;
import cn.com.heaton.blelibrary.ble.callback.BleScanCallback;
import cn.com.heaton.blelibrary.ble.callback.BleWriteEntityCallback;
import cn.com.heaton.blelibrary.ble.model.BleDevice;
import cn.com.heaton.blelibrary.ble.model.EntityData;
import cn.com.heaton.blelibrary.ble.model.ScanRecord;
import cn.com.heaton.blelibrary.ble.utils.ByteUtils;

public class BleManager {

    private final static BleManager instance = new BleManager();
    private String targetDeviceName = "Uart";//设备名称,用来过滤
    private String advertiseUUID = "0000FFE0-0000-1000-8000-00805F9B34FB"; //设备广播出来的UUID,用来过滤
    private String mainSeviceUUID = "0000FFE0-0000-1000-8000-00805F9B34FB"; //主服务UUID
    private String readCharacteristicUUID = "0000FFE1-0000-1000-8000-00805F9B34FB";//主特征读UUID
    private String writeCharacteristicUUID = "0000FFE1-0000-1000-8000-00805F9B34FB";//主特征通知UUID
    private String notifyCharacteristicUUID = "0000FFE1-0000-1000-8000-00805F9B34FB";//主特征写UUID

    private Ble<BleDevice> mBle;
    private Context mContext;

    private HashSet<BleListener> bleListeners = new HashSet<>();

    private boolean autoScan = true;// 是否自动扫描连接 ;//主动断开的时候设置为false

    public void addBleListener(BleListener bleListener) {
        bleListeners.add(bleListener);
    }

    public void removeBleListener(BleListener bleListener) {
        bleListeners.remove(bleListener);
    }


    private BleDevice targetDevice;

    private BleManager() {

    }


    public static BleManager getInstance() {
        return instance;
    }


    public void init(Context context, String targetDeviceName, String advertiseUUID, String mainSeviceUUID, String readCharacteristicUUID, String notifyCharacteristicUUID, String writeCharacteristicUUID) {
        this.mContext = context;
        this.targetDeviceName = targetDeviceName;
        this.advertiseUUID = advertiseUUID;
        this.mainSeviceUUID = mainSeviceUUID;
        this.readCharacteristicUUID = readCharacteristicUUID;
        this.notifyCharacteristicUUID = notifyCharacteristicUUID;
        this.writeCharacteristicUUID = writeCharacteristicUUID;
        initBle();
    }


    public boolean isBluetoothOpen() {
        if (mBle != null) {
            return mBle.isBleEnable();
        }
        return false;
    }

    public void openBluetooth() {
        if (mBle != null) {
            mBle.turnOnBlueToothNo();
        }

    }

    public boolean isDeviceConnect() {
        if (targetDevice != null) {
            return targetDevice.isConnected();
        }
        return false;
    }


    private void startNotify() {
        if (targetDevice != null) {
            mBle.startNotify(targetDevice, new BleNotiftCallback<BleDevice>() {

                @Override
                public void onChanged(BleDevice device, BluetoothGattCharacteristic characteristic) {
                    String data = ByteUtils.byteArrayToHexStr(characteristic.getValue());
                    for (BleListener listener : bleListeners) {
                        listener.onBleNotifyData(data);
                    }
                }

            });
        }

    }

    public void write(String hexStr, BleWriteListener writeListener) {
        if (isDeviceConnect()) {
            EntityData data = new EntityData.Builder()
                    .setAutoWriteMode(true)
                    .setData(ByteUtils.hexStrToByteArray(hexStr))
                    .setPackLength(20)
                    .setAddress(targetDevice.getBleAddress()).build();
            mBle.writeEntity(data, new BleWriteEntityCallback<BleDevice>() {
                @Override
                public void onWriteSuccess() {
                    if (writeListener != null) {
                        writeListener.onWriteSuccess();
                    }
                }

                @Override
                public void onWriteFailed() {
                    if (writeListener != null) {
                        writeListener.onWriteFailed();
                    }
                }
            });
        } else {
            if (writeListener != null) {
                writeListener.onWriteFailed();
            }
        }
    }


    private static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }


    private void initBle() {
        if (mBle != null) {
            return;
        }
        Ble.Options options = Ble.options()
                .setThrowBleException(true)//设置是否抛出蓝牙异常
                .setAutoConnect(true)//设置是否自动连接
                .setFilterScan(true)//设置是否过滤扫描到的设备
                .setConnectFailedRetryCount(3)
                .setUuidService(UUID.fromString(mainSeviceUUID))
                .setUuidNotify(UUID.fromString(notifyCharacteristicUUID))
                .setUuidWriteCha(UUID.fromString(writeCharacteristicUUID))
                .setUuidReadCha(UUID.fromString(readCharacteristicUUID))
                .setConnectTimeout(10 * 1000)//设置连接超时时长
                .setScanPeriod(12 * 1000);
        mBle = options.create(mContext);
        bleStatusListener();
        if (!mBle.isBleEnable()) {
            mBle.turnOnBlueToothNo();//强制开启蓝牙
        }
//        else {
//            //需要延时，等待service bind完成
//            new Handler().postDelayed(this::startScan, 2000);
//        }

    }

    public boolean checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int p = PermissionChecker.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION);
            return p == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public void requestLocationPermission() {

       // if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) mContext, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            AlertDialog dialog = new AlertDialog.Builder(mContext, R.style.Theme_AppCompat_Light_Dialog_Alert)
                    .setTitle("提示")
                    .setMessage("Android 6.0开始需要打开位置权限才可以搜索到蓝牙设备,请授权App使用位置权限以便正常使用")
                    .setCancelable(false)
                    .setPositiveButton("好的", (dialog1, which) -> {
                        dialog1.dismiss();
                        ActivityCompat.requestPermissions((Activity) mContext, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 2018);
                    })
                    .create();
            dialog.show();


//        } else {
//            ActivityCompat.requestPermissions((Activity) mContext, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 2018);
//        }


    }


    //GPS是否开启
    public static final boolean isLocationEnable(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean networkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        boolean gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (networkProvider || gpsProvider) return true;
        return false;
    }


    public void startScan() {

        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        if (targetDevice == null && mBle != null) {
            if (!mBle.isBleEnable() || mBle.isScanning()) {
                return;
            }
            autoScan = true;
            Log.e("BLE", "开始扫描");
            mBle.startScan(new BleScanCallback<BleDevice>() {
                @Override
                public void onLeScan(BleDevice device, int rssi, byte[] scanRecord) {
                    synchronized (mBle.getLocker()) {
                        ScanRecord parseRecord = ScanRecord.parseFromBytes(scanRecord);
                        String bleName = device.getBleName();

                        boolean foundTargetDevice = false;


                        // Log.e("BLE", "扫描到设备" + bleName + parseRecord.toString());
                        if (bleName != null && targetDeviceName != null && bleName.startsWith(targetDeviceName)) {

                            //可能找到指定的设备
                            if (parseRecord.getServiceUuids() != null && advertiseUUID != null) {
                                for (ParcelUuid uuid : parseRecord.getServiceUuids()) {
                                    if (uuid.getUuid().toString().equals(UUID.fromString(advertiseUUID).toString())) {
                                        //可能找到指定的设备
                                        foundTargetDevice = true;
                                        targetDevice = device;
                                        break;
                                    }
                                }
                            } else {
                                foundTargetDevice = true;
                                targetDevice = device;
                            }
                        } else {
                            if (parseRecord.getServiceUuids() != null && advertiseUUID != null) {
                                for (ParcelUuid uuid : parseRecord.getServiceUuids()) {

                                    if (uuid.getUuid().toString().equals(UUID.fromString(advertiseUUID).toString())) {
                                        //可能找到指定的设备
                                        foundTargetDevice = true;
                                        targetDevice = device;
                                        break;
                                    }
                                }

                            }
                        }
                        if (targetDevice != null && foundTargetDevice) {
                            connectTargetDevice();
                        }
                    }
                }

                @Override
                public void onStop() {
                    ///停止搜索
                    if (targetDevice == null) {
                        //没有搜索到,查看系统是否连接上了
                        BluetoothDevice device = getSystemBondDevice();
                        if (device != null) {
                            targetDevice = BleFactory.create(device);
                            connectTargetDevice();
                        } else {
                            startScan();
                        }
                    }
                }
            });
        }

    }

    private void connectTargetDevice() {
        if (targetDevice != null) {
            Log.e("BLE", "找到指定的设备,停止扫描,开始连接");
            stopScan();
            connect();
        }
    }

    private void stopScan() {
        if (mBle != null) {
            mBle.stopScan();
        }
    }

    ///主动断开连接
    public void disconnect() {
        autoScan = false;
        if (mBle != null) {
            stopScan();
            if (targetDevice != null) {
                mBle.disconnect(targetDevice);
            }
        }
    }


    public void destory() {
        if (mBle != null) {
            disconnect();
            bleListeners.clear();
            targetDevice = null;
            mBle = null;
            mContext = null;
        }
    }


    private void connect() {
        if (targetDevice != null) {
            targetDevice.setAutoConnect(true);
            mBle.connect(targetDevice, new BleConnectCallback<BleDevice>() {
                @Override
                public void onConnectionChanged(BleDevice device) {
                    targetDevice = device;
                    if (!device.isConnectting()) {
                        if (device.isConnected()) {
                            ///连接成功
                            Log.e("BLE", "连接成功");
                        } else {
                            Log.e("BLE", "连接失败");
                        }
                    } else {
                        Log.e("BLE", "连接中");
                    }
                    for (BleListener listener : bleListeners) {
                        listener.onBleConnectChange(device.isConnected());
                    }

                }

                @Override
                public void onReady(BleDevice device) {
                    super.onReady(device);
                    startNotify();
                }

                @Override
                public void onConnectException(BleDevice device, int errorCode) {
                    super.onConnectException(device, errorCode);
                    Log.e("BLE", "连接异常" + errorCode);
                }

                @Override
                public void onConnectTimeOut(BleDevice device) {
                    super.onConnectTimeOut(device);
                    Log.e("BLE", "连接超时");
                }
            });


        }

    }


    ///蓝牙状态
    private void bleStatusListener() {
        mBle.setBleStatusCallback(isOn -> {
            for (BleListener listener : bleListeners) {
                listener.onBleEnableChange(isOn);
            }
            if (autoScan && isOn) {
                Log.e("BLE", "蓝牙已开启");
                startScan();
            }
            if (!isOn) {
                Log.e("BLE", "蓝牙已关闭");
                stopScan();
            }

        });
    }


    ///获取系统已经配对的设备
    private BluetoothDevice getSystemBondDevice() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> devices = adapter.getBondedDevices();
        for (BluetoothDevice device : devices) {
            if (device.getName().startsWith(targetDeviceName)) {
                return device;
            }
        }
        return null;
    }

}
