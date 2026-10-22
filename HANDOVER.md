# GrantFit 개발 인수인계

> 이 문서는 Claude Code 세션 간 작업을 이어가기 위한 문서이다.
> 프로젝트 설명은 README.md를 참고한다.

---

## 1. 현재 진행 상황

작성일: 2026-10-17

현재 단계:

- [x] 프로젝트 초기 설정
- [x] 지원사업 DB 스키마(DDL) 작성 및 적용
- [x] 공공 API 연동
- [x] 지원사업 DB 저장 (50건, external_id UPSERT)
- [x] 지원사업 목록 API (상세 API는 미착수)
- [ ] Vue 목록/상세 화면
- [x] Gemini API 연동
- [x] AI Structured Output
- [x] AI 결과 DB 저장 (공고 1건)
- [ ] 사용자 조건
- [ ] Rule Engine
- [ ] 적합도 화면
- [x] 예외 처리 (AI 실패 처리, MILESTONE 3)
- [ ] Docker
- [ ] README 정리
- [ ] 최종 테스트

현재 Milestone: MILESTONE 3 (README §28) 구현 완료 → 남은 것: README §12 20건 결과 채우기

현재 작업:

실패 처리(오류 분류·재시도·INTERNAL_ERROR·동시 요청 방지)와 일괄 분석 API 구현 완료.
20건 중 12건이 Gemini 무료 등급 일일 한도(20회)로 미분석 — 한도 초기화 후 재실행한다.

현재 브랜치:

```
main
```

커밋 상태:

```
cff8d3b GrantFit 초기 프로젝트 구성
fb9048c 기업마당 지원사업 API 수집 및 support_program UPSERT 구현
7818403 지원사업 목록 API 구현 (MILESTONE 1 완료)
2ee8186 공고 1건 Gemini AI 분석 구현 (MILESTONE 2 완료)
1c26bad README 체크리스트 진행 상황 반영 (MILESTONE 0~2)
+ MILESTONE 3 AI 실패 처리 커밋
모두 origin/main 푸시
```

---

## 2. 이번 세션에서 완료한 작업

### 기업마당 API 실제 응답 확인 (2026-10-17)

명세 페이지·구현 사례와 실제 응답이 달랐다. **실제 응답을 기준으로 한다.**

```
GET https://www.bizinfo.go.kr/uss/rss/bizinfoApi.do
    ?crtfcKey=...&dataType=json&pageUnit=N&pageIndex=N
```

- 성공: `{"jsonArray": [ {...}, ... ]}` — `jsonArray` 가 바로 배열 (`.item` 아님)
- 결과 없음: `{"jsonArray": []}`
- 오류: `{"reqErr": "..."}` — **HTTP 200** 으로 온다. 본문으로 판별
- `pageUnit` 은 `pageIndex` 와 함께 보내야 한다
- 필드명은 JSON용(`pblancId`, `pblancNm` …). `hashtags` 는 소문자
- 수정일 `updtPnttm` 존재 (명세엔 없었음)
- 신청기간 `reqstBeginEndDe` = `"2026-10-14 ~ 2026-10-29"`, 일부는 `예산 소진시까지`/`상시 접수`/`모집 완료시까지`
- 첨부파일 `fileNm`/`flpthNm` 은 여러 개면 `@` 로 연결, 없는 공고도 있음
- `bsnsSumryCn`(사업개요)은 HTML
- 전체 공고 수 `totCnt` ≈ 1,437 (2026-10-17)

### 필드 매핑

| API | support_program |
|---|---|
| `pblancId` | `external_id` |
| `pblancNm` | `title` |
| `jrsdInsttNm` | `organization` |
| `trgetNm` | `target` |
| `reqstBeginEndDe` | `application_start` / `application_end` (날짜 아니면 둘 다 NULL) |
| `pblancUrl` | `original_url` |
| item JSON 전체 | `raw_content` |
| — | `region`, `support_amount` = NULL (API에 없음) |

수행기관·신청방법·문의처·첨부파일·등록일·수정일은 `raw_content` 로만 보존한다. 컬럼 추가 없음.

### 구현

- `BizinfoClient` — API 호출, `reqErr` 판별, item 원본 JSON 보존
- `ApplicationPeriod` — 신청기간 파싱. 형식 불일치·시작>종료면 NULL
- `SupportProgramMapper.upsert` — `ON CONFLICT (external_id) DO UPDATE`
  - 수집 원천 컬럼만 갱신, `region`/`support_amount` 는 덮어쓰지 않음
  - `created_at` 유지, `updated_at` 은 트리거가 갱신
  - `RETURNING (xmax = 0)` 으로 신규/갱신 구분
- `POST /api/admin/bizinfo/collect?pageIndex=1&pageUnit=50` — 수집 실행 (로컬 개발용, 인증 없음)
  - 응답 `{"fetched":50,"inserted":..,"updated":..}`, API 오류 시 502

### 실제 확인

- 1건 저장 → DB 조회로 컬럼·raw_content 확인
- 같은 1건 재수집 → `updated 1`, id·created_at 유지, updated_at 갱신
- 50건 수집 → `inserted 49, updated 1`, 50행
- 50건 재수집 → `updated 50`, 행 수 변화 없음
- 인증키가 로그·테스트 리포트에 남지 않음 확인

### MILESTONE 3 — AI 실패 처리 (2026-10-22)

오류 분류와 재시도:

| 상황 | error_code | 재시도 |
|---|---|---|
| HTTP 429 | RATE_LIMIT | O (retryDelay 참고, 30초 초과면 X) |
| HTTP 5xx | API_ERROR | O |
| 그 외 4xx (키 오류 등) | API_ERROR | X |
| 응답 시간 초과 | TIMEOUT | O |
| 연결 실패 | NETWORK_ERROR (추가) | O |
| JSON 변환 실패 / MAX_TOKENS | INVALID_JSON | X |
| 검증 실패 | VALIDATION_ERROR | X |
| 그 외 예외 | INTERNAL_ERROR (추가) | X |

- 최대 3회 호출(재시도 2회), 대기 1초 → 2초. `gemini.retry.*` 설정
- INVALID_JSON/VALIDATION_ERROR 를 재시도하지 않는 이유: 재시도로 통과시키면 AI 품질 문제가 가려진다
- `attempt_count` 에 Gemini 호출 횟수 기록 (재시도 없이 성공 = 1)
- 예상 못 한 예외도 FAILED(INTERNAL_ERROR) 로 기록 → ANALYZING 잔류 없음 (ISSUE-006 해결)
- 동시 요청: `upsertAnalyzing` 이 COMPLETED/진행 중 ANALYZING 행을 덮어쓰지 않고 null 반환 → 409
  10분 이상 갱신 없는 ANALYZING 은 서버 중단 잔류로 보고 재분석
- 오류 메시지: 본문 앞부분 자르기 대신 `error.status + message + quotaId` 요약

**시간 초과는 예외 타입이 아니라 원인 체인으로 판별한다.**
Gemini 는 헤더를 먼저 보내고 본문을 늦게 보내서, 읽기 시간 초과가 본문 변환 중에 나고
Spring 이 `RestClientException`(← `SocketTimeoutException`)으로 감싼다. Mock 테스트로는 재현되지 않았고 실제 호출로 발견했다.

일괄 분석:

```
POST /api/admin/analysis/batch?limit=20
→ {total, completed, completedAfterRetry, failed, failuresByCode, skipped, notAttempted, stoppedByRateLimit, items}
```

- v1 COMPLETED 가 없는 공고를 id 순으로 순차 분석, 공고 사이 1초 간격
- RATE_LIMIT 으로 최종 실패하면 나머지는 시도하지 않고 멈춘다

실제 확인 (2026-10-22):

| 상황 | 결과 |
|---|---|
| 잘못된 키 | API_ERROR, 1회 |
| timeout 1초 | TIMEOUT ×2 재시도 → 3회차 실제 503 → API_ERROR, 3회 |
| 닫힌 포트 | NETWORK_ERROR, 3회 |
| FAILED 공고 재분석 | 같은 행 COMPLETED |
| 동시 요청 2건 | 200 + 409 |
| 20건 일괄 | 완료 8(재시도 후 2) / RATE_LIMIT 12 / VALIDATION_ERROR 0 |

누적 v1: COMPLETED 11건(그중 3건은 Gemini 503 을 재시도로 극복), VALIDATION_ERROR 0건.

### Gemini 무료 등급 한도 (중요)

```
quotaId    GenerateRequestsPerDayPerProjectPerModel-FreeTier
quotaValue 20      ← gemini-3.8-flash 하루 20회 (프로젝트·모델 단위)
초기화      태평양 자정 = 한국 16:00 (서머타임 해제 후 17:00)
```

- 무료 등급으로 계속 진행하기로 결정 (2026-10-22). 하루 20회 안에서 작업한다
- 재시도 호출도 한도에 포함된다
- 실제 Gemini 를 호출하는 `GeminiClientLiveTest` 는 `GEMINI_LIVE_TEST=true` 일 때만 실행한다
  (이전에는 `./gradlew test` 마다 2회씩 소모했다)
- 프로토타입·수동 호출도 한도를 쓴다. 꼭 필요한 호출만 한다

### MILESTONE 2 — 공고 1건 AI 분석 (2026-10-17)

```
POST /api/support-programs/{id}/analyze
→ {"analysisId","status","model","promptVersion","reused","errorCode","errorMessage","analyzedAt","result"}
```

흐름:

```
support_program.raw_content
→ AnalysisInput      README §8 항목(공고명·소관기관·사업개요·지원대상·신청기간·신청방법), HTML 정리
→ GeminiClient       generateContent + responseMimeType/responseJsonSchema, temperature 0
→ AnalysisResult     DTO. 스키마에 없는 필드가 오면 INVALID_JSON
→ AnalysisValidator  값 범위 + evidence 원문 포함 검사
→ ai_analysis        COMPLETED / FAILED(error_code)
```

결정사항:

- API: `generateContent` (interactions 아님. 상태 저장 불필요, 자료 많음)
- 모델: `gemini-3.8-flash` 기본, `GEMINI_MODEL` 로 변경 가능. DB `model` 에는 응답의 `modelVersion` 저장
- 프롬프트/스키마: `resources/ai/analysis-prompt-v1.txt`, `analysis-schema-v1.json`, `AnalysisPrompt.VERSION = "v1"`
- 지역 enum: 기업마당 표기 16개 시·도(**광주+전남 = `전남광주`**) + `전국`
- evidence 가 원문에 하나라도 없으면 분석 전체 FAILED(VALIDATION_ERROR)
- 같은 (공고, prompt_version) COMPLETED 가 있으면 Gemini 호출 없이 재사용. FAILED 면 같은 행을 비우고 재분석
- Gemini 호출 중 트랜잭션을 잡지 않는다

ai_analysis 컬럼 매핑 (README §9 → DDL):

| 응답 | 컬럼 |
|---|---|
| regions, businessAge, applicantTypes, eligibility | `eligibility_json` = `{regions, businessAge, applicantTypes, conditions}` |
| exclusions | `exclusion_json` |
| preferences | `preference_json` (추가) |
| support | `support_amount_json` |
| requiredDocuments | `required_documents_json` |
| uncertainConditions | `uncertain_conditions_json` |
| evidence (구조화 값의 근거) | `evidence_json` |
| summary | `summary` (추가) |

DDL 변경: `preference_json JSONB`, `summary TEXT` 추가. schema.sql 에 `ADD COLUMN IF NOT EXISTS` 포함, 개발 DB 적용 완료.

실제 확인:

- 프로토타입 4건(id 50, 45, 42, 11): 스키마 거부 없음, evidence 18/18 원문 일치, 4~11초
- API 1차 6.9초 COMPLETED, 2차 0.01초 reused
- temperature 0 이어도 실행마다 결과가 다를 수 있음 (requiredDocuments 3개 ↔ 1개) → MILESTONE 4 측정 대상

### 목록 API (2026-10-17)

```
GET /api/support-programs?page=0&size=20
→ {"items":[...], "page":0, "size":20, "totalCount":50}
```

- 마감되지 않은 공고만 (`application_end IS NULL OR >= 오늘`)
- 정렬: `application_end ASC NULLS LAST, id DESC`
- 오늘 = **KST 기준, Java에서 계산해 전달**. DB timezone 이 UTC 라 `current_date` 를 쓰지 않는다
- `raw_content` 는 목록 응답에서 제외
- `page < 0`, `size` 1~100 밖이면 400

실제 50건 호출로 정렬·페이징 확인. 마감된 공고 제외는 실제 데이터에 마감 공고가 없어 Mapper 테스트로만 확인.

### 결정사항

- 목록 기본 정렬은 마감일순, `announced_at` 컬럼은 추가하지 않음 (ISSUE-004 종료)
- `region` 은 이번에 보류, 목록 응답엔 null 로 노출 (ISSUE-003 유지)
- README §7 "수정일" 유지 (`updtPnttm` 존재)
- 날짜가 아닌 신청기간은 NULL, 원문은 raw_content
- `raw_content` 는 item JSON 원본 그대로
- 수집 실행 진입점은 관리용 POST API

---

## 3. 현재 작업

현재 작업:

README §12 20건 일괄 분석 중 미분석 12건 재실행 대기 (Gemini 일일 한도)

상태:

`ai_analysis` v1: COMPLETED 11건, FAILED(RATE_LIMIT) 12건.
일괄 분석 API 를 다시 호출하면 COMPLETED 가 없는 공고(FAILED 포함)만 다시 분석한다.

관련 파일:

- `src/main/java/com/grantfit/bizinfo/`
- `src/main/java/com/grantfit/program/`
- `src/main/resources/mapper/SupportProgramMapper.xml`

---

## 4. 현재 이슈

### ISSUE-001

문제:

현재 테스트가 로컬 PostgreSQL 환경에 의존한다.

또한 5432 포트를 다른 프로젝트의 PostgreSQL 컨테이너와 공유하고 있어,
해당 컨테이너가 중지되면 GrantFit도 DB에 연결할 수 없다.

확인한 내용:

- 로컬 PostgreSQL 실행 상태에서 테스트 6개 전부 통과
- GrantFit 전용 DB `grantfit` 사용
- `HealthMapperTest`, `SchemaTest`가 `@SpringBootTest`로 실제 DB에 접속

아직 하지 않은 것:

- CI 환경에서 사용할 테스트 DB 구성
- Testcontainers 적용 여부 결정
- GrantFit 전용 PostgreSQL 컨테이너 분리

다음 작업:

MVP 개발을 우선 진행하고,
CI 또는 테스트 환경이 필요해지는 시점에 테스트 DB 구성을 결정한다.
전용 컨테이너 분리는 Docker 구성 단계에서 진행한다. (README §39)

---

### ISSUE-002

문제:

`src/main/resources/db/schema.sql`은 애플리케이션 기동 시 자동 실행되지 않는다.
새 환경에서는 수동으로 적용해야 한다.

적용 방법:

```
PG_CONTAINER=$(docker ps --filter publish=5432 --format '{{.Names}}' | head -1)

docker exec -i "$PG_CONTAINER" \
  psql -U grantfit -d grantfit -v ON_ERROR_STOP=1 \
  < src/main/resources/db/schema.sql
```

적용을 잊은 경우 `SchemaTest`가 실패하면서 드러난다.

다음 작업:

마이그레이션 도구(Flyway 등) 도입은 필요성이 확인될 때 판단한다.
현재는 새 기술을 추가하지 않는다.

---

### ISSUE-003

문제:

`support_program.region`, `support_amount` 를 채우는 단계가 없다.
API에 해당 필드가 없고, README §3상 AI 추출 대상이다.
그런데 `region` 컬럼과 인덱스는 목록 필터용으로 존재한다.

확인한 내용 (50건 hashtags):

- 시·도 1개: 30건 / 15개: 17건 / 0개: 3건
- hashtags 는 "지원 조건 지역"과 "주최 지역"을 구분하지 못한다

선택지:

1. AI 결과를 `support_program.region` 에 반영
2. 목록 조회 시 `ai_analysis` 조인
3. 수집 시 hashtags/공고명 접두어로 표시용 지역만 채움 (적합도에 사용 안 함)

`support_amount` 는 `ai_analysis.support_amount_json` 과 역할이 겹친다.

다음 작업:

목록 API 단계에서 보류로 결정했다. 목록 응답의 region 은 null.
MILESTONE 2 AI 분석 결과 구조를 본 뒤 1/2/3안 중 결정한다.
지역 필터는 MILESTONE 8 UI 범위다.

---

### ISSUE-004 (종료)

`announced_at` 컬럼은 추가하지 않는다. 목록은 마감일순으로 정렬한다.
공고 등록일 정렬이 필요해지면 raw_content 의 `creatPnttm` 으로 백필한다.

---

### ISSUE-005

문제:

숫자가 아닌 page/size(`page=abc`)는 400 이지만 메시지가 `For input string: "abc"` 로 노출된다.

다음 작업:

전역 예외 처리(MILESTONE 3 실패 처리)를 만들 때 함께 정리한다.

---

### ISSUE-006 (해결)

MILESTONE 3 에서 INTERNAL_ERROR 기록과 동시 요청 409 로 해결했다.

---

### ISSUE-007

문제:

Gemini 무료 등급 일일 20회 한도. MILESTONE 4(20~30건 Golden Dataset, 프롬프트 버전 비교)는
분석·재분석 호출이 많아 하루에 끝나지 않는다.

결정:

무료 등급 유지. 하루 20회 단위로 나눠 진행한다.

---

## 5. 다음 세션 작업

1. 한도 초기화 후 `POST /api/admin/analysis/batch?limit=20` 재실행 → README §12 20건 결과 기록, MILESTONE 3 완료
2. MILESTONE 4 — AI 정확도 측정: Golden Dataset 대상 공고 선정과 기준값 작성 방식 결정 (사람이 직접 작성)
3. ISSUE-005 (숫자 아닌 page 파라미터 메시지) 정리

---

## 6. 최근 변경사항

### 2026-10-22 (MILESTONE 3)

- 오류 분류·재시도·attempt_count·INTERNAL_ERROR·동시 요청 409 구현
- error_code 에 NETWORK_ERROR, INTERNAL_ERROR 추가 (DDL CHECK 변경)
- 일괄 분석 API `POST /api/admin/analysis/batch`
- 실패 상황 실제 재현, 20건 일괄 분석 1차(8건 완료, 12건 일일 한도)
- Gemini 실제 호출 테스트를 GEMINI_LIVE_TEST=true 일 때만 실행하도록 변경
- 테스트 86개 (실행 84, Gemini live 2 skip)

### 2026-10-17 (MILESTONE 2)

- Gemini API 키 발급, generateContent + Structured Output 실제 호출 확인
- `POST /api/support-programs/{id}/analyze` 구현 → MILESTONE 2 완료
- ai_analysis `preference_json`, `summary` 컬럼 추가
- 테스트 60개 전부 통과

### 2026-10-17

- 기업마당 API 인증키 발급, `.env` 의 `BIZINFO_API_KEY` 에 저장
- 실제 응답 확인 (명세와 다른 부분 §2 참고)
- 응답 → DTO → support_program UPSERT 구현
- 수집 API `POST /api/admin/bizinfo/collect` 추가
- 실제 공고 50건 저장, 재수집 시 갱신 확인
- 테스트 26개 전부 통과
- 목록 API `GET /api/support-programs` 추가 → MILESTONE 1 완료
- 테스트 32개 전부 통과

### 2026-10-01

- Spring Boot 프로젝트 초기 구성 완료
- Java 17 toolchain 적용
- Spring Boot 3.5.16 적용
- MyBatis 3.0.5 적용
- Gradle Wrapper 8.14.3 추가
- PostgreSQL GrantFit 전용 DB/role 생성
- `.env` 기반 환경변수 설정
- Lombok 의존성 추가
- 테스트 datasource 설정 충돌 문제 해결
- 초기 커밋 `6f1dfd1` 생성
- README.md 전면 개편 (Milestone 기반 40개 섹션)
- `support_program`, `ai_analysis` DDL 작성 및 적용
- `SchemaTest` 추가, 테스트 6개 전부 통과
- 아직 공공 API 및 Gemini API 연동은 시작하지 않음

### 2026-09-30

- GrantFit 프로젝트 생성
- 프로젝트 기본 기획 확정
- MVP 범위 확정
- 기술 스택 확정
- README.md 작성
- HANDOVER.md 작성

---

## 7. 최근 변경 파일

중요 파일만 기록한다.

MILESTONE 1 수집 커밋:

```
src/main/java/com/grantfit/bizinfo/BizinfoClient.java
src/main/java/com/grantfit/bizinfo/BizinfoItem.java
src/main/java/com/grantfit/bizinfo/ApplicationPeriod.java
src/main/java/com/grantfit/bizinfo/BizinfoApiException.java
src/main/java/com/grantfit/bizinfo/BizinfoCollectService.java
src/main/java/com/grantfit/bizinfo/BizinfoCollectController.java
src/main/java/com/grantfit/bizinfo/CollectResult.java
src/main/java/com/grantfit/program/SupportProgram.java
src/main/java/com/grantfit/program/SupportProgramMapper.java
src/main/resources/mapper/SupportProgramMapper.xml
src/main/resources/application.yml      (bizinfo 설정)
.env.example                            (BIZINFO_API_KEY)
src/test/java/com/grantfit/bizinfo/
src/test/java/com/grantfit/program/
src/test/resources/bizinfo/response-sample.json
```

MILESTONE 3 AI 실패 처리 커밋:

```
src/main/java/com/grantfit/analysis/AnalysisRetryPolicy.java      (신규)
src/main/java/com/grantfit/analysis/AnalysisBatchService.java     (신규)
src/main/java/com/grantfit/analysis/AnalysisBatchController.java  (신규)
src/main/java/com/grantfit/analysis/AnalysisInProgressException.java (신규)
src/main/java/com/grantfit/analysis/GeminiClient.java             (오류 분류, errorSummary, retryDelay)
src/main/java/com/grantfit/analysis/AnalysisService.java          (재시도, INTERNAL_ERROR, 409)
src/main/resources/mapper/AiAnalysisMapper.xml                    (조건부 upsert, attempt_count, 일괄 대상 조회)
src/main/resources/db/schema.sql                                  (error_code 2개, attempt_count)
src/test/java/com/grantfit/analysis/                              (RetryPolicy, ClientError, Batch 테스트)
```

MILESTONE 2 AI 분석 커밋:

```
src/main/java/com/grantfit/analysis/        (AnalysisService, GeminiClient, AnalysisValidator,
                                             AnalysisInput, AnalysisResult, AnalysisPrompt,
                                             AiAnalysis, AiAnalysisMapper, AnalysisController 등)
src/main/resources/ai/analysis-prompt-v1.txt
src/main/resources/ai/analysis-schema-v1.json
src/main/resources/mapper/AiAnalysisMapper.xml
src/main/resources/db/schema.sql            (preference_json, summary)
src/main/resources/application.yml          (gemini 설정)
src/test/java/com/grantfit/analysis/
src/test/resources/analysis/                (실제 Gemini 응답·공고 샘플)
```

MILESTONE 1 목록 API 커밋:

```
src/main/java/com/grantfit/program/SupportProgramController.java
src/main/java/com/grantfit/program/SupportProgramService.java
src/main/java/com/grantfit/program/SupportProgramSummary.java
src/main/java/com/grantfit/program/SupportProgramPage.java
src/main/java/com/grantfit/program/SupportProgramMapper.java   (selectOpenList, countOpen)
src/main/resources/mapper/SupportProgramMapper.xml
src/test/java/com/grantfit/program/
```

---

## 8. 테스트 상태

현재 테스트:

- [x] Spring Boot 기본 테스트
- [x] DB 연결 확인
- [x] MyBatis XML 매핑 확인
- [x] 스키마 존재 확인
- [x] 공공 API 호출 확인
- [x] DB 저장 확인
- [x] 중복 공고 UPSERT 확인
- [x] 목록 API 확인
- [ ] 상세 API 확인
- [x] Gemini API 호출 확인
- [x] AI JSON Validation 확인
- [x] AI 결과 DB 저장 확인
- [ ] Rule Engine 테스트
- [ ] Vue 화면 확인
- [ ] Docker 실행 확인

테스트 결과 (`./gradlew test`, 2026-10-17):

```
GrantFitApplicationTests   tests=1
SchemaTest                 tests=3
HealthMapperTest           tests=2
ApplicationPeriodTest      tests=9
BizinfoClientParseTest     tests=7   (샘플 JSON, 네트워크 없음)
BizinfoClientLiveTest      tests=1   (실제 API, 키 없으면 skip)
SupportProgramMapperTest   tests=5   (실제 DB, 롤백)
SupportProgramServiceTest  tests=2   (Mock, KST 기준일)
SupportProgramControllerTest tests=2 (MockMvc, 실제 DB 조회만)
AnalysisInputTest          tests=3
AnalysisValidatorTest      tests=7
GeminiClientParseTest      tests=5   (실제 응답 샘플, 네트워크 없음)
AnalysisServiceTest        tests=12  (Mock — 재사용/성공/INVALID_JSON/VALIDATION_ERROR/재시도/INTERNAL_ERROR/409)
AiAnalysisMapperTest       tests=9   (실제 DB, 롤백)
GeminiClientLiveTest       tests=2   (실제 Gemini, GEMINI_LIVE_TEST=true 일 때만)
GeminiClientErrorTest      tests=10  (MockRestServiceServer — 429/5xx/4xx/timeout/network)
AnalysisRetryPolicyTest    tests=5
AnalysisBatchServiceTest   tests=1
────────────────────────────────────────────────────────
합계                       tests=86  failures=0  errors=0  skipped=2 (Gemini live)

Gemini live 테스트 실행:
GEMINI_LIVE_TEST=true ./gradlew test --tests '*GeminiClientLiveTest'   (호출 2회 소모)
```

테스트 롤백으로 identity 값이 소비되어 `support_program.id` 는 1부터 시작하지 않는다.

PostgreSQL 연결 환경에서 테스트가 정상 동작하는 것을 확인했다.

DDL 제약은 psql에서 직접 검증했다.
(external_id UNIQUE, status/error_code CHECK, prompt_version UNIQUE,
FK CASCADE, updated_at 트리거, JSONB 경로 조회)

---

## 9. 실행 상태

Backend:

```
./gradlew bootRun
→ http://localhost:8080

GET /api/health      → {"status":"UP"}
GET /api/health/db   → {"connected":true,"database":"grantfit"}
POST /api/admin/bizinfo/collect?pageIndex=1&pageUnit=50
                     → {"fetched":50,"inserted":..,"updated":..}
GET  /api/support-programs?page=0&size=20
                     → {"items":[...],"page":0,"size":20,"totalCount":50}
POST /api/support-programs/{id}/analyze
                     → {"status":"COMPLETED","reused":false,"attemptCount":1,"result":{...}}
                       분석 중이면 409
POST /api/admin/analysis/batch?limit=20
                     → {"total":20,"completed":..,"failed":..,"failuresByCode":{..},"stoppedByRateLimit":..}
```

Frontend:

아직 구현하지 않음.

Database:

PostgreSQL 연결 설정 완료.
GrantFit 전용 `grantfit` DB와 role 생성 완료.
`support_program`, `ai_analysis` 테이블 생성 완료.
`support_program` 에 실제 공고 50건 저장됨.

Docker:

GrantFit 전용 Docker 설정은 아직 하지 않음.

현재는 5432 포트에서 실행 중인 다른 프로젝트의 PostgreSQL 컨테이너를 개발 환경에서 재사용한다.

---

## 10. 환경변수

실제 API Key나 비밀번호를 이 파일에 작성하지 않는다.

필요한 환경변수:

```
DB_URL
DB_USERNAME
DB_PASSWORD
BIZINFO_API_KEY  (기업마당, 미설정 시 수집 API 오류 / LiveTest skip)
GEMINI_API_KEY   (Gemini, 미설정 시 분석 FAILED(API_ERROR) / LiveTest skip)
GEMINI_MODEL     (선택, 기본 gemini-3.8-flash)
```

환경변수의 실제 값은 `.env` 또는 로컬 환경에서 관리한다.

`.env`는 Git에 커밋하지 않는다.

`.env.example`에는 필요한 환경변수의 이름과 형식만 작성한다.

새 환경에서는 다음과 같이 시작한다.

```
cp .env.example .env
```

---

## 11. 주요 결정사항

### 기술 스택

현재 사용 기술 (확정된 버전):

```
Java 17 toolchain
Spring Boot 3.5.16
MyBatis 3.0.5
Gradle 8.14.3
PostgreSQL 17
Lombok                 (compileOnly)
Vue                    (미착수)
Gemini API             gemini-3.8-flash, generateContent
Docker                 (미착수)
```

Spring Boot 4.x 대신 3.5.x를 선택했다.
자료가 많고, MyBatis 3.0.5가 Boot 3.5.0을 타깃으로 릴리스되어 조합이 검증되어 있다.

### AI의 역할

AI는 지원사업 공고의 비정형 내용을 구조화한다.

AI에게 최종 적합도 점수나
최종 지원 가능 여부를 직접 판단시키지 않는다.

AI는 다음과 같은 정보를 추출한다.

```
지원 조건
제외 조건
우대 조건
지원 금액
필요 서류
불확실한 조건
근거 문장(evidence)
```

### AI 결과 검증

Gemini 응답은 Structured Output / JSON Schema를 사용하여
정해진 구조로 받는다.

AI 응답을 그대로 신뢰하지 않고
서버에서 DTO 및 Validation을 통해 검증한 후 저장한다.

### 적합도 계산

Java Rule Engine에서 계산한다.

AI가 최종 적합도를 직접 계산하지 않는다.

### 분석 결과 재사용

이미 분석된 공고가 있고
현재 prompt version과 동일하다면 기존 분석 결과를 재사용한다.

필요한 경우에만 재분석한다.

AI 분석 결과에는 최소한 다음 정보를 저장한다.

```
model
prompt_version
status
분석 결과
created_at
updated_at
```

### DDL 설계

`ai_analysis` UNIQUE 제약:

```
UNIQUE (support_program_id, prompt_version)
```

README §16의 재사용 조건(공고 + prompt_version)과 일치시켰다.
버전이 다르면 별도 행이 되어 README §15의 프롬프트 버전 비교가 가능하다.

README §27 목록에 없는 컬럼 3개를 추가했다.

```
error_code     -- README §12 실패 원인
error_message  -- README §12
analyzed_at    -- README §15
```

MILESTONE 3(AI 실패 처리)에 필요한 최소 컬럼이며,
지금 넣어 두면 나중에 ALTER TABLE이 필요 없다.

`support_program.support_amount`는 `TEXT`로 두었다.
공공 API가 금액을 자연어로 내려주는 경우가 있어 원문 표기를 보관하고,
구조화된 금액은 `ai_analysis.support_amount_json`이 담당한다.

`status` CHECK 값은 README §28 MILESTONE 3의
`WAITING / ANALYZING / COMPLETED / FAILED`를 기준으로 했다.
README §22의 화면 예시에는 `SUCCESS`로 적혀 있으나 정식 상태 목록을 따랐다.

### MVP 기술 범위

현재 사용하지 않는 기술:

```
Redis
Kafka
Elasticsearch
RAG
Vector DB
Embedding
로컬 LLM
```

필요성이 확인되기 전까지 추가하지 않는다.

---

## 12. 개발 목표

최종적으로 다음 흐름이 정상적으로 동작해야 한다.

```
공공 API
→ 지원사업 수집
→ PostgreSQL 저장
→ 공고 조회
→ Gemini 분석
→ Structured JSON
→ 서버 Validation
→ AI 결과 저장
→ 사용자 조건 입력
→ Java Rule Engine
→ 적합도 계산
→ 화면 표시
```

핵심 원칙:

AI는 비정형 공고 내용을 구조화하고,
최종적인 조건 비교와 적합도 계산은 Java에서 담당한다.

---

## 13. 세션 종료 시 업데이트 규칙

세션 종료 전에 반드시 이 문서를 업데이트한다.

확인할 항목:

- [ ] 완료된 작업 체크
- [ ] 현재 작업 작성
- [ ] 현재 이슈 작성
- [ ] 다음 세션 작업 작성
- [ ] 최근 변경사항 추가
- [ ] 테스트 상태 업데이트
- [ ] 현재 브랜치 및 커밋 확인

긴 설명은 작성하지 않는다.

다음 세션에서 바로 작업을 시작할 수 있을 정도로만 기록한다.

---

## 14. Claude Code 세션 시작 규칙

새로운 Claude Code 세션에서는 코드를 바로 수정하지 않는다.

먼저 다음 파일과 상태를 확인한다.

1. HANDOVER.md
2. README.md
3. git status
4. 현재 브랜치
5. 현재 작업과 관련된 소스 코드

확인 후 다음 내용을 간단히 정리한다.

- 현재까지 완료된 작업
- 현재 작업
- 현재 이슈
- 다음 작업

그 후 사용자가 요청한 작업을 진행한다.

이전 세션의 내용을 추측하지 않는다.

HANDOVER.md의 내용과 실제 코드가 다르면
실제 코드와 Git 상태를 우선 확인한다.

---

## 15. 주의사항

- API Key를 커밋하지 않는다.
- 기업마당 API 오류는 HTTP 200으로 온다. `reqErr` 로 판별한다.
- Gemini 무료 등급은 하루 20회. 실제 호출 테스트·프로토타입·일괄 분석 모두 한도에 포함된다.
- 기업마당 요청 URL에 인증키가 들어간다. 예외 메시지·로그에 URL을 남기지 않는다.
- 비밀번호를 문서에 기록하지 않는다.
- 불필요한 라이브러리를 추가하지 않는다.
- 기존 코드를 확인하지 않고 구조를 변경하지 않는다.
- 한 번에 전체 프로젝트를 다시 작성하지 않는다.
- 문제가 발생했을 때 기존 구조를 무조건 갈아엎지 않는다.
- 새로운 기술을 추가하기 전에 필요한 이유를 확인한다.
- MVP 범위를 임의로 확대하지 않는다.
- 현재 Milestone의 완료 조건을 확인한 후 다음 기능으로 넘어간다.
- AI가 최종 적합 여부를 직접 판단하도록 구현하지 않는다.
- AI 응답은 반드시 서버 측 Validation을 거친 후 사용한다.
- AI 분석 결과에는 model과 prompt_version을 기록한다.
- 실제 API 연동을 Mock으로 대체하여 구현 완료로 처리하지 않는다.
- 작업하지 않은 기능을 완료된 것으로 기록하지 않는다.
- `schema.sql`은 자동 실행되지 않는다. 새 환경에서는 수동 적용한다.
- 5432 포트의 PostgreSQL 컨테이너가 꺼져 있으면 테스트가 실패한다.
