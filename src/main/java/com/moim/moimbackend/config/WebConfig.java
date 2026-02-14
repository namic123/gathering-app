package com.moim.moimbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS(Cross-Origin Resource Sharing) 설정.
 *
 * 브라우저는 다른 도메인(origin)으로의 HTTP 요청을 기본 차단함.
 * FE(localhost:3000)에서 BE(localhost:8080)로 직접 요청하려면 허용이 필요.
 *
 * 개발: localhost:3000 허용
 * 배포 시: 실제 도메인으로 변경 (예: https://moim.app)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")                    // /api로 시작하는 모든 경로
                .allowedOrigins("http://localhost:3000")   // Vue 개발 서버
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")                       // 모든 헤더 허용 (X-Admin-Token 등)
                .allowCredentials(false)                    // 쿠키 미사용
                .maxAge(3600);                             // preflight 캐시 1시간
    }
}