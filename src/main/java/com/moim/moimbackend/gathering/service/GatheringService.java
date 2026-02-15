package com.moim.moimbackend.gathering.service;

import com.moim.moimbackend.common.exception.BusinessException;
import com.moim.moimbackend.common.exception.ErrorCode;
import com.moim.moimbackend.common.security.TokenHashUtil;
import com.moim.moimbackend.common.util.ShareCodeGenerator;
import com.moim.moimbackend.gathering.dto.CreateGatheringRequest;
import com.moim.moimbackend.gathering.dto.CreateGatheringResponse;
import com.moim.moimbackend.gathering.dto.GatheringDetailResponse;
import com.moim.moimbackend.gathering.entity.*;
import com.moim.moimbackend.gathering.repository.GatheringRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.moim.moimbackend.vote.entity.CandidateType;
import com.moim.moimbackend.vote.repository.ParticipantRepository;
import com.moim.moimbackend.vote.repository.VoteRepository;

/**
 * 모임 생성/조회 비즈니스 로직.
 *
 * @Service: 스프링이 빈으로 관리
 * @RequiredArgsConstructor: final 필드를 생성자 주입 (Lombok)
 * @Transactional(readOnly=true): 기본적으로 읽기 전용 트랜잭션 (성능 최적화)
 * 쓰기 메서드에만 @Transactional 별도 지정
 */

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatheringService {

    private final GatheringRepository gatheringRepository;
    private final ParticipantRepository participantRepository;
    private final VoteRepository voteRepository;
    /**
     * 모임 생성.
     * <p>
     * 처리 흐름:
     * 1. 마감 시간 검증 (과거인지, 30일 초과인지)
     * 2. 모임 타입에 따른 후보 검증
     * 3. 공유 코드 생성 (중복 체크)
     * 4. 관리 토큰 생성 + 해시 저장
     * 5. Entity 조립 + DB 저장
     * 6. 응답 반환 (원본 토큰 포함)
     *
     * @Transactional: 쓰기 작업이므로 readOnly 해제
     */
    @Transactional
    public CreateGatheringResponse createGathering(CreateGatheringRequest request) {
        log.info("[모임 생성] 시작 - title={}, hostName={}, type={}",
                request.getTitle(), request.getHostName(), request.getType());

        // --- 1. 마감 시간 검증 ---
        Instant now = Instant.now();
        if (request.getDeadline().isBefore(now.plusSeconds(600))) {
            // 현재 시각 + 10분 이후여야 함 (너무 빠른 마감 방지)
            throw new BusinessException(ErrorCode.DEADLINE_PAST, "마감 시간은 최소 10분 후여야 합니다.");
        }
        if (request.getDeadline().isAfter(now.plusSeconds(30L * 24 * 60 * 60))) {
            // 30일 초과 금지 (무한정 열려있는 모임 방지)
            throw new BusinessException(ErrorCode.INVALID_INPUT, "마감 시간은 30일 이내여야 합니다.");
        }

        // --- 2. 모임 타입 파싱 + 후보 검증 ---
        GatheringType type;

        try {
            type = GatheringType.valueOf(request.getType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "모임 타입은 TIME_ONLY, PLACE_ONLY, BOTH 중 하나여야 합니다.");
        }

        // 시간 투표인데 시간 후보가 없으면 에러
        if (type != GatheringType.PLACE_ONLY) {
            if (request.getTimeCandidates() == null || request.getTimeCandidates().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "시간 후보를 1개 이상 등록해주세요.");
            }

            if (request.getTimeCandidates().size() > 20) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "시간 후보는 최대 20개까지 등록 가능합니다.");
            }
        }
        // 장소 투표인데 장소 후보가 없으면 에러
        if (type != GatheringType.PLACE_ONLY) {
            if (request.getPlaceCandidates() == null || request.getPlaceCandidates().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "장소 후보를 1개 이상 등록해주세요.");
            }
            if (request.getPlaceCandidates().size() > 10) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "장소 후보는 최대 10개까지 등록 가능합니다.");
            }
        }

        // --- 3. 공유 코드 생성 (중복 시 재생성) ---
        String shareCode;

        do {
            shareCode = ShareCodeGenerator.generate();        // 62^6 ≈ 568억 조합이라 루프는 거의 1회만 실행됨
            // 62^6 ≈ 568억 조합이라 루프는 거의 1회만 실행됨
        } while (gatheringRepository.existsByShareCode(shareCode));

        // --- 4. 관리 토큰 생성 ---
        String adminToken = TokenHashUtil.generateToken(); // UUID 원본
        String adminTokenHash = TokenHashUtil.hash(adminToken);  // SHA-256 해시 (DB 저장용)

        // --- 5. Entity 조립 ---
        Gathering gathering = Gathering.builder()
                .shareCode(shareCode)
                .title(request.getTitle().trim())
                .hostName(request.getHostName().trim())
                .description(request.getDescription())
                .type(type)
                .adminTokenHash(adminTokenHash)
                .deadline(request.getDeadline())
                .build();

        // 시간 후보 추가
        if (request.getTimeCandidates() != null) {
            for (int i = 0; i < request.getTimeCandidates().size(); i++) {
                CreateGatheringRequest.TimeCandidateItem item = request.getTimeCandidates().get(i);
                TimeCandidate candidate = TimeCandidate.builder()
                        .candidateDate(LocalDate.parse(item.getDate()))
                        .startTime(LocalTime.parse(item.getStartTime()))
                        .endTime(item.getEndTime() != null ? LocalTime.parse(item.getEndTime()) : null)
                        .displayOrder(i)  // 등록 순서 저장
                        .build();
                gathering.addTimeCandidate(candidate);  // 양방향 관계 설정
            }
        }

        // 장소 후보 추가
        if (request.getPlaceCandidates() != null) {
            for (int i = 0; i < request.getPlaceCandidates().size(); i++) {
                CreateGatheringRequest.PlaceCandidateItem item = request.getPlaceCandidates().get(i);
                PlaceCandidate candidate = PlaceCandidate.builder()
                        .name(item.getName().trim())
                        .mapLink(item.getMapLink())
                        .memo(item.getMemo())
                        .estCost(item.getEstCost())
                        .travelMin(item.getTravelMin())
                        .moodTags(item.getMoodTags())
                        .displayOrder(i)
                        .build();
                gathering.addPlaceCandidate(candidate);
            }
        }
        // --- 6. DB 저장 ---
        // cascade ALL이므로 Gathering 저장 시 후보들도 함께 INSERT됨
        gatheringRepository.save(gathering);

        // --- 7. 응답 반환 ---
        return CreateGatheringResponse.builder()
                .shareCode(shareCode)
                .adminToken(adminToken)    // 원본 토큰 (이 시점에만 1회 반환!)
                .shareUrl("/g/" + shareCode)
                .deadline(request.getDeadline().toString())
                .build();
    }

    /**
     * 공유 코드로 모임 상세 조회.
     *
     * 참여자 링크 접속, 대시보드, 투표 현황 등 여러 화면에서 사용.
     * voteCount는 아직 0 → D3(투표 도메인)에서 집계 로직 추가 예정.
     */
    /**
     * 공유 코드로 모임 상세 조회.
     *
     * 참여자 링크 접속, 대시보드, 투표 현황 등 여러 화면에서 사용.
     * voteCount는 아직 0 → D3(투표 도메인)에서 집계 로직 추가 예정.
     */
    public GatheringDetailResponse getGathering(String shareCode) {
        // 로그 기록: 모임 조회 시작 시 공유 코드 출력
        log.info("[모임 조회] shareCode={}", shareCode);

        // 1. 공유 코드로 모임 엔티티 조회
        // - 존재하지 않는 경우 BusinessException 발생 (GATHERING_NOT_FOUND 오류)
        Gathering gathering = gatheringRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.GATHERING_NOT_FOUND));

        // 2. 시간별 후보와 장소별 후보에 대한 득표수 Map 생성
        // - 후보 ID를 key, 득표수를 value로 매핑
        Map<Long, Long> timeVoteMap = buildVoteCountMap(gathering.getId(), CandidateType.TIME); // 시간 후보 득표수 매핑
        Map<Long, Long> placeVoteMap = buildVoteCountMap(gathering.getId(), CandidateType.PLACE); // 장소 후보 득표수 매핑

        // 3. 시간 후보 데이터 변환
        // - Gathering에 저장된 시간 후보 엔티티를 `GatheringDetailResponse.TimeCandidateItem`으로 매핑
        List<GatheringDetailResponse.TimeCandidateItem> timeItems =
                gathering.getTimeCandidates().stream()
                        .map(tc -> GatheringDetailResponse.TimeCandidateItem.builder()
                                .id(tc.getId()) // 후보 ID
                                .date(tc.getCandidateDate().toString()) // 날짜
                                .startTime(tc.getStartTime().toString()) // 시작 시간
                                .endTime(tc.getEndTime() != null ? tc.getEndTime().toString() : null) // 종료 시간 (null일 수 있음)
                                .voteCount(timeVoteMap.getOrDefault(tc.getId(), 0L)) // 득표수, 기본값 0
                                .build())
                        .toList(); // 최종적으로 변환된 리스트 생성

        // 4. 장소 후보 데이터 변환
        // - Gathering에 저장된 장소 후보 엔티티를 `GatheringDetailResponse.PlaceCandidateItem`으로 매핑
        List<GatheringDetailResponse.PlaceCandidateItem> placeItems =
                gathering.getPlaceCandidates().stream()
                        .map(pc -> GatheringDetailResponse.PlaceCandidateItem.builder()
                                .id(pc.getId()) // 후보 ID
                                .name(pc.getName()) // 장소 이름
                                .mapLink(pc.getMapLink()) // 지도 링크
                                .memo(pc.getMemo()) // 메모
                                .estCost(pc.getEstCost()) // 예상 비용
                                .travelMin(pc.getTravelMin()) // 예상 소요 시간 (분 단위)
                                .moodTags(pc.getMoodTags() != null
                                        ? Arrays.asList(pc.getMoodTags().split(",")) // 분위기 태그를 ","로 분리해 리스트로 변환
                                        : Collections.emptyList()) // moodTags가 null인 경우 빈 리스트 반환
                                .voteCount(placeVoteMap.getOrDefault(pc.getId(), 0L)) // 득표수, 기본값 0
                                .build())
                        .toList(); // 최종적으로 변환된 리스트 생성

        // 5. 참여자 수 계산
        // - 해당 모임에 등록된 참여자 수를 조회
        long participantCount = participantRepository.countByGatheringId(gathering.getId());

        // 6. 결과 응답 객체 빌드 및 반환
        return GatheringDetailResponse.builder()
                .title(gathering.getTitle()) // 모임 제목
                .hostName(gathering.getHostName()) // 호스트 이름
                .description(gathering.getDescription()) // 모임 설명
                .type(gathering.getType().name()) // 모임 타입 (문자열)
                .status(gathering.getStatus().name()) // 모임 상태 (문자열)
                .deadline(gathering.getDeadline().toString()) // 마감 시간 (문자열)
                .timeCandidates(timeItems) // 변환된 시간 후보 리스트
                .placeCandidates(placeItems) // 변환된 장소 후보 리스트
                .participantCount((int) participantCount) // 참여자 수
                .build();
    }
    /** 후보별 득표수 Map 조립 */
    private Map<Long, Long> buildVoteCountMap(Long gatheringId, CandidateType type) {
        // 후보 ID → 득표수를 매핑하는 Map 생성
        return voteRepository.countByGatheringAndType(gatheringId, type).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],   // 후보 ID (첫 번째 컬럼)
                        row -> (Long) row[1]    // 득표 수 (두 번째 컬럼)
                ));
    }
}
