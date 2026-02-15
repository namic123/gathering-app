package com.moim.moimbackend.vote.repository;

import com.moim.moimbackend.vote.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    /** 모임 내 닉네임 중복 체크 */
    boolean existsByGatheringIdAndName(Long gatheringId, String name);

    /** 세션 토큰 해시로 참여자 조회 (투표 변경 시 본인 확인) */
    Optional<Participant> findByGatheringIdAndSessionTokenHash(Long gatheringId, String sessionTokenHash);

    /** 모임의 전체 참여자 목록 */
    List<Participant> findByGatheringId(Long gatheringId);

    /** 모임의 참여자 수 */
    long countByGatheringId(Long gatheringId);
}
