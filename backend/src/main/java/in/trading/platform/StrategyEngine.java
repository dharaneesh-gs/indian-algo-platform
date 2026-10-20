package in.trading.platform;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class StrategyEngine {
    private final InstrumentService instruments;
    private final MarketDataService market;
    private final AngelOneClient angel;
    private final TradingService trading;
    private final AuditService audit;
    private final PlatformProperties props;
    private volatile boolean enabled=false;
    private volatile String symbol="DEMO", exchange="NSE", mode="PAPER";
    private volatile int fast=5;
    private volatile int slow=12;
    private volatile int quantity=1;
    private volatile String lastSignal="No signal yet", lastError="";

    StrategyEngine(InstrumentService instruments, MarketDataService market, AngelOneClient angel,
                   TradingService trading, AuditService audit, PlatformProperties props) {
        this.instruments=instruments; this.market=market; this.angel=angel; this.trading=trading; this.audit=audit; this.props=props;
    }

    synchronized BrokerModels.StrategyStatus configure(BrokerModels.StrategyConfigRequest req) {
        if(enabled) throw new IllegalStateException("Stop the strategy before changing its configuration.");
        String m=req.mode().toUpperCase(), ex=req.exchange().toUpperCase(), sym=req.symbol().toUpperCase();
        if(!List.of("PAPER","LIVE").contains(m)) throw new IllegalArgumentException("Strategy mode must be PAPER or LIVE.");
        if(!List.of("NSE","BSE").contains(ex)) throw new IllegalArgumentException("Strategy exchange must be NSE or BSE.");
        if(req.fastPeriod()<2 || req.slowPeriod()<=req.fastPeriod() || req.slowPeriod()>500)
            throw new IllegalArgumentException("Periods must satisfy 2 <= fastPeriod < slowPeriod <= 500.");
        if(req.quantity()<1 || req.quantity()>100000) throw new IllegalArgumentException("Strategy quantity must be between 1 and 100000.");
        if(m.equals("LIVE") && !props.live().enabled()) throw new IllegalStateException("Set LIVE_TRADING_ENABLED=true on the backend only after broker and compliance setup.");
        if(m.equals("LIVE") && trading.liveKillSwitch()) throw new IllegalStateException("Clear the live kill switch with explicit confirmation before configuring live automation.");
        if(m.equals("LIVE")) instruments.require(ex,sym);
        symbol=sym; exchange=ex; mode=m; fast=req.fastPeriod(); slow=req.slowPeriod(); quantity=req.quantity();
        lastError="";
        audit.record("STRATEGY_CONFIGURED",mode,"SMA "+fast+"/"+slow+" "+exchange+":"+symbol+" qty="+quantity);
        return status();
    }
    synchronized BrokerModels.StrategyStatus start() {
        if(!angel.sessionPresent()) throw new IllegalStateException("Authorize Angel One to provide current market prices before starting the strategy.");
        if(mode.equals("LIVE") && (!props.live().enabled() || trading.liveKillSwitch()))
            throw new IllegalStateException("Live strategy blocked by server live flag or kill switch.");
        instruments.require(exchange,symbol);
        enabled=true; lastError="";
        audit.record("STRATEGY_STARTED",mode,"SMA "+fast+"/"+slow+" "+exchange+":"+symbol);
        return status();
    }
    synchronized BrokerModels.StrategyStatus stop() {
        enabled=false; audit.record("STRATEGY_STOPPED",mode,"Strategy stopped by user"); return status();
    }
    BrokerModels.StrategyStatus status() {
        return new BrokerModels.StrategyStatus(enabled,symbol,exchange,mode,fast,slow,quantity,lastSignal,lastError,
            "Strategies poll Angel One LTP every 15 seconds and evaluate only when a new minute candle begins. iOS backgrounding does not control backend execution.");
    }

    @Scheduled(fixedDelayString="${trading.strategy.poll-ms:15000}")
    void tick() {
        if(!enabled) return;
        try {
            if(mode.equals("LIVE") && (trading.liveKillSwitch() || !props.live().enabled())) {
                enabled=false; lastError="Live safety gate closed; strategy automatically stopped."; audit.record("STRATEGY_AUTO_STOP","LIVE",lastError); return;
            }
            InstrumentService.Instrument instrument=instruments.require(exchange,symbol);
            double price=angel.ltpPrice(instrument);
            boolean newMinute=market.appendLiveTick(symbol,price,Instant.now());
            if(!newMinute) return;
            List<ApiModels.CandleDto> data=market.get(symbol);
            if(data.size()<=slow+2) { lastError="Not enough candles. Import historical candles before starting."; return; }
            // Evaluate the candle that just closed, not the newly opened live minute candle.
            int end=data.size()-2;
            double f=avg(data,end,fast), s=avg(data,end,slow);
            double fp=avg(data,end-1,fast), sp=avg(data,end-1,slow);
            String side=null;
            int currentQty=mode.equals("PAPER")?trading.paperQuantity(symbol):angel.positionQuantity(instrument);
            if(fp<=sp && f>s && currentQty<=0) side="BUY";
            else if(fp>=sp && f<s && currentQty>0) side="SELL";
            if(side==null) { lastSignal="No crossover at "+data.get(end).time(); lastError=""; return; }
            int orderQty=side.equals("SELL")?Math.min(quantity,currentQty):quantity;
            if(mode.equals("PAPER")) {
                trading.paperOrder(new ApiModels.OrderRequest(symbol,side,orderQty,price,null));
            } else {
                double rounded=Math.round(price/instrument.tickSize())*instrument.tickSize();
                String id=UUID.randomUUID().toString().replace("-","").substring(0,16);
                trading.liveOrder(new BrokerModels.LiveOrderRequest(symbol,exchange,side,orderQty,rounded,"LIVE",id));
            }
            lastSignal=side+" "+orderQty+" "+symbol+" at "+price+" • "+data.get(end).time();
            lastError="";
            audit.record("STRATEGY_SIGNAL",mode,lastSignal);
        } catch(Exception ex) {
            lastError=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
        }
    }
    private double avg(List<ApiModels.CandleDto> d,int end,int n) {
        double sum=0; int start=Math.max(0,end-n+1);
        for(int i=start;i<=end;i++) sum+=d.get(i).close();
        return sum/(end-start+1);
    }
}
