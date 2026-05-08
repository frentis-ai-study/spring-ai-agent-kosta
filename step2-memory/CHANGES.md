# CHANGES — step2-memory (vs step1-chat)

## 변경

- `build.gradle.kts`
  - `org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc` 추가
- `src/main/java/com/kosta/agent/config/AgentConfig.java`
  - `ChatMemory chatMemory(JdbcChatMemoryRepository)` 빈 추가
  - `defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())` 추가
- `src/main/java/com/kosta/agent/web/AgentController.java`
  - `ChatRequest` record에 `conversationId` 추가
  - `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))` 적용
- `src/main/resources/application.yml`
  - `spring.ai.chat.memory.repository.jdbc.initialize-schema: always`

## 변경 없음

- 도메인 / OrderController / docker-compose
