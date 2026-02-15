package com.moim.moimbackend.vote.repository;

import com.moim.moimbackend.vote.entity.CandidateType;
import com.moim.moimbackend.vote.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    /** 특정 참여자의 특정 타입 투표 목록 (투표 변경 시 기존 투표 삭제용) */
    List<Vote> findByParticipantIdAndCandidateType(Long participantId, CandidateType candidateType);

    /** 특정 참여자의 전체 투표 삭제 */
    void deleteByParticipantId(Long participantId);

    /** 후보별 득표수 조회. Object[0]=candidateId, Object[1]=count */
    @Query("SELECT v.candidateId, COUNT(v) FROM Vote v " +
            "WHERE v.gathering.id = :gatheringId AND v.candidateType = :type " +
            "GROUP BY v.candidateId")
    List<Object[]> countByGatheringAndType(@Param("gatheringId") Long gatheringId,
                                           @Param("type") CandidateType type);

    /** 특정 후보에 투표한 참여자 ID 목록 */
    @Query("SELECT v.participant.id FROM Vote v " +
            "WHERE v.candidateId = :candidateId AND v.candidateType = :type")
    List<Long> findParticipantIdsByCandidateAndType(@Param("candidateId") Long candidateId,
                                                    @Param("type") CandidateType type);
}