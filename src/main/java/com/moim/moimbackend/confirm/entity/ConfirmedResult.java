package com.moim.moimbackend.confirm.entity;

import com.moim.moimbackend.gathering.entity.Gathering;
import com.moim.moimbackend.gathering.entity.TimeCandidate;
import com.moim.moimbackend.gathering.entity.PlaceCandidate;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 확정 결과 엔티티 — DB의 confirmed_result 테이블과 매핑.
 *
 * 모임당 최대 1건만 존재한다 (gathering_id UNIQUE).
 *
 * 모임 타입에 따라 nullable 필드가 달라진다:
 * - TIME_ONLY  → timeCandidateId만 채워짐, placeCandidateId는 null
 * - PLACE_ONLY → placeCandidateId만 채워짐, timeCandidateId는 null
 * - BOTH       → 둘 다 채워짐
 *
 * confirmedBy 필드로 자동 확정(AUTO)인지 주최자 선택(HOST)인지 구분.
 * 이 정보는 결과 카드 UI에서 "자동으로 결정됐어요" / "주최자가 선택했어요" 표시에 사용.
 */
@Entity
@Table(name = "confirmed_result")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ConfirmedResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 확정 대상 모임 (1:1 관계).
     *
     * @JoinColumn의 unique=true → 모임당 확정 결과는 단 1건.
     * 중복 생성 시도 시 DB에서 ConstraintViolationException 발생.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id", nullable = false, unique = true, referencedColumnName = "id")
    private Gathering gathering;

    /**
     * 확정된 시간 후보.
     *
     * PLACE_ONLY 타입이면 null.
     * @ManyToOne: 이론적으로 같은 시간 후보가 여러 모임에서 확정될 수 있지만,
     * 실제로는 후보가 모임 종속이므로 사실상 1:1.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_candidate_id", referencedColumnName = "id")
    private TimeCandidate timeCandidate;

    /**
     * 확정된 장소 후보.
     * TIME_ONLY 타입이면 null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_candidate_id", referencedColumnName = "id")
    private PlaceCandidate placeCandidate;

    /** 확정된 시각 (UTC). 결과 카드에 "2월 15일 확정됨" 표시용. */
    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    /**
     * 확정 방식.
     * @Enumerated(STRING): DB에 "AUTO" 또는 "HOST" 문자열로 저장.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "confirmed_by", nullable = false, length = 10)
    private ConfirmType confirmedBy;
}