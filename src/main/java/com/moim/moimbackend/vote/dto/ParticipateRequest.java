package com.moim.moimbackend.vote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 참여 + 투표 요청 DTO.
 * POST /api/v1/gatherings/{shareCode}/participate
 *
 * 닉네임 등록과 투표를 한 번에 처리 (3초 투표 UX).
 */
@Getter
@Setter
public class ParticipateRequest {

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 30, message = "닉네임은 30자 이내로 입력해주세요.")
    private String name;

    /** 선택한 시간 후보 ID 목록 (복수 선택 가능) */
    private List<Long> timeCandidateIds;

    /** 선택한 장소 후보 ID 목록 (복수 선택 가능) */
    private List<Long> placeCandidateIds;
}