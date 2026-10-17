-- GrantFit 스키마
-- 대상: PostgreSQL 17
-- 기준: README.md §26 support_program, §27 ai_analysis
--
-- 이 파일은 애플리케이션 기동 시 자동 실행되지 않는다.
-- 수동으로 psql 에 적용한다. (README §28 MILESTONE 0 범위)


-- ============================================================
-- support_program : 외부 공고 원문과 기본 정보
-- ============================================================
CREATE TABLE IF NOT EXISTS support_program (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 외부 API 의 공고 식별자. 중복 수집 방지 기준 (README §26)
    external_id       VARCHAR(100) NOT NULL,

    title             TEXT         NOT NULL,
    organization      TEXT,
    region            TEXT,
    target            TEXT,

    application_start DATE,
    application_end   DATE,

    -- 공공 API 가 금액을 자연어로 내려주는 경우가 있어 원문 문자열로 보관한다.
    -- 구조화된 금액은 ai_analysis.support_amount_json 이 담당한다. (README §27)
    support_amount    TEXT,

    original_url      TEXT,

    -- 수집 시점의 원문. AI 분석 입력으로 사용한다. (README §8)
    raw_content       TEXT,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_support_program_external_id UNIQUE (external_id),
    CONSTRAINT ck_support_program_period      CHECK (
        application_start IS NULL
        OR application_end IS NULL
        OR application_start <= application_end
    )
);

COMMENT ON TABLE  support_program                   IS '외부 공공 API 에서 수집한 지원사업 공고';
COMMENT ON COLUMN support_program.external_id       IS '외부 API 공고 식별자. 중복 수집 방지 기준';
COMMENT ON COLUMN support_program.support_amount    IS '공고 원문의 지원금 표기. 구조화 값은 ai_analysis.support_amount_json';
COMMENT ON COLUMN support_program.raw_content       IS 'AI 분석 입력으로 사용할 공고 원문';

-- 목록 화면의 기본 정렬/필터 (README §20.2 : 마감일, 지역)
CREATE INDEX IF NOT EXISTS ix_support_program_application_end
    ON support_program (application_end DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS ix_support_program_region
    ON support_program (region);
CREATE INDEX IF NOT EXISTS ix_support_program_created_at
    ON support_program (created_at DESC);


-- ============================================================
-- ai_analysis : Gemini Structured Output 검증 결과
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_analysis (
    id                        BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    support_program_id        BIGINT       NOT NULL,

    model                     VARCHAR(100) NOT NULL,
    prompt_version            VARCHAR(20)  NOT NULL,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'WAITING',

    -- 서버 Validation 을 통과한 구조화 결과 (README §9, §27)
    eligibility_json          JSONB,
    exclusion_json            JSONB,
    required_documents_json   JSONB,
    support_amount_json       JSONB,
    uncertain_conditions_json JSONB,
    evidence_json             JSONB,
    -- README §9 의 preferences, summary (MILESTONE 2 에서 추가)
    preference_json           JSONB,
    summary                   TEXT,

    -- 실패 원인 구분 (README §12, MILESTONE 3)
    error_code                VARCHAR(30),
    error_message             TEXT,

    -- 분석이 실제로 완료된 시각 (README §15)
    analyzed_at               TIMESTAMPTZ,

    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_ai_analysis_support_program
        FOREIGN KEY (support_program_id) REFERENCES support_program (id)
        ON DELETE CASCADE,

    -- 공고 + prompt_version 으로 기존 결과를 재사용한다. (README §16)
    -- 버전이 다르면 별도 행이 되어 프롬프트 변경 전후 비교가 가능하다. (README §15)
    CONSTRAINT uq_ai_analysis_program_prompt
        UNIQUE (support_program_id, prompt_version),

    CONSTRAINT ck_ai_analysis_status CHECK (
        status IN ('WAITING', 'ANALYZING', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT ck_ai_analysis_error_code CHECK (
        error_code IS NULL
        OR error_code IN ('API_ERROR', 'TIMEOUT', 'RATE_LIMIT', 'INVALID_JSON', 'VALIDATION_ERROR')
    ),
    -- FAILED 면 원인을 반드시 남긴다.
    CONSTRAINT ck_ai_analysis_failed_has_error CHECK (
        status <> 'FAILED' OR error_code IS NOT NULL
    )
);

-- MILESTONE 2 이전에 테이블을 만든 DB 용
ALTER TABLE ai_analysis ADD COLUMN IF NOT EXISTS preference_json JSONB;
ALTER TABLE ai_analysis ADD COLUMN IF NOT EXISTS summary         TEXT;

COMMENT ON TABLE  ai_analysis                IS 'AI 가 구조화하고 서버가 검증한 공고 분석 결과';
COMMENT ON COLUMN ai_analysis.status         IS 'WAITING / ANALYZING / COMPLETED / FAILED (README §28 MILESTONE 3)';
COMMENT ON COLUMN ai_analysis.error_code     IS 'API_ERROR / TIMEOUT / RATE_LIMIT / INVALID_JSON / VALIDATION_ERROR (README §12)';
COMMENT ON COLUMN ai_analysis.prompt_version IS '프롬프트 버전. 재사용 및 버전 비교 기준 (README §15, §16)';
COMMENT ON COLUMN ai_analysis.analyzed_at    IS '분석이 실제로 완료된 시각';
COMMENT ON COLUMN ai_analysis.eligibility_json IS '{regions, businessAge, applicantTypes, conditions[]}';
COMMENT ON COLUMN ai_analysis.evidence_json  IS '구조화 값(regions, businessAge, applicantTypes, support)의 근거 문장';
COMMENT ON COLUMN ai_analysis.preference_json IS '우대 조건 [{condition, evidence}]';

-- 공고 상세에서 해당 공고의 분석 이력 조회 (README §22)
CREATE INDEX IF NOT EXISTS ix_ai_analysis_support_program_id
    ON ai_analysis (support_program_id);
-- 상태별 조회 / 재시도 대상 탐색 (README MILESTONE 3)
CREATE INDEX IF NOT EXISTS ix_ai_analysis_status
    ON ai_analysis (status);


-- ============================================================
-- updated_at 자동 갱신
-- ============================================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_support_program_updated_at ON support_program;
CREATE TRIGGER trg_support_program_updated_at
    BEFORE UPDATE ON support_program
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_ai_analysis_updated_at ON ai_analysis;
CREATE TRIGGER trg_ai_analysis_updated_at
    BEFORE UPDATE ON ai_analysis
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
