package in.trading.platform;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class BrokerModels {
    private BrokerModels() {}
    public record LoginRequest(@NotBlank String pin, @NotBlank String totp) {}
    public record AngelConfigRequest(@NotBlank String apiKey, @NotBlank String clientCode, @NotBlank String registeredStaticIp,
                                     @NotBlank String clientLocalIp, @NotBlank String macAddress, String productType) {}
    public record LiveModeRequest(boolean enabled, String confirmation) {}
    public record InstrumentInput(@NotBlank String symbol, @NotBlank String token, @NotBlank String exchange,
                                  double tickSize, int lotSize) {}
    public record LiveOrderRequest(@NotBlank String symbol, @NotBlank String exchange, @NotBlank String side,
                                   int quantity, double limitPrice, @NotBlank String confirmation, @NotBlank String clientOrderId) {}
    public record QuoteRequest(@NotBlank String symbol, @NotBlank String exchange) {}
    public record ResolveUnknownRequest(@NotBlank String clientOrderId, @NotBlank String outcome,
                                        String brokerOrderId, @NotBlank String confirmation) {}
    public record CancelOrderRequest(@NotBlank String orderId, String variety, @NotBlank String confirmation) {}
    public record ModifyOrderRequest(@NotBlank String orderId, @NotBlank String symbol, @NotBlank String exchange,
                                     @NotBlank String side, int quantity, double limitPrice, @NotBlank String confirmation) {}
    public record StrategyConfigRequest(@NotBlank String symbol, @NotBlank String exchange,
                                        @NotBlank String mode, int fastPeriod, int slowPeriod, int quantity) {}
    public record StrategyStatus(boolean enabled, String symbol, String exchange, String mode,
                                 int fastPeriod, int slowPeriod, int quantity, String lastSignal,
                                 String lastError, String note) {}
    public record KillSwitchRequest(boolean enabled, String confirmation) {}
    public record HistoricalRequest(@NotBlank String symbol, @NotBlank String exchange, @NotBlank String interval,
                                    @NotBlank String fromDate, @NotBlank String toDate) {}
    public record LoginStatus(boolean loggedIn, String message) {}
}
