package com.az.studyforge.auth.config;

import com.az.studyforge.auth.security.RestAccessDeniedHandler;
import com.az.studyforge.auth.security.RestAuthenticationEntryPoint;
import com.az.studyforge.common.ratelimit.RateLimitFilter;
import com.az.studyforge.common.ratelimit.RateLimitProperties;
import com.az.studyforge.common.ratelimit.RateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper,
                                            ObjectProvider<RateLimiter> rateLimiter,
                                            ObjectProvider<RateLimitProperties> rateLimitProperties) throws Exception {
        var entryPoint = new RestAuthenticationEntryPoint(jsonMapper);
        var deniedHandler = new RestAccessDeniedHandler(jsonMapper);

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler));

        RateLimiter limiter = rateLimiter.getIfAvailable();
        RateLimitProperties limits = rateLimitProperties.getIfAvailable();
        if (limiter != null && limits != null && limits.enabled()) {
            http.addFilterAfter(new RateLimitFilter(limiter, limits.rules(), jsonMapper),
                    BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }

    private JwtAuthenticationConverter jwtConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return converter;
    }
}
