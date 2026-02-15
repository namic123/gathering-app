package com.moim.moimbackend.vote.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 투표 현황 응답 DTO.
 * GET /api/v1/gatherings/{shareCode}/votes
 * 후보별 득표수 + 누가 투표했는지 포함.
 */
@Getter
@Builder
@AllArgsConstructor
public class VoteSummaryResponse {

    private int participantCount;
    private List<String> participantNames;
    private List<CandidateVote> timeCandidateVotes;
    private List<CandidateVote> placeCandidateVotes;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CandidateVote {
        private Long candidateId;
        private long voteCount;
        /** 이 후보에 투표한 참여자 닉네임 목록 */
        private List<String> voterNames;
    }
}