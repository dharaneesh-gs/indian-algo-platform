package in.trading.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading")
public record PlatformProperties(Paper paper, Risk risk, Live live, Angel angel) {
    public record Paper(double startingCash, double brokerageRate) {}
    public record Risk(double maxOrderValue, double maxOpenPositionValue, double maxDailyLoss) {}
    public record Live(boolean enabled) {}
    public record Angel(String apiKey, String clientCode, String registeredStaticIp, String clientLocalIp,
                        String macAddress, String baseUrl, String productType) {}
}
