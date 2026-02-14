package com.moim.moimbackend.gathering.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 모임 생성 API 응답 DTO.
 *
 * POST /api/v1/gatherings → 201 Created
 *
 * adminToken은 이 응답에서만 1회 반환.
 * 프론트에서 localStorage에 저장해야 함.
 * 분실 시 복구 불가 (해시만 DB에 저장되므로).
 */
@Getter
@Builder
@AllArgsConstructor
public class CreateGatheringResponse {

    /** 초대 링크용 코드 (예: "aB3kX7") */
    private String shareCode;

    /** 주최자 관리 토큰 (원본, 1회만 반환!) */
    private String adminToken;

    /** 공유 URL (프론트에서 조립해도 되지만, 편의상 서버에서 제공) */
    private String shareUrl;

    /** 마감 시각 */
    private String deadline;
}