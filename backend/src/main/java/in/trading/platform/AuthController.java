package in.trading.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/auth")
class AuthController {
    record RegistrationRequest(@NotBlank @Size(min=3,max=64) @Pattern(regexp="[A-Za-z0-9._-]+") String username,
                               @NotBlank @Size(min=12,max=128) String password) {}

    private final AuthAccountService accounts;
    AuthController(AuthAccountService accounts) { this.accounts=accounts; }

    @GetMapping("/status") Map<String,Object> status() {
        return Map.of("firstAccountSetupOpen",accounts.setupOpen());
    }

    @PostMapping("/register") Map<String,Object> register(@Valid @RequestBody RegistrationRequest request, HttpServletRequest servletRequest) {
        if(accounts.setupOpen() && !isLoopback(servletRequest.getRemoteAddr()))
            throw new IllegalStateException("Create the first account from the backend computer before enabling network access.");
        accounts.registerFirstUser(request.username(),request.password());
        return Map.of("status","created","message","Account created. Sign in to continue.");
    }

    private boolean isLoopback(String address) {
        try { return java.net.InetAddress.getByName(address).isLoopbackAddress(); }
        catch(Exception ignored) { return false; }
    }
}
