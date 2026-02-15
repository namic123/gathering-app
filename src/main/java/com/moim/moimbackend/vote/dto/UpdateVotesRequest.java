package com.moim.moimbackend.vote.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 투표 변경 요청 DTO.
 * PUT /api/v1/gatherings/{shareCode}/votes
 * X-Session-Token 헤더 필수.
 *
 * 기존 투표를 전체 교체하는 방식 (부분 수정 X).
 */
@Getter
@Setter
public class UpdateVotesRequest {

    /** 새로 선택한 시간 후보 ID 목록 */
    private List<Long> timeCandidateIds;

    /** 새로 선택한 장소 후보 ID 목록 */
    private List<Long> placeCandidateIds;
}