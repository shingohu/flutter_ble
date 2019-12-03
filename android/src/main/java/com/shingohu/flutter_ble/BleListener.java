package com.shingohu.flutter_ble;

public interface BleListener {
    void onBleEnableChange(boolean enable);

    void onBleConnectChange(boolean connect);

    void onBleNotifyData(String hexStr);
}

