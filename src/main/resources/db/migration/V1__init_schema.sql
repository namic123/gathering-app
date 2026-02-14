-- ============================================================
-- V1__init_schema.sql
-- 모임 결정 앱의 초기 데이터베이스 스키마
-- ============================================================

-- ============================================================
-- gathering: 모임 (핵심 테이블)
-- 주최자가 생성하는 모임의 모든 정보를 담는다.
-- 다른 모든 테이블이 이 테이블을 참조한다.
-- ============================================================
CREATE TABLE gathering (
                           id              BIGSERIAL PRIMARY KEY,             -- 자동 증가 PK
                           share_code      VARCHAR(8)   NOT NULL,             -- 초대 링크용 고유 코드 (예: "aB3kX7")
                           title           VARCHAR(100) NOT NULL,             -- 모임 제목
                           host_name       VARCHAR(30)  NOT NULL,             -- 주최자 닉네임
                           description     VARCHAR(500),                      -- 모임 설명 (선택 입력)
                           type            VARCHAR(20)  NOT NULL,             -- 모임 타입: TIME_ONLY / PLACE_ONLY / BOTH
                           admin_token_hash VARCHAR(64) NOT NULL,             -- 주최자 관리 토큰의 SHA-256 해시
    -- (원본은 생성 시 1회만 반환, DB에는 해시만 저장)
                           deadline        TIMESTAMPTZ  NOT NULL,             -- 투표 마감 시각 (타임존 포함)
                           status          VARCHAR(20)  NOT NULL DEFAULT 'VOTING', -- 모임 상태: VOTING → TIEBREAK → CONFIRMED
                           created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                           updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- share_code로 모임을 조회하는 것이 가장 빈번한 쿼리 (참여자 링크 접속)
CREATE UNIQUE INDEX idx_gathering_share_code ON gathering(share_code);

-- 스케줄러가 "마감 지난 VOTING 상태 모임"을 찾을 때 사용
CREATE INDEX idx_gathering_status_deadline ON gathering(status, deadline);


-- ============================================================
-- time_candidate: 시간 후보
-- 하나의 모임에 여러 시간 후보가 등록된다. (1:N)
-- ============================================================
CREATE TABLE time_candidate (
                                id              BIGSERIAL PRIMARY KEY,
                                gathering_id    BIGINT  NOT NULL REFERENCES gathering(id) ON DELETE CASCADE,
    -- 모임 삭제 시 후보도 함께 삭제
                                candidate_date  DATE    NOT NULL,                  -- 후보 날짜 (예: 2025-02-21)
                                start_time      TIME    NOT NULL,                  -- 시작 시간 (예: 18:00)
                                end_time        TIME,                              -- 종료 시간 (선택, 예: 20:00)
                                display_order   INTEGER NOT NULL DEFAULT 0,        -- 등록 순서 (동점 시 선등록 후보 우선 확정용)
                                created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- ============================================================
-- place_candidate: 장소 후보
-- 하나의 모임에 여러 장소 후보가 등록된다. (1:N)
-- ============================================================
CREATE TABLE place_candidate (
                                 id              BIGSERIAL PRIMARY KEY,
                                 gathering_id    BIGINT       NOT NULL REFERENCES gathering(id) ON DELETE CASCADE,
                                 name            VARCHAR(100) NOT NULL,             -- 장소명 (예: "강남 고기집")
                                 map_link        VARCHAR(500),                      -- 지도 URL (카카오/네이버/구글맵)
                                 memo            VARCHAR(200),                      -- 간단 메모 (예: "삼겹살 맛집")
                                 est_cost        INTEGER,                           -- 예상 비용 (원, Should 기능)
                                 travel_min      INTEGER,                           -- 예상 이동시간 (분, Should 기능)
                                 mood_tags       VARCHAR(200),                      -- 분위기 태그 (쉼표 구분, Should 기능)
                                 display_order   INTEGER NOT NULL DEFAULT 0,        -- 등록 순서 (동점 처리용)
                                 created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- ============================================================
-- participant: 참여자
-- 링크를 통해 들어온 투표 참여자.
-- 로그인 없이 닉네임 + 세션토큰으로 본인 식별.
-- ============================================================
CREATE TABLE participant (
                             id                   BIGSERIAL PRIMARY KEY,
                             gathering_id         BIGINT      NOT NULL REFERENCES gathering(id) ON DELETE CASCADE,
                             name                 VARCHAR(30) NOT NULL,          -- 참여자 닉네임
                             session_token_hash   VARCHAR(64) NOT NULL,          -- 세션 토큰의 SHA-256 해시
    -- (투표 변경 시 본인 확인용)
                             created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 같은 모임 내에서 닉네임 중복 방지
    -- "김민수"가 이미 투표했으면 다른 사람이 "김민수"로 참여 불가
                             UNIQUE(gathering_id, name)
);


-- ============================================================
-- vote: 투표
-- 참여자가 후보에 투표한 기록.
-- candidate_type으로 시간/장소 투표를 구분한다.
-- candidate_id는 time_candidate 또는 place_candidate의 id를 참조.
-- (FK 없이 candidate_type으로 논리적 구분 — 다형성 패턴)
-- ============================================================
CREATE TABLE vote (
                      id              BIGSERIAL PRIMARY KEY,
                      gathering_id    BIGINT      NOT NULL REFERENCES gathering(id) ON DELETE CASCADE,
    -- 조회 편의용 (투표 집계 시 gathering 기준으로 조회)
                      participant_id  BIGINT      NOT NULL REFERENCES participant(id) ON DELETE CASCADE,
                      candidate_id    BIGINT      NOT NULL,              -- time_candidate.id 또는 place_candidate.id
                      candidate_type  VARCHAR(10) NOT NULL,              -- 'TIME' 또는 'PLACE'
                      created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 한 참여자가 같은 후보에 중복 투표하는 것을 DB 레벨에서 차단
                      UNIQUE(participant_id, candidate_id, candidate_type)
);

-- 투표 집계 쿼리 최적화: "이 모임의 시간 투표" 또는 "이 모임의 장소 투표"
CREATE INDEX idx_vote_gathering_type ON vote(gathering_id, candidate_type);


-- ============================================================
-- confirmed_result: 확정 결과
-- 마감 후 자동 확정 또는 주최자가 선택한 최종 결과.
-- 모임당 최대 1건만 존재 (UNIQUE).
-- ============================================================
CREATE TABLE confirmed_result (
                                  id                  BIGSERIAL PRIMARY KEY,
                                  gathering_id        BIGINT      NOT NULL UNIQUE REFERENCES gathering(id) ON DELETE CASCADE,
    -- UNIQUE: 모임당 확정 결과는 1건만
                                  time_candidate_id   BIGINT      REFERENCES time_candidate(id),  -- 확정된 시간 (PLACE_ONLY면 NULL)
                                  place_candidate_id  BIGINT      REFERENCES place_candidate(id), -- 확정된 장소 (TIME_ONLY면 NULL)
                                  confirmed_at        TIMESTAMPTZ NOT NULL,           -- 확정 시각
                                  confirmed_by        VARCHAR(10) NOT NULL            -- 'AUTO' (자동확정) 또는 'HOST' (주최자 선택)
);