package com.moim.moimbackend.confirm.entity;

/**
 * 확정 방식 구분.
 *
 * 모임 결과가 어떻게 확정되었는지를 기록한다.
 * 결과 카드에 "자동 확정" 또는 "주최자 선택"으로 표시하는 데 사용.
 *
 * AUTO: 마감 시각 도래 → 스케줄러가 득표수 1위 후보를 자동 확정
 * HOST: 동점(TIEBREAK) 상태에서 주최자가 직접 선택하여 확정
 */
public enum ConfirmType {
    AUTO,
    HOST
}