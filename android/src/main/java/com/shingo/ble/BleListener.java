package com.shingo.ble;

public interface BleListener {
    void onBleEnableChange(boolean enable);

    void onBleConnectChange(boolean connect);

    void onBleNotifyData(byte[] bytes);

}

