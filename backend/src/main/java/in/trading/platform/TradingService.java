package in.trading.platform;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
class TradingService {
    private final PlatformProperties props;
    private final MarketDataService market;
    private final HoldingRepository holdings;
    private final TradeRepository trades;
    private final AuditService audit;
    private final AngelOneClient angel;
    private final InstrumentService instruments;
    private final PaperAccountRepository paperAccounts;
    private boolean liveKillSwitch = true;
    private boolean liveTradingArmed = false;
    private long lastLiveOrderNanos = 0L;
    private double cash;
    private double realizedPnl=0;
    private double dailyRealizedPnl=0;
    private String accountDay = java.time.LocalDate.now().toString();
    private double dayStartEquity;

    TradingService(PlatformProperties props, MarketDataService market, HoldingRepository holdings,
                   TradeRepository trades, AuditService audit, AngelOneClient angel, InstrumentService instruments,
                   PaperAccountRepository paperAccounts) {
        this.props=props; this.market=market; this.holdings=holdings; this.trades=trades; this.audit=audit;
        this.angel=angel; this.instruments=instruments; this.paperAccounts=paperAccounts;
        PaperAccountEntity saved=paperAccounts.findById(1L).orElse(null);
        if(saved==null) {
            this.cash=props.paper().startingCash(); this.dayStartEquity=this.cash;
            paperAccounts.save(new PaperAccountEntity(cash,0,0,accountDay,dayStartEquity));
        } else {
            this.cash=saved.cash; this.realizedPnl=saved.realizedPnl; this.dailyRealizedPnl=saved.dailyRealizedPnl;
            this.accountDay=saved.currentDay==null?accountDay:saved.currentDay;
            this.dayStartEquity=saved.dayStartEquity;
        }
    }
    synchronized ApiModels.AccountResponse account() {
        rolloverDay();
        var list=holdings.findAllByOrderBySymbolAsc().stream().map(h -> {
            double mark=market.get(h.symbol).stream().reduce((a,b)->b).map(ApiModels.CandleDto::close).orElse(h.averagePrice);
            return new ApiModels.HoldingDto(h.symbol,h.quantity,h.averagePrice,mark,(mark-h.averagePrice)*h.quantity);
        }).toList();
        double hv=list.stream().mapToDouble(h -> h.quantity()*h.markPrice()).sum();
        double upnl=list.stream().mapToDouble(ApiModels.HoldingDto::unrealizedPnl).sum();
        return new ApiModels.AccountResponse("PAPER",cash,hv,cash+hv,realizedPnl,upnl,list);
    }
    synchronized int paperQuantity(String symbol) {
        return holdings.findBySymbol(symbol.toUpperCase()).map(h -> h.quantity).orElse(0);
    }
    @Transactional
    synchronized ApiModels.OrderResponse paperOrder(ApiModels.OrderRequest req) {
        rolloverDay();
        String symbol=req.symbol().trim().toUpperCase(), side=req.side().trim().toUpperCase();
        if(!Set.of("BUY","SELL").contains(side)) throw new IllegalArgumentException("side must be BUY or SELL");
        double price=req.price()!=null?req.price():market.get(symbol).stream().reduce((a,b)->b).map(ApiModels.CandleDto::close).orElse(0.0);
        if(price<=0) throw new IllegalArgumentException("No price available. Import candles or provide a positive price.");
        double gross=price*req.quantity(), fee=gross*props.paper().brokerageRate();
        if(gross>props.risk().maxOrderValue()) throw new IllegalArgumentException("Order exceeds configured maximum order value.");
        double currentExposure=holdings.findAllByOrderBySymbolAsc().stream().mapToDouble(h -> {
            double mark=market.get(h.symbol).stream().reduce((a,b)->b).map(ApiModels.CandleDto::close).orElse(h.averagePrice);
            return h.quantity*mark;
        }).sum();
        if(side.equals("BUY") && currentExposure+gross>props.risk().maxOpenPositionValue())
            throw new IllegalArgumentException("Order would exceed configured aggregate open-position value.");
        double currentDailyPnl=cash+currentExposure-dayStartEquity;
        if(side.equals("BUY") && currentDailyPnl <= -props.risk().maxDailyLoss())
            throw new IllegalStateException("Paper daily-loss limit reached. New BUY orders are blocked; sells remain available to reduce risk.");
        if(side.equals("BUY")) {
            if(cash<gross+fee) throw new IllegalArgumentException("Insufficient paper cash.");
            var old=holdings.findBySymbol(symbol).orElse(null);
            if(old==null) holdings.save(new HoldingEntity(symbol,req.quantity(),(gross+fee)/req.quantity()));
            else {
                int newQty=old.quantity+req.quantity();
                old.averagePrice=(old.averagePrice*old.quantity+gross+fee)/newQty; old.quantity=newQty; holdings.save(old);
            }
            cash-=gross+fee;
        } else {
            var old=holdings.findBySymbol(symbol).orElseThrow(()->new IllegalArgumentException("No paper holding for symbol."));
            if(old.quantity<req.quantity()) throw new IllegalArgumentException("Insufficient paper quantity.");
            double realized=(price-old.averagePrice)*req.quantity()-fee;
            realizedPnl+=realized; dailyRealizedPnl+=realized;
            old.quantity-=req.quantity();
            if(old.quantity==0) holdings.delete(old); else holdings.save(old);
            cash+=gross-fee;
        }
        trades.save(new TradeEntity("PAPER",symbol,side,req.quantity(),price,fee,"SIMULATED",null,"Paper order filled at supplied/latest candle price"));
        persistPaperAccount();
        audit.record("PAPER_ORDER","PAPER",side+" "+req.quantity()+" "+symbol+" @ "+price);
        return new ApiModels.OrderResponse("PAPER",symbol,side,req.quantity(),price,fee,"SIMULATED","Paper fill only; no broker order was sent.",null);
    }
    ApiModels.BacktestResponse backtest(String symbol, ApiModels.StrategyRequest req) {
        var data=market.get(symbol);
        int fast=req.fastPeriod(), slow=req.slowPeriod();
        if(fast>=slow) throw new IllegalArgumentException("fastPeriod must be lower than slowPeriod.");
        if(data.size()<=slow+1) throw new IllegalArgumentException("Not enough candles for selected periods.");
        double funds=req.startingCash(), qty=0, entry=0, peak=funds, maxDD=0;
        int closed=0,wins=0;
        for(int i=slow;i<data.size();i++) {
            double f=avg(data,i,fast),s=avg(data,i,slow),fp=avg(data,i-1,fast),sp=avg(data,i-1,slow),p=data.get(i).close();
            if(fp<=sp&&f>s&&qty==0) { qty=Math.floor(funds/(p*(1+props.paper().brokerageRate()))); if(qty>0){funds-=qty*p*(1+props.paper().brokerageRate());entry=p;} }
            else if(fp>=sp&&f<s&&qty>0) { funds+=qty*p*(1-props.paper().brokerageRate());closed++;if(p>entry)wins++;qty=0; }
            double equity=funds+qty*p; peak=Math.max(peak,equity); maxDD=Math.max(maxDD,(peak-equity)/peak*100);
        }
        double fin=funds+qty*data.get(data.size()-1).close();
        audit.record("BACKTEST","PAPER","SMA "+fast+"/"+slow+" on "+symbol+" candles="+data.size());
        return new ApiModels.BacktestResponse(closed,wins,(fin/req.startingCash()-1)*100,fin,maxDD,
            "Simplified SMA backtest. Does not fully model taxes, exchange fees, slippage, liquidity, corporate actions or realistic fills.");
    }
    private double avg(List<ApiModels.CandleDto> d,int end,int n) {
        double sum=0; for(int i=Math.max(0,end-n+1);i<=end;i++)sum+=d.get(i).close();
        return sum/(end-Math.max(0,end-n+1)+1);
    }

    synchronized Map<String,Object> resolveUnknownLiveOrder(BrokerModels.ResolveUnknownRequest req) {
        if(!"RECONCILED".equals(req.confirmation())) throw new IllegalArgumentException("Resolution requires confirmation='RECONCILED'.");
        TradeEntity trade=trades.findByClientOrderId(req.clientOrderId()).orElseThrow(()->new IllegalArgumentException("Client order ID not found."));
        if(!"UNKNOWN".equals(trade.status)) throw new IllegalStateException("Only UNKNOWN live orders can be reconciled.");
        String outcome=req.outcome().toUpperCase();
        if("SUBMITTED".equals(outcome)) {
            if(req.brokerOrderId()==null || req.brokerOrderId().isBlank()) throw new IllegalArgumentException("Enter the broker order ID after verifying the broker order book.");
            trade.status="SUBMITTED"; trade.brokerOrderId=req.brokerOrderId().trim();
            trade.message="Manually reconciled as submitted from the Angel One order book.";
        } else if("REJECTED".equals(outcome)) {
            trade.status="REJECTED"; trade.message="Manually reconciled as rejected from the Angel One order book.";
        } else throw new IllegalArgumentException("Outcome must be SUBMITTED or REJECTED.");
        trades.save(trade);
        audit.record("LIVE_ORDER_RECONCILED","LIVE","clientOrderId="+req.clientOrderId()+" outcome="+outcome);
        return Map.of("status",trade.status,"clientOrderId",trade.clientOrderId,"brokerOrderId",trade.brokerOrderId==null?"":trade.brokerOrderId,"message","Order outcome manually reconciled.");
    }
    synchronized Map<String,Object> cancelLiveOrder(BrokerModels.CancelOrderRequest req) {
        if(!"CANCEL".equals(req.confirmation())) throw new IllegalArgumentException("Order cancellation requires confirmation='CANCEL'.");
        String variety=req.variety()==null||req.variety().isBlank()?"NORMAL":req.variety().toUpperCase();
        if(!"NORMAL".equals(variety)) throw new IllegalArgumentException("Only NORMAL order cancellation is supported.");
        Map<String,Object> response=angel.cancelOrder(req.orderId(),variety);
        audit.record("LIVE_ORDER_CANCEL_REQUEST","LIVE","orderId="+req.orderId());
        return Map.of("status","REQUEST_ACCEPTED","message","Cancel request accepted by broker; verify final order status.","brokerResponse",response);
    }
    synchronized Map<String,Object> modifyLiveOrder(BrokerModels.ModifyOrderRequest req) {
        if(!"MODIFY LIVE".equals(req.confirmation())) throw new IllegalArgumentException("Order modification requires confirmation='MODIFY LIVE'.");
        if(liveKillSwitch || !liveTradingArmed) throw new IllegalStateException("Live order modification blocked by live mode or kill switch.");
        if(req.quantity()<1 || req.limitPrice()<=0) throw new IllegalArgumentException("Quantity and limit price must be positive.");
        if(!Set.of("BUY","SELL").contains(req.side().toUpperCase())) throw new IllegalArgumentException("Side must be BUY or SELL.");
        InstrumentService.Instrument instrument=instruments.require(req.exchange(),req.symbol());
        if(req.quantity()%instrument.lotSize()!=0) throw new IllegalArgumentException("Quantity must be a multiple of instrument lot size.");
        if(Math.abs(req.limitPrice()/instrument.tickSize()-Math.rint(req.limitPrice()/instrument.tickSize()))>0.000001)
            throw new IllegalArgumentException("Price does not align with instrument tick size.");
        double gross=req.quantity()*req.limitPrice();
        if(gross>props.risk().maxOrderValue()) throw new IllegalArgumentException("Modified order exceeds max order value.");
        if(req.side().equalsIgnoreCase("BUY")) {
            Map<String,Double> brokerRisk=angel.positionRiskSnapshot();
            if(brokerRisk.getOrDefault("realizedPnl",0.0)+brokerRisk.getOrDefault("unrealizedPnl",0.0) <= -props.risk().maxDailyLoss())
                throw new IllegalStateException("Broker RMS daily MTM loss limit reached; BUY modification blocked.");
            if(brokerRisk.getOrDefault("grossExposure",0.0)+gross>props.risk().maxOpenPositionValue())
                throw new IllegalArgumentException("Modified order would exceed configured exposure limit.");
        }
        long now=System.nanoTime();
        if(lastLiveOrderNanos!=0 && now-lastLiveOrderNanos<200_000_000L)
            throw new IllegalStateException("Local order rate limit reached. Wait before modifying another order.");
        lastLiveOrderNanos=now;
        Map<String,Object> response=angel.modifyLimitOrder(instrument,req.orderId(),req.side().toUpperCase(),req.quantity(),req.limitPrice());
        audit.record("LIVE_ORDER_MODIFY_REQUEST","LIVE","orderId="+req.orderId()+" "+req.side()+" "+req.quantity()+" "+req.symbol());
        return Map.of("status","REQUEST_ACCEPTED","message","Modification accepted by broker; verify final order status.","brokerResponse",response);
    }

    private void rolloverDay() {
        String today=java.time.LocalDate.now().toString();
        if(!today.equals(accountDay)) {
            accountDay=today; dailyRealizedPnl=0;
            dayStartEquity=cash+holdings.findAllByOrderBySymbolAsc().stream().mapToDouble(h -> {
                double mark=market.get(h.symbol).stream().reduce((a,b)->b).map(ApiModels.CandleDto::close).orElse(h.averagePrice);
                return h.quantity*mark;
            }).sum();
            persistPaperAccount();
            audit.record("PAPER_DAY_ROLLOVER","PAPER","New local trading day started");
        }
    }
    private void persistPaperAccount() {
        PaperAccountEntity state=paperAccounts.findById(1L).orElse(new PaperAccountEntity(cash,realizedPnl,dailyRealizedPnl,accountDay,dayStartEquity));
        state.cash=cash; state.realizedPnl=realizedPnl; state.dailyRealizedPnl=dailyRealizedPnl; state.currentDay=accountDay; state.dayStartEquity=dayStartEquity;
        paperAccounts.save(state);
    }
    synchronized boolean liveKillSwitch() { return liveKillSwitch; }
    synchronized boolean liveTradingArmed() { return liveTradingArmed; }
    synchronized void setLiveMode(boolean enabled, String confirmation) {
        if(enabled) {
            if(!"ENABLE LIVE".equals(confirmation)) throw new IllegalArgumentException("Enabling live mode requires the exact confirmation ENABLE LIVE.");
            if(!angel.configured() || !angel.sessionPresent()) throw new IllegalStateException("Configure Angel One and log in before enabling live mode.");
            liveTradingArmed=true; liveKillSwitch=false;
            audit.record("LIVE_MODE_ENABLED","LIVE","Live mode explicitly armed from dashboard");
        } else {
            liveTradingArmed=false; liveKillSwitch=true;
            audit.record("LIVE_MODE_DISABLED","LIVE","Live mode disarmed; kill switch enabled");
        }
    }
    synchronized void setLiveKillSwitch(boolean enabled, String confirmation) {
        if(!enabled && !"ENABLE LIVE".equals(confirmation))
            throw new IllegalArgumentException("Clearing the kill switch requires confirmation='ENABLE LIVE'.");
        liveKillSwitch=enabled;
        audit.record(enabled ? "LIVE_KILL_SWITCH_ON" : "LIVE_KILL_SWITCH_OFF","LIVE",
            enabled ? "Live orders blocked by kill switch" : "Kill switch cleared; environment and broker checks still apply");
    }

    synchronized ApiModels.OrderResponse liveOrder(BrokerModels.LiveOrderRequest req) {
        String symbol=req.symbol().trim().toUpperCase(), side=req.side().trim().toUpperCase();
        String exchange=req.exchange().trim().toUpperCase();
        if(!"LIVE".equals(req.confirmation())) throw new IllegalArgumentException("Live order requires the exact confirmation text LIVE.");
        if(req.clientOrderId()==null || !req.clientOrderId().matches("[A-Za-z0-9_-]{1,20}"))
            throw new IllegalArgumentException("clientOrderId must be 1–20 letters, numbers, underscores or hyphens.");
        if(trades.findByClientOrderId(req.clientOrderId()).isPresent())
            throw new IllegalStateException("This clientOrderId has already been used. Check broker order book before submitting a new request.");
        boolean unresolved=trades.findByModeOrderByCreatedAtDesc("LIVE").stream().anyMatch(t -> "UNKNOWN".equals(t.status));
        if(unresolved) throw new IllegalStateException("A prior live order has an UNKNOWN outcome. Reconcile it from the broker order book before any new live order.");
        if(!Set.of("BUY","SELL").contains(side)) throw new IllegalArgumentException("side must be BUY or SELL");
        if(!Set.of("NSE","BSE").contains(exchange)) throw new IllegalArgumentException("Only NSE/BSE cash equities are enabled.");
        if(req.quantity()<1 || req.quantity()>100000) throw new IllegalArgumentException("Quantity must be between 1 and 100000.");
        if(req.limitPrice()<=0) throw new IllegalArgumentException("Live orders require a positive limit price.");
        if(liveKillSwitch) throw new IllegalStateException("Live kill switch is ON. Disable it deliberately through the authenticated API after review.");
        if(!liveTradingArmed) throw new IllegalStateException("Live mode is OFF. Enable it in the dashboard after completing broker setup.");
        long now=System.nanoTime();
        if(lastLiveOrderNanos!=0 && now-lastLiveOrderNanos<200_000_000L)
            throw new IllegalStateException("Local order rate limit reached. Wait before submitting another order.");
        double gross=req.limitPrice()*req.quantity();
        if(gross>props.risk().maxOrderValue()) throw new IllegalArgumentException("Order exceeds configured max order value.");
        if(side.equals("BUY") && gross>props.risk().maxOpenPositionValue()) throw new IllegalArgumentException("Order alone exceeds configured open-position limit.");
        InstrumentService.Instrument instrument=instruments.require(exchange,symbol);
        if(side.equals("BUY")) {
            Map<String,Double> brokerRisk=angel.positionRiskSnapshot();
            if(brokerRisk.getOrDefault("realizedPnl",0.0)+brokerRisk.getOrDefault("unrealizedPnl",0.0) <= -props.risk().maxDailyLoss())
                throw new IllegalStateException("Broker RMS daily MTM P&L has reached the configured loss limit; new BUY orders are blocked.");
            if(brokerRisk.getOrDefault("grossExposure",0.0)+gross>props.risk().maxOpenPositionValue())
                throw new IllegalArgumentException("Order would exceed configured broker-position exposure limit.");
        }
        if(req.quantity()%instrument.lotSize()!=0) throw new IllegalArgumentException("Quantity must be a multiple of instrument lot size "+instrument.lotSize()+".");
        double tick=instruments.require(exchange,symbol).tickSize();
        double tickUnits=req.limitPrice()/tick;
        if(Math.abs(tickUnits-Math.rint(tickUnits))>0.000001) throw new IllegalArgumentException("Limit price does not align to the instrument tick size "+tick+".");
        // Deliberately LIMIT only: current retail-algo requirements prohibit market/IOC orders.
        lastLiveOrderNanos=now;
        TradeEntity attempt=new TradeEntity("LIVE",symbol,side,req.quantity(),req.limitPrice(),0,"PENDING",null,
            req.clientOrderId(),"Submission in progress; do not repeat this client order ID.");
        attempt=trades.save(attempt);
        audit.record("LIVE_ORDER_SUBMIT","LIVE",side+" "+req.quantity()+" "+exchange+":"+symbol+" limit="+req.limitPrice()+" clientOrderId="+req.clientOrderId());
        Map<String,Object> response;
        try {
            response=angel.placeLimitOrder(instrument,side,req.quantity(),req.limitPrice(),req.clientOrderId());
        } catch (RuntimeException ex) {
            attempt.status="UNKNOWN";
            attempt.message="Submission outcome is unknown. Do not retry this order; inspect Angel One order book first.";
            trades.save(attempt);
            audit.record("LIVE_ORDER_UNKNOWN","LIVE","clientOrderId="+req.clientOrderId()+"; inspect broker order book; error="+ex.getClass().getSimpleName());
            return new ApiModels.OrderResponse("LIVE",symbol,side,req.quantity(),req.limitPrice(),0,"UNKNOWN",
                "The broker request did not return a confirmed response. Do not retry; inspect the broker order book.",null);
        }
        String orderId=String.valueOf(response.getOrDefault("orderId",""));
        String uniqueId=String.valueOf(response.getOrDefault("uniqueOrderId",""));
        attempt.status="SUBMITTED"; attempt.brokerOrderId=orderId;
        attempt.message="Broker submission accepted; verify final status in broker order book. Unique ID="+uniqueId;
        trades.save(attempt);
        audit.record("LIVE_ORDER_ACCEPTED","LIVE",side+" "+req.quantity()+" "+exchange+":"+symbol+" brokerOrderId="+orderId);
        return new ApiModels.OrderResponse("LIVE",symbol,side,req.quantity(),req.limitPrice(),0,"SUBMITTED",
            "Broker accepted the order request; this does not mean it was filled. Check broker order book.",orderId);
    }
}
