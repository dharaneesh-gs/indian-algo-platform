import SwiftUI

struct MainView: View {
    @EnvironmentObject var model: TradingModel
    @State private var tab = 0
    var body: some View {
        TabView(selection: $tab) {
            dashboard.tabItem { Label("Home", systemImage: "chart.xyaxis.line") }.tag(0)
            orders.tabItem { Label("Paper", systemImage: "arrow.left.arrow.right") }.tag(1)
            strategy.tabItem { Label("Strategy", systemImage: "slider.horizontal.3") }.tag(2)
            broker.tabItem { Label("Broker", systemImage: "building.columns") }.tag(3)
        }.tint(.mint)
    }
    var dashboard: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Local algo-trading workspace").font(.title2.bold())
                    Text(model.health).font(.caption).foregroundStyle(.secondary)
                    TextField("Backend URL", text: $model.baseURL).textInputAutocapitalization(.never).keyboardType(.URL).textFieldStyle(.roundedBorder)
                    SecureField("Backend API token", text: $model.apiToken).textFieldStyle(.roundedBorder)
                    Button("Connect / Refresh") { Task { await model.connect() } }.buttonStyle(.borderedProminent)
                    if let a = model.account {
                        HStack { metric("Equity", money(a.equity)); metric("Cash", money(a.cash)) }
                        HStack { metric("Unrealized P&L", money(a.unrealizedPnl)); metric("Realized P&L", money(a.realizedPnl)) }
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Holdings").font(.headline)
                            if a.holdings.isEmpty { Text("No paper holdings yet").foregroundStyle(.secondary) }
                            ForEach(a.holdings) { h in
                                HStack { VStack(alignment: .leading) { Text(h.symbol).bold(); Text("\(h.quantity) shares • avg \(money(h.averagePrice))").font(.caption).foregroundStyle(.secondary) }; Spacer(); Text(money(h.unrealizedPnl)) }
                            }
                        }.padding().background(.white.opacity(0.05), in: RoundedRectangle(cornerRadius: 16))
                    }
                    if !model.candles.isEmpty {
                        VStack(alignment: .leading) {
                            Text("\(model.symbol) candles").font(.headline)
                            Text("Latest close: \(money(model.candles.last?.close ?? 0))").foregroundStyle(.secondary)
                            MiniChart(values: Array(model.candles.suffix(50)).map(\.close)).frame(height: 130)
                        }.padding().background(.white.opacity(0.05), in: RoundedRectangle(cornerRadius: 16))
                    }
                    Text("Paper orders only in this build. Real-order path is intentionally blocked until broker authorization and compliance prerequisites are verified.")
                        .font(.footnote).foregroundStyle(.orange)
                    if !model.message.isEmpty { Text(model.message).font(.caption).foregroundStyle(.secondary) }
                }.padding()
            }.background(Color(red: 0.035, green: 0.05, blue: 0.08)).navigationTitle("Trading Platform")
        }
    }
    var orders: some View {
        NavigationStack {
            Form {
                Section("Paper order") {
                    TextField("Symbol", text: $model.symbol).textInputAutocapitalization(.characters)
                    Picker("Side", selection: $model.side) { Text("BUY").tag("BUY"); Text("SELL").tag("SELL") }.pickerStyle(.segmented)
                    TextField("Quantity", text: $model.quantity).keyboardType(.numberPad)
                    Button("Simulate paper order") { Task { await model.placePaperOrder() } }
                    Text("Uses the latest candle price available on the backend. No live order is sent.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Trade ledger") {
                    Button("Refresh trades") { Task { await model.refreshTradeHistory() } }
                    if !model.tradeHistoryText.isEmpty {
                        Text(model.tradeHistoryText).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                    }
                }
                Section("Audit log") {
                    Button("Refresh audit log") { Task { await model.refreshAuditHistory() } }
                    if !model.auditHistoryText.isEmpty {
                        Text(model.auditHistoryText).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                    }
                }
                if !model.message.isEmpty { Text(model.message) }
            }.navigationTitle("Paper Orders")
        }
    }
    var strategy: some View {
        NavigationStack {
            Form {
                Section("SMA crossover") {
                    TextField("Symbol", text: $model.symbol).textInputAutocapitalization(.characters)
                    Picker("Exchange", selection: $model.exchange) { Text("NSE").tag("NSE"); Text("BSE").tag("BSE") }
                    Stepper("Fast SMA: \(model.fast)", value: $model.fast, in: 2...50)
                    Stepper("Slow SMA: \(model.slow)", value: $model.slow, in: 3...200)
                    TextField("Order quantity", text: $model.quantity).keyboardType(.numberPad)
                    Picker("Execution mode", selection: $model.strategyMode) {
                        Text("Paper").tag("PAPER"); Text("Real account").tag("LIVE")
                    }.pickerStyle(.segmented)
                    Button("Configure strategy") { Task { await model.configureStrategy() } }
                    Button("Run backtest") { Task { await model.runBacktest() } }
                }
                Section("Backend strategy runner") {
                    HStack { Text("Status"); Spacer(); Text(model.strategyEnabled ? "RUNNING" : "STOPPED").foregroundStyle(model.strategyEnabled ? Color.mint : Color.secondary) }
                    Text(model.strategyStatusText).font(.footnote)
                    if !model.strategyLastError.isEmpty { Text(model.strategyLastError).font(.footnote).foregroundStyle(.orange) }
                    Button("Refresh status") { Task { await model.refreshStrategyStatus() } }
                    Button("Start strategy") { Task { await model.startStrategy() } }
                        .disabled(model.strategyEnabled)
                    Button("Stop strategy", role: .destructive) { Task { await model.stopStrategy() } }
                        .disabled(!model.strategyEnabled)
                    Text("The home-computer backend polls Angel One quotes every 15 seconds and evaluates new one-minute candles. A live strategy can submit real limit orders when the server flag is enabled, the kill switch is off, and all risk checks pass.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                if let r = model.backtest {
                    Section("Backtest results") {
                        line("Final equity", money(r.finalEquity))
                        line("Return", String(format: "%.2f%%", r.returnPct))
                        line("Closed trades", "\(r.closedTrades)")
                        line("Winning trades", "\(r.wins)")
                        line("Max drawdown", String(format: "%.2f%%", r.maxDrawdownPct))
                        Text(r.caveat).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                if !model.message.isEmpty { Text(model.message) }
            }.navigationTitle("Strategy Lab")
        }
    }
    var broker: some View {
        NavigationStack {
            Form {
                Section("Angel One session") {
                    HStack { Text("Configured"); Spacer(); Text(model.brokerStatus?.configured == true ? "Yes" : "No") }
                    HStack { Text("Session"); Spacer(); Text(model.brokerStatus?.sessionPresent == true ? "Connected" : "Not connected") }
                    HStack { Text("Live enabled on server"); Spacer(); Text(model.brokerStatus?.liveTradingEnabled == true ? "Yes" : "No") }
                    HStack { Text("Kill switch"); Spacer(); Text(model.killSwitchOn ? "ON — blocked" : "OFF") }
                    SecureField("Angel One trading PIN", text: $model.pin)
                    TextField("6-digit TOTP", text: $model.totp).keyboardType(.numberPad)
                    Button("Authorize Angel One session") { Task { await model.loginBroker() } }
                    Button("Refresh session") { Task { await model.refreshBrokerSession() } }
                    Button("Load live broker account snapshot") { Task { await model.loadBrokerAccount() } }
                    Button("Logout / clear session", role: .destructive) { Task { await model.logoutBroker() } }
                    if !model.brokerAccountText.isEmpty {
                        Text(model.brokerAccountText).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                    }
                    Text("PIN and TOTP are sent only to your configured backend for login and are cleared from the app form after the request. Use HTTPS/private VPN; never use this over an untrusted network.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Instrument mapping") {
                    Button("Refresh official Angel One instrument master") { Task { await model.refreshOfficialMaster() } }
                    Picker("Exchange", selection: $model.exchange) { Text("NSE").tag("NSE"); Text("BSE").tag("BSE") }
                    TextField("Exact broker trading symbol", text: $model.symbol).textInputAutocapitalization(.characters)
                    TextField("Instrument token", text: $model.instrumentToken)
                    TextField("Tick size (e.g. 0.05)", text: $model.tickSize).keyboardType(.decimalPad)
                    TextField("Lot size", text: $model.lotSize).keyboardType(.numberPad)
                    Button("Save instrument mapping") { Task { await model.registerInstrument() } }
                    Button("Fetch live quote") { Task { await model.refreshLiveQuote() } }
                    if !model.liveQuoteText.isEmpty {
                        Text(model.liveQuoteText).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                    }
                    Text("Use the current official Angel One instrument master. Incorrect symbol/token mapping can send an order to the wrong instrument.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Historical market data") {
                    Picker("Interval", selection: $model.interval) {
                        Text("1 minute").tag("ONE_MINUTE"); Text("5 minutes").tag("FIVE_MINUTE")
                        Text("15 minutes").tag("FIFTEEN_MINUTE"); Text("30 minutes").tag("THIRTY_MINUTE")
                        Text("1 hour").tag("ONE_HOUR"); Text("Daily").tag("ONE_DAY")
                    }
                    TextField("From (YYYY-MM-DD HH:mm)", text: $model.fromDate)
                    TextField("To (YYYY-MM-DD HH:mm)", text: $model.toDate)
                    Button("Import Angel One historical candles") { Task { await model.fetchHistorical() } }
                }
                Section("Live order — limit orders only") {
                    Picker("Side", selection: $model.side) { Text("BUY").tag("BUY"); Text("SELL").tag("SELL") }.pickerStyle(.segmented)
                    TextField("Quantity", text: $model.quantity).keyboardType(.numberPad)
                    TextField("Limit price", text: $model.limitPrice).keyboardType(.decimalPad)
                    TextField("Type LIVE to confirm", text: $model.liveConfirmation).textInputAutocapitalization(.characters)
                    if !model.lastClientOrderId.isEmpty {
                        Text("Last client order ID: \(model.lastClientOrderId)").font(.caption).textSelection(.enabled)
                    }
                    Button("Submit REAL LIMIT order", role: .destructive) { Task { await model.placeLiveOrder() } }
                        .disabled(model.liveConfirmation != "LIVE" || model.killSwitchOn || model.brokerStatus?.liveTradingEnabled != true || model.brokerStatus?.sessionPresent != true)
                    Text("Real orders require server-side LIVE_TRADING_ENABLED=true, kill switch OFF, valid broker session, current instrument mapping, registered static IP, and risk checks. Orders can lose money.")
                        .font(.footnote).foregroundStyle(.orange)
                }
                Section("Emergency stop") {
                    HStack {
                        Text("Current state")
                        Spacer()
                        Text(model.killSwitchOn ? "ON — live orders blocked" : "OFF")
                            .foregroundStyle(model.killSwitchOn ? Color.orange : Color.mint)
                    }
                    SecureField("Type ENABLE LIVE to clear stop", text: $model.killSwitchConfirmation)
                    Button("Clear live kill switch", role: .destructive) { Task { await model.setKillSwitch(false) } }
                        .disabled(!model.killSwitchOn || model.killSwitchConfirmation != "ENABLE LIVE")
                    Button("Turn kill switch ON") { Task { await model.setKillSwitch(true) } }
                        .disabled(model.killSwitchOn)
                    Text("The kill switch starts ON after every backend restart. Clearing it does not bypass the server's live-trading flag or risk checks.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Reconcile UNKNOWN submission") {
                    TextField("Client order ID", text: $model.resolveClientOrderId)
                    Picker("Verified outcome", selection: $model.resolveOutcome) {
                        Text("Broker shows submitted").tag("SUBMITTED")
                        Text("Broker shows rejected").tag("REJECTED")
                    }
                    if model.resolveOutcome == "SUBMITTED" {
                        TextField("Broker order ID", text: $model.resolveBrokerOrderId)
                    }
                    TextField("Type RECONCILED to confirm", text: $model.resolveConfirmation)
                    Button("Save reconciliation") { Task { await model.reconcileUnknownOrder() } }
                        .disabled(model.resolveConfirmation != "RECONCILED" || model.resolveClientOrderId.isEmpty)
                    Text("Only use this after checking the live Angel One order book. Resolving UNKNOWN incorrectly can permit a duplicate order.")
                        .font(.footnote).foregroundStyle(.orange)
                }
                Section("Manage broker order") {
                    TextField("Broker order ID", text: $model.brokerOrderId)
                    TextField("Type CANCEL to confirm", text: $model.cancelConfirmation)
                    Button("Cancel broker order", role: .destructive) { Task { await model.cancelBrokerOrder() } }
                        .disabled(model.cancelConfirmation != "CANCEL" || model.brokerOrderId.isEmpty)
                    TextField("Type MODIFY LIVE to confirm", text: $model.modifyConfirmation)
                    Button("Modify as limit order") { Task { await model.modifyBrokerOrder() } }
                        .disabled(model.modifyConfirmation != "MODIFY LIVE" || model.brokerOrderId.isEmpty || model.brokerStatus?.liveTradingEnabled != true || model.killSwitchOn)
                    Text("Verify order ID, symbol, exchange, side, quantity and limit price against the broker order book before modifying.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Broker order book") {
                    Button("Refresh broker order book") { Task { await model.refreshBrokerOrderBook() } }
                    if !model.brokerOrderBookText.isEmpty {
                        Text(model.brokerOrderBookText).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                    }
                }
                Section("Official docs") {
                    Link("Angel One SmartAPI documentation", destination: URL(string: "https://smartapi.angelone.in/docs")!)
                    Text(model.brokerStatus?.note ?? "Connect to backend to inspect status.").font(.footnote).foregroundStyle(.secondary)
                }
                if !model.message.isEmpty { Section("Status") { Text(model.message).font(.footnote) } }
            }.navigationTitle("Broker & Live Trading")
        }
    }
    func metric(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 8) { Text(title).font(.caption).foregroundStyle(.secondary); Text(value).font(.headline).lineLimit(1).minimumScaleFactor(0.7) }
            .frame(maxWidth: .infinity, alignment: .leading).padding().background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 14))
    }
    func line(_ title: String, _ value: String) -> some View { HStack { Text(title); Spacer(); Text(value).foregroundStyle(.secondary) } }
    func money(_ n: Double) -> String { n.formatted(.currency(code: "INR").precision(.fractionLength(2))) }
}
private struct MiniChart: View {
    let values: [Double]
    var body: some View {
        GeometryReader { g in
            let lo = values.min() ?? 0, hi = values.max() ?? 1, span = max(hi-lo, 0.0001)
            Path { p in for (i,v) in values.enumerated() {
                let x = CGFloat(i)/CGFloat(max(values.count-1,1))*g.size.width
                let y = g.size.height-CGFloat((v-lo)/span)*g.size.height
                if i == 0 { p.move(to: CGPoint(x:x,y:y)) } else { p.addLine(to: CGPoint(x:x,y:y)) }
            }}.stroke(.mint, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
        }
    }
}
