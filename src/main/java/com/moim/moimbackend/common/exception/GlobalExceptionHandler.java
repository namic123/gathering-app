package com.moim.moimbackend.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리기.
 *
 * @RestControllerAdvice: 모든 @RestController에서 발생하는 예외를 가로챈다.
 * 일관된 에러 응답 형식을 보장:
 * {
 *   "code": "GATHERING_NOT_FOUND",
 *   "message": "모임을 찾을 수 없습니다.",
 *   "timestamp": "2025-02-15T00:00:00Z"
 * }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * BusinessException 처리.
     * Service에서 throw new BusinessException(ErrorCode.XXX)을 던지면 여기서 잡힌다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        log.warn("[BusinessException] code={}, message={}", e.getErrorCode().name(), e.getMessage());

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("code", e.getErrorCode().name()); // enum 이름 그대로 (예: "GATHERING_NOT_FOUND")
        responseBody.put("message", e.getMessage());
        responseBody.put("timestamp", Instant.now().toString());
        return ResponseEntity
                .status(e.getErrorCode().getStatus())     // ErrorCode에 정의된 HTTP 상태 코드
                .body(responseBody);
    }
    /**
     * @Valid 검증 실패 처리.
     * DTO의 @NotBlank, @Size 등 어노테이션 검증이 실패하면 여기서 잡힌다.
     * 필드별 에러 메시지를 상세하게 반환한다.
     *
     * 응답 예:
     * {
     *   "code": "INVALID_INPUT",
     *   "message": "입력값이 올바르지 않습니다.",
     *   "errors": { "title": "제목은 필수입니다.", "hostName": "1~30자 이내로 입력해주세요." }
     * }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidion(MethodArgumentNotValidException e) {
        // 필드별 에러 메시지 수집
        Map<String, String> fieldErrors = new HashMap<>();
        for(FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("[ValidationException] errors={}", fieldErrors);

        Map<String, Object> body = new HashMap<>();
        body.put("code", ErrorCode.INVALID_INPUT.name());
        body.put("message", ErrorCode.INVALID_INPUT.getMessage());
        body.put("errors",fieldErrors);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(body);
    }
    /**
     * 예상치 못한 예외 처리.
     * 위에서 잡히지 않는 모든 예외가 여기로 온다.
     * 사용자에게는 내부 정보를 노출하지 않고, 서버 로그에만 상세 기록.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("[UnexpectedException] type={}, message={}", e.getClass().getSimpleName(), e.getMessage(), e);

        Map<String, Object> body = new HashMap<>();
        body.put("code", "INTERNAL_ERROR");
        body.put("message", "서버 내부 오류가 발생했습니다.");
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity
                .internalServerError()
                .body(body);
    }
}
