package com.moim.moimbackend.confirm.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 주최자 수동 확정 요청 DTO.
 * POST /api/v1/gatherings/{shareCode}/confirm
 *
 * 주최자가 투표 결과를 보고 직접 "이걸로 확정!" 할 때 사용.
 * X-Admin-Token 헤더 필수.
 *
 * 모임 타입에 따라 필요한 필드가 다름:
 * - TIME_ONLY  → timeCandidateId만 필수
 * - PLACE_ONLY → placeCandidateId만 필수
 * - BOTH       → 둘 다 필수
 */
@Getter
@Setter
public class ConfirmRequest {

    /** 확정할 시간 후보 ID (TIME_ONLY, BOTH일 때 필수) */
    private Long timeCandidateId;

    /** 확정할 장소 후보 ID (PLACE_ONLY, BOTH일 때 필수) */
    private Long placeCandidateId;
}