import Flutter
import Foundation
import UIKit





public class SwiftFlutterBlePlugin: NSObject, FlutterPlugin,BleProtocol {
   
    var channel:FlutterMethodChannel?
    
    
    
  public static func register(with registrar: FlutterPluginRegistrar) {
    let instance = SwiftFlutterBlePlugin()
    instance.channel = FlutterMethodChannel(name: "flutter_ble", binaryMessenger: registrar.messenger());
    BleManager.INSTANCE.addBleListener(weakRefP: WeakRef<BleProtocol>(value:instance))
    registrar.addMethodCallDelegate(instance, channel: instance.channel!)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    
    let method = call.method
    
    
    if( method == "initUUID"){
        initUUID(uuids: call.arguments as! Dictionary<String, String>)
        result(true)
        return
    }
    
    if(method == "startScan"){
        startScan()
        result(true)
        return
    }
    
    if(method == "openBle"){
        
        openBle()
        result(true)
        return
    }
    
    if(method == "write"){
        self.write(hexStr: call.arguments as! String, writeSuccess: {
            result(true)
        }) {
            result(false)
        }
    }
    
    
  }
    
    private func initUUID(uuids:Dictionary<String,String>){
        let targetDeviceName = uuids["targetDeviceName"]
        let advertiseUUID = uuids["advertiseUUID"]
        let mainServiceUUID = uuids["mainServiceUUID"]
        let readcharacteristicUUID = uuids["readcharacteristicUUID"]
        let notifycharacteristicUUID = uuids["notifycharacteristicUUID"]
        let writecharacteristicUUID = uuids["writecharacteristicUUID"]
        BleManager.INSTANCE.initWithUUID(deviceName: targetDeviceName!, deviceAdvertUUID: advertiseUUID!, mainServiceUUID: mainServiceUUID!, readCharacteristicUUID: readcharacteristicUUID!,notifyCharacteristicUUID: notifycharacteristicUUID!,writeCharacteristicUUID: writecharacteristicUUID!)
        
    }
    
    private func startScan(){
        BleManager.INSTANCE.scanAndConnect()
    }
    
    private func openBle(){
        if let url = URL(string: UIApplication.openSettingsURLString), UIApplication.shared.canOpenURL(url) {
                      if #available(iOS 10.0, *) {
                          UIApplication.shared.open(url, options: convertToUIApplicationOpenExternalURLOptionsKeyDictionary([:]), completionHandler: nil)
                      }else{
                          UIApplication.shared.openURL(url)
                      }
              }
              
    }
    
    let SEND_MAX_LENGTH = 20
    
    private func write(hexStr:String,writeSuccess: WriteSuccess? = nil, writeFail: WriteFail? = nil){
        let data = DataUtil.hexStr2Data(from: hexStr)
        let byteslength = data.count
        
        for i in stride(from: 0, to: byteslength, by: SEND_MAX_LENGTH) {
            if( i + SEND_MAX_LENGTH) < byteslength {
                let subData = data.subdata(in: i..<(i+SEND_MAX_LENGTH))
                BleManager.INSTANCE.write(data: subData)
            }else{
                let subData = data.subdata(in: i..<byteslength)
                BleManager.INSTANCE.write(data: subData)
            }
        }
        
        
    }
    
    
    func onBleStateChange(isOn: Bool) {
        channel?.invokeMethod("bleEnable", arguments: isOn)
    }
    
    func onConnectStateChange(isConnect: Bool) {
        channel?.invokeMethod("bleConnect", arguments: isConnect)
    }
    
    func onNofitySuccess(value: Data) {
        
        channel?.invokeMethod("notify", arguments: value.hexEncodedString())
    }
}




class DataUtil {

    //将十六进制字符串转化为 Data
    static func hexStr2Data(from hexStr: String) -> Data {
        let bytes = self.bytes(from: hexStr)
        return Data(bytes: bytes)
    }

    // 将16进制字符串转化为 [UInt8]
    // 使用的时候直接初始化出 Data
    // Data(bytes: Array<UInt8>)
    static func bytes(from hexStr: String) -> [UInt8] {
        assert(hexStr.count % 2 == 0, "输入字符串格式不对，8位代表一个字符")
        var bytes = [UInt8]()
        var sum = 0
        // 整形的 utf8 编码范围
        let intRange = 48...57
        // 小写 a~f 的 utf8 的编码范围
        let lowercaseRange = 97...102
        // 大写 A~F 的 utf8 的编码范围
        let uppercasedRange = 65...70
        for (index, c) in hexStr.utf8CString.enumerated() {
            var intC = Int(c.byteSwapped)
            if intC == 0 {
                break
            } else if intRange.contains(intC) {
                intC -= 48
            } else if lowercaseRange.contains(intC) {
                intC -= 87
            } else if uppercasedRange.contains(intC) {
                intC -= 55
            } else {
                assertionFailure("输入字符串格式不对，每个字符都需要在0~9，a~f，A~F内")
            }
            sum = sum * 16 + intC
            // 每两个十六进制字母代表8位，即一个字节
            if index % 2 != 0 {
                bytes.append(UInt8(sum))
                sum = 0
            }
        }
        return bytes
    }


    static func int2Hex(int:Int) -> String{
        var s = String(int,radix:16).uppercased()
        if s.count % 2 != 0 {
            s = "0" + s
        }
        return s
    }

    static func hex2Int(hex:String) ->Int{
        let str = hex.uppercased()
        var sum = 0
        for i in str.utf8 {
            sum = sum * 16 + Int(i) - 48 // 0-9 从48开始
            if i >= 65 {                 // A-Z 从65开始，但有初始值10，所以应该是减去55
                sum -= 7
            }
        }
        return sum
    }
    
    static func byte2hex(_ b:[UInt8])->String{
        return Data.init(bytes: b).hexEncodedString()
    }
    
    static func byte2Int(_ b:UInt8)->Int{
        return hex2Int(hex: byte2hex([b]))
    }

    
    //两个byte合并
    static func byteMerger(b1:[UInt8],b2:[UInt8]) -> [UInt8]{
        let b3 = b1 + b2
        
        return b3 ;
        
        
        
        
    }


}


extension Data {
    /// A hexadecimal string representation of the bytes.
    func hexEncodedString() -> String {
        let hexDigits = Array("0123456789ABCDEF".utf16)
        var hexChars = [UTF16.CodeUnit]()
        hexChars.reserveCapacity(count * 2)

        for byte in self {
            let (index1, index2) = Int(byte).quotientAndRemainder(dividingBy: 16)
            hexChars.append(hexDigits[index1])
            hexChars.append(hexDigits[index2])
        }

        return String(utf16CodeUnits: hexChars, count: hexChars.count)
    }
    public func subdata(in range: CountableClosedRange<Data.Index>) -> Data
    {
        return self.subdata(in: range.lowerBound..<range.upperBound + 1)
    }
}

// Helper function inserted by Swift 4.2 migrator.
fileprivate func convertToUIApplicationOpenExternalURLOptionsKeyDictionary(_ input: [String: Any]) -> [UIApplication.OpenExternalURLOptionsKey: Any] {
	return Dictionary(uniqueKeysWithValues: input.map { key, value in (UIApplication.OpenExternalURLOptionsKey(rawValue: key), value)})
}
