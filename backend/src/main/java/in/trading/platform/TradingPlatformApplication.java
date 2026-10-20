package in.trading.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class TradingPlatformApplication {
    public static void main(String[] args) {
        try { java.nio.file.Files.createDirectories(java.nio.file.Path.of("data")); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot create backend data directory.", e); }
        SpringApplication.run(TradingPlatformApplication.class, args);
    }
}
