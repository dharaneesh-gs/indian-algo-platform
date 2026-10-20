package in.trading.platform;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class AuthAccountService implements UserDetailsService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;

    AuthAccountService(AppUserRepository users, PasswordEncoder passwords) {
        this.users=users;
        this.passwords=passwords;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUserEntity user=users.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password."));
        return User.withUsername(user.username).password(user.passwordHash).roles("USER").build();
    }

    synchronized void registerFirstUser(String username, String password) {
        if(users.count()!=0) throw new IllegalStateException("First-account setup is already complete. This installation currently allows one local account.");
        if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes.");
        String normalized=username.trim().toLowerCase(java.util.Locale.ROOT);
        if(users.findByUsernameIgnoreCase(normalized).isPresent()) throw new IllegalArgumentException("That username is already in use.");
        users.saveAndFlush(new AppUserEntity(normalized,passwords.encode(password)));
    }

    boolean setupOpen() { return users.count()==0; }
}
