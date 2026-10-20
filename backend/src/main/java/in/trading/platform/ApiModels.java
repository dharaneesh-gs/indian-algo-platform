package in.trading.platform;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ApiModels {
    private ApiModels() {}
    public record OrderRequest(@NotBlank String symbol, @NotBlank String side,
                               @Min(1) @Max(100000) int quantity,
                               @Positive Double price, String confirmation) {}
    public record CandleRequest(@NotNull Instant time, @Positive double open, @Positive double high,
                                @Positive double low, @Positive double close, double volume) {}
    public record StrategyRequest(@Min(2) @Max(100) int fastPeriod,
                                  @Min(3) @Max(500) int slowPeriod,
                                  @Positive double startingCash) {}
    public record CandleDto(Instant time, double open, double high, double low, double close, double volume) {}
    public record OrderResponse(String mode, String symbol, String side, int quantity,
                                double price, double fee, String status, String message, String brokerOrderId) {}
    public record AccountResponse(String mode, double cash, double holdingsValue, double equity,
                                  double realizedPnl, double unrealizedPnl, List<HoldingDto> holdings) {}
    public record HoldingDto(String symbol, int quantity, double averagePrice, double markPrice, double unrealizedPnl) {}
    public record BacktestResponse(int closedTrades, int wins, double returnPct, double finalEquity,
                                   double maxDrawdownPct, String caveat) {}
    public record BrokerStatus(boolean configured, boolean liveTradingEnabled, boolean sessionPresent,
                               String broker, String note) {}
}
