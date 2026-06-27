# demo-pdf-rag — PDF SimpleRAG 챗봇

PDF를 업로드하면 즉석에서 임베딩·인덱싱하여, 그 내용만 근거로 답하는 독립 실행형 Spring AI RAG 챗봇 데모입니다.

## 특징

- **SimpleVectorStore** 파일 영속(`./data/vector-store.json`) — 외부 벡터 DB 불필요, 재시작 후에도 인덱스 유지
- **중복 인덱싱 방지** — 업로드 PDF의 SHA-256 내용 해시로 판정(`./data/indexed-files.json`). 같은 파일을 다른 이름으로 올려도 차단
- **근거 표시** — 답변과 함께 참조한 파일·페이지 노출
- **단일 웹 UI** — 드래그&드롭 업로드 + 채팅

## 사전 준비

- Java 21
- `OPENAI_API_KEY` 환경변수

```bash
export OPENAI_API_KEY=sk-...
```

## 실행

```bash
cd demo-pdf-rag
./gradlew bootRun
```

브라우저에서 `http://localhost:8080` 접속 → PDF 업로드 → 질문.

> 테스트용 PDF는 아무 PDF나 사용하면 됩니다. 한국어 PDF로 한국어 질문 시 가장 자연스럽습니다.

## REST API

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/pdf` | multipart `file` 업로드 → `{status, filename, pages, chunks}` (중복이면 `status:"duplicate"`) |
| POST | `/api/chat` | `{message}` → `{content, sources:[{filename, page}]}` |
| GET | `/api/status` | 인덱싱 현황 `{files, totalChunks}` |
| POST | `/api/reset` | 인덱스 초기화 |

### 예시

```bash
curl -F "file=@sample.pdf" http://localhost:8080/api/pdf
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"이 문서의 핵심 요약을 알려줘"}'
```

## 동작 원리

1. 업로드 PDF → `PagePdfDocumentReader`(PDFBox)로 페이지별 추출
2. `TokenTextSplitter`로 청킹(기본 ~800토큰)
3. 각 청크에 `content_hash`·`filename`·`page_number` 메타 스탬프 후 `SimpleVectorStore`에 적재
4. 질의 시 `QuestionAnswerAdvisor`가 유사도 검색(topK 4, threshold 0.3)으로 컨텍스트를 끼워 답변

## 구조

```
src/main/java/com/kosta/demo/pdfrag/
├─ PdfRagApplication.java        # 진입점
├─ config/RagConfig.java         # SimpleVectorStore + QuestionAnswerAdvisor ChatClient 빈
├─ ingest/
│  ├─ PdfTextExtractor.java      # PagePdfDocumentReader 어댑터
│  ├─ IngestManifest.java        # SHA-256 인덱싱 이력(중복 방지·reset)
│  └─ PdfIngestService.java      # 파싱·청킹·중복방지·영속 오케스트레이션
└─ web/
   ├─ ChatController.java        # REST 엔드포인트
   └─ RagQueryService.java       # 가드 + RAG 답변 + 근거
src/main/resources/static/index.html  # 단일 페이지 UI
```
