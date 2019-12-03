package com.shingohu.flutter_ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import cn.com.heaton.blelibrary.ble.Ble;
import cn.com.heaton.blelibrary.ble.BleFactory;
import cn.com.heaton.blelibrary.ble.callback.BleConnectCallback;
import cn.com.heaton.blelibrary.ble.callback.BleNotiftCallback;
import cn.com.heaton.blelibrary.ble.callback.BleScanCallback;
import cn.com.heaton.blelibrary.ble.callback.BleStatusCallback;
import cn.com.heaton.blelibrary.ble.model.BleDevice;
import cn.com.heaton.blelibrary.ble.model.ScanRecord;
import cn.com.heaton.blelibrary.ble.utils.ByteUtils;

public class BleManager {

    private final static BleManager instance = new BleManager();
    private String targetDeviceName = "Uart";//设备名称,用来过滤
    private String advertiseUUID = "0000FFE0-0000-1000-8000-00805F9B34FB"; //设备广播出来的UUID,用来过滤
    private String mainSeviceUUID = "0000FFE0-0000-1000-8000-00805F9B34FB"; //主服务UUID
    private String mainCharacteristicUUID = "0000FFE1-0000-1000-8000-00805F9B34FB";//主特征UUID

    private Ble<BleDevice> mBle;
    private Context mContext;

    private HashSet<BleListener> bleListeners = new HashSet<>();


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


    public void init(Context context, String targetDeviceName, String advertiseUUID, String mainSeviceUUID, String mainCharacteristicUUID) {
        this.mContext = context;
        this.targetDeviceName = targetDeviceName;
        this.advertiseUUID = advertiseUUID;
        this.mainSeviceUUID = mainSeviceUUID;
        this.mainCharacteristicUUID = mainCharacteristicUUID;
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
                    String data = ByteUtils.BinaryToHexString(characteristic.getValue());
                    Log.e("BLE", "获取到数据:" + data);
                    for (BleListener listener : bleListeners) {
                        listener.onBleNotifyData(data);
                    }
                }

            });
        }

    }


    private void initBle() {
        if (mBle != null) {
            return;
        }
        Ble.Options options = Ble.options()
                .setLogBleExceptions(true)//设置是否输出打印蓝牙日志
                .setThrowBleException(true)//设置是否抛出蓝牙异常
                .setAutoConnect(true)//设置是否自动连接
                .setFilterScan(true)//设置是否过滤扫描到的设备
                .setConnectFailedRetryCount(3)
                .setUuidService(UUID.fromString(mainSeviceUUID))
                .setUuidNotify(UUID.fromString(mainCharacteristicUUID))
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


    public void startScan() {
        if (targetDevice == null && mBle != null) {
            Log.e("BLE", "开始扫描");
            mBle.startScan(new BleScanCallback<BleDevice>() {
                @Override
                public void onLeScan(BleDevice device, int rssi, byte[] scanRecord) {
                    synchronized (mBle.getLocker()) {
                        ScanRecord parseRecord = ScanRecord.parseFromBytes(scanRecord);
                        String bleName = device.getBleName();

                        boolean foundTargetDevice = false;
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
                            BleDevice bleDevice = BleFactory.create(BleDevice.class, device);
                            targetDevice = bleDevice;
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

    public void stopScan() {
        mBle.stopScan();
    }


    public void destory() {
        stopScan();
        mBle.destory(mContext);
        bleListeners.clear();
        targetDevice = null;
        mBle = null;
        mContext = null;
    }


    public void connect() {
        if (targetDevice != null) {

            targetDevice.setAutoConnect(true);
            mBle.connect(targetDevice, new BleConnectCallback<BleDevice>() {
                @Override
                public void onConnectionChanged(BleDevice device) {
                    targetDevice = device;
                    if (!device.isConnectting()) {
                        if (device.isConnected()) {
                            startNotify();
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
        mBle.setBleStatusCallback(new BleStatusCallback() {
            @Override
            public void onBluetoothStatusOn() {
                for (BleListener listener : bleListeners) {
                    listener.onBleEnableChange(true);
                }
                startScan();
                Log.e("BLE", "蓝牙已开启");
            }

            @Override
            public void onBluetoothStatusOff() {
                for (BleListener listener : bleListeners) {
                    listener.onBleEnableChange(false);
                }
                stopScan();
                Log.e("BLE", "蓝牙已关闭");
            }
        });
    }


    public BluetoothDevice getSystemBondDevice() {
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
