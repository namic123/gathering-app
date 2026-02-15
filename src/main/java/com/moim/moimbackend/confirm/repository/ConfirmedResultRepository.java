package com.moim.moimbackend.confirm.repository;

import com.moim.moimbackend.confirm.entity.ConfirmedResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 확정 결과 Repository.
 *
 * 모임당 최대 1건이므로 findByGatheringId가 핵심 쿼리.
 */
public interface ConfirmedResultRepository extends JpaRepository<ConfirmedResult, Long> {

    /**
     * 모임 ID로 확정 결과 조회.
     *
     * 결과 카드 화면에서 사용:
     * - 있으면 → 확정된 시간/장소 표시
     * - 없으면 → "아직 확정되지 않았습니다" 표시
     */
    Optional<ConfirmedResult> findByGatheringId(Long gatheringId);

    /**
     * 이미 확정 결과가 존재하는지 확인.
     *
     * 중복 확정 방지용.
     * 스케줄러가 동시에 여러 번 실행되거나,
     * 주최자가 확정 버튼을 빠르게 두 번 누르는 경우를 방어.
     */
    boolean existsByGatheringId(Long gatheringId);
}