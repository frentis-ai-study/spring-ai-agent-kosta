# CHANGES — step4-rag (vs step3-tools)

## 추가

- `src/main/java/com/kosta/agent/rag/PolicyIndexer.java`
- `src/main/resources/docs/order-policy.txt`
- `src/main/resources/docs/return-policy.txt`
- `src/main/resources/docs/faq.txt`

## 변경

- `build.gradle.kts`
  - `spring-ai-starter-vector-store-pgvector`
  - `spring-ai-advisors-vector-store`
  - `spring-ai-rag`
- `src/main/java/com/kosta/agent/config/AgentConfig.java`
  - `RetrievalAugmentationAdvisor` 빈 추가
  - `defaultAdvisors`에 `ragAdvisor` 추가 (Memory + RAG)
  - 시스템 프롬프트에 "정책 질문은 검색된 문서 근거로만" 지침
- `src/main/java/com/kosta/agent/web/AgentController.java`
  - `PolicyIndexer` 주입
  - `POST /api/index` 핸들러 추가
- `src/main/resources/application.yml`
  - `spring.ai.openai.embedding.options.model: text-embedding-3-small`
  - `spring.ai.vectorstore.pgvector.*` (HNSW, COSINE_DISTANCE, dimensions 1536, initialize-schema true)

## 변경 없음

- `tool/OrderTools.java` (step3 동일, 누적)
- 도메인 / OrderController / DataSeeder
