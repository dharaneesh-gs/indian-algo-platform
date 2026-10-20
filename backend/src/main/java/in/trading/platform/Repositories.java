package in.trading.platform;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface TradeRepository extends JpaRepository<TradeEntity, Long> {
    List<TradeEntity> findTop100ByOrderByCreatedAtDesc();
    List<TradeEntity> findByModeOrderByCreatedAtDesc(String mode);
    Optional<TradeEntity> findByClientOrderId(String clientOrderId);
}
interface AuditRepository extends JpaRepository<AuditEntity, Long> {
    List<AuditEntity> findTop200ByOrderByCreatedAtDesc();
}
interface HoldingRepository extends JpaRepository<HoldingEntity, Long> {
    Optional<HoldingEntity> findBySymbol(String symbol);
    List<HoldingEntity> findAllByOrderBySymbolAsc();
}

interface PaperAccountRepository extends JpaRepository<PaperAccountEntity, Long> {}
interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {
    Optional<AppUserEntity> findByUsernameIgnoreCase(String username);
}
