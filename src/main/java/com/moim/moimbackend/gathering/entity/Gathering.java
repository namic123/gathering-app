package com.moim.moimbackend.gathering.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
/**
 * 모임 엔티티 — DB의 gathering 테이블과 매핑.
 *
 * 핵심 테이블: 모든 데이터의 루트.
 * 하나의 Gathering에 여러 TimeCandidate, PlaceCandidate가 연결된다.
 *
 * @Entity: JPA가 이 클래스를 DB 테이블로 인식
 * @Table: 테이블명 명시 (클래스명과 다를 때 사용)
 */
@Entity
@Table(name = "gathering")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 필수: 기본 생성자 (외부에서 직접 호출 방지)
@AllArgsConstructor
@Builder
public class Gathering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // PostgreSQL의 BIGSERIAL 자동 증가
    private Long id;

    /**
     * 초대 링크용 고유 코드 (예: "aB3kX7").
     * URL에 노출되므로 auto increment ID 대신 사용 → ID 추측 공격 방지.
     */
    @Column(name="share_code", nullable = false, unique = true, length = 8)
    private String shareCode;

    @Column(nullable = false, length = 100)
    private String title;

    /** 주최자 닉네임. 로그인 없으므로 이름만 저장. */
    @Column(name = "host_name", nullable = false, length = 30)
    private String hostName;

    @Column(length = 500)
    private String description;

    /**
     * 모임 타입 (TIME_ONLY / PLACE_ONLY / BOTH).
     * @Enumerated(STRING): DB에 "BOTH" 문자열로 저장 (ORDINAL은 순서 변경 시 위험)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GatheringType type;

    /**
     * 관리 토큰의 SHA-256 해시.
     * 원본 토큰은 모임 생성 시 1회만 클라이언트에게 반환됨.
     */
    @Column(name = "admin_token_hash", nullable = false, length = 64)
    private String adminTokenHash;

    /** 투표 마감 시각 (UTC). Instant는 타임존 독립적. */
    @Column(nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GatheringStatus status = GatheringStatus.VOTING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    // === 연관 관계 ===

    /**
     * 시간 후보 목록 (1:N).
     * cascade ALL: Gathering 저장 시 TimeCandidate도 함께 저장/삭제
     * orphanRemoval: 리스트에서 제거된 후보는 DB에서도 삭제
     */
    @OneToMany(mappedBy = "gathering", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TimeCandidate> timeCandidates = new ArrayList<>();

    /** 장소 후보 목록 (1:N). */
    @OneToMany(mappedBy = "gathering", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PlaceCandidate> placeCandidates = new ArrayList<>();

// === 편의 메서드 ===

    /** 시간 후보 추가 시 양방향 관계 설정. */
    public void addTimeCandidate(TimeCandidate candidate) {
        timeCandidates.add(candidate);
        candidate.setGathering(this);
    }

    /** 장소 후보 추가 시 양방향 관계 설정. */
    public void addPlaceCandidate(PlaceCandidate candidate) {
        placeCandidates.add(candidate);
        candidate.setGathering(this);
    }

    /** 엔티티 수정 시 updatedAt 자동 갱신. */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
