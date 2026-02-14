package com.moim.moimbackend.gathering.entity;

/**
 * 모임 상태 (생명주기).
 *
 * 상태 전이:
 * VOTING → CONFIRMED  (동점 없이 자동 확정)
 * VOTING → TIEBREAK   (동점 발생, 주최자 선택 대기)
 * TIEBREAK → CONFIRMED (주최자 선택 또는 24h 후 자동 확정)
 */
public enum GatheringStatus {
    VOTING,     // 투표 진행 중 (기본값)
    TIEBREAK,   // 동점 발생, 주최자 선택 대기
    CONFIRMED,  // 최종 확정 완료
    EXPIRED     // 투표 없이 마감됨 (참여자 0명)
}