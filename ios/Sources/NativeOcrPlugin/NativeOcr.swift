import Foundation

@objc public class NativeOcr: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
