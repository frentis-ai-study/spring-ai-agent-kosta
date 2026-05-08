# CHANGES — step6-prod (vs step5-agent)

## 추가

- `src/main/java/com/kosta/agent/config/ResilienceConfig.java`
- `src/main/java/com/kosta/agent/advisor/ModerationInputAdvisor.java`
- `src/main/java/com/kosta/agent/scheduled/ChatMemoryCleanupJob.java`

## 변경

- `build.gradle.kts`
  - `org.springframework.boot:spring-boot-starter-aop`
  - `io.github.resilience4j:resilience4j-spring-boot3:2.2.0`
  - `io.github.resilience4j:resilience4j-reactor:2.2.0`
  - `io.micrometer:micrometer-registry-prometheus`
- `src/main/java/com/kosta/agent/SpringAiAgentApplication.java`
  - `@EnableScheduling` 추가
- `src/main/java/com/kosta/agent/config/AgentConfig.java`
  - `ModerationInputAdvisor` 빈 추가 (`order=Integer.MIN_VALUE+1000`, 가장 먼저 실행)
  - `defaultAdvisors` 선두에 ModerationInputAdvisor 배치
  - 시스템 프롬프트에 "위해 콘텐츠 거절" 지침 추가
- `src/main/java/com/kosta/agent/web/AgentController.java`
  - `chatResponse()`로 변경, `finish_reason` 추출하여 응답에 포함
  - `Retry` + `CircuitBreaker` 데코레이터로 호출 감쌈
- `src/main/resources/application.yml`
  - `management.endpoints.web.exposure.include` 에 `prometheus` 추가
  - `management.metrics.tags.application` 추가
  - `resilience4j.circuitbreaker.instances.openai`, `resilience4j.retry.instances.openai`

## 변경 없음

- 도메인 / OrderTools / RAG / 정책 문서 / OrderController / docker-compose
