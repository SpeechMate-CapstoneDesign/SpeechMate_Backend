package com.example.speechmate_backend.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    public SecurityConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.
                csrf(AbstractHttpConfigurer::disable);
        http.
                formLogin(AbstractHttpConfigurer::disable);
        http.
                httpBasic(AbstractHttpConfigurer::disable);

        http.addFilterBefore(new JwtFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);

        http.
                authorizeHttpRequests((auth) -> auth
                        .requestMatchers("/api/auth/**", "/swagger-ui.html","/swagger-ui/**",
                                "/v3/api-docs/**", // OpenAPI 3 문서 JSON
                                "/swagger-resources/**", // Swagger 리소스
                                "/webjars/**" // Swagger UI 정적 리소스
                        ).permitAll()
                        .anyRequest().authenticated());


        http.
                sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

}
