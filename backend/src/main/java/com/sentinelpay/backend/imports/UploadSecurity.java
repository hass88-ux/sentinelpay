package com.sentinelpay.backend.imports;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@Profile("uploads")
@EnableWebSecurity
public class UploadSecurity {
    @Bean JwtDecoder uploadJwtDecoder(@Value("${uploads.supabase-url}") String projectUrl) {
        String issuer = projectUrl + "/auth/v1";
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.ES256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), UploadSecurity::validateAccount));
        return decoder;
    }

    static OAuth2TokenValidatorResult validateAccount(Jwt jwt) {
        try {
            UUID id = UUID.fromString(jwt.getSubject());
            if (!id.toString().equals(jwt.getSubject()) || jwt.getExpiresAt() == null
                    || !jwt.getAudience().contains("authenticated")
                    || !"authenticated".equals(jwt.getClaimAsString("role"))
                    || Boolean.TRUE.equals(jwt.getClaimAsBoolean("is_anonymous"))) throw new IllegalArgumentException();
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException ex) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "A signed-in account is required", null));
        }
    }

    @Bean SecurityFilterChain uploadSecurityFilterChain(HttpSecurity http, @Value("${uploads.allowed-origin}") String origin) throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/api/uploads/**", cors);
        return http.cors(c -> c.configurationSource(source))
                // This API accepts explicit bearer tokens only, never browser cookies.
                .csrf(c -> c.disable()).sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c -> c.requestMatchers("/actuator/health", "/api/account-config").permitAll()
                    .requestMatchers("/api/uploads", "/api/uploads/**").authenticated().anyRequest().denyAll())
                .oauth2ResourceServer(c -> c.jwt(j -> {})).build();
    }
}
