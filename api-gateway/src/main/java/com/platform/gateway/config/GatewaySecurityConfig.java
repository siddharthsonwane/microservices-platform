package com.platform.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /*@Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers("/fallback/**").permitAll()
                .pathMatchers("/api/v1/auth/**").permitAll()
                    .pathMatchers("/api/v1/orders/**").permitAll()
                    .pathMatchers("/api/v1/payments/**").permitAll()
                    .pathMatchers("/api/v1/inventory/**").permitAll()
                //.pathMatchers("/api/v1/orders/**").hasAnyRole("USER", "ADMIN")
                //.pathMatchers("/api/v1/payments/**").hasAnyRole("USER", "ADMIN")
                //.pathMatchers("/api/v1/inventory/**").hasAnyRole("ADMIN", "WAREHOUSE")
                //.anyRequest().authenticated()
                 .anyExchange().authenticated()   // ✅ FIXED
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})
            );
        return http.build();
    }*/

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()
                )
                .build();
    }
}
