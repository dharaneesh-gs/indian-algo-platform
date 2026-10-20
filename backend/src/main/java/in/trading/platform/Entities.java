package in.trading.platform;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "paper_trades")
class TradeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    Instant createdAt;
    String mode;
    String symbol;
    String side;
    int quantity;
    double price;
    double fee;
    String status;
    String brokerOrderId;
    @Column(unique = true) String clientOrderId;
    String message;
    protected TradeEntity() {}
    TradeEntity(String mode, String symbol, String side, int quantity, double price, double fee,
                String status, String brokerOrderId, String message) {
        this.createdAt=Instant.now(); this.mode=mode; this.symbol=symbol; this.side=side;
        this.quantity=quantity; this.price=price; this.fee=fee; this.status=status;
        this.brokerOrderId=brokerOrderId; this.message=message;
    }
    TradeEntity(String mode, String symbol, String side, int quantity, double price, double fee,
                String status, String brokerOrderId, String clientOrderId, String message) {
        this(mode,symbol,side,quantity,price,fee,status,brokerOrderId,message);
        this.clientOrderId=clientOrderId;
    }
}

@Entity
@Table(name = "audit_events")
class AuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    Instant createdAt;
    String actor;
    String action;
    String mode;
    String detail;
    protected AuditEntity() {}
    AuditEntity(String actor, String action, String mode, String detail) {
        this.createdAt=Instant.now(); this.actor=actor; this.action=action; this.mode=mode; this.detail=detail;
    }
}

@Entity
@Table(name = "paper_holdings", uniqueConstraints = @UniqueConstraint(columnNames = "symbol"))
class HoldingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(unique = true, nullable = false) String symbol;
    int quantity;
    double averagePrice;
    protected HoldingEntity() {}
    HoldingEntity(String symbol, int quantity, double averagePrice) {
        this.symbol=symbol; this.quantity=quantity; this.averagePrice=averagePrice;
    }
}


@Entity
@Table(name = "paper_account_state")
class PaperAccountEntity {
    @Id Long id = 1L;
    double cash;
    double realizedPnl;
    double dailyRealizedPnl;
    String currentDay;
    double dayStartEquity;
    protected PaperAccountEntity() {}
    PaperAccountEntity(double cash, double realizedPnl, double dailyRealizedPnl, String currentDay, double dayStartEquity) {
        this.id=1L; this.cash=cash; this.realizedPnl=realizedPnl; this.dailyRealizedPnl=dailyRealizedPnl;
        this.currentDay=currentDay; this.dayStartEquity=dayStartEquity;
    }
}

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
class AppUserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, unique = true, length = 64) String username;
    @Column(nullable = false, length = 100) String passwordHash;
    Instant createdAt;
    protected AppUserEntity() {}
    AppUserEntity(String username, String passwordHash) {
        this.username=username; this.passwordHash=passwordHash; this.createdAt=Instant.now();
    }
}
