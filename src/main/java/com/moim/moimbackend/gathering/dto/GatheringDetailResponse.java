package com.moim.moimbackend.gathering.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/*
* 왜 필요한가? 모임 상세 조회 응답. 후보 목록 + 득표수까지 포함. 참여자/주최자 모두 이 API로 모임 정보를 확인.
* */

/**
 * 모임 상세 조회 API 응답 DTO.
 *
 * GET /api/v1/gatherings/{shareCode} → 200 OK
 *
 * 참여자가 링크 접속 시, 주최자가 대시보드 확인 시 모두 이 응답을 사용.
 * voteCount는 DB 집계 쿼리로 채워진다 (D3 투표 도메인에서 구현).
 */
@Getter
@Builder
@AllArgsConstructor
public class GatheringDetailResponse {

    private String title;
    private String hostName;
    private String description;
    private String type;
    private String status;
    private String deadline;

    private List<TimeCandidateItem> timeCandidates;
    private List<PlaceCandidateItem> placeCandidates;

    private int participantCount;

    // === 내부 응답 아이템 ===

    @Getter
    @Builder
    @AllArgsConstructor
    public static class TimeCandidateItem {
        private Long id;
        private String date;
        private String startTime;
        private String endTime;
        private long voteCount;   // D3에서 투표 집계 후 채워질 필드 (당장은 0)
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class PlaceCandidateItem {
        private Long id;
        private String name;
        private String mapLink;
        private String memo;
        private Integer estCost;
        private Integer travelMin;
        private List<String> moodTags;
        private long voteCount;   // D3에서 채워질 필드
    }
}