//
//  BleManager.swift
//  BLE
//
//  Created by 胡杰 on 2018/3/27.
//  Copyright © 2018年 胡杰. All rights reserved.
//

import UIKit
import RxSwift
import CoreBluetooth




typealias WriteSuccess = () -> ()
typealias WriteFail = () -> ()


class BleManager{
    
    private var bleListenerList = Array<WeakRef<BleProtocol>>()
    
    
    //设备广播ID(广播出来的服务标识)
    private var targetDeviceAdvertUUID = CBUUID.init(string: "FFE0")
    
    
    //需要用到的服务ID
    private var targetServiceUUID = CBUUID.init(string: "FFE0");
    // 需要用到的服务下面的特征ID (一般直接支持读写通知)
    private var notifyCharacteristicUUID = CBUUID.init(string: "FFE1")
    private var writeCharacteristicUUID = CBUUID.init(string: "FFE1")
    private var characteristics:Array<Characteristic>? //服务下面所有特征的集合
    private var peripheral: Peripheral?//指定设备
    
    private var targetDeviceName = "" //指定设备名称
    
    private var autoScanAndConnect = true //是否自动扫描连接
    
    
   
    
    public var isTargetDeviceConnected = false //指定设备是否已经连接
    
    private var bluetoothState = BluetoothState.unknown //手机蓝牙状态
    private var bleStateDisposable :Disposable? //蓝牙状态订阅
    private var notificationDisposable :Disposable?//通知的订阅
    private var scanDisposable :Disposable?//扫描的订阅
    private var connectStateDisposable :Disposable?//连接状态订阅
    private var connectDisposable :Disposable?//连接订阅
    private var autoScanDisposable :Disposable?//自动扫描连接设备的订阅
    
    private var isScan = false
    private var isFirst = true
    
    private var MTU = 20
    private var requestMTU = 20
    
    
    
    
    private lazy var centralManager = { () -> CentralManager in
        let bundleId =  Bundle.main.bundleIdentifier!
        return CentralManager(queue:.main,options: [CBCentralManagerOptionRestoreIdentifierKey: bundleId as AnyObject])
    }()
    
    
   
    
    
    
    private init(){
        
    }
    
    static let INSTANCE:BleManager = BleManager()
    
    
    
    public func initWithUUID(deviceName:String,deviceAdvertUUID:String,mainServiceUUID:String,notifyCharacteristicUUID:String,writeCharacteristicUUID:String,requestMTU:Int){
        self.targetDeviceName = deviceName.uppercased()
        self.targetDeviceAdvertUUID = CBUUID.init(string:deviceAdvertUUID)
        self.targetServiceUUID = CBUUID.init(string:mainServiceUUID)
        
        self.notifyCharacteristicUUID = CBUUID.init(string:notifyCharacteristicUUID)
        self.writeCharacteristicUUID = CBUUID.init(string:writeCharacteristicUUID)
        if(requestMTU > 20){
            self.requestMTU = requestMTU
        }
        bleStateListener()
        self.startAutoScanConnectPeripheral()
        
    }
    
    
    
    

    //连接状态的监听
    private func connectStateListener(_ peripheral:Peripheral){
      
       connectStateDisposable =  peripheral.observeConnection().subscribe(onNext:{ (b) in
            if b {
                if !self.isTargetDeviceConnected {
                    print("蓝牙连接成功回调")
                    
                    let maxMTU = (peripheral.maximumWriteValueLength(for: .withoutResponse))
                    if(maxMTU - 3 > self.requestMTU){
                        self.MTU = self.requestMTU
                    }else{
                        self.MTU = maxMTU - 3
                    }
                    self.isTargetDeviceConnected = true
                    if(self.setNotificationSuccess){
                        self.onConnectStateChange(isConnect: true)
                    }
                }
              
            }
            else{
                if self.isTargetDeviceConnected {
                    print("蓝牙连接断开回调")
                    self.onDisConnect()
                    self.onConnectStateChange(isConnect: false)
                }
            }
       })
        

    }
    
    //扫描并且连接设备
    public func scanAndConnect()->Bool{
        
        autoScanAndConnect = true
    
        if(!hasBlueToothPermission){
            print("没有蓝牙权限")
            return false
        }
        
        if !isBlueToothOpen{
            print("没有打开蓝牙")
            return false
        }
        
        if(isTargetDeviceConnected){
            print("设备已连接")
            return false
        }
        if(isScan){
            print("正在扫描蓝牙设备")
            return true
        }
    
       if(self.retrieveConnectedPeripheralsWithServices()){
           ///已配对设备中连接了
            return false
       }
        
        
    
       self.isScan = true
       print("开始扫描并连接设备")
       scanDisposable = connectObservable(peripheralObs:  centralManager.scanForPeripherals(withServices: [targetDeviceAdvertUUID])
        .timeout(DispatchTimeInterval.seconds(12), scheduler: MainScheduler.instance)
        .filter({ per in
            if(!self.targetDeviceName.isEmpty){
                return (per.peripheral.name?.uppercased().hasPrefix(self.targetDeviceName) ?? false)
            }else{
                return true
            }
        })
       .take(1).flatMap({ (sp) -> Observable<Peripheral> in
        print("扫描到指定的设备,开始连接")
        return Observable.from(optional: sp.peripheral)
       }))
        
        return true
        
    }
    
    
    ///停止扫描
    public func setStopScan(){
       updateAutoScanAndConnect(autoScanAndConnect: false)
    }
    
    
   
    
    
    ///连接操作
    private func connectObservable(peripheralObs:Observable<Peripheral>) -> Disposable {
        
       return peripheralObs.flatMap({ (p) -> Observable<Peripheral> in
                self.connectStateListener(p)
                return p.establishConnection()
            })
        .flatMap { $0.discoverServices([self.targetServiceUUID])}
        .flatMap { Observable.from($0) }
        .flatMap {
            $0.discoverCharacteristics(nil)
       }
        .subscribe(onNext: { (chars) in
            self.characteristics = chars
            self.onScanAndConnectConnected()
        }, onError: { (error) in
            self.onScanAndConnectError()
        })
        
    }
    
    
    func connect(peripheral:Peripheral){
        if(self.isTargetDeviceConnected || self.isScan){
            return
        }
        self.isScan = true
        connectDisposable = connectObservable(peripheralObs: Observable.from(optional: peripheral))
    }
    
    
    
    
    
      //根据SERVICE UUID 获取系统已经连接的设备
    func retrieveConnectedPeripheralsWithServices() -> Bool  {
           if isBlueToothOpen  {
               if  !isTargetDeviceConnected{
                   print("获取已经连接的设备")
                   let deviceArray = centralManager.retrieveConnectedPeripherals(withServices: [targetDeviceAdvertUUID])
                   if deviceArray.count>0{
                    for p in deviceArray{
                        
                        if(!self.targetDeviceName.isEmpty){
                            if(p.name?.uppercased().hasPrefix(self.targetDeviceName) ?? false )
                        {
                            print("系统已连接指定设备,这里直接连接")
                            self.connect(peripheral: p)
                            return true;
                         }
                        }else{
                            
                            self.connect(peripheral: p)
                            return true
                        }
                    }
                   }else{
                       print("已经连接的设备中未找到指定设备")
                   }
               }
           }else{
               print("蓝牙未开启")
           }
        
        return false
       }

    
    
    
    
    //当断开连接的时候
    private func onDisConnect(){
        self.isTargetDeviceConnected = false
        self.setNotificationSuccess = false
        self.MTU = 20
        if(notificationDisposable != nil){
            notificationDisposable?.dispose()
            notificationDisposable = nil
        }
        
        if(connectStateDisposable != nil ){
            connectStateDisposable?.dispose()
            connectStateDisposable = nil
        }
        
        stopScanAndConnect()
        
        if(isBlueToothOpen){
            self.startAutoScanConnectPeripheral()
        }else{
            self.stopAutoScanConnectPeripheral()
        }
        
        
    }
    
    private func onScanAndConnectConnected(){
        print("扫描连接操作成功")
        ///停止自动扫描连接
        stopAutoScanConnectPeripheral()
        self.setNotification()
    
    }
    
    //停止扫描连接
    private func stopScanAndConnect(){
        
        self.isScan = false
        if(scanDisposable != nil){
            print("停止扫描")
            scanDisposable?.dispose()
            scanDisposable = nil
        }
        
        if(connectDisposable != nil){
            print("停止连接")
            connectDisposable?.dispose()
            connectDisposable = nil
        }
        self.characteristics = nil
        
        self.peripheral = nil
    }
    
    
    
    //当扫描连接失败时
    private func onScanAndConnectError(){
        print("扫描连接超时操作失败")
        self.stopScanAndConnect()
    
    }
    
    
  
    
    

    //蓝牙的状态变化的监听
    private func bleStateListener(){
        if(bleStateDisposable == nil){
            bleStateDisposable =  centralManager.observeState().startWith(centralManager.state)
                .filter{ $0 == .poweredOn || $0 == .poweredOff}
                .subscribe(onNext: { (state) in
                    
                    self.notifyBleStateChange(isBluetoothOpen: state == .poweredOn)
                    
                    if(state == BluetoothState.unknown || state == BluetoothState.unauthorized){
                       //未授权 或授权中 不处理
                    }else{
                        
                        if state == .poweredOn{
                            print("蓝牙已打开")
                            self.startAutoScanConnectPeripheral()
                        }else {
                            print("蓝牙已关闭")
                            self.stopAutoScanConnectPeripheral()
                       }
                    }
                    self.bluetoothState = state
                    
                    
                })
        }
        
    }
    
    
    //后台自动扫描并连接的设备  间隔10
    private func startAutoScanConnectPeripheral(){
        stopAutoScanConnectPeripheral()
        if(autoScanAndConnect && !self.isTargetDeviceConnected){
            self.scanAndConnect()
            autoScanDisposable =  Observable<Int>.interval(DispatchTimeInterval.seconds(13), scheduler: MainScheduler.instance).subscribe({_ in
                self.scanAndConnect()
            })
        }
        
    }
    
    private func updateAutoScanAndConnect(autoScanAndConnect:Bool){
        if(autoScanAndConnect != self.autoScanAndConnect){
            self.autoScanAndConnect =  autoScanAndConnect
            if(!self.autoScanAndConnect){
                stopAutoScanConnectPeripheral()
            }else{
                startAutoScanConnectPeripheral()
            }
        }
        
    }
    
    
    //停止后台自动扫描连接设备
    private func stopAutoScanConnectPeripheral()  {
        if(autoScanDisposable != nil ){
            print("停止自动扫描连接")
            autoScanDisposable?.dispose()
            autoScanDisposable = nil
        }
       
    }
    
    
    
    
    //主动断开连接,则不要自动扫描重连了
    public func disconnect(){
        updateAutoScanAndConnect(autoScanAndConnect:false )
        stopScanAndConnect()
    }
    
  
    
    var setNotificationSuccess = false
        
        //通知回调
        private func setNotification(){
            
            if self.characteristics != nil {
                //过滤出来通知特征
                let notiftChar:Characteristic?  = self.characteristics?.filter({ (c) -> Bool in
                    return c.uuid == self.notifyCharacteristicUUID
                })[0];
                    
                    self.setNotificationSuccess = true
                    notificationDisposable = notiftChar?.observeValueUpdateAndSetNotification().subscribe(onNext: { (characteristic) in
                        let value = characteristic.value
                        if value != nil{
                            self.onNotifySuccess(value: value!)
                        }
                    }, onError: { (error) in
                        self.setNotificationSuccess = false
                        print("设置通知出错")
                        print(error)
                        self.onNotifyError(error: error)
                    })
                Observable<Int>.timer(RxTimeInterval.milliseconds(500), scheduler: MainScheduler.instance).subscribe { () in
                    if(self.setNotificationSuccess){
                        self.onConnectStateChange(isConnect: self.isTargetDeviceConnected)
                    }
                }
            }
        }
    
    
    
    //写入数据
    private func writeOne(data:Data,writeSuccess: WriteSuccess? = nil, writeFail: WriteFail? = nil){
         if self.characteristics != nil {
            //过滤出来通知特征
            let writeChar:Characteristic?  = self.characteristics?.filter({ (c) -> Bool in
                return c.uuid == self.writeCharacteristicUUID
            })[0];
             writeChar?.writeValue(data, type: .withoutResponse).subscribe(onSuccess: { (c) in
                 writeSuccess?()
             }, onFailure: { (e) in
                writeFail?()
            })
         }else{
            writeFail?()
         }
    }
    
    
    
    
  
    public func write(data:Data,writeSuccess: WriteSuccess? = nil, writeFail: WriteFail? = nil){
        
        if(!isTargetDeviceConnected){
            writeFail?()
            return;
        }
        
        
        let byteslength = data.count
        
        if(byteslength > MTU){
            ///分包发送
            multipleWrite(data: data,writeSuccess: writeSuccess,writeFail: writeFail)
        }else{
            writeOne(data: data,writeSuccess: writeSuccess,writeFail: writeFail)
        }
        
        
      
        
        
    }
    
    private func multipleWrite(data:Data,writeSuccess: WriteSuccess? = nil, writeFail: WriteFail? = nil){
        
        var byteslength = data.count
        
        if(MTU <  byteslength){
            let subData = data.subdata(in: 0..<MTU)
            ///剩余数据
            let lastData = data.subdata(in: MTU..<byteslength)
            writeOne(data: subData) {
                //这里可以考虑延时下,避免写入失败？
                ////写入成功 就写入下次数据
                self.multipleWrite(data: lastData,writeSuccess: writeSuccess,writeFail: writeFail)
            } writeFail: {
                ///写入失败,就直接失败吧
                writeFail?()
                return
            }
        }else{
            writeOne(data: data,writeSuccess: writeSuccess,writeFail: writeFail)
        }
        
        
        
        
    }
    
    
    
    
    
    
    
    
    //连接状态发送变化
    private func onConnectStateChange(isConnect:Bool){
        bleListenerList.forEach {
            $0.value?.onConnectStateChange?(isConnect: isConnect)
        }
    }
    
    //收到通知
    private func onNotifySuccess(value:Data)  {
        bleListenerList.forEach {
            $0.value?.onNofitySuccess?(value: value)
        }
    }
    
    //通知失败
    private func onNotifyError(error:Error)  {
        bleListenerList.forEach {
            $0.value?.onNofityError?(error: error)
        }
    }
    
    
    
   
    
    
    
    
    //蓝牙状态变化通知
    private func notifyBleStateChange(isBluetoothOpen:Bool){
        bleListenerList.forEach {
            $0.value?.onBleStateChange?(isOn: isBluetoothOpen)
        }
    }
    
    
    
    //手机蓝牙状态
    public var isBlueToothOpen:Bool{
        return self.centralManager.state == .poweredOn
    }
    
    
    ///是否有蓝牙权限
    public var hasBlueToothPermission:Bool{
        return self.centralManager.state != .unauthorized && self.centralManager.state != .unknown
    }
    
    public var getMTU:Int{
        
        return self.MTU
    }
    
    
    ///是否已连接设备
    public var isConnected:Bool{
        
        return self.isTargetDeviceConnected
    }
    
    
    
    
    //添加蓝牙监听
    public func addBleListener(weakRefP: WeakRef<BleProtocol>? )  {
        
        bleListenerList = bleListenerList.filter{ $0.value != nil }
        
        let contain = bleListenerList.contains { (wp) -> Bool in
            if wp == weakRefP{
                return true
            }else{
                return false
            }
        }
        if !contain && weakRefP != nil{
            bleListenerList.append(weakRefP!)
        }
        
    }
    
    
    //移除蓝牙状态监听
    public func removeBleListener(weakRefP: WeakRef<BleProtocol>?) {
        bleListenerList = bleListenerList.filter{
            if  $0.value != nil{
                if  $0 == weakRefP {
                    return false
                }
                return true
            }else{
                return false
            }
        }
        
        
    }
    
    deinit {
        bleStateDisposable?.dispose()
        connectStateDisposable?.dispose()
        notificationDisposable?.dispose()
        scanDisposable?.dispose()
        autoScanDisposable?.dispose()
    }
    
}





@objc protocol BleProtocol {
    
    //蓝牙状态变化
    @objc optional func onBleStateChange(isOn:Bool)
    //收到通知成功
    @objc optional func onNofitySuccess(value:Data)
    //通知失败
    @objc optional func onNofityError(error:Error)
    
    //连接状态发送变化
    @objc optional func onConnectStateChange(isConnect:Bool)
}


class WeakRef<T:AnyObject> :NSObject {
    private(set) weak var value:T?
    init(value:T?) {
        self.value = value
    }
}
