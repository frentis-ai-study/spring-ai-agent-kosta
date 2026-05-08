# step4-rag — pgvector 기반 RAG

정책 문서를 임베딩하여 pgvector에 적재하고, `RetrievalAugmentationAdvisor`로 질의에 컨텍스트를 주입합니다.

## 목표

- `spring-ai-starter-vector-store-pgvector` + `spring-ai-rag` 의존성 추가.
- `classpath:docs/*.txt` 정책 문서를 토큰 단위로 청킹해 적재(`PolicyIndexer`).
- `RetrievalAugmentationAdvisor`를 advisor 체인에 등록.
- 정책 질문(반품, 배송, FAQ)에 검색된 문서를 근거로 답하도록 시스템 프롬프트 보강.

## 추가/변경 파일

| 종류 | 경로 | 설명 |
|------|------|------|
| 추가 | `rag/PolicyIndexer.java` | classpath 문서 토큰 청킹 + pgvector 적재 |
| 추가 | `resources/docs/order-policy.txt` | 주문 정책 |
| 추가 | `resources/docs/return-policy.txt` | 반품/교환 정책 |
| 추가 | `resources/docs/faq.txt` | 자주 묻는 질문 |
| 변경 | `config/AgentConfig.java` | `RetrievalAugmentationAdvisor` 추가 |
| 변경 | `web/AgentController.java` | `POST /api/index` 엔드포인트 추가 |
| 변경 | `build.gradle.kts` | `spring-ai-starter-vector-store-pgvector`, `spring-ai-advisors-vector-store`, `spring-ai-rag` 추가 |
| 변경 | `application.yml` | `spring.ai.vectorstore.pgvector.*`, `embedding.options.model` 추가 |

## 멱등성

`PolicyIndexer.indexAll()`은 호출 시마다 청크를 추가합니다. 운영에서는 LO-RAG 본문의 EventListener
패턴(체크섬 비교 후 `delete()` 후 `add()`) 또는 별도 `processed_docs` 테이블로 중복 인덱싱을 막아야 합니다.
학습 단계에서는 호출을 1회만 하시거나, 컨테이너를 재기동하여 vector_store를 비운 뒤 재인덱싱하시기 바랍니다.

## 실행

```bash
docker compose up -d
export OPENAI_API_KEY=sk-...
./gradlew bootRun

# 인덱싱 트리거 (1회)
curl -X POST http://localhost:8080/api/index
```

## 5가지 체크포인트

1. `/api/index` 호출 후 `vector_store` 테이블에 임베딩이 적재됨 (`SELECT count(*)`)
2. "환불 정책 알려줘" 질의 시 응답에 정책 문장 일부가 그대로 인용됨
3. 정책에 없는 질문(예: "오늘 날씨 알려줘")에는 "모릅니다" 류 응답 반환
4. `similarityThreshold: 0.3`을 0.7로 올리면 검색 누락이 늘어나 "모릅니다" 응답이 증가
5. `topK: 4`를 1로 낮추면 답이 단편적이 되고, 8로 올리면 토큰 사용이 증가

## 한계

- SafeGuard, 모더레이션, 로깅 어드바이저가 아직 없다 (step5/6에서 추가)
