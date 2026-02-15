package com.moim.moimbackend.confirm.scheduler;

import com.moim.moimbackend.confirm.service.ConfirmService;
import com.moim.moimbackend.gathering.entity.Gathering;
import com.moim.moimbackend.gathering.entity.GatheringStatus;
import com.moim.moimbackend.gathering.repository.GatheringRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 마감 처리 스케줄러.
 *
 * 1분마다 실행되어 두 가지 작업을 수행:
 *
 * 1) VOTING → 자동 확정
 *    마감 시각이 지났는데 아직 VOTING 상태인 모임을 찾아서 autoConfirm 호출.
 *
 * 2) TIEBREAK → 24h 자동 해소
 *    TIEBREAK 상태가 24시간 이상 지속된 모임을 찾아서 autoResolveTiebreak 호출.
 *    (deadline + 24h가 현재 시각보다 이전이면 24h 초과)
 *
 * @Scheduled(fixedRate): 이전 실행 시작 시점 기준 1분 간격.
 * fixedDelay는 이전 실행 완료 후 1분이므로, 처리 시간이 길어지면 간격이 벌어짐.
 * fixedRate가 더 일관된 주기를 보장.
 *
 * 주의: @EnableScheduling이 메인 클래스에 있어야 동작.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineScheduler {

    private final GatheringRepository gatheringRepository;
    private final ConfirmService confirmService;

    /**
     * 1분마다 실행: 마감 지난 VOTING 모임 자동 확정.
     *
     * 처리 중 개별 모임에서 에러가 발생해도 다음 모임은 계속 처리.
     * try-catch로 감싸서 하나의 실패가 전체 스케줄러를 멈추지 않도록 함.
     */
    @Scheduled(fixedRate = 60_000)  // 60,000ms = 1분
    public void processExpiredVoting() {
        // 현재 시각 기준, 마감이 지난 VOTING 상태 모임을 모두 조회
        List<Gathering> expired = gatheringRepository
                .findByStatusAndDeadlineBefore(GatheringStatus.VOTING, Instant.now());

        if (expired.isEmpty()) return;

        log.info("[스케줄러] 마감 모임 {} 건 처리 시작", expired.size());

        for (Gathering gathering : expired) {
            try {
                confirmService.autoConfirm(gathering);
            } catch (Exception e) {
                // 개별 모임 처리 실패 시 로그만 남기고 계속 진행
                log.error("[스케줄러] 자동확정 실패 - shareCode={}, error={}",
                        gathering.getShareCode(), e.getMessage(), e);
            }
        }
    }

    /**
     * 1분마다 실행: 24시간 초과된 TIEBREAK 모임 자동 해소.
     *
     * 판정 기준: deadline + 24시간 < 현재 시각
     * 즉, 원래 마감 시각으로부터 24시간이 지나면 자동 해소.
     *
     * 예: deadline = 2월 15일 23시
     *     → TIEBREAK 전환: 2월 15일 23시
     *     → 자동 해소: 2월 16일 23시 이후
     */
    @Scheduled(fixedRate = 60_000)
    public void processExpiredTiebreak() {
        // deadline + 24h < now → deadline < now - 24h
        Instant cutoff = Instant.now().minusSeconds(24 * 60 * 60);

        List<Gathering> stuckTiebreaks = gatheringRepository
                .findByStatusAndDeadlineBefore(GatheringStatus.TIEBREAK, cutoff);

        if (stuckTiebreaks.isEmpty()) return;

        log.info("[스케줄러] 타이브레이크 자동해소 {} 건 처리 시작", stuckTiebreaks.size());

        for (Gathering gathering : stuckTiebreaks) {
            try {
                confirmService.autoResolveTiebreak(gathering);
            } catch (Exception e) {
                log.error("[스케줄러] 타이브레이크 자동해소 실패 - shareCode={}, error={}",
                        gathering.getShareCode(), e.getMessage(), e);
            }
        }
    }
}