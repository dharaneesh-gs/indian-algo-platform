import Foundation
import SwiftUI

@MainActor final class TradingModel: ObservableObject {
    @Published var baseURL = "https://YOUR-HOME-COMPUTER-TAILNET-URL"
    @Published var apiToken = ""
    @Published var pin = ""
    @Published var totp = ""
    @Published var exchange = "NSE"
    @Published var instrumentToken = ""
    @Published var tickSize = "0.05"
    @Published var lotSize = "1"
    @Published var limitPrice = ""
    @Published var liveConfirmation = ""
    @Published var brokerOrderId = ""
    @Published var lastClientOrderId = ""
    @Published var resolveClientOrderId = ""
    @Published var resolveBrokerOrderId = ""
    @Published var resolveOutcome = "SUBMITTED"
    @Published var resolveConfirmation = ""
    @Published var cancelConfirmation = ""
    @Published var modifyConfirmation = ""
    @Published var killSwitchConfirmation = ""
    @Published var fromDate = "2026-10-01 09:15"
    @Published var toDate = "2026-10-17 15:30"
    @Published var interval = "FIVE_MINUTE"
    @Published var brokerOrderBookText = ""
    @Published var tradeHistoryText = ""
    @Published var auditHistoryText = ""
    @Published var brokerAccountText = ""
    @Published var liveQuoteText = ""
    @Published var killSwitchOn = true
    @Published var health = "Not connected"
    @Published var account: AccountDTO?
    @Published var candles: [CandleDTO] = []
    @Published var audit: [[String: AnyCodable]] = []
    @Published var message = ""
    @Published var brokerStatus: BrokerStatusDTO?
    @Published var symbol = "DEMO"
    @Published var quantity = "1"
    @Published var side = "BUY"
    @Published var fast = 5
    @Published var slow = 12
    @Published var backtest: BacktestDTO?
    @Published var strategyMode = "PAPER"
    @Published var strategyEnabled = false
    @Published var strategyStatusText = "Strategy stopped"
    @Published var strategyLastError = ""
    private let decoder = JSONDecoder()

    func connect() async {
        do {
            let h: HealthDTO = try await get("/api/health")
            health = "Connected • \(h.status)"
            account = try await get("/api/account")
            brokerStatus = try await get("/api/broker/angel/status")
            killSwitchOn = brokerStatus?.killSwitchOn ?? true
            candles = try await get("/api/market/\(symbol)/candles")
            message = "Connected to home backend."
        } catch { health = "Connection failed"; message = error.localizedDescription }
    }
    func refresh() async {
        do {
            account = try await get("/api/account")
            brokerStatus = try await get("/api/broker/angel/status")
            candles = try await get("/api/market/\(symbol)/candles")
        } catch { message = error.localizedDescription }
    }
    func placePaperOrder() async {
        do {
            let req = OrderRequest(symbol: symbol, side: side, quantity: Int(quantity) ?? 0, price: nil, confirmation: nil)
            let _: OrderResponseDTO = try await post("/api/paper/orders", body: req)
            message = "Paper order simulated."
            await refresh()
        } catch { message = error.localizedDescription }
    }
    func runBacktest() async {
        do {
            let req = StrategyRequest(fastPeriod: fast, slowPeriod: slow, startingCash: 100000)
            backtest = try await post("/api/backtest/\(symbol)", body: req)
            message = "Backtest complete."
        } catch { message = error.localizedDescription }
    }
    func loadAudit() async {
        do { audit = try await get("/api/audit") } catch { message = error.localizedDescription }
    }
    func refreshStrategyStatus() async {
        do {
            let status: StrategyStatusDTO = try await get("/api/strategy/status")
            strategyEnabled = status.enabled
            strategyStatusText = status.lastSignal
            strategyLastError = status.lastError
        } catch { message = error.localizedDescription }
    }
    func configureStrategy() async {
        do {
            let req = StrategyConfigRequest(symbol: symbol, exchange: exchange, mode: strategyMode,
                                            fastPeriod: fast, slowPeriod: slow, quantity: Int(quantity) ?? 1)
            let status: StrategyStatusDTO = try await post("/api/strategy/configure", body: req)
            strategyEnabled = status.enabled; strategyStatusText = status.lastSignal; strategyLastError = status.lastError
            message = "Strategy configured."
        } catch { message = error.localizedDescription }
    }
    func startStrategy() async {
        do {
            let status: StrategyStatusDTO = try await post("/api/strategy/start", body: EmptyRequest())
            strategyEnabled = status.enabled; strategyStatusText = status.lastSignal; strategyLastError = status.lastError
            message = "Strategy started on the home computer."
        } catch { message = error.localizedDescription }
    }
    func stopStrategy() async {
        do {
            let status: StrategyStatusDTO = try await post("/api/strategy/stop", body: EmptyRequest())
            strategyEnabled = status.enabled; strategyStatusText = status.lastSignal; strategyLastError = status.lastError
            message = "Strategy stopped."
        } catch { message = error.localizedDescription }
    }
    func loginBroker() async {
        do {
            let _: LoginStatusDTO = try await post("/api/broker/angel/login", body: BrokerLoginRequest(pin: pin, totp: totp))
            pin = ""; totp = ""; message = "Broker session established."
            brokerStatus = try await get("/api/broker/angel/status")
        } catch { message = error.localizedDescription }
    }
    func refreshBrokerSession() async {
        do {
            let _: LoginStatusDTO = try await post("/api/broker/angel/refresh", body: EmptyRequest())
            message = "Broker session refreshed."
            brokerStatus = try await get("/api/broker/angel/status")
        } catch { message = error.localizedDescription }
    }
    func loadBrokerAccount() async {
        do { brokerAccountText = try await getRaw("/api/broker/angel/account") }
        catch { message = error.localizedDescription }
    }
    func refreshLiveQuote() async {
        do { liveQuoteText = try await getRaw("/api/broker/angel/quote/\(exchange)/\(symbol)") }
        catch { message = error.localizedDescription }
    }
    func logoutBroker() async {
        do {
            let _: SimpleStatusDTO = try await post("/api/broker/angel/logout", body: EmptyRequest())
            message = "Broker session cleared."
            brokerStatus = try await get("/api/broker/angel/status")
        } catch { message = error.localizedDescription }
    }
    func refreshOfficialMaster() async {
        do {
            let result: InstrumentImportDTO = try await post("/api/instruments/refresh-official", body: EmptyRequest())
            message = "Imported \(result.imported ?? 0) official instrument mappings. Verify before trading."
            brokerStatus = try await get("/api/broker/angel/status")
        } catch { message = error.localizedDescription }
    }
    func registerInstrument() async {
        do {
            let req = [InstrumentRequest(symbol: symbol, token: instrumentToken, exchange: exchange,
                                         tickSize: Double(tickSize) ?? 0.05, lotSize: Int(lotSize) ?? 1)]
            let _: InstrumentImportDTO = try await post("/api/instruments", body: req)
            message = "Instrument mapping saved in backend memory."
            brokerStatus = try await get("/api/broker/angel/status")
        } catch { message = error.localizedDescription }
    }
    func fetchHistorical() async {
        do {
            let req = HistoricalRequest(symbol: symbol, exchange: exchange, interval: interval, fromDate: fromDate, toDate: toDate)
            let _: InstrumentImportDTO = try await post("/api/broker/angel/history", body: req)
            candles = try await get("/api/market/\(symbol)/candles")
            message = "Historical candles imported."
        } catch { message = error.localizedDescription }
    }
    func placeLiveOrder() async {
        do {
            let orderKey = String(UUID().uuidString.prefix(16))
            lastClientOrderId = orderKey
            let req = LiveOrderRequest(symbol: symbol, exchange: exchange, side: side, quantity: Int(quantity) ?? 0,
                                       limitPrice: Double(limitPrice) ?? 0, confirmation: liveConfirmation,
                                       clientOrderId: orderKey)
            let response: OrderResponseDTO = try await post("/api/live/orders", body: req)
            message = "Live order request: \(response.status). Verify broker order book; submission is not a fill."
            liveConfirmation = ""; await refresh()
        } catch { message = error.localizedDescription }
    }
    func setKillSwitch(_ enabled: Bool) async {
        do {
            let req = KillSwitchRequest(enabled: enabled, confirmation: killSwitchConfirmation)
            let _: KillSwitchResponseDTO = try await post("/api/risk/kill-switch", body: req)
            killSwitchOn = enabled; killSwitchConfirmation = ""
            message = enabled ? "Live orders blocked." : "Kill switch cleared. Other live-trading gates still apply."
            brokerStatus = try await get("/api/broker/angel/status")
        } catch { message = error.localizedDescription }
    }
    func reconcileUnknownOrder() async {
        do {
            let req = ResolveUnknownRequest(clientOrderId: resolveClientOrderId, outcome: resolveOutcome,
                                            brokerOrderId: resolveBrokerOrderId, confirmation: resolveConfirmation)
            let _: BrokerActionDTO = try await post("/api/live/orders/resolve-unknown", body: req)
            resolveConfirmation = ""; message = "Unknown order reconciled. Refresh order book and trade ledger."
            await refreshTradeHistory(); await refreshBrokerOrderBook()
        } catch { message = error.localizedDescription }
    }
    func cancelBrokerOrder() async {
        do {
            let req = CancelOrderRequest(orderId: brokerOrderId, variety: "NORMAL", confirmation: cancelConfirmation)
            let _: BrokerActionDTO = try await post("/api/broker/angel/cancel-order", body: req)
            message = "Cancel request submitted; verify final status in broker order book."
            cancelConfirmation = ""; await refreshBrokerOrderBook()
        } catch { message = error.localizedDescription }
    }
    func modifyBrokerOrder() async {
        do {
            let req = ModifyOrderRequest(orderId: brokerOrderId, symbol: symbol, exchange: exchange, side: side,
                                         quantity: Int(quantity) ?? 0, limitPrice: Double(limitPrice) ?? 0,
                                         confirmation: modifyConfirmation)
            let _: BrokerActionDTO = try await post("/api/broker/angel/modify-limit-order", body: req)
            message = "Modify request submitted; verify final status in broker order book."
            modifyConfirmation = ""; await refreshBrokerOrderBook()
        } catch { message = error.localizedDescription }
    }
    func refreshTradeHistory() async {
        do { tradeHistoryText = try await getRaw("/api/trades") }
        catch { message = error.localizedDescription }
    }
    func refreshAuditHistory() async {
        do { auditHistoryText = try await getRaw("/api/audit") }
        catch { message = error.localizedDescription }
    }
    func refreshBrokerOrderBook() async {
        do { brokerOrderBookText = try await getRaw("/api/broker/angel/order-book") }
        catch { message = error.localizedDescription }
    }
    func getRaw(_ path: String) async throws -> String {
        guard let url = URL(string: baseURL + path) else { throw APIError.badURL }
        var request = URLRequest(url: url); request.timeoutInterval = 12
        if !apiToken.isEmpty { request.setValue("Bearer \(apiToken)", forHTTPHeaderField: "Authorization") }
        let (data,response)=try await URLSession.shared.data(for:request)
        try check(response,data)
        return String(data:data,encoding:.utf8) ?? "No response body"
    }
    func get<T: Decodable>(_ path: String) async throws -> T {
        guard let url = URL(string: baseURL + path) else { throw APIError.badURL }
        var request = URLRequest(url: url); request.timeoutInterval = 12
        if !apiToken.isEmpty { request.setValue("Bearer \(apiToken)", forHTTPHeaderField: "Authorization") }
        let (data, response) = try await URLSession.shared.data(for: request)
        try check(response, data)
        return try decoder.decode(T.self, from: data)
    }
    func post<B: Encodable, T: Decodable>(_ path: String, body: B) async throws -> T {
        guard let url = URL(string: baseURL + path) else { throw APIError.badURL }
        var request = URLRequest(url: url); request.httpMethod = "POST"; request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if !apiToken.isEmpty { request.setValue("Bearer \(apiToken)", forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONEncoder().encode(body)
        let (data, response) = try await URLSession.shared.data(for: request)
        try check(response, data)
        return try decoder.decode(T.self, from: data)
    }
    private func check(_ response: URLResponse, _ data: Data) throws {
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else {
            let text = String(data: data, encoding: .utf8) ?? "HTTP \(http.statusCode)"
            throw APIError.server(text)
        }
    }
    enum APIError: LocalizedError {
        case badURL, invalidResponse, server(String)
        var errorDescription: String? {
            switch self {
            case .badURL: return "Invalid backend URL."
            case .invalidResponse: return "Invalid server response."
            case .server(let s): return s
            }
        }
    }
}
struct HealthDTO: Decodable { var status: String }
struct CandleDTO: Decodable, Identifiable {
    var time: String; var open: Double; var high: Double; var low: Double; var close: Double; var volume: Double
    var id: String { time }
}
struct HoldingDTO: Decodable, Identifiable {
    var symbol: String; var quantity: Int; var averagePrice: Double; var markPrice: Double; var unrealizedPnl: Double
    var id: String { symbol }
}
struct AccountDTO: Decodable {
    var mode: String; var cash: Double; var holdingsValue: Double; var equity: Double; var realizedPnl: Double; var unrealizedPnl: Double
    var holdings: [HoldingDTO]
}
struct OrderRequest: Encodable { var symbol: String; var side: String; var quantity: Int; var price: Double?; var confirmation: String? }
struct OrderResponseDTO: Decodable { var mode: String; var symbol: String; var side: String; var quantity: Int; var price: Double; var fee: Double; var status: String; var message: String; var brokerOrderId: String? }
struct StrategyRequest: Encodable { var fastPeriod: Int; var slowPeriod: Int; var startingCash: Double }
struct BacktestDTO: Decodable { var closedTrades: Int; var wins: Int; var returnPct: Double; var finalEquity: Double; var maxDrawdownPct: Double; var caveat: String }
struct BrokerStatusDTO: Decodable {
    var configured: Bool; var liveTradingEnabled: Bool; var sessionPresent: Bool
    var broker: String; var note: String
    var killSwitchOn: Bool?; var instrumentCount: Int?
}
struct AnyCodable: Decodable { let value: String
    init(from decoder: Decoder) throws { let c = try decoder.singleValueContainer(); if let s=try? c.decode(String.self){value=s} else { value=String(describing: try c.decode(Int.self)) } }
}

struct BrokerLoginRequest: Encodable { var pin: String; var totp: String }
struct LoginStatusDTO: Decodable { var loggedIn: Bool; var message: String }
struct EmptyRequest: Encodable {}
struct SimpleStatusDTO: Decodable { var status: String; var message: String }
struct InstrumentRequest: Encodable { var symbol: String; var token: String; var exchange: String; var tickSize: Double; var lotSize: Int }
struct InstrumentImportDTO: Decodable { var imported: Int?; var count: Int?; var total: Int?; var symbol: String?; var exchange: String? }
struct HistoricalRequest: Encodable { var symbol: String; var exchange: String; var interval: String; var fromDate: String; var toDate: String }
struct LiveOrderRequest: Encodable { var symbol: String; var exchange: String; var side: String; var quantity: Int; var limitPrice: Double; var confirmation: String; var clientOrderId: String }
struct KillSwitchRequest: Encodable { var enabled: Bool; var confirmation: String }
struct KillSwitchResponseDTO: Decodable { var killSwitchOn: Bool; var message: String }

struct StrategyConfigRequest: Encodable { var symbol: String; var exchange: String; var mode: String; var fastPeriod: Int; var slowPeriod: Int; var quantity: Int }
struct StrategyStatusDTO: Decodable {
    var enabled: Bool; var symbol: String; var exchange: String; var mode: String
    var fastPeriod: Int; var slowPeriod: Int; var quantity: Int
    var lastSignal: String; var lastError: String; var note: String
}

struct CancelOrderRequest: Encodable { var orderId: String; var variety: String; var confirmation: String }
struct ModifyOrderRequest: Encodable { var orderId: String; var symbol: String; var exchange: String; var side: String; var quantity: Int; var limitPrice: Double; var confirmation: String }
struct BrokerActionDTO: Decodable { var status: String; var message: String }

struct ResolveUnknownRequest: Encodable { var clientOrderId: String; var outcome: String; var brokerOrderId: String; var confirmation: String }
