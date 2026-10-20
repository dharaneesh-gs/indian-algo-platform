import SwiftUI

@main
struct TradingPlatformApp: App {
    @StateObject private var model = TradingModel()
    var body: some Scene {
        WindowGroup { MainView().environmentObject(model).preferredColorScheme(.dark) }
    }
}
