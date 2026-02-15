package com.moim.moimbackend.vote.service;

import com.moim.moimbackend.common.exception.BusinessException;
import com.moim.moimbackend.common.exception.ErrorCode;
import com.moim.moimbackend.common.security.TokenHashUtil;
import com.moim.moimbackend.gathering.entity.Gathering;
import com.moim.moimbackend.gathering.entity.GatheringStatus;
import com.moim.moimbackend.gathering.repository.GatheringRepository;
import com.moim.moimbackend.vote.dto.*;
import com.moim.moimbackend.vote.entity.CandidateType;
import com.moim.moimbackend.vote.entity.Participant;
import com.moim.moimbackend.vote.entity.Vote;
import com.moim.moimbackend.vote.repository.ParticipantRepository;
import com.moim.moimbackend.vote.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteService {

    private final GatheringRepository gatheringRepository;
    private final ParticipantRepository participantRepository;
    private final VoteRepository voteRepository;

    /**
     * 참여 등록 + 투표 동시 처리.
     * 참여자 엔티티에 사용자 등록
     * 흐름:
     * 1. 모임 조회 + VOTING 상태 검증
     * 2. 닉네임 중복 검증
     * 3. 참여자 생성 (세션 토큰 발급)
     * 4. 투표 저장
     * 5. 세션 토큰 원본 1회 반환
     */
    @Transactional
    public ParticipateResponse participate(String shareCode, ParticipateRequest request){
        // 1. 모임 조회 + Voting 상태 검증
        Gathering gathering = findGatheringByCode(shareCode);
        validateVotingOpen(gathering);

        // 2. 닉네임 중복 검증
        if(participantRepository.existsByGatheringIdAndName(gathering.getId(), request.getName().trim())){
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }

        // 3. 참여자 생성 (세션 토큰 발급)
        String sessionToken = TokenHashUtil.generateToken();
        Participant participant = Participant.builder()
                .gathering(gathering)
                .name(request.getName().trim())
                .sessionTokenHash(TokenHashUtil.hash(sessionToken))
                .build();

        participantRepository.save(participant);

        // 4. 투표 저장
        saveVotes(gathering, participant, request.getTimeCandidateIds(), CandidateType.TIME);
        saveVotes(gathering, participant, request.getPlaceCandidateIds(), CandidateType.PLACE);

        log.info("[투표] 참여 완료 - shareCode={}, name={}, participantId={}",
                shareCode, participant.getName(), participant.getId());

        return ParticipateResponse.builder()
                .participantId(participant.getId())
                .name(participant.getName())
                .sessionToken(sessionToken)
                .build();
    }

    /**
     * 투표 변경.
     *
     * 기존 투표를 전체 삭제 후 새로 저장하는 방식 (Replace 전략).
     * 부분 수정(add/remove)보다 구현이 단순하고 프론트에서도 쉬움.
     */
    @Transactional
    public void updateVotes(String shareCode, String sessionToken , UpdateVotesRequest request){
        // 코드로 모임 조회
        Gathering gathering = findGatheringByCode(shareCode);
        // 투표 현황 검증
        validateVotingOpen(gathering);

        // 세션 토큰 및 모임 코드로 투표자 조회
        Participant participant = findParticipantByToken(gathering.getId(), sessionToken);

        // 기존 TIME 투표 삭제 + 새로 저장
        // 투표자 ID와 후보 타입으로 기존 시간 목록 조회
        List<Vote> oldTimeVotes = voteRepository.findByParticipantIdAndCandidateType(
                participant.getId(), CandidateType.TIME);
        // 기존 투표 전체 삭제
        voteRepository.deleteAll(oldTimeVotes);
        // 새 투표 저장
        saveVotes(gathering, participant, request.getTimeCandidateIds(), CandidateType.TIME);

        // 기존 PLACE 투표 삭제 + 새로 저장
        List<Vote> oldPlaceVotes = voteRepository.findByParticipantIdAndCandidateType(
                participant.getId(), CandidateType.PLACE);
        voteRepository.deleteAll(oldPlaceVotes);
        saveVotes(gathering, participant, request.getPlaceCandidateIds(), CandidateType.PLACE);

        log.info("[투표] 변경 완료 - shareCode={}, participantId={}", shareCode, participant.getId());
    }

    /**
     * 투표 현황 조회.
     *
     * 후보별 득표수 + 투표한 참여자 닉네임 목록 반환.
     * 5초 폴링으로 호출되므로 쿼리 최적화 필요 (인덱스 활용).
     */
    public VoteSummaryResponse getVoteSummary(String shareCode) {
        // 모임 코드 조회
        Gathering gathering = findGatheringByCode(shareCode);

        // 참여자 목록 조회
        // 참여자 목록
        List<Participant> participants = participantRepository.findByGatheringId(gathering.getId());
        Map<Long, String> participantNameMap = participants.stream()
                .collect(Collectors.toMap(Participant::getId, Participant::getName));

        // 시간 후보별 집계
        List<VoteSummaryResponse.CandidateVote> timeVotes =
                buildCandidateVotes(gathering.getId(), CandidateType.TIME, participantNameMap);

        // 장소 후보별 집계
        List<VoteSummaryResponse.CandidateVote> placeVotes =
                buildCandidateVotes(gathering.getId(), CandidateType.PLACE, participantNameMap);

        return VoteSummaryResponse.builder()
                .participantCount(participants.size())
                .participantNames(participants.stream().map(Participant::getName).toList())
                .timeCandidateVotes(timeVotes)
                .placeCandidateVotes(placeVotes)
                .build();
    }

    // ========== Private 메서드 ==========

    // 모임 코드로 모임 조회
    private Gathering findGatheringByCode(String shareCode) {
        return gatheringRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.GATHERING_NOT_FOUND));
    }

    /** VOTING 상태가 아니면 투표 불가 */
    private void validateVotingOpen(Gathering gathering) {
        if (gathering.getStatus() != GatheringStatus.VOTING) {
            throw new BusinessException(ErrorCode.VOTING_CLOSED);
        }
    }

    /** 세션 토큰으로 참여자 조회 */
    private Participant findParticipantByToken(Long gatheringId, String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "세션 토큰이 필요합니다.");
        }
        String tokenHash = TokenHashUtil.hash(sessionToken);
        return participantRepository.findByGatheringIdAndSessionTokenHash(gatheringId, tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 세션 토큰입니다."));
    }

    /**
     * 투표 저장 (공통).
     * candidateIds가 null이거나 비어있으면 skip.
     * 유효하지 않은 candidateId는 Service에서 검증하지 않음 (FK 없으므로).
     * → MVP에서는 프론트가 유효한 ID만 보내는 것을 신뢰.
     */
    private void saveVotes(Gathering gathering, Participant participant,
                           List<Long> candidateIds, CandidateType type) {
        // 후보가 없으면 스킵
        if (candidateIds == null || candidateIds.isEmpty()) {
            return;
        }

        // 중복 distinct 후, map -> list로 변환
        List<Vote> votes = candidateIds.stream()
                .distinct()  // 클라이언트 중복 전송 방어
                .map(candidateId -> Vote.builder()
                        .gathering(gathering)
                        .participant(participant)
                        .candidateId(candidateId)
                        .candidateType(type)
                        .build())
                .toList();

        // 투표 저장
        voteRepository.saveAll(votes);
    }

    /** 후보별 득표수 + 투표자 이름 조립 */
    private List<VoteSummaryResponse.CandidateVote> buildCandidateVotes(
            Long gatheringId, CandidateType type, Map<Long, String> participantNameMap) {

        // 후보별 득표수 조회
        List<Object[]> voteCounts = voteRepository.countByGatheringAndType(gatheringId, type);

        // 조회한 득표 데이터를 기반으로 CandidateVote 리스트 생성
        return voteCounts.stream()
                .map(row -> {
                    // 후보 ID와 해당 후보의 득표 수를 각각 추출
                    Long candidateId = (Long) row[0];  // 후보 ID
                    long count = (Long) row[1];        // 해당 후보의 득표 수

                    // 해당 후보에 투표한 참여자들의 ID 목록 조회
                    List<Long> voterIds = voteRepository.findParticipantIdsByCandidateAndType(candidateId, type);

                    // 참여자 ID를 기반으로 참여자 이름을 매핑, 이름 목록 생성
                    List<String> voterNames = voterIds.stream()
                            .map(id -> participantNameMap.getOrDefault(id, "알 수 없음")) // 참여자 이름이 없을 경우 "알 수 없음" 반환
                            .toList();

                    // 후보 ID, 득표 수, 참여자 이름 목록을 포함한 CandidateVote 객체 생성
                    return VoteSummaryResponse.CandidateVote.builder()
                            .candidateId(candidateId)
                            .voteCount(count)
                            .voterNames(voterNames)
                            .build();
                })
                .toList(); // 최종적으로 CandidateVote 객체 리스트를 반환
    }
}
