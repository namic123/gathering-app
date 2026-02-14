package com.moim.moimbackend.gathering.entity;

/**
 * 모임 타입.
 * 어떤 종류의 투표를 진행하는지 결정한다.
 *
 * TIME_ONLY:  시간만 투표 (장소는 이미 정해짐)
 * PLACE_ONLY: 장소만 투표 (시간은 이미 정해짐)
 * BOTH:       시간 + 장소 둘 다 투표
 */
public enum GatheringType {
    TIME_ONLY,
    PLACE_ONLY,
    BOTH
}