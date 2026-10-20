package in.trading.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
class InstrumentService {
    record Instrument(String symbol, String token, String exchange, double tickSize, int lotSize) {}
    private final Map<String,Instrument> items = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final RestClient publicClient = RestClient.builder().build();
    private final Path file = Path.of("data","instruments.json");
    InstrumentService(ObjectMapper mapper) { this.mapper=mapper; }
    private String key(String exchange, String symbol) { return exchange.toUpperCase()+":"+symbol.toUpperCase(); }

    @PostConstruct
    synchronized void loadSaved() {
        try {
            if (Files.exists(file)) {
                List<Instrument> saved=mapper.readValue(file.toFile(),new TypeReference<List<Instrument>>() {});
                saved.forEach(i -> items.put(key(i.exchange(),i.symbol()),i));
            }
        } catch(Exception e) { throw new IllegalStateException("Could not load saved instrument mappings from data/instruments.json.",e); }
    }
    synchronized void upsert(Instrument i) {
        if (!Set.of("NSE","BSE").contains(i.exchange().toUpperCase())) throw new IllegalArgumentException("Only NSE and BSE cash instruments are supported in this starter.");
        if (i.symbol().isBlank() || i.token().isBlank() || i.tickSize() <= 0 || i.lotSize() < 1)
            throw new IllegalArgumentException("Instrument requires symbol, token, positive tick size and lot size.");
        items.put(key(i.exchange(),i.symbol()), new Instrument(i.symbol().toUpperCase(),i.token(),i.exchange().toUpperCase(),i.tickSize(),i.lotSize()));
        persist();
    }
    Instrument require(String exchange, String symbol) {
        Instrument i=items.get(key(exchange,symbol));
        if (i==null) throw new IllegalArgumentException("Instrument is not loaded. Enter the exact current official Angel One exchange/symbol/token/tick-size mapping first.");
        return i;
    }
    synchronized int importRows(List<Instrument> rows) { rows.forEach(this::upsert); return rows.size(); }
    synchronized int refreshOfficialMaster() {
        final String url="https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json";
        String json;
        try { json=publicClient.get().uri(url).retrieve().body(String.class); }
        catch(Exception e) { throw new IllegalStateException("Angel One instrument-master download failed. Use the manual mapping form and retry later.",e); }
        try {
            var root=mapper.readTree(json);
            if(!root.isArray()) throw new IllegalStateException("Instrument-master response was not a JSON array.");
            int count=0;
            Map<String,Instrument> refreshed=new HashMap<>();
            for(var node:root) {
                String exchange=node.path("exch_seg").asText("");
                String symbol=node.path("symbol").asText("");
                String token=node.path("token").asText("");
                String type=node.path("instrumenttype").asText("");
                if(!Set.of("NSE","BSE").contains(exchange) || !symbol.endsWith("-EQ") || (!type.isBlank() && !type.equalsIgnoreCase("EQ"))) continue;
                double rawTick;
                try { rawTick=Double.parseDouble(node.path("tick_size").asText("0")); } catch(Exception e) { continue; }
                double tick=rawTick>1 ? rawTick/100.0 : rawTick;
                int lot;
                try { lot=Integer.parseInt(node.path("lotsize").asText("1")); } catch(Exception e) { lot=1; }
                if(tick<=0 || token.isBlank()) continue;
                refreshed.put(key(exchange,symbol),new Instrument(symbol,token,exchange,tick,Math.max(1,lot)));
                count++;
            }
            if(count==0) throw new IllegalStateException("No NSE/BSE cash-equity instruments were recognized. Check the official master format and use manual mapping.");
            items.clear();
            items.putAll(refreshed);
            persist();
            return count;
        } catch(IllegalStateException e) { throw e; }
        catch(Exception e) { throw new IllegalStateException("Could not parse Angel One instrument master. Use manual mapping or retry later.",e); }
    }
    List<Instrument> all() { return items.values().stream().sorted(Comparator.comparing(Instrument::exchange).thenComparing(Instrument::symbol)).toList(); }
    private void persist() {
        try { Files.createDirectories(file.getParent()); mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(),all()); }
        catch(Exception e) { throw new IllegalStateException("Could not persist instrument mappings.",e); }
    }
}
