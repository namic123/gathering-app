package com.moim.moimbackend.vote.entity;

import com.moim.moimbackend.gathering.entity.Gathering;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 투표 참여자 엔티티.
 *
 * 로그인 없이 닉네임 + 세션토큰으로 본인 식별.
 * 같은 모임 내 닉네임 중복은 DB UNIQUE 제약으로 차단.
 */
@Entity
@Table(name = "participant",
        uniqueConstraints = @UniqueConstraint(columnNames = {"gathering_id", "name"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id", nullable = false, referencedColumnName = "id")
    private Gathering gathering;

    /** 참여자 닉네임 (모임 내 고유) */
    @Column(nullable = false, length = 30)
    private String name;

    /** 세션 토큰 SHA-256 해시. 투표 변경 시 본인 확인용. */
    @Column(name = "session_token_hash", nullable = false, length = 64)
    private String sessionTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}