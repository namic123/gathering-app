package com.moim.moimbackend.confirm.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 동점 해소 요청 DTO.
 * POST /api/v1/gatherings/{shareCode}/tiebreak
 *
 * 자동 확정 시 동점이 발생하면 상태가 TIEBREAK로 전환된다.
 * 주최자가 동점 후보 중 하나를 선택하여 확정.
 * X-Admin-Token 헤더 필수.
 *
 * ConfirmRequest와 구조는 동일하지만, 의미적으로 분리:
 * - ConfirmRequest: 주최자가 마감 전에 미리 확정
 * - TiebreakRequest: 동점 발생 후 주최자가 선택
 */
@Getter
@Setter
public class TiebreakRequest {

    /** 동점 후보 중 선택한 시간 후보 ID */
    private Long timeCandidateId;

    /** 동점 후보 중 선택한 장소 후보 ID */
    private Long placeCandidateId;
}