# step5-agent — Advisor Chain 통합 (SafeGuard + Logger + SSE)

step4까지 누적된 Memory + RAG + Tools 위에 `SafeGuardAdvisor`와 `SimpleLoggerAdvisor`를 더하고,
SSE 스트리밍 엔드포인트(`timeout`, `doOnCancel` 포함)를 추가합니다. 이 단계가 KOSTA 강의의 종합 Agent 데모입니다.

## 목표

- Advisor 체인을 정해진 순서로 등록 (Memory → RAG → SafeGuard → Logger).
- 민감어(주민등록번호, 신용카드번호, 비밀번호 등)는 SafeGuard가 즉시 차단 응답으로 단락.
- SSE 스트리밍 호출에서 `timeout(60s)`과 `doOnCancel` 콜백을 적용하여 클라이언트 취소 시 자원 정리.

## 추가/변경 파일

| 종류 | 경로 | 설명 |
|------|------|------|
| 변경 | `config/AgentConfig.java` | `SafeGuardAdvisor` + `SimpleLoggerAdvisor` 추가, 통합 시스템 프롬프트 |
| 변경 | `web/AgentController.java` | `GET /api/agent/stream` 추가 (SSE, timeout, doOnCancel) |

## 사전 준비

- Java 21 + `OPENAI_API_KEY` 환경변수만 있으면 됩니다.
- DB는 H2 file (`./data/agentdb`), 벡터 인덱스는 `./data/vector-store.json` 파일로 자동 관리됩니다.

## 실행

```bash
export OPENAI_API_KEY=sk-...
./gradlew bootRun
curl -X POST http://localhost:8080/api/index    # RAG 인덱싱 1회
```

## 5가지 시나리오

1. **A. 일반 정책 질문** — "환불 정책 알려줘" → RAG가 정책 문장 인용
2. **B. 도구 호출** — "alice@example.com의 최근 주문" → findCustomer + getRecentOrders 연쇄 호출
3. **C. 민감어 차단** — "비밀번호가 뭐야?" → SafeGuard가 가드 메시지로 차단
4. **D. 메모리 활용** — 같은 cid로 "내 등급?" 후 "그럼 무료배송 되나?" → 이전 대화 맥락 인지
5. **E. SSE 스트리밍 취소** — `/api/agent/stream` 응답을 중간에 끊으면 서버 콘솔에 `client cancelled` 로그

## 운영 시 주의

- `SafeGuardAdvisor`는 단순 키워드 매칭이므로 변형 우회 가능. 운영은 OpenAI Moderation API를 사용 (step6 참조).
- `SimpleLoggerAdvisor`는 평문 프롬프트/응답을 로그에 남기므로 PII 마스킹 필요.

## 운영 환경 전환 안내

`application.yml`의 `datasource`를 PostgreSQL로, VectorStore 의존성을 `spring-ai-starter-vector-store-pgvector`로 바꾸면 동일한 코드가 그대로 동작합니다. 이는 Spring의 PSA(Portable Service Abstraction) 가치 그 자체입니다.

## 다음 단계

`step6-prod`로 넘어가 Resilience4j, 모더레이션, 메트릭, 야간 잡을 추가합니다.
