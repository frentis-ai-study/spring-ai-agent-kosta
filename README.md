# Spring AI Agent — KOSTA 8시간 강의 실습 저장소

KOSTA Spring AI 8시간 강의 수강생을 위한 단계별 누적 학습 저장소입니다. 각 폴더는 독립적으로 빌드/실행 가능한 완전한 Spring Boot 프로젝트이며, 한 단계씩 따라가면서 자연스럽게 Memory, Tool Calling, RAG, Advisor Chain, 운영 안정화까지 학습할 수 있도록 구성되어 있습니다.

## 사전 준비

- **Java 21** (Temurin/Adoptium 권장)
- **Docker / Docker Desktop** — pgvector 컨테이너 실행용
- **OpenAI API 키** — 환경변수 `OPENAI_API_KEY` 로 주입
- (선택) `pnpm`, `httpie`, `jq` 등 REST 호출 도구

## 진행 방법

1. 본 저장소를 클론합니다.
2. `step0-base` 폴더로 이동하여 README 안내를 따라 빌드/실행합니다.
3. 동작이 확인되면 `step1-chat`으로 넘어가 같은 방식으로 진행합니다.
4. step6까지 단계별로 진행하면서 각 단계의 `CHANGES.md`로 어떤 파일이 추가/변경되었는지 확인합니다.

> 각 step은 이전 step의 모든 파일을 포함하므로, 한 폴더만 IDE에 열어도 그 단계 학습을 완수할 수 있습니다.

## 단계 구성

| 단계 | 폴더 | 추가 요소 | 시간표(예시) | 관련 LO |
|------|------|----------|--------------|---------|
| 0 | `step0-base` | 도메인 + REST CRUD (AI 없음) | 09:00-09:50 (1블록) | LO-Spring AI 개요 |
| 1 | `step1-chat` | + ChatClient (단순 챗봇) | 09:50-10:50 | LO-ChatClient |
| 2 | `step2-memory` | + Structured Output 데모 + JDBC ChatMemory | 11:00-12:00 | LO-Memory & Structured Output |
| 3 | `step3-tools` | + Tool Calling 4종 (조회 + 취소) | 13:00-14:30 | LO-Tool Calling |
| 4 | `step4-rag` | + pgvector RAG (정책 문서 3개) | 14:30-15:50 | LO-RAG |
| 5 | `step5-agent` | + Advisor Chain (SafeGuard, Logger 통합) + SSE | 16:00-17:00 | LO-Advisor Chain |
| 6 | `step6-prod` | + Resilience4j, 모더레이션, 메트릭, 야간 잡 | 17:00-18:00 | LO-Production |

## 공통 인프라

모든 step(step1 이상)은 다음을 공유합니다.

- PostgreSQL + pgvector (`docker compose up -d` 한 번으로 기동)
- Spring Boot 3.5.0 / Spring AI 1.1.5 (2026-04-27 GA)
- 패키지 베이스: `com.kosta.agent.{config, domain, web, tool, rag, advisor, scheduled}`

### 컨테이너는 모든 step에서 공유됩니다

각 step 폴더의 `docker-compose.yml`은 동일한 project name (`spring-ai-agent-kosta`)을 사용합니다. 어느 step 폴더에서 `docker compose up -d`를 실행해도 같은 컨테이너·같은 볼륨을 공유합니다.

이는 학습 흐름상 매우 중요합니다.

- step3에서 만든 Customer/Order 데이터가 step5에서 그대로 보입니다
- step4에서 인덱싱한 정책 문서가 step6까지 유지됩니다
- step 전환 시 컨테이너를 다시 띄울 필요가 없습니다

```bash
# 강의 시작 시 한 번만
cd step0-base
docker compose up -d
export OPENAI_API_KEY=sk-...

# 이후 step 전환은 앱만 재시작 (컨테이너 그대로)
cd ../step1-chat && ./gradlew bootRun
# Ctrl+C 후
cd ../step2-memory && ./gradlew bootRun
# ... step6-prod까지

# 강의 종료 시
docker compose down       # 컨테이너만 정리, 데이터(named volume) 유지
docker compose down -v    # 데이터까지 모두 삭제 (재시작 시 처음부터)
```

### 데이터 흐름

| step | 사용/생성하는 테이블 |
|------|--------------------|
| step0~3 | `customers`, `orders` (DataSeeder가 첫 부팅 시 생성) |
| step2 이상 | + `SPRING_AI_CHAT_MEMORY` (자동 생성) |
| step4 이상 | + `vector_store` (pgvector 인덱싱) |

## 학습 체크리스트

- [ ] step0: `/api/orders`, `/api/customers` 호출이 정상 응답한다
- [ ] step1: `/api/agent`로 한국어 응답을 받는다
- [ ] step2: 같은 `conversationId`로 두 번 호출하면 이전 발화를 기억한다
- [ ] step3: "내 ORD-1 취소해줘" 요청에 도구가 호출되고 DB가 갱신된다
- [ ] step4: "환불 정책 알려줘" 요청에 RAG가 동작해 정책 문장을 인용한다
- [ ] step5: SafeGuard가 민감어를 차단하고 SSE 스트리밍이 정상 동작한다
- [ ] step6: Actuator `/actuator/prometheus`에서 메트릭 확인, 모더레이션 차단 응답 확인

## 라이선스 / 사용 안내

본 저장소는 KOSTA 강의 수강생 학습용으로 작성되었으며, 운영 환경 적용 시에는 보안/SecurityContext 처리, 시크릿 관리, 모더레이션 정책을 환경에 맞게 보강하여야 합니다.
