package com.shingohu.flutter_ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.PermissionChecker;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import cn.com.heaton.blelibrary.ble.Ble;
import cn.com.heaton.blelibrary.ble.callback.BleConnectCallback;
import cn.com.heaton.blelibrary.ble.callback.BleNotifyCallback;
import cn.com.heaton.blelibrary.ble.callback.BleScanCallback;
import cn.com.heaton.blelibrary.ble.callback.BleWriteEntityCallback;
import cn.com.heaton.blelibrary.ble.model.BleDevice;
import cn.com.heaton.blelibrary.ble.model.BleFactory;
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

    //private boolean autoScan = true;// 是否自动扫描连接 ;//主动断开的时候设置为false

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

            mBle.enableNotify(targetDevice, true, new BleNotifyCallback<BleDevice>() {

                @Override
                public void onChanged(BleDevice device, BluetoothGattCharacteristic characteristic) {
                    String data = ByteUtils.bytes2HexStr(characteristic.getValue()).toUpperCase();
                    //Log.e("BLE", "接收数据" + data);
                    for (BleListener listener : bleListeners) {
                        listener.onBleNotifyData(data);
                    }
                }

                @Override
                public void onNotifySuccess(BleDevice device) {
                    Log.e("BLE", "通知成功");
                    for (BleListener listener : bleListeners) {
                        listener.onBleConnectChange(device.isConnected());
                    }
                }

                @Override
                public void onNotifyCanceled(BleDevice device) {
                    Log.e("BLE", "通知取消");
                }
            });
        }


    }

    public void write(String hexStr, final BleWriteListener writeListener) {
        if (isDeviceConnect()) {
            EntityData data = new EntityData.Builder()
                    .setAutoWriteMode(false)
                    .setData(ByteUtils.hexStr2Bytes(hexStr))
                    .setPackLength(20)
                    .setDelay(50)
                    .setLastPackComplete(false)
                    .setAddress(targetDevice.getBleAddress()).build();

            mBle.writeEntity(data, new BleWriteEntityCallback<BleDevice>() {
                @Override
                public void onWriteSuccess() {

                    if (writeListener != null) {
                        UIHandler.of().post(writeListener::onWriteSuccess);
                    }
                }

                @Override
                public void onWriteProgress(double progress) {
                    // Log.e("BLE","写入数据进度:"+progress);
                }

                @Override
                public void onWriteFailed() {
                    if (writeListener != null) {
                        UIHandler.of().post(writeListener::onWriteFailed);
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


    private synchronized void initBle() {
        if (mBle != null) {
            return;
        }
        Ble.Options options = Ble.options()
                .setThrowBleException(true)//设置是否抛出蓝牙异常
                // .setAutoConnect(true)//设置是否自动连接
                .setFactory(new BleFactory() {
                    @Override
                    public BleDevice create(String address, String name) {
                        return super.create(address, name);
                    }
                })
                .setLogBleEnable(false)
                .setConnectFailedRetryCount(3)
                .setUuidService(UUID.fromString(mainSeviceUUID))
                .setUuidNotifyCha(UUID.fromString(notifyCharacteristicUUID))
                .setUuidWriteCha(UUID.fromString(writeCharacteristicUUID))
                .setUuidReadCha(UUID.fromString(readCharacteristicUUID))
                .setConnectTimeout(10 * 1000)//设置连接超时时长
                .setScanPeriod(12 * 1000);
        mBle = options.create(mContext);
        bleStatusListener();
        if (!isBluetoothOpen()) {
            openBluetooth();//强制开启蓝牙
        } else {
            startScan();
        }
    }


    public boolean checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int p = PermissionChecker.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION);
            return p == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }


    public synchronized void startScan() {

        if (!checkLocationPermission()) {
            for (BleListener listener : bleListeners) {
                listener.requestLocationPermission();
            }
            Log.e("BLE", "没有定位权限 无法搜索到蓝牙设备");
            return;
        }

        if (targetDevice == null && mBle != null) {
            Log.e("BLE", "开始扫描");
            mBle.startScan(new BleScanCallback<BleDevice>() {
                @Override
                public void onLeScan(BleDevice device, int rssi, byte[] scanRecord) {
                    synchronized (mBle.getLocker()) {
                        if (targetDevice != null) {
                            ///找到了指定的设备
                            return;
                        }

                        String bleName = device.getBleName();
                        if (TextUtils.isEmpty(bleName)) {
                            return;
                        }
                        ScanRecord parseRecord = ScanRecord.parseFromBytes(scanRecord);
                        Log.e("BLE", "扫描到设备" + bleName + parseRecord.toString());
                        if (bleName != null && targetDeviceName != null && bleName.startsWith(targetDeviceName)) {
                            //可能找到指定的设备
                            targetDevice = device;
                        } else {
                            if (isTargetBleDevice(parseRecord.getServiceUuids())) {
                                targetDevice = device;
                            }
                        }
                        if (targetDevice != null) {
                            stopScan();
                            connectTargetDevice();
                        }
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.e("BLE", "onScanFailed" + errorCode);
                }

                @Override
                public void onStop() {
                    ///停止搜索 //可能超时,可能蓝牙关闭等
                    Log.e("BLE", "停止搜索");
                    if (targetDevice == null && isBluetoothOpen()) {
                        //没有搜索到,查看系统是否连接配对上了
                        BluetoothDevice device = getSystemBondDevice();
                        if (device != null) {
                            targetDevice = Ble.options().getFactory().create(device.getAddress(), device.getName());
                            connectTargetDevice();
                        } else {
                            delayStartScan();
                        }
                    }
                }
            });
        }

    }

    ///延迟执行搜索
    private void delayStartScan() {
        new Handler().postDelayed(() -> {
            startScan();
        }, 1000);
    }

    private synchronized boolean isTargetBleDevice(List<ParcelUuid> advertiseServices) {
        if (advertiseServices != null && advertiseUUID != null) {
            for (ParcelUuid uuid : advertiseServices) {
                if (uuid.getUuid().toString().equals(UUID.fromString(advertiseUUID).toString())) {
                    //可能找到指定的设备
                    return true;
                }
            }
        }
        return false;
    }

    private synchronized void connectTargetDevice() {
        if (targetDevice != null) {
            Log.e("BLE", "找到指定的设备,停止扫描,开始连接");
            connect();
        }
    }

    private synchronized void stopScan() {
        if (mBle != null && mBle.isScanning()) {
            mBle.stopScan();
        }
    }

    ///主动断开连接
    public synchronized void disconnect() {
        if (isDeviceConnect()) {
            mBle.disconnect(targetDevice);
        }
    }


    public synchronized void destory() {
        if (mBle != null) {
            disconnect();
            mBle.released();
            bleListeners.clear();
            targetDevice = null;
            mBle = null;
            mContext = null;
        }
    }


    private synchronized void connect() {
        if (targetDevice != null) {
            //targetDevice.setAutoConnect(true);
            mBle.connect(targetDevice, new BleConnectCallback<BleDevice>() {
                @Override
                public void onConnectionChanged(BleDevice device) {
                    targetDevice = device;
                    if (!device.isConnecting()) {
                        if (device.isConnected()) {
                            ///连接成功
                            Log.e("BLE", "连接成功");
                        } else {
                            Log.e("BLE", "连接断开");
                            onDisconnected();
                            for (BleListener listener : bleListeners) {
                                listener.onBleConnectChange(device.isConnected());
                            }
                        }

                    } else {
                        Log.e("BLE", "连接中");
                    }
                }

                @Override
                public void onReady(BleDevice device) {
                    super.onReady(device);
                    startNotify();

                }

                @Override
                public void onConnectException(BleDevice device, int errorCode) {
                    Log.e("BLE", "连接异常" + errorCode);
                }

                @Override
                public void onConnectTimeOut(BleDevice device) {
                    Log.e("BLE", "连接超时");
                }
            });


        }

    }


    private void onDisconnected() {
        targetDevice = null;
        if (isBluetoothOpen()) {
            startScan();
        }
    }


    ///蓝牙状态
    private synchronized void bleStatusListener() {
        mBle.setBleStatusCallback(isOn -> {
            for (BleListener listener : bleListeners) {
                listener.onBleEnableChange(isOn);
            }
            if (isOn) {
                Log.e("BLE", "蓝牙已开启");
                startScan();
            } else {
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
            if (device.getName() != null) {
                if (device.getName().startsWith(targetDeviceName)) {
                    return device;
                }
            }
        }
        return null;
    }

}
