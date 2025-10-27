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
                        .requestMatchers("/api/auth/oauth/kakao/login",
                                "/api/auth/oauth/kakao/signup",
                                "/api/auth/test",
                                "/api/auth/issue-id-token",
                                "/api/auth/reissue", "/swagger-ui.html","/swagger-ui/**", "/api/speech/test/**",
                                "/v3/api-docs/**", // OpenAPI 3 문서 JSON
                                "/swagger-resources/**", // Swagger 리소스
                                "/webjars/**" // Swagger UI 정적 리소스
                                , "/actuator/**"
                        ).permitAll()
                        .anyRequest().authenticated());


        http.
                sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

}
