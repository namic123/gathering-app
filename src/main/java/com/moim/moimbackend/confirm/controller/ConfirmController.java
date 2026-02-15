package com.moim.moimbackend.confirm.controller;

import com.moim.moimbackend.confirm.dto.ConfirmRequest;
import com.moim.moimbackend.confirm.dto.ConfirmedResultResponse;
import com.moim.moimbackend.confirm.dto.TiebreakRequest;
import com.moim.moimbackend.confirm.service.ConfirmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 확정 REST API 컨트롤러.
 *
 * 담당 엔드포인트:
 * - POST /{shareCode}/confirm   → 주최자 수동 확정
 * - POST /{shareCode}/tiebreak  → 동점 해소
 * - GET  /{shareCode}/result    → 확정 결과 조회
 * - GET  /{shareCode}/result/ics → .ics 캘린더 파일 다운로드
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/gatherings/{shareCode}")
@RequiredArgsConstructor
public class ConfirmController {

    private final ConfirmService confirmService;

    /**
     * 주최자 수동 확정.
     *
     * VOTING 상태에서만 가능.
     * X-Admin-Token 헤더로 주최자 인증.
     *
     * 사용 시나리오:
     * "투표 결과 보니 이미 결론 난 것 같으니 마감 전에 확정할게요"
     */
    @PostMapping("/confirm")
    public ResponseEntity<Void> manualConfirm(
            @PathVariable String shareCode,
            @RequestHeader("X-Admin-Token") String adminToken,
            @RequestBody ConfirmRequest request) {

        log.info("[API] POST /confirm - shareCode={}", shareCode);
        confirmService.manualConfirm(shareCode, adminToken, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 동점 해소.
     *
     * TIEBREAK 상태에서만 가능.
     * 주최자가 동점 후보 중 하나를 선택.
     *
     * 사용 시나리오:
     * "시간 후보 2개가 동점이네요. 금요일로 할게요!"
     */
    @PostMapping("/tiebreak")
    public ResponseEntity<Void> resolveTiebreak(
            @PathVariable String shareCode,
            @RequestHeader("X-Admin-Token") String adminToken,
            @RequestBody TiebreakRequest request) {

        log.info("[API] POST /tiebreak - shareCode={}", shareCode);
        confirmService.resolveTiebreak(shareCode, adminToken, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 확정 결과 조회.
     *
     * 인증 불필요 — 누구나 결과를 볼 수 있음.
     * 결과 카드 화면에서 사용.
     */
    @GetMapping("/result")
    public ResponseEntity<ConfirmedResultResponse> getResult(
            @PathVariable String shareCode) {

        ConfirmedResultResponse response = confirmService.getResult(shareCode);
        return ResponseEntity.ok(response);
    }

    /**
     * .ics 캘린더 파일 다운로드.
     *
     * 응답 헤더 설명:
     * - Content-Type: text/calendar → 브라우저가 캘린더 파일로 인식
     * - Content-Disposition: attachment → 페이지 이동 대신 파일 다운로드
     * - filename: moim-{shareCode}.ics → 저장될 파일명
     *
     * 사용 시나리오:
     * 결과 카드에서 "캘린더에 추가" 버튼 클릭 → 이 URL로 이동
     * → 브라우저가 .ics 파일 다운로드 → 구글캘린더/아이폰캘린더에서 열기
     */
    @GetMapping("/result/ics")
    public ResponseEntity<String> downloadIcs(@PathVariable String shareCode) {

        log.info("[API] GET /result/ics - shareCode={}", shareCode);
        String icsContent = confirmService.generateIcsFile(shareCode);

        return ResponseEntity.ok()
                // text/calendar: iCalendar MIME 타입 (RFC 5545)
                .contentType(MediaType.parseMediaType("text/calendar"))
                // attachment: 브라우저가 파일을 다운로드로 처리
                // inline으로 하면 브라우저에서 텍스트로 표시됨
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=moim-" + shareCode + ".ics")
                .body(icsContent);
    }
}