package com.moim.moimbackend.vote.controller;

import com.moim.moimbackend.vote.dto.*;
import com.moim.moimbackend.vote.service.VoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/gatherings/{shareCode}")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;

    /**
     * 참여 등록 + 투표.
     * 카카오 링크 접속 → 닉네임 입력 → 후보 선택 → 제출.
     *
     * @return 201 Created + sessionToken (1회 반환)
     */
    @PostMapping("/participate")
    public ResponseEntity<ParticipateResponse> participate(
            @PathVariable String shareCode,
            @Valid @RequestBody ParticipateRequest request) {
        log.info("[API] POST /participate - shareCode={}, name={}", shareCode, request.getName());

        ParticipateResponse response = voteService.participate(shareCode, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 투표 변경.
     * X-Session-Token 헤더로 본인 확인.
     */
    @PutMapping("/votes")
    public ResponseEntity<Void> updateVotes(
            @PathVariable String shareCode,
            @RequestHeader("X-Session-Token") String sessionToken,
            @RequestBody UpdateVotesRequest request) {
        log.info("[API] PUT /votes - shareCode={}", shareCode);
        voteService.updateVotes(shareCode, sessionToken, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 투표 현황 조회.
     * 인증 불필요. 5초 폴링으로 호출됨.
     */
    @GetMapping("/votes")
    public ResponseEntity<VoteSummaryResponse> getVoteSummary(
            @PathVariable String shareCode) {

        VoteSummaryResponse response = voteService.getVoteSummary(shareCode);
        return ResponseEntity.ok(response);
    }

}
