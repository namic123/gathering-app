package com.moim.moimbackend.gathering.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 시간 후보 엔티티 — DB의 time_candidate 테이블과 매핑.
 *
 * 예: "2025-02-21 (금) 18:00~20:00"
 *
 * Gathering과 N:1 관계.
 * displayOrder: 등록 순서를 저장하여 동점 시 "먼저 등록된 후보" 판별에 사용.
 */
@Entity
@Table(name = "time_candidate")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TimeCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소속 모임 (N:1).
     * FetchType.LAZY: 시간 후보 조회 시 Gathering을 바로 로드하지 않음
     * → 필요할 때만 추가 쿼리 실행 (성능 최적화)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    /** 후보 날짜 (예: 2025-02-21) */
    @Column(name = "candidate_date", nullable = false)
    private LocalDate candidateDate;

    /** 시작 시간 (예: 18:00) */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** 종료 시간 (선택, 예: 20:00) */
    @Column(name = "end_time")
    private LocalTime endTime;

    /**
     * 등록 순서.
     * 동점 시 "가장 먼저 제안된 후보" 우선 확정 규칙에 사용.
     * 0부터 시작, 후보 추가 시 +1.
     */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}