package in.trading.platform;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
class AngelOneClient {
    private final PlatformProperties props;
    private final RestClient client;
    private final AtomicReference<String> jwt = new AtomicReference<>("");
    private final AtomicReference<String> feed = new AtomicReference<>("");
    private final AtomicReference<String> refresh = new AtomicReference<>("");

    AngelOneClient(PlatformProperties props) {
        this.props=props;
        this.client=RestClient.builder().baseUrl(props.angel().baseUrl()).build();
    }
    synchronized BrokerModels.LoginStatus login(String pin, String totp) {
        requireBrokerConfig();
        if (pin == null || pin.isBlank() || totp == null || !totp.matches("\\d{6}"))
            throw new IllegalArgumentException("Provide the current broker PIN and six-digit TOTP. They are used only for this login request.");
        Map<String,Object> body=Map.of("clientcode",props.angel().clientCode(),"password",pin,"totp",totp);
        Map response=client.post().uri("/rest/auth/angelbroking/user/v1/loginByPassword")
            .headers(h -> commonHeaders(h, false)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
        if (response==null || !Boolean.TRUE.equals(response.get("status"))) {
            String msg=response==null?"Empty response":String.valueOf(response.getOrDefault("message","Login failed"));
            throw new IllegalStateException("Angel One login failed: "+msg);
        }
        Object data=response.get("data");
        if (!(data instanceof Map<?,?> d)) throw new IllegalStateException("Broker login response did not contain session data.");
        jwt.set(String.valueOf(d.containsKey("jwtToken") ? d.get("jwtToken") : ""));
        feed.set(String.valueOf(d.containsKey("feedToken") ? d.get("feedToken") : ""));
        refresh.set(String.valueOf(d.containsKey("refreshToken") ? d.get("refreshToken") : ""));
        if (jwt.get().isBlank()) throw new IllegalStateException("Broker login response did not provide a JWT session.");
        return new BrokerModels.LoginStatus(true,"Angel One session established in memory. Tokens are not returned to the iPhone.");
    }

    synchronized BrokerModels.LoginStatus refreshSession() {
        if (!sessionPresent() || refresh.get().isBlank()) throw new IllegalStateException("No refresh token is available; log in again.");
        Map response=client.post().uri("/rest/auth/angelbroking/jwt/v1/generateTokens")
            .headers(h -> commonHeaders(h,true)).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("refreshToken",refresh.get())).retrieve().body(Map.class);
        if(response==null || !Boolean.TRUE.equals(response.get("status"))) throw new IllegalStateException("Session refresh failed; log in again.");
        Object raw=response.get("data");
        if(!(raw instanceof Map<?,?> d)) throw new IllegalStateException("Session refresh response is incomplete.");
        jwt.set(String.valueOf(d.containsKey("jwtToken")?d.get("jwtToken"):""));
        feed.set(String.valueOf(d.containsKey("feedToken")?d.get("feedToken"):""));
        refresh.set(String.valueOf(d.containsKey("refreshToken")?d.get("refreshToken"):refresh.get()));
        if(jwt.get().isBlank()) throw new IllegalStateException("Session refresh returned no JWT.");
        return new BrokerModels.LoginStatus(true,"Session refreshed in memory.");
    }

    Map<String,Object> accountSnapshot() {
        if(!sessionPresent()) throw new IllegalStateException("Log in to Angel One first.");
        Map rms=client.get().uri("/rest/secure/angelbroking/user/v1/getRMS")
            .headers(h -> commonHeaders(h,true)).retrieve().body(Map.class);
        Map holdings=client.get().uri("/rest/secure/angelbroking/portfolio/v1/getHolding")
            .headers(h -> commonHeaders(h,true)).retrieve().body(Map.class);
        Map positions=client.get().uri("/rest/secure/angelbroking/order/v1/getPosition")
            .headers(h -> commonHeaders(h,true)).retrieve().body(Map.class);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("rms",rms==null?Map.of():rms);
        result.put("holdings",holdings==null?Map.of():holdings);
        result.put("positions",positions==null?Map.of():positions);
        result.put("note","Read-only broker snapshot. Verify with the official Angel One app/order book before relying on balances.");
        return result;
    }

    synchronized void logout() {
        String token=jwt.get();
        try {
            if (!token.isBlank()) client.post().uri("/rest/secure/angelbroking/user/v1/logout")
                .headers(h -> commonHeaders(h,true)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("clientcode",props.angel().clientCode())).retrieve().body(Map.class);
        } finally { jwt.set(""); feed.set(""); refresh.set(""); }
    }
    boolean configured() {
        return notBlank(props.angel().apiKey()) && notBlank(props.angel().clientCode())
            && notBlank(props.angel().registeredStaticIp()) && notBlank(props.angel().clientLocalIp())
            && notBlank(props.angel().macAddress());
    }
    boolean sessionPresent() { return notBlank(jwt.get()); }
    String feedToken() { return feed.get(); }
    Map<String,Object> status(boolean enabled) {
        return Map.of("configured",configured(),"liveTradingEnabled",enabled,"sessionPresent",sessionPresent(),
            "broker","Angel One SmartAPI","note","Session tokens remain in backend memory only; logout clears them.");
    }
    Map<String,Object> placeLimitOrder(InstrumentService.Instrument instrument, String side, int quantity, double price, String clientOrderId) {
        if (!sessionPresent()) throw new IllegalStateException("Log in to Angel One from the Broker tab before live orders.");
        if (!configured()) throw new IllegalStateException("Broker API key, client code, registered static IP, local IP and MAC address must be configured.");
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("variety","NORMAL"); body.put("tradingsymbol",instrument.symbol()); body.put("symboltoken",instrument.token());
        body.put("transactiontype",side); body.put("exchange",instrument.exchange()); body.put("ordertype","LIMIT");
        body.put("producttype",props.angel().productType()); body.put("duration","DAY");
        body.put("price",String.format(Locale.US,"%.2f",price)); body.put("quantity",Integer.toString(quantity));
        body.put("triggerprice","0"); body.put("squareoff","0"); body.put("stoploss","0");
        body.put("trailingstoploss","0"); body.put("disclosedquantity","0"); body.put("ordertag",clientOrderId);
        Map response=client.post().uri("/rest/secure/angelbroking/order/v1/placeOrder")
            .headers(h -> commonHeaders(h,true)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
        if (response==null || !Boolean.TRUE.equals(response.get("status"))) {
            String msg=response==null?"Empty broker response":String.valueOf(response.getOrDefault("message","Order rejected"));
            throw new IllegalStateException("Angel One rejected order: "+msg);
        }
        Object d=response.get("data");
        if (!(d instanceof Map<?,?> data)) throw new IllegalStateException("Broker response had no order data; check broker order book before retrying.");
        Map<String,Object> safe=new LinkedHashMap<>();
        String orderId=String.valueOf(data.containsKey("orderid") ? data.get("orderid") : "");
        if(orderId.isBlank()) throw new IllegalStateException("Broker response omitted the order ID. Submission outcome is unknown; inspect the broker order book.");
        safe.put("orderId",orderId);
        safe.put("uniqueOrderId",String.valueOf(data.containsKey("uniqueorderid") ? data.get("uniqueorderid") : ""));
        safe.put("status","SUBMITTED");
        safe.put("message","Broker accepted the order request; acceptance is not a fill. Reconcile order book for final status.");
        return safe;
    }
    double ltpPrice(InstrumentService.Instrument instrument) {
        Map<String,Object> result=ltp(instrument);
        Object data=result.get("data");
        if (!(data instanceof Map<?,?> m)) throw new IllegalStateException("Live quote response is incomplete.");
        double price=number(m.get("ltp"));
        if(price<=0) throw new IllegalStateException("Live quote did not contain a valid last traded price.");
        return price;
    }
    int positionQuantity(InstrumentService.Instrument instrument) {
        if(!sessionPresent()) throw new IllegalStateException("Log in to Angel One first.");
        Map response=client.get().uri("/rest/secure/angelbroking/order/v1/getPosition")
            .headers(h -> commonHeaders(h,true)).retrieve().body(Map.class);
        if(response==null || !Boolean.TRUE.equals(response.get("status"))) throw new IllegalStateException("Cannot verify broker position quantity.");
        Object raw=response.get("data"); int qty=0;
        if(raw instanceof List<?> rows) for(Object row:rows) {
            if(row instanceof Map<?,?> m && instrument.symbol().equalsIgnoreCase(String.valueOf(m.get("tradingsymbol")))
                && instrument.exchange().equalsIgnoreCase(String.valueOf(m.get("exchange")))) qty += (int)number(m.get("netqty"));
        }
        if(qty==0) {
            Map holdingResponse=client.get().uri("/rest/secure/angelbroking/portfolio/v1/getHolding")
                .headers(h -> commonHeaders(h,true)).retrieve().body(Map.class);
            Object holdingData=holdingResponse==null?null:holdingResponse.get("data");
            if(holdingData instanceof List<?> rows) for(Object row:rows) {
                if(row instanceof Map<?,?> m && instrument.symbol().equalsIgnoreCase(String.valueOf(m.get("tradingsymbol")))
                    && instrument.exchange().equalsIgnoreCase(String.valueOf(m.get("exchange")))) qty += (int)number(m.get("quantity"));
            }
        }
        return qty;
    }
    Map<String,Object> ltp(InstrumentService.Instrument instrument) {
        if (!sessionPresent()) throw new IllegalStateException("Log in to Angel One to request live quotes.");
        Map response=client.post().uri("/rest/secure/angelbroking/order/v1/getLtpData")
            .headers(h -> commonHeaders(h,true)).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("exchange",instrument.exchange(),"tradingsymbol",instrument.symbol(),"symboltoken",instrument.token()))
            .retrieve().body(Map.class);
        if (response==null || !Boolean.TRUE.equals(response.get("status"))) throw new IllegalStateException("Live quote request failed.");
        return Map.of("broker","Angel One SmartAPI","exchange",instrument.exchange(),"symbol",instrument.symbol(),
            "data",response.getOrDefault("data",Map.of()));
    }

    Map<String,Object> cancelOrder(String orderId, String variety) {
        if(!sessionPresent()) throw new IllegalStateException("Log in to Angel One first.");
        if(orderId==null || orderId.isBlank()) throw new IllegalArgumentException("Broker order ID is required.");
        Map<?,?> existing=findOpenOrder(orderId);
        if(existing==null) throw new IllegalStateException("Order not found or no longer open in broker order book; refresh the order book.");
        Map response=client.post().uri("/rest/secure/angelbroking/order/v1/cancelOrder")
            .headers(h -> commonHeaders(h,true)).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("variety",variety,"orderid",orderId)).retrieve().body(Map.class);
        if(response==null || !Boolean.TRUE.equals(response.get("status")))
            throw new IllegalStateException("Broker cancel request failed; check order book before retrying.");
        return response;
    }
    Map<String,Object> modifyLimitOrder(InstrumentService.Instrument instrument, String orderId, String side, int quantity, double price) {
        if(!sessionPresent()) throw new IllegalStateException("Log in to Angel One first.");
        Map<?,?> existing=findOpenOrder(orderId);
        if(existing==null) throw new IllegalStateException("Order not found or no longer open in broker order book; refresh the order book.");
        if(!instrument.symbol().equalsIgnoreCase(String.valueOf(existing.get("tradingsymbol")))
            || !instrument.exchange().equalsIgnoreCase(String.valueOf(existing.get("exchange")))
            || !side.equalsIgnoreCase(String.valueOf(existing.get("transactiontype"))))
            throw new IllegalArgumentException("Instrument/exchange/side does not match the open broker order.");
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("variety","NORMAL"); body.put("orderid",orderId); body.put("tradingsymbol",instrument.symbol());
        body.put("symboltoken",instrument.token()); body.put("transactiontype",side); body.put("exchange",instrument.exchange());
        body.put("ordertype","LIMIT"); body.put("producttype",props.angel().productType()); body.put("duration","DAY");
        body.put("price",String.format(Locale.US,"%.2f",price)); body.put("quantity",Integer.toString(quantity));
        body.put("triggerprice","0"); body.put("disclosedquantity","0");
        Map response=client.post().uri("/rest/secure/angelbroking/order/v1/modifyOrder")
            .headers(h -> commonHeaders(h,true)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
        if(response==null || !Boolean.TRUE.equals(response.get("status")))
            throw new IllegalStateException("Broker modify request failed; check order book before retrying.");
        return response;
    }

    private Map<?,?> findOpenOrder(String orderId) {
        Map book=orderBook();
        Object raw=book.get("data");
        if(!(raw instanceof List<?> rows)) return null;
        for(Object row:rows) {
            if(!(row instanceof Map<?,?> m)) continue;
            String id=String.valueOf(m.get("orderid"));
            if(!orderId.equals(id)) continue;
            String status=String.valueOf(m.containsKey("orderstatus")?m.get("orderstatus"):m.get("status")).toLowerCase(Locale.ROOT);
            if(Set.of("open","pending","trigger pending","validation pending").contains(status)) return m;
            return null;
        }
        return null;
    }
    Map<String,Object> orderBook() {
        if (!sessionPresent()) throw new IllegalStateException("Log in to Angel One first.");
        Map response=client.get().uri("/rest/secure/angelbroking/order/v1/getOrderBook")
            .headers(h -> commonHeaders(h,true)).retrieve().body(Map.class);
        return response==null?Map.of("status",false):response;
    }

    List<ApiModels.CandleDto> historical(InstrumentService.Instrument instrument, String interval, String fromDate, String toDate) {
        if (!sessionPresent()) throw new IllegalStateException("Log in to Angel One first.");
        if (!Set.of("ONE_MINUTE","THREE_MINUTE","FIVE_MINUTE","TEN_MINUTE","FIFTEEN_MINUTE","THIRTY_MINUTE","ONE_HOUR","ONE_DAY").contains(interval))
            throw new IllegalArgumentException("Unsupported interval.");
        Map response=client.post().uri("/rest/secure/angelbroking/historical/v1/getCandleData")
            .headers(h -> commonHeaders(h,true)).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("exchange",instrument.exchange(),"symboltoken",instrument.token(),"interval",interval,
                         "fromdate",fromDate,"todate",toDate)).retrieve().body(Map.class);
        if (response==null || !Boolean.TRUE.equals(response.get("status"))) throw new IllegalStateException("Historical candle request failed.");
        Object raw=response.get("data");
        if (!(raw instanceof List<?> rows)) return List.of();
        List<ApiModels.CandleDto> out=new ArrayList<>();
        for (Object row: rows) {
            if (!(row instanceof List<?> a) || a.size()<6) continue;
            try {
                String date=String.valueOf(a.get(0)).replace(" ","T");
                java.time.Instant time;
                try { time=java.time.OffsetDateTime.parse(date).toInstant(); }
                catch(Exception ex) { time=java.time.LocalDateTime.parse(date).toInstant(java.time.ZoneOffset.UTC); }
                double o=Double.parseDouble(String.valueOf(a.get(1))), h=Double.parseDouble(String.valueOf(a.get(2)));
                double l=Double.parseDouble(String.valueOf(a.get(3))), c=Double.parseDouble(String.valueOf(a.get(4)));
                double v=Double.parseDouble(String.valueOf(a.get(5)));
                out.add(new ApiModels.CandleDto(time,o,h,l,c,v));
            } catch (Exception ignored) { }
        }
        return out;
    }

    Map<String,Double> positionRiskSnapshot() {
        if (!sessionPresent()) throw new IllegalStateException("Log in to Angel One first.");
        Map rms=client.get().uri("/rest/secure/angelbroking/user/v1/getRMS")
            .headers(h -> commonHeaders(h,true)).retrieve().body(Map.class);
        if (rms==null || !Boolean.TRUE.equals(rms.get("status")) || !(rms.get("data") instanceof Map<?,?> rm))
            throw new IllegalStateException("Cannot verify broker RMS daily P&L; live order blocked.");
        if (!rm.containsKey("m2mrealized") || !rm.containsKey("m2munrealized")
            || rm.get("m2mrealized")==null || rm.get("m2munrealized")==null)
            throw new IllegalStateException("Broker RMS response lacks daily MTM fields; live order blocked.");
        double realized=requiredNumber(rm.get("m2mrealized"),"m2mrealized");
        double unrealized=requiredNumber(rm.get("m2munrealized"),"m2munrealized");
        Map positions=client.get().uri("/rest/secure/angelbroking/order/v1/getPosition")
            .headers(h -> commonHeaders(h,true)).retrieve().body(Map.class);
        if (positions==null || !Boolean.TRUE.equals(positions.get("status")))
            throw new IllegalStateException("Cannot verify current broker positions; live order blocked.");
        Object raw=positions.get("data");
        double exposure=0;
        if (raw instanceof List<?> rows) {
            for (Object row: rows) {
                if (!(row instanceof Map<?,?> m)) continue;
                double qty=Math.abs(number(m.get("netqty")));
                double ltp=number(m.get("ltp"));
                if (ltp>0) exposure += qty*ltp;
            }
        }
        // Include settled holdings too. This may conservatively double-count broker-reported
        // delivery positions on some account configurations, which is safer than undercounting.
        Map holdingResponse=client.get().uri("/rest/secure/angelbroking/portfolio/v1/getHolding")
            .headers(h -> commonHeaders(h,true)).retrieve().body(Map.class);
        if(holdingResponse==null || !Boolean.TRUE.equals(holdingResponse.get("status")))
            throw new IllegalStateException("Cannot verify settled holdings; live BUY blocked.");
        Object holdingRaw=holdingResponse.get("data");
        if(holdingRaw instanceof List<?> rows) for(Object row:rows) {
            if(!(row instanceof Map<?,?> m)) continue;
            double qty=Math.abs(number(m.get("quantity")));
            double ltp=number(m.get("ltp"));
            if(qty>0 && ltp<=0) throw new IllegalStateException("Holding quote is missing; live BUY blocked.");
            if(ltp>0) exposure+=qty*ltp;
        }
        return Map.of("realizedPnl",realized,"unrealizedPnl",unrealized,"grossExposure",exposure);
    }
    private double requiredNumber(Object value, String field) {
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (Exception e) { throw new IllegalStateException("Broker RMS field "+field+" is invalid; live BUY blocked."); }
    }
    private double number(Object value) {
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return 0; }
    }

    private void requireBrokerConfig() {
        if (!configured()) throw new IllegalStateException("Configure ANGEL_API_KEY, ANGEL_CLIENT_CODE, ANGEL_REGISTERED_STATIC_IP, ANGEL_CLIENT_LOCAL_IP and ANGEL_MAC_ADDRESS on the home computer.");
    }
    private void commonHeaders(org.springframework.http.HttpHeaders h, boolean auth) {
        h.set("X-PrivateKey",props.angel().apiKey());
        h.set("X-ClientLocalIP",props.angel().clientLocalIp());
        h.set("X-ClientPublicIP",props.angel().registeredStaticIp());
        h.set("X-MACAddress",props.angel().macAddress());
        h.set("X-UserType","USER"); h.set("X-SourceID","WEB");
        h.set("Accept","application/json");
        if (auth && sessionPresent()) h.setBearerAuth(jwt.get());
    }
    private boolean notBlank(String v) { return v!=null&&!v.isBlank(); }
}
