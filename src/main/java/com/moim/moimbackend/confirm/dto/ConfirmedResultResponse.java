package com.moim.moimbackend.confirm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 확정 결과 조회 응답 DTO.
 * GET /api/v1/gatherings/{shareCode}/result
 *
 * 결과 카드 화면에 표시할 모든 정보를 담는다:
 * - 모임 제목, 주최자
 * - 확정된 시간/장소 정보
 * - 확정 방식 (자동/주최자)
 * - .ics 다운로드 URL
 */
@Getter
@Builder
@AllArgsConstructor
public class ConfirmedResultResponse {

    // --- 모임 기본 정보 ---
    private String title;
    private String hostName;

    // --- 확정된 시간 정보 ---
    /** null이면 PLACE_ONLY 모임 */
    private String confirmedDate;
    private String confirmedStartTime;
    private String confirmedEndTime;

    // --- 확정된 장소 정보 ---
    /** null이면 TIME_ONLY 모임 */
    private String confirmedPlaceName;
    private String confirmedPlaceMapLink;

    // --- 메타 정보 ---
    /** "AUTO" 또는 "HOST" */
    private String confirmedBy;
    private String confirmedAt;

    /**
     * .ics 캘린더 파일 다운로드 URL.
     * 프론트에서 이 URL을 <a href>로 걸어주면
     * 사용자가 클릭 시 구글캘린더/아이폰캘린더에 일정 추가 가능.
     */
    private String icsDownloadUrl;
}