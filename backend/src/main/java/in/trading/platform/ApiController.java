package in.trading.platform;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
class ApiController {
    private final TradingService trading;
    private final MarketDataService market;
    private final AuditService audit;
    private final AngelOneClient angel;
    private final PlatformProperties props;
    private final TradeRepository tradeRepository;
    private final InstrumentService instruments;
    private final StrategyEngine strategy;

    ApiController(TradingService trading, MarketDataService market, AuditService audit, AngelOneClient angel,
                  PlatformProperties props, TradeRepository tradeRepository, InstrumentService instruments, StrategyEngine strategy) {
        this.trading=trading; this.market=market; this.audit=audit; this.angel=angel; this.props=props;
        this.tradeRepository=tradeRepository; this.instruments=instruments; this.strategy=strategy;
    }
    @GetMapping("/health") Map<String,Object> health() {
        return Map.of("status","ok","service","indian-algo-platform","liveTradingConfigured",props.live().enabled());
    }
    @GetMapping("/account") ApiModels.AccountResponse account() { return trading.account(); }
    @GetMapping("/strategy/status") BrokerModels.StrategyStatus strategyStatus() { return strategy.status(); }
    @PostMapping("/strategy/configure") BrokerModels.StrategyStatus configureStrategy(@Valid @RequestBody BrokerModels.StrategyConfigRequest req) { return strategy.configure(req); }
    @PostMapping("/strategy/start") BrokerModels.StrategyStatus startStrategy() { return strategy.start(); }
    @PostMapping("/strategy/stop") BrokerModels.StrategyStatus stopStrategy() { return strategy.stop(); }
    @PostMapping("/paper/orders") ApiModels.OrderResponse paper(@Valid @RequestBody ApiModels.OrderRequest req) { return trading.paperOrder(req); }
    @PostMapping("/live/orders") ApiModels.OrderResponse live(@Valid @RequestBody BrokerModels.LiveOrderRequest req) { return trading.liveOrder(req); }
    @PostMapping("/live/orders/resolve-unknown") Map<String,Object> resolveUnknown(@Valid @RequestBody BrokerModels.ResolveUnknownRequest req) { return trading.resolveUnknownLiveOrder(req); }
    @PostMapping("/broker/angel/cancel-order") Map<String,Object> cancelOrder(@Valid @RequestBody BrokerModels.CancelOrderRequest req) { return trading.cancelLiveOrder(req); }
    @PostMapping("/broker/angel/modify-limit-order") Map<String,Object> modifyOrder(@Valid @RequestBody BrokerModels.ModifyOrderRequest req) { return trading.modifyLiveOrder(req); }
    @GetMapping("/market/{symbol}/candles") List<ApiModels.CandleDto> candles(@PathVariable String symbol) { return market.get(symbol); }
    @GetMapping("/market/{symbol}/summary") Map<String,Object> summary(@PathVariable String symbol) { return market.summary(symbol); }
    @PutMapping("/market/{symbol}/candles") Map<String,Object> importCandles(@PathVariable String symbol, @RequestBody List<ApiModels.CandleRequest> candles) {
        int count=market.replace(symbol,candles); audit.record("CANDLES_IMPORTED","PAPER",symbol+" count="+count);
        return Map.of("symbol",symbol.toUpperCase(),"count",count);
    }
    @PostMapping("/market/demo") Map<String,Object> demo() { market.loadDemo(); audit.record("DEMO_DATA_RELOADED","PAPER","Synthetic candles reloaded"); return Map.of("status","ok","note","Synthetic data only"); }
    @PostMapping("/backtest/{symbol}") ApiModels.BacktestResponse backtest(@PathVariable String symbol,@Valid @RequestBody ApiModels.StrategyRequest req) { return trading.backtest(symbol,req); }
    @GetMapping("/trades") List<Map<String,Object>> trades() {
        return tradeRepository.findTop100ByOrderByCreatedAtDesc().stream().map(t -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id",t.id); row.put("time",t.createdAt==null?"":t.createdAt.toString());
            row.put("mode",t.mode); row.put("symbol",t.symbol); row.put("side",t.side);
            row.put("quantity",t.quantity); row.put("price",t.price); row.put("fee",t.fee);
            row.put("status",t.status); row.put("brokerOrderId",t.brokerOrderId); row.put("clientOrderId",t.clientOrderId); row.put("message",t.message);
            return row;
        }).toList();
    }
    @GetMapping("/audit") List<Map<String,Object>> audit() { return audit.recent(); }

    @GetMapping("/broker/angel/status") Map<String,Object> brokerStatus() {
        Map<String,Object> result=new LinkedHashMap<>(angel.status(props.live().enabled()));
        result.put("killSwitchOn",trading.liveKillSwitch());
        result.put("instrumentCount",instruments.all().size());
        return result;
    }
    @PostMapping("/broker/angel/login") BrokerModels.LoginStatus login(@Valid @RequestBody BrokerModels.LoginRequest req) {
        BrokerModels.LoginStatus result=angel.login(req.pin(),req.totp());
        audit.record("BROKER_LOGIN","LIVE","Angel One session established; secrets not logged");
        return result;
    }
    @PostMapping("/broker/angel/refresh") BrokerModels.LoginStatus refreshSession() {
        BrokerModels.LoginStatus result=angel.refreshSession();
        audit.record("BROKER_SESSION_REFRESH","LIVE","Angel One session refreshed; tokens not logged");
        return result;
    }
    @GetMapping("/broker/angel/account") Map<String,Object> brokerAccount() { return angel.accountSnapshot(); }
    @PostMapping("/broker/angel/logout") Map<String,Object> logout() {
        angel.logout(); audit.record("BROKER_LOGOUT","LIVE","Angel One session cleared");
        return Map.of("status","ok","message","Broker session cleared from backend memory.");
    }
    @GetMapping("/broker/angel/order-book") Map<String,Object> brokerOrderBook() { return angel.orderBook(); }
    @PostMapping("/broker/angel/history") Map<String,Object> historical(@Valid @RequestBody BrokerModels.HistoricalRequest req) {
        var instrument=instruments.require(req.exchange(),req.symbol());
        var candles=angel.historical(instrument,req.interval(),req.fromDate(),req.toDate());
        int count=market.replace(req.symbol(),candles.stream().map(c -> new ApiModels.CandleRequest(c.time(),c.open(),c.high(),c.low(),c.close(),c.volume())).toList());
        audit.record("BROKER_HISTORY_IMPORTED","MARKET",req.exchange()+":"+req.symbol()+" count="+count);
        return Map.of("symbol",req.symbol(),"exchange",req.exchange(),"count",count);
    }
    @GetMapping("/broker/angel/quote/{exchange}/{symbol}") Map<String,Object> quote(@PathVariable String exchange,@PathVariable String symbol) {
        return angel.ltp(instruments.require(exchange,symbol));
    }
    @GetMapping("/instruments") List<InstrumentService.Instrument> instruments() { return instruments.all(); }
    @PostMapping("/instruments/refresh-official") Map<String,Object> refreshOfficialInstruments() {
        int count=instruments.refreshOfficialMaster();
        audit.record("OFFICIAL_INSTRUMENT_MASTER_REFRESH","SYSTEM","NSE/BSE equity mappings loaded="+count);
        return Map.of("imported",count,"total",instruments.all().size(),"note","Verify the exact token and tick size before real orders.");
    }
    @PostMapping("/instruments") Map<String,Object> importInstruments(@RequestBody List<BrokerModels.InstrumentInput> inputs) {
        int count=instruments.importRows(inputs.stream().map(i -> new InstrumentService.Instrument(i.symbol(),i.token(),i.exchange(),i.tickSize(),i.lotSize())).toList());
        audit.record("INSTRUMENTS_IMPORTED","SYSTEM","count="+count);
        return Map.of("imported",count,"total",instruments.all().size(),"note","Use current official Angel One instrument data; validate symbols and tokens before live trading.");
    }
    @PostMapping("/risk/kill-switch") Map<String,Object> killSwitch(@RequestBody BrokerModels.KillSwitchRequest req) {
        trading.setLiveKillSwitch(req.enabled(),req.confirmation());
        return Map.of("killSwitchOn",trading.liveKillSwitch(),"message",req.enabled()?"Live orders blocked.":"Kill switch cleared; all other live-trading gates remain active.");
    }
}
