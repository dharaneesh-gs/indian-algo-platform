package in.trading.platform;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
class AuditService {
    private final AuditRepository repo;
    AuditService(AuditRepository repo) { this.repo=repo; }
    @Transactional
    void record(String action, String mode, String detail) {
        repo.save(new AuditEntity("local-user", action, mode, sanitize(detail)));
    }
    List<Map<String,Object>> recent() {
        return repo.findTop200ByOrderByCreatedAtDesc().stream().map(a -> Map.<String,Object>of(
            "id", a.id, "time", a.createdAt.toString(), "actor", a.actor, "action", a.action,
            "mode", a.mode, "detail", a.detail)).toList();
    }
    private String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)(jwt|token|secret|password|pin|totp)\\s*[:=]\\s*[^, ]+", "$1=[REDACTED]");
    }
}
