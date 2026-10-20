package in.trading.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
class ApiTokenFilter extends OncePerRequestFilter {
    @Value("${APP_API_TOKEN:}")
    private String configuredToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals("/api/health") || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (configuredToken == null || configuredToken.length() < 32) {
            response.setStatus(503);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"API_TOKEN_NOT_CONFIGURED\",\"message\":\"Set APP_API_TOKEN to a random secret of at least 32 characters.\"}");
            return;
        }
        String header = request.getHeader("Authorization");
        String supplied = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
        boolean valid = MessageDigest.isEqual(configuredToken.getBytes(StandardCharsets.UTF_8),
                                              supplied.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
