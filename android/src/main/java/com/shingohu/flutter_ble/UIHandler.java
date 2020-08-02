package com.shingohu.flutter_ble;


import android.os.Handler;
import android.os.Looper;

public class UIHandler extends Handler {

    private static final String TAG = "BleHandler";
    private static UIHandler sHandler;//Handler for manipulating the Ble state

    public static UIHandler of(){
        synchronized (UIHandler.class){
            if(sHandler == null){
                sHandler = new UIHandler();
            }
            return sHandler;
        }
    }

    private UIHandler(){
        super(Looper.getMainLooper());
    }
}
