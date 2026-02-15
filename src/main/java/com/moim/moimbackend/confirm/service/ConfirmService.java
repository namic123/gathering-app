package com.moim.moimbackend.confirm.service;

import com.moim.moimbackend.common.exception.BusinessException;
import com.moim.moimbackend.common.exception.ErrorCode;
import com.moim.moimbackend.common.security.TokenHashUtil;
import com.moim.moimbackend.confirm.dto.ConfirmRequest;
import com.moim.moimbackend.confirm.dto.ConfirmedResultResponse;
import com.moim.moimbackend.confirm.dto.TiebreakRequest;
import com.moim.moimbackend.confirm.entity.ConfirmType;
import com.moim.moimbackend.confirm.entity.ConfirmedResult;
import com.moim.moimbackend.confirm.repository.ConfirmedResultRepository;
import com.moim.moimbackend.gathering.entity.*;
import com.moim.moimbackend.gathering.repository.GatheringRepository;
import com.moim.moimbackend.vote.entity.CandidateType;
import com.moim.moimbackend.vote.repository.ParticipantRepository;
import com.moim.moimbackend.vote.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 모임 확정 비즈니스 로직.
 *
 * 세 가지 확정 경로:
 *
 * 1) 자동 확정 (autoConfirm)
 *    - 스케줄러가 마감 지난 모임을 감지 → 득표수 1위 후보를 자동 확정
 *    - 동점 발생 시 → 상태를 TIEBREAK로 전환
 *    - 참여자 0명 → 상태를 EXPIRED로 전환
 *
 * 2) 주최자 수동 확정 (manualConfirm)
 *    - 마감 전이라도 주최자가 원하면 바로 확정 가능
 *    - X-Admin-Token 헤더로 인증
 *
 * 3) 동점 해소 (resolveTiebreak)
 *    - TIEBREAK 상태에서 주최자가 동점 후보 중 하나를 선택
 *    - 24시간 내 선택하지 않으면 스케줄러가 선등록(displayOrder) 후보로 자동 확정
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfirmService {

    private final GatheringRepository gatheringRepository;
    private final ConfirmedResultRepository confirmedResultRepository;
    private final VoteRepository voteRepository;
    private final ParticipantRepository participantRepository;
    private final IcsService icsService;

    // ========== 1) 자동 확정 ==========

    /**
     * 마감 지난 모임을 자동 확정.
     * DeadlineScheduler에서 1분마다 호출.
     *
     * 처리 흐름:
     * ① 참여자 0명 → EXPIRED
     * ② 모임 타입별로 득표수 1위 후보 추출
     * ③ 동점 없음 → CONFIRMED + 결과 저장
     * ④ 동점 있음 → TIEBREAK (주최자 선택 대기)
     */
    @Transactional
    public void autoConfirm(Gathering gathering) {
        log.info("[자동확정] 시작 - id={}, shareCode={}", gathering.getId(), gathering.getShareCode());

        // 이미 확정 결과가 존재하면 스킵 (중복 방지)
        if (confirmedResultRepository.existsByGatheringId(gathering.getId())) {
            log.info("[자동확정] 이미 확정됨 - 스킵");
            return;
        }

        // ① 참여자 0명 → EXPIRED
        long participantCount = participantRepository.countByGatheringId(gathering.getId());
        if (participantCount == 0) {
            gathering.setStatus(GatheringStatus.EXPIRED);
            gatheringRepository.save(gathering);
            log.info("[자동확정] 참여자 없음 → EXPIRED");
            return;
        }

        // ② 모임 타입별 1위 후보 추출
        GatheringType type = gathering.getType();
        TimeCandidate bestTime = null;
        PlaceCandidate bestPlace = null;
        boolean hasTie = false;

        // --- 시간 후보 처리 ---
        if (type != GatheringType.PLACE_ONLY) {
            VoteResult timeResult = findTopCandidate(gathering, CandidateType.TIME);
            if (timeResult.isTied) {
                hasTie = true;
                log.info("[자동확정] 시간 동점 발생 - 후보수={}", timeResult.tiedCandidateIds.size());
            } else {
                bestTime = gathering.getTimeCandidates().stream()
                        .filter(tc -> tc.getId().equals(timeResult.topCandidateId))
                        .findFirst().orElse(null);
            }
        }

        // --- 장소 후보 처리 ---
        if (type != GatheringType.TIME_ONLY) {
            VoteResult placeResult = findTopCandidate(gathering, CandidateType.PLACE);
            if (placeResult.isTied) {
                hasTie = true;
                log.info("[자동확정] 장소 동점 발생 - 후보수={}", placeResult.tiedCandidateIds.size());
            } else {
                bestPlace = gathering.getPlaceCandidates().stream()
                        .filter(pc -> pc.getId().equals(placeResult.topCandidateId))
                        .findFirst().orElse(null);
            }
        }

        // ④ 동점 → TIEBREAK
        if (hasTie) {
            gathering.setStatus(GatheringStatus.TIEBREAK);
            gatheringRepository.save(gathering);
            log.info("[자동확정] 동점 → TIEBREAK 전환");
            return;
        }

        // ③ 동점 없음 → CONFIRMED
        saveConfirmedResult(gathering, bestTime, bestPlace, ConfirmType.AUTO);
        log.info("[자동확정] 완료 - timeId={}, placeId={}",
                bestTime != null ? bestTime.getId() : "null",
                bestPlace != null ? bestPlace.getId() : "null");
    }

    // ========== 2) 주최자 수동 확정 ==========

    /**
     * 주최자가 직접 후보를 선택하여 확정.
     *
     * VOTING 상태에서만 가능 (이미 CONFIRMED/TIEBREAK면 에러).
     * 마감 전이라도 "더 이상 투표 필요 없다"고 판단하면 사용.
     */
    @Transactional
    public void manualConfirm(String shareCode, String adminToken, ConfirmRequest request) {
        Gathering gathering = findAndValidateAdmin(shareCode, adminToken);

        // VOTING 상태에서만 수동 확정 가능
        if (gathering.getStatus() != GatheringStatus.VOTING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "VOTING 상태에서만 수동 확정할 수 있습니다. 현재 상태: " + gathering.getStatus());
        }

        // 후보 검증 + 조회
        TimeCandidate timeCandidate = resolveTimeCandidate(gathering, request.getTimeCandidateId());
        PlaceCandidate placeCandidate = resolvePlaceCandidate(gathering, request.getPlaceCandidateId());

        saveConfirmedResult(gathering, timeCandidate, placeCandidate, ConfirmType.HOST);
        log.info("[수동확정] 완료 - shareCode={}", shareCode);
    }

    // ========== 3) 동점 해소 ==========

    /**
     * TIEBREAK 상태에서 주최자가 동점 후보 중 하나를 선택.
     *
     * TIEBREAK 상태에서만 호출 가능.
     * 선택한 후보로 확정 결과 생성 + 상태를 CONFIRMED로 전환.
     */
    @Transactional
    public void resolveTiebreak(String shareCode, String adminToken, TiebreakRequest request) {
        Gathering gathering = findAndValidateAdmin(shareCode, adminToken);

        // TIEBREAK 상태에서만 동점 해소 가능
        if (gathering.getStatus() != GatheringStatus.TIEBREAK) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "TIEBREAK 상태에서만 동점 해소할 수 있습니다. 현재 상태: " + gathering.getStatus());
        }

        TimeCandidate timeCandidate = resolveTimeCandidate(gathering, request.getTimeCandidateId());
        PlaceCandidate placeCandidate = resolvePlaceCandidate(gathering, request.getPlaceCandidateId());

        saveConfirmedResult(gathering, timeCandidate, placeCandidate, ConfirmType.HOST);
        log.info("[동점해소] 완료 - shareCode={}", shareCode);
    }

    // ========== 4) 결과 조회 ==========

    /**
     * 확정 결과 조회.
     *
     * 인증 불필요 — 링크를 가진 누구나 결과를 볼 수 있음.
     * 아직 확정되지 않았으면 404 (GATHERING_NOT_FOUND가 아닌, 결과 자체가 없음).
     */
    public ConfirmedResultResponse getResult(String shareCode) {
        Gathering gathering = gatheringRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.GATHERING_NOT_FOUND));

        ConfirmedResult result = confirmedResultRepository.findByGatheringId(gathering.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "아직 확정된 결과가 없습니다."));

        return buildResultResponse(gathering, result);
    }

    // ========== 5) .ics 파일 생성 ==========

    /**
     * .ics 캘린더 파일 내용 반환.
     *
     * Controller에서 Content-Type: text/calendar로 응답하여
     * 브라우저가 .ics 파일로 다운로드하도록 유도.
     */
    public String generateIcsFile(String shareCode) {
        Gathering gathering = gatheringRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.GATHERING_NOT_FOUND));

        ConfirmedResult result = confirmedResultRepository.findByGatheringId(gathering.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "확정 결과가 없어 .ics를 생성할 수 없습니다."));

        return icsService.generateIcs(result, gathering.getTitle());
    }

    // ========== 6) TIEBREAK 자동 해소 (24h 초과) ==========

    /**
     * TIEBREAK 상태가 24시간 이상 지속된 모임을 자동 확정.
     * DeadlineScheduler에서 호출.
     *
     * 선택 기준: 각 카테고리에서 displayOrder가 가장 낮은(가장 먼저 등록된) 후보.
     * → 주최자가 가장 먼저 떠올린 후보가 의미적으로 우선순위가 높다는 가정.
     */
    @Transactional
    public void autoResolveTiebreak(Gathering gathering) {
        log.info("[타이브레이크 자동해소] 시작 - shareCode={}", gathering.getShareCode());

        if (confirmedResultRepository.existsByGatheringId(gathering.getId())) {
            log.info("[타이브레이크 자동해소] 이미 확정됨 - 스킵");
            return;
        }

        GatheringType type = gathering.getType();
        TimeCandidate bestTime = null;
        PlaceCandidate bestPlace = null;

        // 시간: 동점 후보 중 displayOrder가 가장 낮은 후보 선택
        if (type != GatheringType.PLACE_ONLY) {
            VoteResult timeResult = findTopCandidate(gathering, CandidateType.TIME);
            Long selectedId = timeResult.isTied
                    ? selectByDisplayOrder(gathering.getTimeCandidates(), timeResult.tiedCandidateIds)
                    : timeResult.topCandidateId;

            bestTime = gathering.getTimeCandidates().stream()
                    .filter(tc -> tc.getId().equals(selectedId))
                    .findFirst().orElse(null);
        }

        // 장소: 동일 로직
        if (type != GatheringType.TIME_ONLY) {
            VoteResult placeResult = findTopCandidate(gathering, CandidateType.PLACE);
            Long selectedId = placeResult.isTied
                    ? selectByDisplayOrder(gathering.getPlaceCandidates(), placeResult.tiedCandidateIds)
                    : placeResult.topCandidateId;

            bestPlace = gathering.getPlaceCandidates().stream()
                    .filter(pc -> pc.getId().equals(selectedId))
                    .findFirst().orElse(null);
        }

        saveConfirmedResult(gathering, bestTime, bestPlace, ConfirmType.AUTO);
        log.info("[타이브레이크 자동해소] 완료 - shareCode={}", gathering.getShareCode());
    }

    // ========== Private 헬퍼 메서드 ==========

    /**
     * 공유 코드로 모임 조회 + 관리 토큰 검증.
     *
     * 주최자 전용 API(수동확정, 동점해소)에서 공통으로 사용.
     * 토큰이 불일치하면 401 Unauthorized.
     */
    private Gathering findAndValidateAdmin(String shareCode, String adminToken) {
        Gathering gathering = gatheringRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.GATHERING_NOT_FOUND));

        if (adminToken == null || !TokenHashUtil.matches(adminToken, gathering.getAdminTokenHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "관리자 토큰이 유효하지 않습니다.");
        }
        return gathering;
    }

    /**
     * 시간 후보 ID로 검증 + 조회.
     *
     * 해당 모임의 후보가 아닌 ID를 보내면 에러.
     * TIME_ONLY/BOTH일 때 필수, PLACE_ONLY일 때 null 허용.
     */
    private TimeCandidate resolveTimeCandidate(Gathering gathering, Long candidateId) {
        if (candidateId == null) {
            // PLACE_ONLY면 시간 후보 없어도 OK
            if (gathering.getType() == GatheringType.PLACE_ONLY) return null;
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시간 후보 ID가 필요합니다.");
        }
        return gathering.getTimeCandidates().stream()
                .filter(tc -> tc.getId().equals(candidateId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "유효하지 않은 시간 후보 ID: " + candidateId));
    }

    /** 장소 후보 ID로 검증 + 조회. resolveTimeCandidate와 동일 패턴. */
    private PlaceCandidate resolvePlaceCandidate(Gathering gathering, Long candidateId) {
        if (candidateId == null) {
            if (gathering.getType() == GatheringType.TIME_ONLY) return null;
            throw new BusinessException(ErrorCode.INVALID_INPUT, "장소 후보 ID가 필요합니다.");
        }
        return gathering.getPlaceCandidates().stream()
                .filter(pc -> pc.getId().equals(candidateId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "유효하지 않은 장소 후보 ID: " + candidateId));
    }

    /**
     * 확정 결과 저장 + 상태 CONFIRMED 전환.
     *
     * 여러 경로(자동/수동/타이브레이크)에서 최종 확정 시 공통 호출.
     * 이 메서드가 호출되면 모임의 생명주기가 종료된다.
     */
    private void saveConfirmedResult(Gathering gathering,
                                     TimeCandidate timeCandidate,
                                     PlaceCandidate placeCandidate,
                                     ConfirmType confirmType) {
        ConfirmedResult result = ConfirmedResult.builder()
                .gathering(gathering)
                .timeCandidate(timeCandidate)
                .placeCandidate(placeCandidate)
                .confirmedAt(Instant.now())
                .confirmedBy(confirmType)
                .build();
        confirmedResultRepository.save(result);

        // 상태 전환: → CONFIRMED (최종 상태)
        gathering.setStatus(GatheringStatus.CONFIRMED);
        gatheringRepository.save(gathering);
    }

    /**
     * 특정 타입(TIME/PLACE)의 득표수 1위 후보를 찾는다.
     *
     * 반환값 VoteResult:
     * - isTied=false → topCandidateId에 단독 1위 ID
     * - isTied=true  → tiedCandidateIds에 동점 후보 ID 목록
     *
     * 알고리즘:
     * 1. DB에서 후보별 득표수 조회 (GROUP BY)
     * 2. 최대 득표수 계산
     * 3. 최대 득표수를 가진 후보가 2개 이상이면 동점
     */
    private VoteResult findTopCandidate(Gathering gathering, CandidateType type) {
        // DB 쿼리: SELECT candidate_id, COUNT(*) FROM vote GROUP BY candidate_id
        List<Object[]> voteCounts = voteRepository.countByGatheringAndType(gathering.getId(), type);

        if (voteCounts.isEmpty()) {
            // 해당 타입에 투표가 하나도 없음 → 선등록 후보로 폴백
            return VoteResult.noVotes();
        }

        // 최대 득표수
        long maxVotes = voteCounts.stream()
                .mapToLong(row -> (Long) row[1])
                .max()
                .orElse(0);

        // 최대 득표수를 가진 후보 ID 수집
        List<Long> topIds = voteCounts.stream()
                .filter(row -> (Long) row[1] == maxVotes)
                .map(row -> (Long) row[0])
                .toList();

        if (topIds.size() == 1) {
            return VoteResult.single(topIds.get(0));
        } else {
            return VoteResult.tied(topIds);
        }
    }

    /**
     * 동점 후보 중 displayOrder가 가장 낮은 후보의 ID를 선택.
     *
     * displayOrder = 주최자가 후보를 등록한 순서 (0부터 시작).
     * 가장 먼저 등록된 후보가 주최자의 1순위라는 가정 하에 선택.
     *
     * 제네릭 타입이 아닌 Object로 처리하는 이유:
     * TimeCandidate와 PlaceCandidate가 공통 부모 클래스를 갖지 않기 때문.
     * MVP에서는 이 정도면 충분하고, v2에서 공통 인터페이스 추출 가능.
     */
    private Long selectByDisplayOrder(List<?> candidates, List<Long> tiedIds) {
        // tiedIds가 비어있으면 (투표 0건) 첫 번째 후보 반환
        if (tiedIds == null || tiedIds.isEmpty()) {
            if (candidates.isEmpty()) return null;
            // 첫 번째 후보의 ID 반환
            Object first = candidates.get(0);
            if (first instanceof TimeCandidate tc) return tc.getId();
            if (first instanceof PlaceCandidate pc) return pc.getId();
            return null;
        }

        // tiedIds에 해당하는 후보만 필터링 후 displayOrder 기준 정렬
        return candidates.stream()
                .filter(c -> {
                    Long id = (c instanceof TimeCandidate tc) ? tc.getId() : ((PlaceCandidate) c).getId();
                    return tiedIds.contains(id);
                })
                .min(Comparator.comparingInt(c -> {
                    return (c instanceof TimeCandidate tc) ? tc.getDisplayOrder() : ((PlaceCandidate) c).getDisplayOrder();
                }))
                .map(c -> (c instanceof TimeCandidate tc) ? tc.getId() : ((PlaceCandidate) c).getId())
                .orElse(null);
    }

    /** 결과 응답 DTO 조립 */
    private ConfirmedResultResponse buildResultResponse(Gathering gathering, ConfirmedResult result) {
        ConfirmedResultResponse.ConfirmedResultResponseBuilder builder = ConfirmedResultResponse.builder()
                .title(gathering.getTitle())
                .hostName(gathering.getHostName())
                .confirmedBy(result.getConfirmedBy().name())
                .confirmedAt(result.getConfirmedAt().toString())
                .icsDownloadUrl("/api/v1/gatherings/" + gathering.getShareCode() + "/result/ics");

        // 시간 정보
        if (result.getTimeCandidate() != null) {
            TimeCandidate tc = result.getTimeCandidate();
            builder.confirmedDate(tc.getCandidateDate().toString())
                    .confirmedStartTime(tc.getStartTime().toString())
                    .confirmedEndTime(tc.getEndTime() != null ? tc.getEndTime().toString() : null);
        }

        // 장소 정보
        if (result.getPlaceCandidate() != null) {
            PlaceCandidate pc = result.getPlaceCandidate();
            builder.confirmedPlaceName(pc.getName())
                    .confirmedPlaceMapLink(pc.getMapLink());
        }

        return builder.build();
    }

    // ========== 내부 DTO ==========

    /**
     * 투표 집계 결과를 담는 내부 클래스.
     *
     * Service 내부에서만 사용되는 intermediate result.
     * 외부로 노출할 필요 없으므로 private static.
     */
    private static class VoteResult {
        boolean isTied;
        Long topCandidateId;
        List<Long> tiedCandidateIds;

        /** 단독 1위 */
        static VoteResult single(Long id) {
            VoteResult r = new VoteResult();
            r.isTied = false;
            r.topCandidateId = id;
            r.tiedCandidateIds = Collections.emptyList();
            return r;
        }

        /** 동점 */
        static VoteResult tied(List<Long> ids) {
            VoteResult r = new VoteResult();
            r.isTied = true;
            r.topCandidateId = null;
            r.tiedCandidateIds = ids;
            return r;
        }

        /** 투표 0건 (해당 타입에 아무도 투표 안 함) */
        static VoteResult noVotes() {
            VoteResult r = new VoteResult();
            r.isTied = false;
            r.topCandidateId = null;
            r.tiedCandidateIds = Collections.emptyList();
            return r;
        }
    }
}