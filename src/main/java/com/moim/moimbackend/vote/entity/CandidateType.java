package com.moim.moimbackend.vote.entity;

/**
 * 투표 대상 구분.
 * Vote 테이블에서 candidate_id가 어느 테이블을 참조하는지 구분한다.
 */
public enum CandidateType {
    TIME,   // time_candidate 테이블 참조
    PLACE   // place_candidate 테이블 참조
}