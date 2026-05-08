# CHANGES — step1-chat (vs step0-base)

## 추가

- `src/main/java/com/kosta/agent/config/AgentConfig.java`
- `src/main/java/com/kosta/agent/web/AgentController.java`

## 변경

- `build.gradle.kts`
  - `org.springframework.ai:spring-ai-starter-model-openai` 추가
  - `spring-ai-bom` 1.1.5 import (Maven Central, milestone repo 불필요)
- `src/main/resources/application.yml`
  - `spring.ai.openai.api-key` (`${OPENAI_API_KEY:}`)
  - `spring.ai.openai.chat.options.model: gpt-5.4-mini`
  - `spring.ai.openai.chat.options.temperature: 0.0`

## 변경 없음

- 도메인(`Customer`, `Order`, `*Repository`, `DataSeeder`)
- `web/OrderController.java`
- `docker-compose.yml`
