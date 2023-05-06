//
//  BluetoothPermissionHandler.swift
//  flutter_ble
//
//  Created by shingohu on 2023/5/6.
//

import UIKit

import CoreBluetooth
import RxSwift


typealias PermissionCallback = (Bool) -> ()

class BluetoothPermissionHandler: NSObject {

    
    var permissionCallback:PermissionCallback?
    var stateDisposable: Disposable?;
    
    
    
   
    
    func requestPermission( permissionCallback:@escaping PermissionCallback){
        
        self.permissionCallback = permissionCallback
       
        var notDetermined = true
        
        if #available(iOS 13.1, *) {
            if( CBCentralManager.authorization == .allowedAlways || CBCentralManager.authorization == .restricted){
                permissionCallback(true)
                notDetermined = false
            }else  if(CBCentralManager.authorization == .denied){
                permissionCallback(false)
                notDetermined = false
            }
        } else if #available(iOS 13.0, *) {
            
            if(CBCentralManager().authorization == CBManagerAuthorization.allowedAlways || CBCentralManager().authorization == CBManagerAuthorization.restricted){
                permissionCallback(true)
                notDetermined = false
                
            }else if(CBCentralManager().authorization == CBManagerAuthorization.denied){
                permissionCallback(false)
                notDetermined = false
            }
        }else{
            // Before iOS 13, Bluetooth permissions are not required
            permissionCallback(true)
            notDetermined = false
            
        }
        
        if(notDetermined){
            stateDisposable = BleManager.INSTANCE.centralManager.observeState().startWith(BleManager.INSTANCE.centralManager.state)
                .subscribe(onNext: { (state) in
                    if(self.stateDisposable != nil){
                        self.requestPermission(permissionCallback: permissionCallback);
                    }
                })
            
        }else{
            self.permissionCallback = nil
            self.stateDisposable?.dispose()
        }
           
          
       
        
    }
    

}

