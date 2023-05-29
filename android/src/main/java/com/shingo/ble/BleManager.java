package com.shingo.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.content.PermissionChecker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import cn.com.heaton.blelibrary.ble.Ble;
import cn.com.heaton.blelibrary.ble.Options;
import cn.com.heaton.blelibrary.ble.callback.BleConnectCallback;
import cn.com.heaton.blelibrary.ble.callback.BleMtuCallback;
import cn.com.heaton.blelibrary.ble.callback.BleNotifyCallback;
import cn.com.heaton.blelibrary.ble.callback.BleScanCallback;
import cn.com.heaton.blelibrary.ble.callback.BleWriteEntityCallback;
import cn.com.heaton.blelibrary.ble.model.BleDevice;
import cn.com.heaton.blelibrary.ble.model.BleFactory;
import cn.com.heaton.blelibrary.ble.model.EntityData;
import cn.com.heaton.blelibrary.ble.model.ScanRecord;


public class BleManager {

    private final static BleManager instance = new BleManager();
    private String targetDeviceName = "";//设备名称,用来过滤
    private String advertiseUUID; //设备广播出来的UUID,一般就是主服务UUID,用来过滤
    private String mainServiceUUID = ""; //主服务UUID
    private String writeCharacteristicUUID = "";//主特征通知UUID
    private String notifyCharacteristicUUID = "";//主特征写UUID

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


    //已连接的设备
    private BleDevice targetConnectedDevice;

    ///是否自动扫描和连接
    private boolean autoScanAndConnect = true;

    ///最终的MTU
    private int MTU = 20;

    ///请求设置的MTU
    private int requestMTU = 20;

    private BleManager() {

    }


    public static BleManager getInstance() {
        return instance;
    }


    public void init(Context context, String targetDeviceName, String advertiseUUID, String mainServiceUUID, String notifyCharacteristicUUID, String writeCharacteristicUUID, int requestMTU) {
        this.mContext = context;
        this.targetDeviceName = targetDeviceName.toUpperCase();
        this.advertiseUUID = advertiseUUID;
        this.mainServiceUUID = mainServiceUUID;
        this.notifyCharacteristicUUID = notifyCharacteristicUUID;
        this.writeCharacteristicUUID = writeCharacteristicUUID;
        if (requestMTU > 20) {
            this.requestMTU = requestMTU;
        }
        initBle();
    }


    public boolean isBluetoothOpen() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }


    public boolean isConnected() {
        if (targetConnectedDevice != null) {
            return targetConnectedDevice.isConnected();
        }
        return false;
    }


    private void startNotify() {
        if (targetConnectedDevice != null) {
            mBle.enableNotify(targetConnectedDevice, true, new BleNotifyCallback<BleDevice>() {
                @Override
                public void onChanged(BleDevice device, BluetoothGattCharacteristic characteristic) {
                    for (BleListener listener : bleListeners) {
                        listener.onBleNotifyData(characteristic.getValue());
                    }
                }

                @Override
                public void onNotifySuccess(BleDevice device) {
                    Log.e("BLE", "蓝牙设备连接成功(通知已开启)");
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

    public synchronized void write(byte[] bytes, final BleWriteListener writeListener) {
        if (isConnected()) {
            EntityData data = new EntityData.Builder()
                    .setAutoWriteMode(true)
                    .setData(bytes)
                    .setPackLength(MTU)
                    .setDelay(50)
                    .setLastPackComplete(false)
                    .setAddress(targetConnectedDevice.getBleAddress()).build();

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


    private synchronized void initBle() {
        if (mBle != null) {
            return;
        }

        Options options = Ble.options()
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
                .setUuidService(UUID.fromString(mainServiceUUID))
                .setUuidNotifyCha(UUID.fromString(notifyCharacteristicUUID))
                .setUuidWriteCha(UUID.fromString(writeCharacteristicUUID))
                .setConnectTimeout(10 * 1000)//设置连接超时时长
                .setScanPeriod(12 * 1000);
        mBle = options.create(mContext);
        bleStatusListener();
        startScan();
    }


    ///检查蓝牙相关权限
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


    ///设置停止扫描
    public void setStopScan() {
        this.autoScanAndConnect = false;
        stopScan();
    }

    public synchronized boolean startScan() {
        autoScanAndConnect = true;
        if (mBle == null) {
            Log.e("BLE", "请先初始化");
            return false;
        }
        if (!checkBluetoothPermission()) {
            Log.e("BLE", "没有蓝牙相关的权限");
            return false;
        }
        if (!isBluetoothOpen()) {
            Log.e("BLE", "没有打开蓝牙");
            return false;
        }
        if (targetConnectedDevice != null) {
            Log.e("BLE", "设备已连接");
            return false;
        }
        if (mBle.isScanning()) {
            Log.e("BLE", "正在扫描蓝牙设备");
            return true;
        }
        BluetoothDevice bondDevice = getSystemBondDevice();
        if (bondDevice != null) {
            Log.e("BLE", "已绑定设备中找到指定蓝牙设备");
            targetConnectedDevice = Ble.options().getFactory().create(bondDevice.getAddress(), bondDevice.getName());
            connectTargetDevice();
            return false;
        }

        Log.e("BLE", "开始扫描蓝牙设备");
        mBle.startScan(new BleScanCallback<BleDevice>() {
            @Override
            public void onLeScan(BleDevice device, int rssi, byte[] scanRecord) {
                synchronized (mBle.getLocker()) {
                    if (targetConnectedDevice != null) {
                        ///找到了指定的设备
                        return;
                    }
                    String bleName = device.getBleName();
                    ScanRecord parseRecord = ScanRecord.parseFromBytes(scanRecord);
                    Log.e("BLE", "扫描到蓝牙设备" + bleName + parseRecord.toString());
                    if (bleName != null && targetDeviceName != null && bleName.toUpperCase().startsWith(targetDeviceName)) {
                        //可能找到指定的设备
                        targetConnectedDevice = device;
                    } else {
                        if (isTargetBleDevice(parseRecord.getServiceUuids())) {
                            targetConnectedDevice = device;
                        }
                    }
                    if (targetConnectedDevice != null) {
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
                if (targetConnectedDevice == null && isBluetoothOpen()) {
                    //没有搜索到,查看系统是否连接配对上了
                    BluetoothDevice device = getSystemBondDevice();
                    if (device != null) {
                        targetConnectedDevice = Ble.options().getFactory().create(device.getAddress(), device.getName());
                        connectTargetDevice();
                    } else {
                        if (!autoScanAndConnect) {
                            return;
                        }
                        delayStartScan();
                    }
                }
            }
        });

        return true;

    }

    ///延迟执行搜索
    private void delayStartScan() {
        new Handler().postDelayed(() -> {
            startScan();
        }, 1000);
    }

    private synchronized boolean isTargetBleDevice(List<ParcelUuid> advertiseServices) {
        if (advertiseServices != null) {
            for (ParcelUuid uuid : advertiseServices) {
                if (advertiseUUID != null) {
                    if (uuid.getUuid().toString().equals(UUID.fromString(advertiseUUID).toString())) {
                        //可能找到指定的设备
                        return true;
                    }
                }
                if (mainServiceUUID != null) {
                    if (uuid.getUuid().toString().equals(UUID.fromString(mainServiceUUID).toString())) {
                        //可能找到指定的设备
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public int getMTU() {
        return MTU;
    }

    public abstract class VoidCallback {

        public void callback() {
        }

    }

    private void setMTU(VoidCallback callback) {
        if (requestMTU > 20) {
            if (mBle != null) {
                mBle.setMTU(targetConnectedDevice.getBleAddress(), requestMTU, new BleMtuCallback() {
                    @Override
                    public void onMtuChanged(BleDevice device, int mtu, int status) {
                        super.onMtuChanged(device, mtu, status);
                        Log.e("BLE", "MTU->" + mtu);
                        MTU = mtu;
                        callback.callback();
                    }
                });
            }
        } else {
            callback.callback();
        }
    }

    private synchronized void connectTargetDevice() {
        if (targetConnectedDevice != null) {
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
    public synchronized void setDisconnect() {
        autoScanAndConnect = false;
        disconnect();
    }


    private synchronized void disconnect() {
        if (isConnected()) {
            mBle.disconnect(targetConnectedDevice);
        }
    }


    public synchronized void destory() {
        if (mBle != null) {
            disconnect();
            mBle.released();
            bleListeners.clear();
            targetConnectedDevice = null;
            MTU = 20;
            mBle = null;
            mContext = null;
            autoScanAndConnect = true;
        }
    }


    private synchronized void connect() {
        if (targetConnectedDevice != null) {
            mBle.connect(targetConnectedDevice, new BleConnectCallback<BleDevice>() {
                @Override
                public void onConnectionChanged(BleDevice device) {
                    targetConnectedDevice = device;
                    if (!device.isConnecting()) {
                        if (device.isConnected()) {
                            ///连接成功

                        } else {
                            Log.e("BLE", "蓝牙设备连接断开");
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
                    setMTU(new VoidCallback() {
                        @Override
                        public void callback() {
                            if (notifyCharacteristicUUID != null && notifyCharacteristicUUID.length() > 0) {
                                startNotify();
                            } else {
                                Log.e("BLE", "蓝牙设备连接成功");
                                for (BleListener listener : bleListeners) {
                                    listener.onBleConnectChange(device.isConnected());
                                }
                            }
                        }
                    });


                }
            });


        }

    }


    private void onDisconnected() {
        targetConnectedDevice = null;
        MTU = 20;
        ///如果是主动断开 则不进行扫描连接
        if (autoScanAndConnect) {
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
                if (autoScanAndConnect) {
                    startScan();
                }
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
            if (device.getName() != null && targetDeviceName != null && !targetDeviceName.isEmpty()) {
                if (device.getName().toUpperCase().startsWith(targetDeviceName)) {
                    return device;
                }
            }
            ParcelUuid[] uuids = device.getUuids();
            if (uuids != null) {
                if (isTargetBleDevice(Arrays.asList(uuids))) {
                    return device;
                }
            }

        }
        return null;
    }

}
