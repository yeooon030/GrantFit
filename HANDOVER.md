# GrantFit 개발 인수인계

> 이 문서는 Claude Code 세션 간 작업을 이어가기 위한 문서이다.
> 프로젝트 설명은 README.md를 참고한다.

---

## 1. 현재 진행 상황

작성일: 2026-10-01

현재 단계:

- [x] 프로젝트 초기 설정
- [x] 지원사업 DB 스키마(DDL) 작성 및 적용
- [ ] 공공 API 연동
- [ ] 지원사업 DB 저장
- [ ] 지원사업 조회 API
- [ ] Vue 목록/상세 화면
- [ ] Gemini API 연동
- [ ] AI Structured Output
- [ ] AI 결과 DB 저장
- [ ] 사용자 조건
- [ ] Rule Engine
- [ ] 적합도 화면
- [ ] 예외 처리
- [ ] Docker
- [ ] README 정리
- [ ] 최종 테스트

현재 Milestone: MILESTONE 0 (README §28) — Vue를 제외하고 완료

현재 작업:

DDL 작성 및 테이블 생성 완료.
다음 단계는 기업마당 공공 API 연동이다.

현재 브랜치:

```
main
```

커밋 상태:

```
초기 커밋 1개 (root commit) — GrantFit 초기 프로젝트 구성
워킹트리 clean
```

---

## 2. 이번 세션에서 완료한 작업

### 프로젝트 초기 구성

- Spring Boot 프로젝트 생성
- Java 17 toolchain 설정 (README §24와 일치)
- Spring Boot 3.5.16 사용
- MyBatis 3.0.5 설정
- Gradle Wrapper 8.14.3 추가
- Lombok 의존성 추가
- PostgreSQL 연결 설정
- `.env` 기반 환경변수 설정
- 기본 Spring Boot 테스트 통과

### PostgreSQL

로컬에 PostgreSQL이 설치되어 있지 않아,
5432 포트에서 이미 실행 중이던 다른 프로젝트의 PostgreSQL 컨테이너를 재사용하여
GrantFit 전용 DB와 role을 생성했다.

- Database: `grantfit`
- Role: `grantfit`
- PostgreSQL: postgres:17-alpine (컨테이너는 다른 프로젝트 소유)
- 포트: 5432

GrantFit은 해당 컨테이너 안에서 별도 DB를 사용한다.
기존 DB의 데이터는 읽거나 변경하지 않았다.

컨테이너 이름은 환경에 따라 다르므로 포트로 찾는다.

```
PG_CONTAINER=$(docker ps --filter publish=5432 --format '{{.Names}}' | head -1)
```

### 환경변수

`application.yml`에서 다음 방식으로 `.env`를 읽는다.

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

`optional:`이므로 `.env`가 없으면 OS 환경변수를 사용한다.

실제 비밀번호 및 API Key는 `.env`에 저장하며 Git에 커밋하지 않는다.

### DDL

`src/main/resources/db/schema.sql`을 작성하고 `grantfit` DB에 적용했다.

생성한 테이블:

```
support_program
ai_analysis
```

### 초기 커밋

프로젝트 초기 설정 + DDL + 스키마 테스트를 하나의 root commit 으로 만들었다.

`.env`, `build/`, `.gradle/`, `.claude/settings.local.json`이
제외된 것을 확인한 후 커밋했다.

### README 개편

README.md를 Milestone 기반 구조(40개 섹션)로 전면 교체했다.

### 테스트

초기 테스트 과정에서
`src/test/resources/application.yml`이 datasource 설정을 가리는 문제가 발생했다.

해당 파일을 삭제한 후 테스트가 정상 통과했다.

---

## 3. 현재 작업

현재 작업:

없음

상태:

Spring Boot, PostgreSQL, MyBatis 기본 설정 완료.
`support_program`, `ai_analysis` 테이블 생성 및 검증 완료.
공공 API 연동은 아직 시작하지 않았다.

관련 파일:

- `src/main/resources/db/schema.sql`
- `src/test/java/com/grantfit/db/SchemaTest.java`

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

## 5. 다음 세션 작업

다음 세션에서는 아래 작업부터 진행한다.

1. 기업마당 지원사업 OpenAPI 스펙 확인 및 API Key 발급
2. `support_program` DTO/Mapper 작성 후 공고 1건 저장 확인
3. 중복 공고 처리(`external_id` UPSERT) 후 50건으로 확장

이후 MILESTONE 1(최소 50건 저장)로 확장한다.

다음 세션 작업은 가능하면 1~3개 정도의
구체적인 작업만 남긴다.

---

## 6. 최근 변경사항

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

초기 커밋에 포함된 주요 파일:

```
build.gradle
settings.gradle
gradlew / gradlew.bat
gradle/wrapper/
src/main/java/com/grantfit/GrantFitApplication.java
src/main/java/com/grantfit/config/MyBatisConfig.java
src/main/java/com/grantfit/health/
src/main/resources/application.yml
src/main/resources/mapper/HealthMapper.xml
src/main/resources/db/schema.sql
src/test/java/com/grantfit/
README.md
HANDOVER.md
.env.example
.gitignore
```

미커밋 변경 없음.

실제 작업이 진행되면 중요한 변경 파일만 추가한다.

---

## 8. 테스트 상태

현재 테스트:

- [x] Spring Boot 기본 테스트
- [x] DB 연결 확인
- [x] MyBatis XML 매핑 확인
- [x] 스키마 존재 확인
- [ ] 공공 API 호출 확인
- [ ] DB 저장 확인
- [ ] 목록 API 확인
- [ ] 상세 API 확인
- [ ] Gemini API 호출 확인
- [ ] AI JSON Validation 확인
- [ ] AI 결과 DB 저장 확인
- [ ] Rule Engine 테스트
- [ ] Vue 화면 확인
- [ ] Docker 실행 확인

테스트 결과 (`./gradlew test`, 2026-10-01):

```
GrantFitApplicationTests   tests=1  failures=0  errors=0
SchemaTest                 tests=3  failures=0  errors=0
HealthMapperTest           tests=2  failures=0  errors=0
────────────────────────────────────────────────────────
합계                       tests=6  failures=0  errors=0
BUILD SUCCESSFUL
```

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
```

Frontend:

아직 구현하지 않음.

Database:

PostgreSQL 연결 설정 완료.
GrantFit 전용 `grantfit` DB와 role 생성 완료.
`support_program`, `ai_analysis` 테이블 생성 완료.

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
GEMINI_API_KEY   (아직 미사용)
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
Gemini API             (미착수)
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
