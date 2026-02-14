package com.moim.moimbackend.gathering.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * 모임 생성 API 요청 DTO.
 *
 * POST /api/v1/gatherings 의 Request Body.
 * @Valid 검증 실패 시 GlobalExceptionHandler에서 400 응답.
 */
@Getter
@Setter
public class CreateGatheringRequest {

    @NotBlank(message = "모임 제목은 필수입니다.")
    @Size(max = 100, message = "제목은 100자 이내로 입력해주세요.")
    private String title;

    @NotBlank(message = "주최자 이름은 필수입니다.")
    @Size(max = 30, message = "이름은 30자 이내로 입력해주세요.")
    private String hostName;

    @Size(max = 500, message = "설명은 500자 이내로 입력해주세요.")
    private String description;

    /** "TIME_ONLY", "PLACE_ONLY", "BOTH" 문자열로 전달 */
    @NotBlank(message = "모임 타입은 필수입니다.")
    private String type;

    /** ISO 8601 형식 (예: "2025-02-14T23:00:00+09:00") */
    @NotNull(message = "마감 시간은 필수입니다.")
    private Instant deadline;

    /**
     * 시간 후보 목록.
     * @Valid: 내부 객체의 검증도 수행 (중첩 검증)
     */
    @Valid
    private List<TimeCandidateItem> timeCandidates;

    /** 장소 후보 목록. */
    @Valid
    private List<PlaceCandidateItem> placeCandidates;

    // === 내부 static 클래스: 후보 아이템 ===

    /**
     * 시간 후보 1건.
     * static 내부 클래스로 정의하면 별도 파일 없이 관련 DTO를 묶을 수 있음.
     */
    @Getter
    @Setter
    public static class TimeCandidateItem {

        @NotBlank(message = "날짜는 필수입니다.")
        private String date;       // "2025-02-21" (ISO 날짜)

        @NotBlank(message = "시작 시간은 필수입니다.")
        private String startTime;  // "18:00"

        private String endTime;    // "20:00" (선택)
    }

    @Getter
    @Setter
    public static class PlaceCandidateItem {

        @NotBlank(message = "장소명은 필수입니다.")
        @Size(max = 100, message = "장소명은 100자 이내로 입력해주세요.")
        private String name;

        @Size(max = 500, message = "지도 링크는 500자 이내로 입력해주세요.")
        private String mapLink;

        @Size(max = 200, message = "메모는 200자 이내로 입력해주세요.")
        private String memo;

        private Integer estCost;     // 예상 비용 (선택)
        private Integer travelMin;   // 이동시간 (선택)
        private String moodTags;     // 분위기 태그 (선택)
    }
}