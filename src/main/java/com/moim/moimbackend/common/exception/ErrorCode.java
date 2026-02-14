package com.moim.moimbackend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 전역 에러 코드 정의.
 * 프론트엔드와 약속된 코드로, API 응답의 "code" 필드에 그대로 내려간다.
 * 예: { "code": "GATHERING_NOT_FOUND", "message": "모임을 찾을 수 없습니다." }
 */
public enum ErrorCode {

    // === 400 Bad Request ===
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),

    // === 401 Unauthorized ===
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "권한이 없습니다."),

    // === 404 Not Found ===
    GATHERING_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."),
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "참여자를 찾을 수 없습니다."),

    // === 409 Conflict ===
    DUPLICATE_NAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

    // === 410 Gone ===
    VOTING_CLOSED(HttpStatus.GONE, "마감된 투표입니다."),

    // === 422 Unprocessable Entity ===
    DEADLINE_PAST(HttpStatus.UNPROCESSABLE_ENTITY, "마감 시간이 과거입니다."),

    // === 429 Too Many Requests ===
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}