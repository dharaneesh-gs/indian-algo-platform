package in.trading.platform;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

@Configuration
@EnableWebSecurity
class SecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurity(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer=OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
            .with(authorizationServer, configurer -> configurer
                .oidc(Customizer.withDefaults()))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/health").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/", "/index.html", "/favicon.ico", "/auth/status", "/auth/register", "/error").permitAll()
                .anyRequest().permitAll())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/auth/register", "/auth/logout"))
            .formLogin(Customizer.withDefaults())
            .logout(logout -> logout.logoutUrl("/auth/logout").logoutSuccessUrl("/"));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    RegisteredClientRepository registeredClientRepository(@Value("${security.oauth.web-redirect-uris:http://127.0.0.1:8080/,http://localhost:8080/}") String redirectUris) {
        RegisteredClient webClient=RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("indian-algo-web")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("indianalgo://oauth/callback/android")
            .redirectUri("indianalgo://oauth/callback/ios")
            .scope(OidcScopes.OPENID)
            .redirectUris(uris -> java.util.Arrays.stream(redirectUris.split(",")).map(String::trim).filter(value -> !value.isBlank()).forEach(uris::add))
            .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(10)).build())
            .build();
        return new InMemoryRegisteredClientRepository(webClient);
    }

    @Bean
    JWKSource<SecurityContext> jwkSource() {
        try {
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair=generator.generateKeyPair();
            RSAKey key=new RSAKey.Builder((RSAPublicKey)pair.getPublic())
                .privateKey((RSAPrivateKey)pair.getPrivate()).keyID(UUID.randomUUID().toString()).build();
            return new ImmutableJWKSet<>(new JWKSet(key));
        } catch(Exception ex) { throw new IllegalStateException("Could not initialize OAuth signing key.",ex); }
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings() { return AuthorizationServerSettings.builder().build(); }
}
