package com.moim.moimbackend.common.exception;

/**
 * 비즈니스 로직 예외.
 *
 * Service 레이어에서 throw하면 GlobalExceptionHandler가 잡아서
 * ErrorCode에 정의된 HTTP 상태 + 메시지로 응답한다.
 *
 * 사용 예:
 *   throw new BusinessException(ErrorCode.GATHERING_NOT_FOUND);
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 커스텀 메시지를 추가로 전달할 때 사용.
     * 예: throw new BusinessException(ErrorCode.INVALID_INPUT, "제목은 100자 이내여야 합니다.");
     */
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
