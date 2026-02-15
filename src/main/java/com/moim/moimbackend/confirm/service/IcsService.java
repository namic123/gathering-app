package com.moim.moimbackend.confirm.service;

import com.moim.moimbackend.confirm.entity.ConfirmedResult;
import com.moim.moimbackend.gathering.entity.TimeCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * iCalendar(.ics) 파일 생성 서비스.
 *
 * .ics란?
 * - RFC 5545 표준 캘린더 파일 포맷
 * - 거의 모든 캘린더 앱(구글, 아이폰, 아웃룩)에서 지원
 * - 사용자가 .ics 파일을 열면 일정이 자동으로 캘린더에 추가됨
 *
 * 생성 흐름:
 * 1. 확정 결과에서 날짜/시간/장소 추출
 * 2. iCalendar 포맷 문자열 조립
 * 3. 컨트롤러에서 Content-Type: text/calendar 로 응답
 *
 * 참고: 실제 파일을 디스크에 저장하지 않고 문자열을 바로 반환.
 * 메모리만 사용하므로 디스크 관리 부담 없음.
 */
@Slf4j
@Service
public class IcsService {

    /**
     * iCalendar 포맷에서 사용하는 날짜 형식.
     * 예: 20260307T180000 (2026년 3월 7일 18시)
     * 'T'는 날짜와 시간의 구분자.
     */
    private static final DateTimeFormatter ICS_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    /**
     * 확정 결과를 기반으로 .ics 문자열 생성.
     *
     * @param result  확정 결과 엔티티
     * @param title   모임 제목 (일정 제목으로 사용)
     * @return iCalendar 포맷 문자열
     */
    public String generateIcs(ConfirmedResult result, String title) {
        log.info("[ICS] 캘린더 파일 생성 - gatheringId={}", result.getGathering().getId());

        StringBuilder sb = new StringBuilder();

        // === iCalendar 헤더 ===
        // VCALENDAR: 캘린더 파일의 루트 컴포넌트
        // VERSION:2.0: iCalendar 스펙 버전
        // PRODID: 이 파일을 생성한 애플리케이션 식별자
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Moim//Moim App//KO\r\n");

        // === 이벤트 시작 ===
        sb.append("BEGIN:VEVENT\r\n");

        // UID: 이벤트의 전역 고유 식별자.
        // 같은 UID의 이벤트를 다시 열면 "업데이트"로 처리됨 (중복 생성 방지).
        sb.append("UID:").append(UUID.randomUUID()).append("@moim.app\r\n");

        // DTSTAMP: 이 .ics 파일이 생성된 시각 (필수 필드)
        sb.append("DTSTAMP:").append(LocalDateTime.now().format(ICS_DATE_FORMAT)).append("\r\n");

        // SUMMARY: 일정 제목 (캘린더 앱에서 굵은 글씨로 표시됨)
        sb.append("SUMMARY:").append(escapeIcsText(title)).append("\r\n");

        // --- 시간 정보 ---
        if (result.getTimeCandidate() != null) {
            TimeCandidate tc = result.getTimeCandidate();
            LocalDate date = tc.getCandidateDate();
            LocalTime startTime = tc.getStartTime();

            // DTSTART: 일정 시작 시각
            LocalDateTime start = LocalDateTime.of(date, startTime);
            sb.append("DTSTART:").append(start.format(ICS_DATE_FORMAT)).append("\r\n");

            // DTEND: 일정 종료 시각
            if (tc.getEndTime() != null) {
                // 종료 시간이 있으면 그대로 사용
                LocalDateTime end = LocalDateTime.of(date, tc.getEndTime());
                sb.append("DTEND:").append(end.format(ICS_DATE_FORMAT)).append("\r\n");
            } else {
                // 종료 시간 미지정 시 기본 2시간으로 설정
                // 캘린더 앱에서 시작 시간만 있으면 표시가 어색할 수 있음
                LocalDateTime end = start.plusHours(2);
                sb.append("DTEND:").append(end.format(ICS_DATE_FORMAT)).append("\r\n");
            }
        }

        // --- 장소 정보 ---
        if (result.getPlaceCandidate() != null) {
            // LOCATION: 캘린더 앱의 "위치" 필드에 표시됨
            // 구글캘린더에서는 이 값으로 구글맵 연동도 됨
            sb.append("LOCATION:").append(escapeIcsText(result.getPlaceCandidate().getName())).append("\r\n");

            // DESCRIPTION: 상세 설명 (장소 지도 링크 포함)
            // 캘린더 앱에서 "메모" 영역에 표시됨
            if (result.getPlaceCandidate().getMapLink() != null) {
                sb.append("DESCRIPTION:지도: ")
                        .append(result.getPlaceCandidate().getMapLink())
                        .append("\r\n");
            }
        }

        // === 이벤트/캘린더 종료 ===
        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");

        return sb.toString();
    }

    /**
     * iCalendar 텍스트 필드 이스케이프.
     *
     * RFC 5545 규격에 따라 특수문자를 이스케이프해야 함:
     * - 쉼표(,) → \,
     * - 세미콜론(;) → \;
     * - 역슬래시(\) → \\
     * - 줄바꿈 → \n (iCalendar에서는 \n을 직접 사용)
     *
     * 이스케이프하지 않으면 캘린더 앱에서 파싱 에러 발생 가능.
     */
    private String escapeIcsText(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")   // 역슬래시 먼저 (순서 중요!)
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n");
    }
}