package com.moim.moimbackend.vote.entity;

import com.moim.moimbackend.gathering.entity.Gathering;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 투표 엔티티.
 *
 * 다형성 패턴: candidateType(TIME/PLACE)으로 참조 테이블 구분.
 * FK 없이 candidateId만 저장 → 유연하지만, 삭제된 후보 참조 가능성은 Service에서 검증.
 *
 * UNIQUE(participant_id, candidate_id, candidate_type): 동일 후보에 중복 투표 차단.
 */
@Entity
@Table(name = "vote",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"participant_id", "candidate_id", "candidate_type"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id", nullable = false, referencedColumnName = "id")
    private Gathering gathering;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false, referencedColumnName = "id")
    private Participant participant;

    /** time_candidate.id 또는 place_candidate.id */
    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_type", nullable = false, length = 10)
    private CandidateType candidateType;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}