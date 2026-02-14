package com.moim.moimbackend.gathering.repository;

import com.moim.moimbackend.gathering.entity.Gathering;
import com.moim.moimbackend.gathering.entity.GatheringStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 모임 Repository.
 *
 * JpaRepository<Gathering, Long>을 상속하면:
 * - save(), findById(), findAll(), delete() 등 기본 CRUD 자동 제공
 * - 메서드 이름 규칙으로 쿼리 자동 생성 (Spring Data Query Methods)
 */

public interface GatheringRepository extends JpaRepository<Gathering, Long> {

    /**
     * 공유 코드로 모임 조회.
     * 참여자가 링크 접속 시 가장 먼저 호출되는 쿼리.
     * → SELECT * FROM gathering WHERE share_code = ?
     */
    Optional<Gathering> findByShareCode(String shareCode);

    /**
     * 공유 코드 존재 여부 확인.
     * 새 코드 생성 시 중복 체크용.
     * → SELECT EXISTS(SELECT 1 FROM gathering WHERE share_code = ?)
     */
    boolean existsByShareCode(String shareCode);

    /**
     * 특정 상태이면서 마감 시각이 지난 모임 목록 조회.
     * 스케줄러에서 1분마다 호출: "VOTING 상태인데 deadline이 지난 모임" 찾기.
     * → SELECT * FROM gathering WHERE status = ? AND deadline < ?
     */
    List<Gathering> findByStatusAndDeadlineBefore(GatheringStatus status, Instant deadline);
}
