package com.moim.moimbackend.gathering.controller;

import com.moim.moimbackend.gathering.dto.CreateGatheringRequest;
import com.moim.moimbackend.gathering.dto.CreateGatheringResponse;
import com.moim.moimbackend.gathering.dto.GatheringDetailResponse;
import com.moim.moimbackend.gathering.service.GatheringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 모임 REST API 컨트롤러.
 * <p>
 * 담당 엔드포인트:
 * - POST /api/v1/gatherings        → 모임 생성 (①)
 * - GET  /api/v1/gatherings/{code}  → 모임 조회 (②)
 *
 * @RestController: JSON 응답 자동 직렬화
 * @RequestMapping: 모든 엔드포인트의 공통 prefix
 */
@RestController
@RequestMapping("/api/v1/gatherings")
@RequiredArgsConstructor
public class GatheringController {

    private final GatheringService gatheringService;

    /**
     * ① 모임 생성.
     *
     * @return 201 Created + { shareCode, adminToken, shareUrl, deadline }
     * @Valid: request body의 @NotBlank, @Size 등 자동 검증
     * 실패 시 GlobalExceptionHandler의 handleValidation()으로 이동
     */
    @PostMapping
    public ResponseEntity<CreateGatheringResponse> createGathering(
            @Valid @RequestBody CreateGatheringRequest request) {
        CreateGatheringResponse response = gatheringService.createGathering(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)  // 201: 리소스 생성 성공
                .body(response);
    }


    /**
     * ② 모임 상세 조회 (공개).
     * <p>
     * 인증 불필요 — 링크를 가진 누구나 조회 가능.
     *
     * @return 200 OK + 모임 정보 (후보 목록 + 득표수 포함)
     * @PathVariable: URL의 {shareCode} 부분을 파라미터로 매핑
     */
    @GetMapping("/{shareCode}")
    public ResponseEntity<GatheringDetailResponse> getGathering(
            @PathVariable String shareCode) {

        GatheringDetailResponse response = gatheringService.getGathering(shareCode);

        return ResponseEntity.ok(response);
    }
}