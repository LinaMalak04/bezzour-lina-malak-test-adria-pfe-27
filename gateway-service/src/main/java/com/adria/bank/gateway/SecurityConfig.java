package com.adria.bank.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    private static final String ADVISOR = "ROLE_ADVISOR";

    @Value("${bank.security.enabled:false}")
    private boolean securityEnabled;

    @Value("${bank.security.jwk-uri:http://localhost:8180/realms/bank/protocol/openid-connect/certs}")
    private String jwkUri;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        if (!securityEnabled) {
            return http.authorizeExchange(exchange -> exchange.anyExchange().permitAll()).build();
        }
        return http
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/**").authenticated()
                        // Writing operations and account administration : advisors only
                        .pathMatchers("/api/accounts/**", "/api/transactions/**", "/accounts/graphql", "/transactions/graphql").hasAuthority(ADVISOR)
                        .anyExchange().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt
                        .jwtDecoder(jwtDecoder())
                        .jwtAuthenticationConverter(this::toAuthentication)))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkUri).build();
    }

    // Keycloak puts the roles in realm_access.roles
    private Mono<AbstractAuthenticationToken> toAuthentication(Jwt jwt) {
        Converter<Jwt, AbstractAuthenticationToken> converter = token -> {
            Map<String, Object> realmAccess = token.getClaimAsMap("realm_access");
            Object roles = realmAccess == null ? List.of() : realmAccess.getOrDefault("roles", List.of());
            List<SimpleGrantedAuthority> authorities = ((List<?>) roles).stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            return new JwtAuthenticationToken(token, authorities);
        };
        return Mono.just(converter.convert(jwt));
    }
}
