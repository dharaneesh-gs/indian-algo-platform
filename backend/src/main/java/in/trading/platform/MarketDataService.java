package in.trading.platform;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
class MarketDataService {
    private final Map<String,List<ApiModels.CandleDto>> candles = new ConcurrentHashMap<>();
    MarketDataService() { loadDemo(); }
    synchronized void loadDemo() {
        List<ApiModels.CandleDto> data = new ArrayList<>();
        double price=1000;
        for (int i=0;i<120;i++) {
            double open=price;
            price=Math.max(20,price+Math.sin(i/8.0)*7+Math.cos(i/17.0)*3+(((i*17)%11)-5)*1.4);
            Instant t=Instant.now().minusSeconds((long)(119-i)*86400);
            data.add(new ApiModels.CandleDto(t,open,Math.max(open,price)+2.5,Math.min(open,price)-2.5,price,100000+i*321));
        }
        candles.put("DEMO", data);
    }
    synchronized List<ApiModels.CandleDto> get(String symbol) { return List.copyOf(candles.getOrDefault(symbol.toUpperCase(), List.of())); }
    synchronized int replace(String symbol, List<ApiModels.CandleRequest> incoming) {
        var data=incoming.stream().sorted(Comparator.comparing(ApiModels.CandleRequest::time))
            .map(c -> new ApiModels.CandleDto(c.time(),c.open(),c.high(),c.low(),c.close(),c.volume())).toList();
        candles.put(symbol.toUpperCase(), new ArrayList<>(data)); return data.size();
    }
    synchronized boolean appendLiveTick(String symbol, double price, Instant timestamp) {
        if (price <= 0) return false;
        String key=symbol.toUpperCase();
        List<ApiModels.CandleDto> list=candles.computeIfAbsent(key,k -> new ArrayList<>());
        Instant minute=timestamp.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        if (!list.isEmpty()) {
            ApiModels.CandleDto last=list.get(list.size()-1);
            Instant lastMinute=last.time().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            if (lastMinute.equals(minute)) {
                list.set(list.size()-1,new ApiModels.CandleDto(last.time(),last.open(),Math.max(last.high(),price),
                    Math.min(last.low(),price),price,last.volume()));
                return false;
            }
            if (minute.isBefore(lastMinute)) return false;
        }
        list.add(new ApiModels.CandleDto(minute,price,price,price,price,0));
        if (list.size()>5000) list.subList(0,list.size()-5000).clear();
        return true;
    }
    Map<String,Object> summary(String symbol) {
        var data=get(symbol);
        if(data.isEmpty()) return Map.of("symbol",symbol,"count",0);
        var last=data.get(data.size()-1);
        return Map.of("symbol",symbol,"count",data.size(),"latestClose",last.close(),"latestTime",last.time().toString(),
                      "source",symbol.equalsIgnoreCase("DEMO")?"SYNTHETIC_DEMO":"IMPORTED_OR_CONFIGURED");
    }
}
