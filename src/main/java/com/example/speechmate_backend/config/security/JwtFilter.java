package com.example.speechmate_backend.config.security;

import com.example.speechmate_backend.common.exception.ExpiredTokenException;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;

@Slf4j // 클래스 레벨에 Slf4j 어노테이션 추가
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.info("JwtFilter가 URI: {} 요청을 처리 중입니다.", request.getRequestURI());

        String authorizationHeader = request.getHeader("Authorization");

        String uri = request.getRequestURI();
        // /reissue 요청은 access token 검사 스킵
        if ("/reissue".equals(uri)) {
            log.debug("/reissue URI가 감지되어 JWT 필터 체인 검사를 건너뜜니다.");
            filterChain.doFilter(request, response);
            return;
        }

        if(authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")){
            log.warn("URI: {} 에 Bearer 토큰이 없거나 헤더 형식이 유효하지 않습니다.", uri);
            filterChain.doFilter(request, response);
            return;
        }

        String accessToken = authorizationHeader.substring(7);
        log.debug("추출된 Access Token (앞 10자): {}", accessToken.substring(0, Math.min(accessToken.length(), 10)));


        try {
            jwtUtil.isExpired(accessToken);
            log.debug("Access Token이 유효합니다.");
        } catch (ExpiredJwtException e){
            log.error("만료된 JwtException이 JwtFilter에서 잡혔습니다: {}", e.getMessage(), e); // 예외 메시지와 스택 트레이스 로깅

            // **** 이 부분이 핵심입니다. 직접 응답을 내려야 합니다. ****
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized
            response.setContentType("application/json;charset=UTF-8"); // JSON 응답을 위한 Content-Type 설정

            // 응답 본문 구성 (프론트엔드와 협의된 형태로)
            PrintWriter writer = response.getWriter();
            String errorResponseJson = "{\"code\":401,\"message\":\"access token expired\"}"; // 예시 JSON
            writer.print(errorResponseJson);
            writer.flush(); // **** 중요: 버퍼 비우기 (응답을 즉시 보내도록) ****

            log.info("만료된 토큰으로 인해 401 Unauthorized 응답을 직접 보냈습니다. URI: {}, 응답 본문: {}", uri, errorResponseJson);
            return; // **** 필터 체인 진행 중단 ****
        } catch (Exception e) { // JWT 관련 다른 예외도 잡아서 처리할 수 있습니다.
            log.error("JWT 토큰 처리 중 예상치 못한 오류가 발생했습니다: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json;charset=UTF-8");
            PrintWriter writer = response.getWriter();
            String errorResponseJson = "{\"code\":500,\"message\":\"Internal server error during token validation\"}";
            writer.print(errorResponseJson);
            writer.flush();
            log.error("예상치 못한 JWT 처리 오류로 인해 500 Internal Server Error 응답을 직접 보냈습니다. URI: {}, 응답 본문: {}", uri, errorResponseJson);
            return;
        }

        String category = jwtUtil.getCategory(accessToken);
        if (!category.equals("access")){
            log.warn("유효하지 않은 토큰 카테고리: {} (URI: {})", category, uri);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            PrintWriter writer = response.getWriter();
            String errorResponseJson = "{\"code\":401,\"message\":\"invalid access token category\"}";
            writer.print(errorResponseJson);
            writer.flush();
            log.info("유효하지 않은 토큰 카테고리로 인해 401 Unauthorized 응답을 직접 보냈습니다. URI: {}, 응답 본문: {}", uri, errorResponseJson);
            return;
        }
        Long userId = jwtUtil.getUserId(accessToken);
        log.debug("토큰 유효, userId: {}", userId);

        CustomUserDetails customUserDetails = new CustomUserDetails(userId);

        Authentication authToken = new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);
        log.info("사용자 {} 인증 성공 (URI: {})", userId, uri);
        filterChain.doFilter(request, response);
    }
}
