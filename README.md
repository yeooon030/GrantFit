# GrantFit

AI 기반 정부지원사업 적합도 분석 서비스

정부지원사업 공고를 수집하고, LLM을 활용해 비정형 공고 내용을 구조화한 뒤
사용자의 기업 조건과 비교하여 지원 가능 여부와 적합도를 분석하는 서비스

---

## 1. 프로젝트 소개

GrantFit은 정부·공공기관에서 제공하는 지원사업 공고를 수집하고,
공고에 포함된 복잡한 신청 조건을 AI를 이용해 구조화한 뒤
사용자의 기업 정보와 비교하는 서비스이다.

단순히 AI API를 호출하는 것을 목표로 하지 않는다.

AI를 처음 서비스에 적용하는 개발자가 AI의 역할과 한계를 이해하고,
실제 백엔드 서비스에 AI를 어떻게 안정적으로 통합할 수 있는지
경험하는 것을 프로젝트의 주요 목표로 한다.

핵심 흐름은 다음과 같다.

```
공공 데이터 수집
        ↓
원문 데이터 저장
        ↓
LLM API 호출
        ↓
Structured JSON
        ↓
응답 검증
        ↓
AI 분석 결과 저장
        ↓
사용자 기업 조건과 비교
        ↓
Java Rule Engine
        ↓
지원 가능 / 지원 불가 / 확인 필요
        ↓
사용자에게 근거와 함께 제공
```

---

## 2. 프로젝트 목적

이 프로젝트의 목적은 단순히 Gemini API를 한 번 호출해보는 것이 아니다.

프로젝트를 완료했을 때 다음 질문에 답할 수 있는 것을 목표로 한다.

1. 왜 이 문제에 AI가 필요한가?
2. AI에게 어떤 데이터를 전달했는가?
3. AI 출력 형식을 어떻게 통제했는가?
4. 잘못된 AI 응답은 어떻게 처리했는가?
5. AI가 잘못된 정보를 추출했는지 어떻게 확인했는가?
6. 같은 공고를 매번 AI에게 다시 보내지 않으려면 어떻게 해야 하는가?
7. AI가 판단하는 부분과 Java 프로그램이 판단하는 부분을 왜 분리했는가?
8. 프롬프트가 변경되었을 때 기존 분석 결과는 어떻게 관리하는가?
9. AI 결과의 품질을 어떻게 측정할 것인가?

---

## 3. 핵심 설계 원칙

GrantFit의 가장 중요한 원칙은
AI에게 최종 비즈니스 판단을 맡기지 않는 것이다.

### AI의 역할

```
공고 원문
    ↓
Gemini
    ↓
구조화된 조건
```

AI는 다음과 같은 비정형 데이터를 구조화한다.

- 지원 대상
- 지역 조건
- 업력 조건
- 기업 형태
- 제외 조건
- 우대 조건
- 지원 금액
- 필요 서류
- 확인이 필요한 조건
- 근거 문장

### Java의 역할

```
AI가 구조화한 조건
+
사용자의 기업 정보
        ↓
Java Rule Engine
        ↓
지원 가능 여부
+
적합도
```

즉,

```
AI   = 문서 이해 / 정보 추출
Java = 비즈니스 규칙 적용 / 최종 판정
```

으로 역할을 분리한다.

---

## 4. AI에게 최종 판단을 맡기지 않는 이유

다음과 같이 요청하는 방식은 사용하지 않는다.

```
이 회사가 이 지원사업에 지원할 수 있는지 판단하고
적합도를 0~100점으로 계산해줘.
```

LLM이 생성한 결과를 그대로 비즈니스 판정에 사용하는 구조가 되기 때문이다.

대신 AI에게는 다음과 같이 요청한다.

```
이 공고에서 다음 정보를 추출하라.
- 지원 대상
- 지역 조건
- 업력 조건
- 기업 형태
- 제외 조건
- 우대 조건
- 지원 금액
- 필요 서류
- 확인이 필요한 조건
- 각 조건의 근거 문장
```

AI가 반환한 결과를 서버에서 검증한 후
Java Rule Engine에서 사용자 조건과 비교한다.

---

## 5. 의도적으로 구현하지 않는 기능

AI를 처음 적용하는 프로젝트이므로 기술을 무작정 추가하지 않는다.

MVP에서는 다음 기능을 구현하지 않는다.

```
X RAG
X Vector DB
X Embedding
X Fine Tuning
X 로컬 LLM
X Agent
X MCP
X 복잡한 PDF OCR
X 복잡한 크롤러
X 실시간 AI Chat
X AI에게 최종 지원 가능 여부 위임
X Redis
X Kafka
X Elasticsearch
```

목표는 AI 기술을 많이 사용하는 것이 아니다.

AI가 필요한 부분에 AI를 정확하게 사용하는 것을 목표로 한다.

---

## 6. 사용자 시나리오

사용자가 자신의 기업 정보를 입력한다.

예:

```
지역          서울
기업 형태     개인사업자
창업일        2024-03-01
업종          IT / SaaS
상시근로자    3명
관심 분야
- AI
- 사업화
- 창업
- R&D
```

서비스는 수집된 지원사업의 AI 분석 결과와 사용자의 조건을 비교한다.

예:

```
2026 AI 초기기업 사업화 지원
지원 가능
적합도 86%
지원금
최대 5,000만원
접수마감
D-17
```

조건 분석:

```
충족 조건
✓ 서울 소재 기업
✓ 창업 7년 이내
✓ IT/SW 기업
✓ 개인사업자 신청 가능
확인 필요
△ 최근 동일사업 수혜 여부
△ 국세 체납 여부
우대 조건
✓ AI 관련 사업
필요 서류
□ 사업계획서
□ 사업자등록증
□ 국세납세증명서
```

그리고 사용자가 AI의 판단 근거를 확인할 수 있도록 원문 근거를 제공한다.

```
창업 7년 이내
근거
"공고일 기준 창업 후 7년이 경과하지 않은 기업"
```

---

## 7. 데이터 수집

초기 데이터 소스는 기업마당 지원사업 OpenAPI 하나로 제한한다.

```
기업마당 OpenAPI
        ↓
Spring Boot
        ↓
지원사업 DTO
        ↓
PostgreSQL
```

초기에는 실제 지원사업 데이터를 최소 50건 이상 저장하는 것을 목표로 한다.

### 수집 데이터

```
공고 ID
공고명
소관기관
수행기관
사업개요
지원대상
신청기간
공고 URL
첨부파일 URL
신청방법
문의처
등록일
수정일
원문 데이터
```

### 1차 성공 기준

- [x] API 호출 성공
- [x] JSON 응답 DTO 변환
- [x] PostgreSQL 저장
- [x] 중복 공고 업데이트
- [x] 목록 API 제공

초기에는 AI를 사용하지 않는다.

---

## 8. AI 분석

AI 분석의 첫 목표는 공고 1건을 정확하게 분석하는 것이다.

처음부터 여러 공고를 한꺼번에 분석하지 않는다.

### AI 입력

초기에는 PDF 전체를 AI에 전달하지 않는다.

공공 API에서 확보한 텍스트 데이터를 우선 사용한다.

```
공고명
사업개요
지원대상
신청기간
신청방법
기타 상세내용
```

예:

```
[공고명]
2026 AI 초기기업 사업화 지원사업
[사업개요]
AI 분야 창업기업의 사업화를 지원...
[지원대상]
서울 소재 창업 7년 이내 기업...
[신청방법]
온라인 신청...
```

---

## 9. Structured Output

AI가 자유로운 텍스트를 반환하도록 하지 않는다.

Gemini의 Structured Output을 사용하여 정해진 JSON 구조로 응답하도록 한다.

예:

```json
{
  "summary": "AI 기술 기반 초기기업 사업화 지원",
  "regions": ["서울"],
  "businessAge": {
    "maxYears": 7
  },
  "applicantTypes": [
    "SOLE_PROPRIETOR",
    "CORPORATION"
  ],
  "support": {
    "maxAmount": 50000000,
    "selfPaymentRequired": true
  },
  "eligibility": [
    {
      "condition": "서울 소재 기업",
      "evidence": "서울특별시에 사업장을 두고 있는 기업"
    }
  ],
  "exclusions": [
    {
      "condition": "국세 또는 지방세 체납 기업",
      "evidence": "국세·지방세 체납 중인 자는 신청할 수 없음"
    }
  ],
  "preferences": [
    {
      "condition": "AI 관련 기술 보유기업",
      "evidence": "AI 관련 기술기업 우대"
    }
  ],
  "requiredDocuments": [
    "사업계획서",
    "사업자등록증"
  ],
  "uncertainConditions": [
    "최근 동일사업 수혜 여부"
  ]
}
```

---

## 10. Evidence

GrantFit에서는 AI가 조건만 반환하지 않고
근거 문장도 함께 반환하도록 설계한다.

예:

```json
{
  "condition": "창업 7년 이하",
  "evidence": "공고일 기준 창업 후 7년이 경과하지 않은 기업"
}
```

이를 통해 사용자가 AI 분석 결과를 직접 확인할 수 있도록 한다.

화면에서는 다음과 같이 제공한다.

```
창업 7년 이내
근거 보기
"공고일 기준 창업 후 7년이
경과하지 않은 기업"
```

AI의 분석 결과만 보여주는 것이 아니라
어떤 원문을 근거로 해당 조건이 추출되었는지 확인할 수 있도록 하는 것이 핵심이다.

---

## 11. AI 첫 번째 성공 목표

처음부터 공고 100개를 분석하지 않는다.

목표는 다음과 같다.

```
공고 1건
    ↓
Gemini API
    ↓
Structured JSON
    ↓
Java DTO
    ↓
Validation
    ↓
PostgreSQL
```

완료 조건:

- [x] Gemini API 호출
- [x] Structured Output 사용
- [x] JSON Schema 정의
- [x] Java DTO 역직렬화
- [x] 서버 측 Validation
- [x] DB 저장
- [ ] 실패 상태 및 로그 처리

공고 1건이 제대로 분석되는 것을 확인한 후에만 다음 단계로 넘어간다.

---

## 12. 여러 공고 분석

1건 분석이 안정화되면 여러 공고를 대상으로 분석한다.

초기 목표:

```
20개 공고 자동 분석
```

예:

```
전체 공고       20
정상 응답       19
재시도 필요      1
최종 실패        0
```

실패가 발생하면 단순히 실패 사실만 기록하지 않고 원인을 구분한다.

```
API_ERROR
TIMEOUT
RATE_LIMIT
INVALID_JSON
VALIDATION_ERROR
```

---

## 13. AI 결과 검증

AI 결과가 실제 공고 내용과 일치하는지 확인하기 위한 검증 데이터를 만든다.

테스트용 공고 약 20~30개를 선정하고
사람이 직접 주요 필드의 기준값을 작성한다.

예:

```json
{
  "region": "서울",
  "maxBusinessAge": 7,
  "maxAmount": 50000000,
  "deadline": "2026-10-31"
}
```

이를 Golden Dataset으로 사용한다.

AI 분석 결과와 Golden Dataset을 비교하여 AI 추출 품질을 측정한다.

---

## 14. AI 품질 평가

처음부터 완벽한 정확도를 목표로 하지 않는다.

중요한 것은 AI 결과를 측정할 수 있는 구조를 만드는 것이다.

주요 평가 대상:

```
지역
업력
신청기간
지원금
기업 형태
```

예:

```
Golden Dataset 30건
지역       29 / 30
업력       28 / 30
지원금     30 / 30
신청기간   30 / 30
```

목표치는 다음과 같이 설정한다.

```
Structured Output 성공률   98% 이상 목표
주요 필드 정확도           90% 이상 목표
Evidence 포함률            90% 이상 목표
```

실제 결과는 테스트 후 기록하며,
목표 수치를 달성하지 못하더라도 결과를 그대로 기록하고 원인을 분석한다.

---

## 15. Prompt Version 관리

AI 분석 결과에는 최소한 다음 정보를 기록한다.

```
model
prompt_version
status
analyzed_at
```

향후 토큰 사용량을 기록할 필요가 생기면 다음 정보를 추가한다.

```
input_tokens
output_tokens
```

예:

```
model
gemini-xxx
prompt_version
v1.2
status
SUCCESS
analyzed_at
2026-10-03
```

프롬프트가 변경되면 버전을 변경한다.

```
v1
v2
v3
```

이를 통해 프롬프트 변경 전후의 결과를 비교할 수 있도록 한다.

---

## 16. AI 결과 재사용

같은 공고를 매번 Gemini에 보내지 않는다.

기존 분석 결과가 존재하고
현재 분석에 사용할 prompt_version과 동일하다면 저장된 결과를 재사용한다.

```
공고
+
prompt_version
        ↓
기존 분석 결과 존재?
        ↓
YES → 기존 결과 사용
NO  → Gemini 분석
```

필요한 경우에만 재분석한다.

이를 통해 불필요한 API 호출과 비용을 줄인다.

---

## 17. Rule Engine

AI 분석 결과와 사용자 기업 정보를 Java에서 비교한다.

예:

```
기업 지역
서울
공고 지역
서울
→ MATCH

기업 업력
2년
공고 최대 업력
7년
→ MATCH

사용자 국세 체납 여부
입력하지 않음
공고 제외조건
국세 체납 기업
→ UNKNOWN
```

개별 조건의 결과를 종합하여 최종 상태를 결정한다.

---

## 18. 최종 상태

GrantFit의 최종 판정은 세 가지 상태로 제한한다.

```
ELIGIBLE
지원 가능

INELIGIBLE
지원 불가

NEEDS_REVIEW
추가 확인 필요
```

AI가 이 값을 직접 생성하지 않는다.

Java Rule Engine이 조건 비교 결과를 기반으로 결정한다.

---

## 19. 적합도 Score

지원 가능 여부와 적합도는 별도로 계산한다.

예:

```
필수 조건
지역       충족
업력       충족
기업 형태  충족
→ ELIGIBLE
```

그 후 선택 조건을 기준으로 내부 적합도 점수를 계산한다.

```
관심 분야 일치       +30
우대 조건 충족       +25
지원 규모 선호       +20
지역 선호            +15
사업 단계 일치       +10
────────────────────────
                     100
```

예:

```
지원 가능
적합도
85%
```

이 점수는 지원 성공 확률이나 실제 선정 가능성을 의미하지 않는다.

사용자가 입력한 조건과 지원사업의 조건이 얼마나 일치하는지를 나타내는
GrantFit 내부 점수이다.

---

## 20. 주요 화면

MVP에서는 다음 화면에 집중한다.

### 20.1 내 기업정보

```
기업 프로필
지역
[ 서울 ▼ ]
기업형태
[ 개인사업자 ▼ ]
창업일
[ 2024-03-01 ]
업종
[ IT / SW ▼ ]
관심분야
☑ AI
☑ 사업화
□ R&D
□ 수출
[분석 시작]
```

### 20.2 지원사업 목록

```
GrantFit
52개 지원사업 분석 완료
───────────────────
적합도 94%
AI 스타트업 사업화 지원
최대 5,000만원
D-17
● 지원 가능
[상세보기]
───────────────────
적합도 87%
초기창업기업 지원사업
최대 3,000만원
D-23
● 확인 필요
```

필터:

```
지원 가능만 보기
지역
분야
마감일
지원금
```

---

## 21. 지원사업 상세

상세 화면은 포트폴리오에서 핵심 화면으로 구성한다.

```
AI 스타트업 사업화 지원
적합도 94%
● 지원 가능
핵심 정보
지원금
최대 5,000만원
접수마감
2026.10.31
지원대상
창업 7년 이내
조건 분석
✓ 서울 소재
✓ 창업 7년 이내
✓ 개인사업자 가능
△ 동일 사업 수혜 여부 확인
우대 조건
✓ AI 기술기업
필요 서류
□ 사업계획서
□ 사업자등록증
□ 국세납세증명서
AI 분석 근거
창업 7년 이내
> "공고일 기준 창업 후
> 7년이 경과하지 않은 기업"
[원문 공고 보기]
```

---

## 22. AI 분석 이력

AI 분석 결과를 단순히 DB에 저장하는 것에서 끝내지 않고
분석 상태와 버전을 확인할 수 있도록 한다.

예:

```
AI Analysis
공고              상태       Prompt
AI 사업화 지원     SUCCESS    v1.2
창업기업 지원      SUCCESS    v1.2
R&D 지원사업       FAILED     v1.2
```

상세:

```
Model
Gemini xxx
Prompt
v1.2
Status
SUCCESS
Analyzed At
2026-10-03
[Input]
[Output JSON]
[다시 분석]
```

이 기능을 통해 AI API 호출을 단순 기능으로 끝내지 않고
서비스에서 AI 결과를 관리하는 구조를 경험한다.

---

## 23. 시스템 구조

```
                    Vue
                     │
                     │ REST API
                     ▼
               Spring Boot
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
 SupportService  AiAnalysisService  MatchService
        │            │            │
        ▼            ▼            ▼
   PostgreSQL    Gemini API      Rule Engine
        │
        ▼
지원사업 / AI 분석 결과
```

AI가 최종 판정을 수행하지 않도록
AiAnalysisService와 MatchService의 역할을 분리한다.

---

## 24. 기술 스택

AI를 처음 적용하는 프로젝트이므로 기술 스택을 최대한 단순하게 유지한다.

### Backend

```
Java 17
Spring Boot 3
MyBatis
Gradle
```

### Frontend

```
Vue
```

### Database

```
PostgreSQL
```

### AI

```
Gemini API
Structured Output
JSON Schema
```

### Infra

```
Docker
Docker Compose
```

### Test

```
JUnit
```

Swagger / OpenAPI 문서화는 필요성이 확인되는 경우 추가한다.

---

## 25. 주요 데이터베이스

초기 MVP에서는 테이블을 과도하게 늘리지 않는다.

현재 핵심 테이블:

```
support_program
ai_analysis
```

사용자 기업 조건과 Rule Engine 관련 테이블은
해당 기능을 구현하는 단계에서 추가한다.

---

## 26. support_program

```
id
external_id
title
organization
region
target
application_start
application_end
support_amount
original_url
raw_content
created_at
updated_at
```

external_id를 기준으로 외부 공고의 중복 저장을 방지한다.

---

## 27. ai_analysis

```
id
support_program_id
model
prompt_version
status
eligibility_json
exclusion_json
required_documents_json
support_amount_json
uncertain_conditions_json
evidence_json
created_at
updated_at
```

AI가 생성한 구조화 결과는
PostgreSQL의 JSONB를 활용하여 저장하는 것을 기본 방향으로 한다.

AI 분석 결과를 원문 형태 그대로 보존하기보다,
서버에서 검증된 구조를 저장하고 필요에 따라 원본 응답도 별도로 관리한다.

---

## 28. 개발 단계

전체 프로젝트를 한 번에 구현하지 않는다.

Milestone 하나를 완료하고 검증한 후 다음 단계로 넘어간다.

---

### MILESTONE 0 — 프로젝트 기본 환경

목표:

```
Spring Boot
PostgreSQL
Vue
```

기본 실행환경 구성.

완료 기준:

- [x] Spring Boot 실행
- [x] PostgreSQL 연결
- [x] MyBatis 기본 설정
- [x] 기본 Health Check
- [ ] Vue 실행

AI 사용 없음.

---

### MILESTONE 1 — 실제 지원사업 데이터

목표:

```
기업마당 OpenAPI
        ↓
Spring Boot
        ↓
PostgreSQL
```

최소 50건의 실제 공고 저장.

완료 기준:

- [x] API 호출
- [x] DTO 변환
- [x] DB 저장
- [x] 중복 공고 처리
- [x] 목록 API

AI 사용 없음.

완료 후 Git Commit.

---

### MILESTONE 2 — 공고 1건 AI 분석

지원사업 하나를 선택한다.

```
POST
/api/support-programs/{id}/analyze
```

처리:

```
공고 원문
    ↓
Gemini
    ↓
Structured JSON
    ↓
Java DTO
    ↓
Validation
    ↓
DB
```

완료 기준:

- [x] Gemini API 호출
- [x] Structured Output
- [x] JSON Schema
- [x] DTO 역직렬화
- [x] Validation
- [x] DB 저장

여기가 첫 번째 AI 성공 지점이다.

이 단계가 제대로 동작하는 것을 직접 확인한 후 다음 단계로 진행한다.

---

### MILESTONE 3 — AI 실패 처리

다음 상황을 처리한다.

```
API Timeout
API Error
Rate Limit
Invalid JSON
Schema Validation 실패
Network 오류
```

상태:

```
WAITING
ANALYZING
COMPLETED
FAILED
```

필요한 경우 제한된 횟수만 재시도한다.

---

### MILESTONE 4 — AI 정확도 측정

20~30개의 공고를 선정한다.

```
Golden Dataset
```

을 작성하고 AI 결과와 비교한다.

평가 대상:

```
지역
업력
지원금
신청기간
기업 형태
Evidence
```

결과는 실제 측정값을 README에 기록한다.

---

### MILESTONE 5 — 기업 Profile

사용자가 자신의 조건을 입력한다.

```
지역
기업 형태
창업일
업종
상시근로자
관심 분야
```

저장하고 조회할 수 있도록 한다.

---

### MILESTONE 6 — Rule Engine

AI 분석 결과와 기업 Profile을 비교한다.

```
MATCH
MISMATCH
UNKNOWN
```

조건을 종합하여:

```
ELIGIBLE
INELIGIBLE
NEEDS_REVIEW
```

를 결정한다.

---

### MILESTONE 7 — 적합도

필수조건 판정과 별도로 0~100점의 내부 적합도를 계산한다.

AI가 점수를 생성하지 않는다.

Java 코드에서 계산한다.

---

### MILESTONE 8 — UI

다음 기능을 중심으로 화면을 완성한다.

```
기업 Profile
지원사업 목록
지원사업 상세
조건 분석
Evidence
적합도
```

특히 지원사업 상세 화면의 완성도를 높인다.

---

### MILESTONE 9 — AI 운영 기능

다음 기능을 추가한다.

```
Prompt Version
Model 정보
AI 분석 이력
재분석
실패 이력
기존 분석 결과 재사용
```

필요한 경우 토큰 사용량도 기록한다.

---

### MILESTONE 10 — 포트폴리오 정리

README에 다음 내용을 정리한다.

1. 문제 정의
2. 왜 AI가 필요한가
3. 전체 Architecture
4. AI Pipeline
5. Structured Output Schema
6. Rule Engine
7. Evaluation 방법
8. Evaluation 결과
9. Failure Handling
10. 주요 화면

---

## 29. Claude Code 사용 원칙

Claude Code에게 전체 프로젝트를 한 번에 구현하도록 요청하지 않는다.

항상 현재 Milestone 하나만 구현한다.

예:

```
현재 Milestone 1을 개발하려고 한다.
목표:
기업마당 OpenAPI에서 지원사업을 가져와
PostgreSQL에 저장한다.
이번 작업에서는 AI 기능을 구현하지 않는다.
구현 범위:
...
완료 조건:
...
먼저 현재 프로젝트를 분석하고
변경할 파일과 구현 순서를 제안한 뒤 구현하라.
```

---

## 30. Claude Code 개발 규칙

1. 요구하지 않은 기능을 추가하지 않는다.
2. 현재 Milestone 범위 밖의 기능을 구현하지 않는다.
3. 새로운 라이브러리를 추가할 경우 이유를 설명한다.
4. 기존 코드 구조와 스타일을 우선한다.
5. 과도한 추상화를 하지 않는다.
6. TODO만 남기고 구현한 것처럼 처리하지 않는다.
7. Mock 데이터로 실제 연동을 대체하지 않는다.
8. 컴파일 및 테스트가 깨진 상태로 종료하지 않는다.
9. 비즈니스 로직은 Controller에 작성하지 않는다.
10. AI 응답은 반드시 DTO 및 Schema Validation을 거친다.
11. AI에게 최종 Eligibility 판단을 맡기지 않는다.
12. 비밀키를 소스 코드에 작성하지 않는다.
13. AI 분석 결과에는 model과 promptVersion을 기록한다.
14. 현재 작업과 관계없는 파일을 수정하지 않는다.
15. 구현 완료 후 변경 파일과 테스트 결과를 요약한다.
16. 한 Milestone이 정상 동작하는 것을 확인한 후 다음 Milestone으로 넘어간다.

---

## 31. 첫 AI 개발 시 확인할 것

AI 개발을 처음 하는 프로젝트이므로 MILESTONE 2에서 다음을 직접 확인한다.

- [ ] Prompt를 직접 작성해본다.
- [ ] Gemini API 요청 구조를 확인한다.
- [ ] Structured Output이 무엇인지 확인한다.
- [ ] JSON Schema가 어떤 역할을 하는지 확인한다.
- [ ] AI 응답을 Java DTO로 변환한다.
- [ ] 서버에서 추가 Validation을 수행한다.
- [ ] 잘못된 응답이 들어왔을 때 어떻게 처리되는지 확인한다.
- [ ] AI 결과를 DB에 저장한다.

Claude Code가 코드를 작성하더라도
각 단계가 왜 필요한지는 직접 확인하고 이해하는 것을 목표로 한다.

---

## 32. 최종 목표

초기 목표는 다음과 같다.

```
실제 지원사업 데이터
100건 이상
AI 분석 완료
50건 이상
Golden Dataset
20~30건
Structured Output 성공률
98% 이상 목표
주요 필드 정확도
90% 이상 목표
Evidence 포함률
90% 이상 목표
AI 결과 재사용
적용
Prompt Version 관리
적용
AI Failure Retry
적용
Rule 기반 Eligibility
적용
```

수천 건의 데이터를 처리하는 것보다
100건의 실제 데이터를 안정적으로 처리하고
AI 결과를 측정할 수 있는 구조를 만드는 것을 우선한다.

---

## 33. 후순위 기능

MVP가 완성된 후 필요성을 확인하여 추가한다.

### 첨부 PDF 분석

```
공고 API
+
첨부 PDF Text
        ↓
AI
        ↓
더 정확한 조건 추출
```

### 데이터 소스 추가

```
기업마당
K-Startup
지자체
```

### 관심 지원사업 저장

```
관심 공고
        ↓
사용자 저장
```

### 마감 임박 알림

```
D-7
D-3
D-1
```

### Prompt 비교

```
Prompt v1
     VS
Prompt v2
```

---

## 34. 포트폴리오에서 보여줄 핵심

이 프로젝트를 단순히 다음과 같이 설명하지 않는다.

```
정부지원사업을 추천하는 AI 서비스를 만들었다.
```

GrantFit에서 보여주고 싶은 핵심은
AI와 일반 애플리케이션의 역할을 분리하고,
AI 결과를 검증하고 측정하는 구조를 직접 설계했다는 것이다.

예:

> 정부지원사업 공고는 자연어 형태로 작성되어 있어
> 기존 조건문만으로 신청조건을 일관되게 구조화하기 어렵다고 판단했습니다.
>
> 따라서 LLM은 비정형 공고에서 신청자격·제외조건·우대조건·필요서류와
> 그 근거를 Structured Output으로 추출하는 역할로 제한했습니다.
>
> 실제 지원 가능 여부와 적합도는 LLM이 아닌 Java Rule Engine에서
> 결정하도록 분리하여 AI의 비결정적인 출력이
> 핵심 비즈니스 판정에 직접 영향을 주지 않도록 설계했습니다.
>
> 또한 Golden Dataset을 구축하여 주요 필드 추출 정확도를 측정하고,
> Prompt Version과 AI 분석 이력을 저장하여
> 프롬프트 변경에 따른 품질 변화도 확인할 수 있도록 구성했습니다.

---

## 35. 프로젝트 진행 원칙

처음부터 완성된 AI 서비스를 만들려고 하지 않는다.

다음 순서로 하나씩 검증한다.

```
① 공공데이터 1건 받기
② DB에 넣기
③ 목록 API로 조회하기
④ 공고 1건 AI에게 보내기
⑤ JSON 제대로 받기
⑥ 서버에서 검증하기
⑦ 결과 저장하기
⑧ 여러 건 분석하기
⑨ 잘못된 결과 찾아보기
⑩ 정확도 측정하기
⑪ 기업 조건과 비교하기
⑫ UI 만들기
⑬ AI 운영 기능 추가하기
```

한 단계가 제대로 동작하지 않으면 다음 단계로 넘어가지 않는다.

이 프로젝트의 AI 학습도 이 순서를 따른다.

---

## 36. 현재 프로젝트 상태

작성일: 2026-09-30

현재 구현 상태:

- [x] Git 초기화
- [x] Spring Boot + Gradle 프로젝트 구성
- [x] PostgreSQL 연결
- [x] MyBatis 기본 설정
- [x] 환경변수 기반 설정
- [x] 기본 테스트
- [x] 지원사업 DDL
- [x] 공공 API 연동
- [x] 지원사업 저장
- [x] 지원사업 조회 API
- [ ] Vue 화면
- [x] Gemini API 연동
- [x] Structured Output
- [x] AI 결과 Validation
- [x] AI 결과 저장
- [ ] 기업 Profile
- [ ] Rule Engine
- [ ] 적합도
- [ ] AI 평가 데이터셋
- [ ] AI 운영 기능
- [ ] Docker

현재는 MILESTONE 0 진행 단계이며,
다음 작업은 지원사업 DB 구조를 확정하고 DDL을 작성하는 것이다.

---

## 37. 세션 간 개발 원칙

Claude Code 세션이 변경될 때마다
HANDOVER.md를 기준으로 이전 작업 상태를 확인한다.

새 세션에서는 먼저:

1. HANDOVER.md 확인
2. README.md 확인
3. git status 확인
4. 현재 branch 확인
5. 관련 소스 코드 확인

후 현재 상태를 요약한다.

문서와 실제 코드 상태가 다를 경우
실제 코드와 Git 상태를 우선한다.

세션 종료 전에는 HANDOVER.md를 현재 상태에 맞게 갱신한다.

---

## 38. 보안 및 환경변수

API Key와 DB 비밀번호 등 민감한 정보는
소스 코드나 Git에 저장하지 않는다.

환경변수 예:

```
DB_URL
DB_USERNAME
DB_PASSWORD
GEMINI_API_KEY
```

.env는 Git에서 제외하고 .env.example만 저장한다.

---

## 39. 개발 환경 주의사항

현재 로컬 개발 환경에서는
PostgreSQL의 5432 포트를 다른 프로젝트와 공유하고 있다.

따라서 해당 PostgreSQL 컨테이너가 중지되면
GrantFit도 DB에 연결할 수 없다.

MVP 초기 개발에서는 현재 환경을 사용하고,
최종 Docker 구성 단계에서 GrantFit 전용 PostgreSQL 컨테이너로 분리한다.

또한 현재 DB 연결 테스트는 로컬 PostgreSQL 실행 여부에 의존한다.

CI 환경을 구성할 경우에는 향후 Testcontainers 등을 검토할 수 있다.

---

## 40. 최종 목표

GrantFit의 최종 목표는 단순히

```
공공 API + Gemini API
```

를 연결하는 것이 아니다.

다음 전체 과정을 직접 경험하고 설명할 수 있는 서비스를 만드는 것이다.

```
비정형 데이터
     ↓
AI 구조화
     ↓
Structured Output
     ↓
서버 Validation
     ↓
DB 저장
     ↓
AI 결과 평가
     ↓
사용자 조건 비교
     ↓
Deterministic Rule Engine
     ↓
서비스 결과
```

이를 통해 AI를 사용하는 백엔드 서비스를
어떻게 설계하고, 검증하고, 운영할 것인지 설명할 수 있는
포트폴리오를 만드는 것을 목표로 한다.
