package com.moim.moimbackend.vote.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 참여 응답 DTO.
 * sessionToken은 이 시점에만 1회 반환. 투표 변경 시 필요.
 */
@Getter
@Builder
@AllArgsConstructor
public class ParticipateResponse {

    private Long participantId;
    private String name;

    /** 세션 토큰 (원본, 1회만 반환!) */
    private String sessionToken;
}