# CHANGES — step5-agent (vs step4-rag)

## 변경

- `src/main/java/com/kosta/agent/config/AgentConfig.java`
  - `SafeGuardAdvisor.builder().sensitiveWords(...)` 추가
  - `defaultAdvisors`에 SafeGuard, `SimpleLoggerAdvisor` 추가
  - 시스템 프롬프트에 도구/RAG 통합 지침
- `src/main/java/com/kosta/agent/web/AgentController.java`
  - `GET /api/agent/stream` SSE 핸들러 추가
  - `Flux.timeout(60s)` + `doOnCancel` 콜백
- `src/main/resources/application.yml`
  - 변경 없음 (advisor 빈 등록만으로 동작)

## 변경 없음

- 도메인 / OrderTools / RAG / Indexer / OrderController
