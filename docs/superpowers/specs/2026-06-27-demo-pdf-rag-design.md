# demo-pdf-rag 설계 문서

- 작성일: 2026-06-27
- 상태: 승인됨 (구현 계획 작성 대기)

## 1. 목적 / 한 줄 요약

PDF를 업로드하면 즉석에서 임베딩·인덱싱하여, 그 내용만 근거로 답하는 **독립 실행형 Spring AI RAG 챗봇** 데모. KOSTA Spring AI 강의 저장소의 `demo-pdf-rag/` 폴더로 추가한다.

기존 `step4-rag`가 **정적** 정책 문서(txt/md)를 섹션 청킹해 RAG를 보여줬다면, 이 데모는 **사용자가 동적으로 올린 PDF**를 대상으로 한다는 점이 다르다.

## 2. 범위 (결정 사항)

- **최소 독립형**: H2 · JDBC · JPA · 주문/고객 도메인 **전부 제외**. PDF 업로드 + SimpleVectorStore RAG + 채팅만.
- **UI**: 단일 `index.html` 정적 페이지 (PDF 드래그&드롭 업로드 + 채팅).
- **폴더명**: `demo-pdf-rag`.
- **영속화**: 파일 기반 (`./data/vector-store.json`).
- **중복 방지**: 동일 내용 PDF는 재인덱싱하지 않음 (SHA-256 내용 해시 기준).

명시적 비범위(YAGNI): 멀티턴 대화 메모리, 인증, 다중 사용자 격리, 외부 벡터 DB, PDF 외 포맷(docx/hwp 등).

## 3. 아키텍처 / 스택

- Spring Boot 3.5.0 + Spring AI 1.1.7, Java 21, Gradle(Kotlin DSL). (저장소 기존 step들과 동일 버전.)
- 외부 인프라 0개. SimpleVectorStore는 spring-ai-core 내장이라 별도 벡터 DB 불필요.
- 패키지 베이스: `com.kosta.demo.pdfrag`.

### 의존성 (build.gradle.kts)

- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.ai:spring-ai-starter-model-openai` — Chat + Embedding
- `org.springframework.ai:spring-ai-pdf-document-reader` — PDF 파싱(PDFBox 기반 `PagePdfDocumentReader`)
- `org.springframework.ai:spring-ai-advisors-vector-store` — `QuestionAnswerAdvisor`
- 테스트: `spring-boot-starter-test`, `junit-platform-launcher`

> 정확한 아티팩트 좌표·클래스 패키지·`QuestionAnswerAdvisor`/`PagePdfDocumentReader`/`TokenTextSplitter` API 시그니처는 구현 단계에서 context7로 Spring AI 1.1.x 규격을 확인해 고정한다.

## 4. 컴포넌트 (각 단일 책임)

| 컴포넌트 | 역할 | 의존 |
|---|---|---|
| `RagConfig` (config) | `SimpleVectorStore`(파일 영속) 빈, 시스템 프롬프트 박은 `ChatClient` 빈(`QuestionAnswerAdvisor` 기본 어드바이저로 등록) | `EmbeddingModel`, `ChatClient.Builder` |
| `IngestManifest` (ingest) | 인덱싱 이력(`hash → 항목`)을 메모리에 보유하고 `./data/indexed-files.json`으로 영속화. 조회/추가/전체삭제 제공 | Jackson `ObjectMapper` |
| `PdfIngestService` (ingest) | 업로드 PDF → SHA-256 계산 → 매니페스트 중복 검사 → `PagePdfDocumentReader` 파싱 → `TokenTextSplitter` 청킹 → 메타데이터 스탬프 → `VectorStore.add()` → 두 파일 저장 | `VectorStore`, `IngestManifest` |
| `ChatController` (web) | REST 엔드포인트. 업로드/채팅/초기화/현황 | `PdfIngestService`, `ChatClient`, `VectorStore` |
| `static/index.html` | PDF 드래그&드롭 업로드 + 채팅 UI + 근거(참조 문서) 표시 | — |

### 매니페스트 항목 스키마 (`indexed-files.json`)

```json
{
  "<sha256-hex>": {
    "filename": "report.pdf",
    "chunks": 42,
    "documentIds": ["<uuid>", "..."],
    "indexedAt": "2026-06-27T10:00:00Z"
  }
}
```

`documentIds`는 `reset` 시 `vectorStore.delete(ids)`로 정확히 제거하기 위해 보관한다(SimpleVectorStore에 public `clear()`가 없음).

## 5. REST API

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/pdf` | multipart `file` | `{status:"indexed", filename, pages, chunks}` 또는 `{status:"duplicate", filename, chunks:0}` |
| POST | `/api/chat` | `{message}` | `{content, sources:[{filename, page}]}` |
| GET | `/api/status` | — | `{files:[{filename, chunks, indexedAt}], totalChunks}` |
| POST | `/api/reset` | — | `{status:"ok", removed:<chunkCount>}` |
| GET | `/` | — | `index.html` |

## 6. 데이터 흐름

### 업로드 (`POST /api/pdf`)

1. 업로드 검증: 비어 있지 않고 `Content-Type`/확장자가 PDF인지 확인. 아니면 400.
2. 업로드 바이트의 **SHA-256** 계산.
3. 매니페스트에 해시 존재 → **인덱싱 건너뜀**, `{status:"duplicate"}` 반환.
4. 없으면 `PagePdfDocumentReader`로 페이지별 `Document` 추출 → `TokenTextSplitter`(기본 ~800토큰)로 청킹.
5. 각 청크 메타데이터에 `content_hash`, `filename`, `page`(리더가 제공) 기록.
6. `vectorStore.add(chunks)` → 생성된 `documentIds` 수집.
7. 매니페스트에 항목 추가 → `vector-store.json` + `indexed-files.json` 저장.
8. `{status:"indexed", filename, pages, chunks}` 반환.

### 질의 (`POST /api/chat`)

1. 매니페스트가 비어 있으면(= 인덱싱된 문서 없음) "먼저 PDF를 업로드해 주세요" 안내 반환(추측 답변 방지 가드).
2. `QuestionAnswerAdvisor`가 유사도 검색(topK 4, `similarityThreshold 0.3`) → 검색된 청크만 컨텍스트로 LLM 답변.
   - `similarityThreshold 0.3`은 한국어 임베딩(`text-embedding-3-small`)에서 cosine 유사도가 낮게 나오는 경향을 보정한 값(step4에서 검증).
3. 답변과 함께 참조 청크의 `filename`·`page`를 `sources`로 반환해 UI에 노출.

### 초기화 (`POST /api/reset`)

매니페스트의 모든 `documentIds` 합집합을 `vectorStore.delete(ids)`로 제거 → 매니페스트 비움 → 두 파일 저장.

## 7. 설정 (application.yml)

- `server.port: 8080`
- `spring.ai.openai.api-key: ${OPENAI_API_KEY:}`
- chat 모델 `gpt-5.4-mini`, temperature `0.3`; embedding 모델 `text-embedding-3-small` (저장소 기존 step과 동일).
- `spring.servlet.multipart.max-file-size` / `max-request-size`를 PDF 업로드에 맞게 상향(예: 20MB).
- `agent.rag.persist-path: ./data/vector-store.json`, `agent.rag.manifest-path: ./data/indexed-files.json`.

## 8. 에러 처리

- 비-PDF·빈 파일 업로드 → 400 + 한국어 메시지.
- PDF 파싱 실패 → 500 + 원인 메시지.
- `OPENAI_API_KEY` 미설정 → 부팅 로그 경고(앱은 기동되되 채팅 시 명확한 오류).
- 인덱싱 0건 상태 질의 → 가드 메시지(위 6.질의 1번).

## 9. 테스트 전략

- `PdfIngestServiceTest`: 샘플 PDF 인제스트 → 청크 생성 검증, **동일 바이트 재인제스트 시 `duplicate` 반환·재적재 없음** 검증, 메타데이터(`content_hash`) 스탬프 검증. (임베딩/`VectorStore.add`는 목 또는 인메모리 더블로 격리.)
- `ChatControllerTest`(MockMvc): 업로드 경로, 빈 스토어 가드, `reset` 경로. 임베딩·LLM 호출은 목 처리.
- `IngestManifestTest`: 저장→로드 라운드트립, 중복 키 검사, 전체 삭제.

## 10. 산출물

- `demo-pdf-rag/` 전체 Gradle 프로젝트(소스, `application.yml`, `static/index.html`, 테스트).
- `demo-pdf-rag/README.md`: 실행법(`OPENAI_API_KEY` 설정 → `./gradlew bootRun` → 브라우저), API 설명.
- 루트 `README.md`에 demo 항목 한 줄 추가(선택).
- 테스트용 샘플 PDF 1개 동봉 또는 생성 안내.

## 11. 핵심 설계 결정 요약

- **파일 영속 SimpleVectorStore**: 재시작 후에도 인덱스 유지(사용자 요구).
- **SHA-256 내용 해시 중복 방지**: 파일명이 아닌 바이트 기준 → 이름만 다른 동일 PDF도 차단, 이름 같아도 내용 다르면 신규 인덱싱(사용자 요구).
- **매니페스트로 dedup·status·reset 일괄 지원**: dedup 상태를 별도 파일로 명시 관리, `documentIds` 보관으로 정확한 삭제 가능.
- **QuestionAnswerAdvisor**: Spring AI에서 가장 단순한 RAG 어드바이저 → "SimpleRAG" 요구에 부합.
- **TokenTextSplitter**: 구조를 알 수 없는 임의 PDF에 적합한 범용 청킹(step4의 섹션 청킹과 대비).
