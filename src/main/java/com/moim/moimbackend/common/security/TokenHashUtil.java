package com.moim.moimbackend.common.security;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 토큰 생성 및 SHA-256 해시 유틸리티.
 * <p>
 * 흐름:
 * 1. 모임 생성 시: generateToken()으로 UUID 기반 토큰 생성
 * 2. DB에는 hash(token)만 저장 (원본 노출 방지)
 * 3. 클라이언트에게 원본 토큰 1회 반환 → localStorage에 보관
 * 4. 이후 요청 시: 클라이언트가 보낸 토큰을 hash()해서 DB 해시와 비교
 * <p>
 * SHA-256을 쓰는 이유:
 * - bcrypt는 의도적으로 느려서(비밀번호용), 매 API 요청마다 쓰기엔 부담
 * - 토큰은 UUID 기반이라 충분히 랜덤 → SHA-256으로도 안전
 */
@Slf4j
public class TokenHashUtil {

    /**
     * 새 토큰 생성 (UUID v4 기반).
     * 하이픈 포함 36자리 문자열.
     */
    public static String generateToken() {
        String token = UUID.randomUUID().toString();
        return token;    }

    /**
     * 토큰을 SHA-256으로 해시.
     * 결과: 64자리 hex 문자열 (예: "a1b2c3d4...")
     */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM에서 지원하므로 사실상 발생하지 않음
            throw new RuntimeException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    /**
     * 원본 토큰과 해시값 비교.
     *
     * @param rawToken  클라이언트에서 받은 원본 토큰
     * @param hashedToken DB에 저장된 SHA-256 해시된 토큰
     * @return 비교 결과: true(일치), false(불일치)
     */
    public static boolean matches(String rawToken, String hashedToken) {
        if (rawToken == null || hashedToken == null) {
            return false; // 하나라도 null이면 false 반환
        }
        // 원본 토큰을 해시화한 값과 저장된 해시 값을 비교
        String hashedRawToken = hash(rawToken); // hash는 기존에 구현된 메서드
        return hashedRawToken.equals(hashedToken);
    }
}
