package com.moim.moimbackend.gathering.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 장소 후보 엔티티 — DB의 place_candidate 테이블과 매핑.
 *
 * 예: "강남 고기집" + 카카오맵 링크 + "삼겹살 맛집" 메모
 *
 * estCost, travelMin, moodTags는 Should 기능 (세부 옵션).
 * MVP에서는 입력만 받고 표시는 v2에서 활용.
 */
@Entity
@Table(name = "place_candidate")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PlaceCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    /** 장소명 (예: "강남 고기집") */
    @Column(nullable = false, length = 100)
    private String name;

    /** 지도 URL — 카카오맵, 네이버지도, 구글맵 중 아무거나 */
    @Column(name = "map_link", length = 500)
    private String mapLink;

    /** 간단 메모 (예: "삼겹살 맛집, 주차 가능") */
    @Column(length = 200)
    private String memo;

    /** 예상 비용 (원). Should 기능. */
    @Column(name = "est_cost")
    private Integer estCost;

    /** 예상 이동시간 (분). Should 기능. */
    @Column(name = "travel_min")
    private Integer travelMin;

    /** 분위기 태그 (쉼표 구분, 예: "조용한,분위기좋은"). Should 기능. */
    @Column(name = "mood_tags", length = 200)
    private String moodTags;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}