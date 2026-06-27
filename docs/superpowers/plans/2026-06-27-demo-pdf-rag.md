# demo-pdf-rag Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PDF를 업로드하면 즉석에서 임베딩·인덱싱하여 그 내용만 근거로 답하는 독립 실행형 Spring AI RAG 챗봇 데모(`demo-pdf-rag/`).

**Architecture:** 외부 인프라 0개. 업로드 PDF → `PagePdfDocumentReader`(페이지별 Document) → `TokenTextSplitter` 청킹 → 파일 영속 `SimpleVectorStore`. 질의는 `QuestionAnswerAdvisor`가 유사도 검색으로 컨텍스트를 끼워 LLM 답변. 동일 내용 PDF는 SHA-256 해시 매니페스트(`indexed-files.json`)로 재인덱싱 차단. PDF 파싱은 얇은 어댑터(`PdfTextExtractor`) 뒤에 격리하여 오케스트레이션 로직을 단위 테스트한다.

**Tech Stack:** Spring Boot 3.5.0, Spring AI 1.1.7, Java 21, Gradle(Kotlin DSL), JUnit 5 + Mockito, vanilla JS 단일 페이지.

## Global Constraints

- Spring Boot `3.5.0`, Spring AI BOM `1.1.7`, Java toolchain `21`. (저장소 기존 step과 동일.)
- 패키지 베이스: `com.kosta.demo.pdfrag` (하위 `config`, `ingest`, `web`).
- 외부 DB/JPA/도메인 없음. 의존성은 `spring-boot-starter-web`, `spring-ai-starter-model-openai`, `spring-ai-pdf-document-reader`, `spring-ai-advisors-vector-store`, 테스트 스타터만.
- OpenAI chat 모델 `gpt-5.4-mini`(temperature 0.3), embedding 모델 `text-embedding-3-small`. API 키는 `${OPENAI_API_KEY:}`.
- 영속 파일: `./data/vector-store.json`(임베딩), `./data/indexed-files.json`(매니페스트).
- RAG 검색: `topK 4`, `similarityThreshold 0.3`(한국어 임베딩 보정값, step4 검증).
- 모든 사용자 노출 문자열은 한국어 격식체.
- 빌드/테스트 명령은 `demo-pdf-rag/` 폴더 안에서 `./gradlew` 사용.

---

### Task 1: 프로젝트 스캐폴드 + 컨텍스트 로드 스모크 테스트

**Files:**
- Create: `demo-pdf-rag/settings.gradle.kts`
- Create: `demo-pdf-rag/build.gradle.kts`
- Create: `demo-pdf-rag/.gitignore`
- Create: `demo-pdf-rag/gradlew`, `demo-pdf-rag/gradlew.bat`, `demo-pdf-rag/gradle/wrapper/gradle-wrapper.jar`, `demo-pdf-rag/gradle/wrapper/gradle-wrapper.properties` (step4에서 복사)
- Create: `demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/PdfRagApplication.java`
- Create: `demo-pdf-rag/src/main/resources/application.yml`
- Create: `demo-pdf-rag/src/test/resources/application-test.yml`
- Test: `demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/PdfRagApplicationTest.java`

**Interfaces:**
- Produces: `PdfRagApplication` (Spring Boot 진입점), Gradle 빌드, 테스트 프로파일.

- [ ] **Step 1: Gradle 래퍼/설정 파일 복사 및 생성**

```bash
mkdir -p demo-pdf-rag/gradle/wrapper
mkdir -p demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag
mkdir -p demo-pdf-rag/src/main/resources/static
mkdir -p demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag
mkdir -p demo-pdf-rag/src/test/resources
cp step4-rag/gradlew step4-rag/gradlew.bat demo-pdf-rag/
cp step4-rag/gradle/wrapper/gradle-wrapper.jar step4-rag/gradle/wrapper/gradle-wrapper.properties demo-pdf-rag/gradle/wrapper/
cp step4-rag/.gitignore demo-pdf-rag/.gitignore
chmod +x demo-pdf-rag/gradlew
```

`demo-pdf-rag/settings.gradle.kts`:

```kotlin
rootProject.name = "demo-pdf-rag"
```

`demo-pdf-rag/build.gradle.kts`:

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.kosta.demo"
version = "0.0.1-SNAPSHOT"
description = "Spring AI PDF SimpleRAG Chatbot Demo"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "1.1.7"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Spring AI - Chat + Embedding (OpenAI)
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // Spring AI - PDF 문서 리더 (PagePdfDocumentReader, PDFBox 기반)
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")

    // Spring AI - RAG 어드바이저 (QuestionAnswerAdvisor)
    implementation("org.springframework.ai:spring-ai-advisors-vector-store")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 2: 진입점 클래스 작성**

`demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/PdfRagApplication.java`:

```java
package com.kosta.demo.pdfrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PdfRagApplication {
    public static void main(String[] args) {
        SpringApplication.run(PdfRagApplication.class, args);
    }
}
```

- [ ] **Step 3: 설정 파일 작성**

`demo-pdf-rag/src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  application:
    name: demo-pdf-rag
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        options:
          model: gpt-5.4-mini
          temperature: 0.3
      embedding:
        options:
          model: text-embedding-3-small

logging:
  level:
    org.springframework.ai: INFO
    com.kosta.demo.pdfrag: DEBUG

agent:
  rag:
    persist-path: ./data/vector-store.json
    manifest-path: ./data/indexed-files.json
```

`demo-pdf-rag/src/test/resources/application-test.yml`:

```yaml
spring:
  ai:
    openai:
      api-key: test-key-not-used-in-unit-tests
agent:
  rag:
    persist-path: ./build/test-data/vector-store.json
    manifest-path: ./build/test-data/indexed-files.json
```

- [ ] **Step 4: 컨텍스트 로드 스모크 테스트 작성 (실패 확인용)**

`demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/PdfRagApplicationTest.java`:

```java
package com.kosta.demo.pdfrag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PdfRagApplicationTest {

    @Test
    void contextLoads() {
        // 컨텍스트가 기동되면 통과 (OpenAI 더미 키로 빈만 생성, 네트워크 호출 없음)
    }
}
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `cd demo-pdf-rag && ./gradlew test`
Expected: BUILD SUCCESSFUL, `PdfRagApplicationTest.contextLoads` PASS. (첫 실행은 의존성 다운로드로 시간이 걸릴 수 있음.)

- [ ] **Step 6: 커밋**

```bash
cd /Users/existmaster/frentis-repos/spring-ai-agent-kosta
git add demo-pdf-rag
git commit -m "feat(demo-pdf-rag): Gradle 스캐폴드 + 컨텍스트 로드 스모크 테스트"
```

---

### Task 2: RagConfig — SimpleVectorStore + ChatClient 빈

**Files:**
- Create: `demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/config/RagConfig.java`
- Test: `demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/config/RagConfigTest.java`

**Interfaces:**
- Consumes: `EmbeddingModel`, `ChatClient.Builder` (OpenAI 자동 구성).
- Produces: `VectorStore` 빈(파일 영속 `SimpleVectorStore`), `ChatClient` 빈 이름 `pdfRagChatClient`(시스템 프롬프트 + `QuestionAnswerAdvisor` 기본 등록, topK 4 / threshold 0.3).

- [ ] **Step 1: 빈 검증 테스트 작성 (실패 확인용)**

`demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/config/RagConfigTest.java`:

```java
package com.kosta.demo.pdfrag.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RagConfigTest {

    @Autowired
    private VectorStore vectorStore;

    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("pdfRagChatClient")
    private ChatClient pdfRagChatClient;

    @Test
    void RAG_빈이_등록된다() {
        assertThat(vectorStore).isNotNull();
        assertThat(pdfRagChatClient).isNotNull();
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd demo-pdf-rag && ./gradlew test --tests '*RagConfigTest'`
Expected: FAIL — `pdfRagChatClient` 빈 없음 / 주입 실패.

- [ ] **Step 3: RagConfig 구현**

`demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/config/RagConfig.java`:

```java
package com.kosta.demo.pdfrag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * PDF SimpleRAG 챗봇의 핵심 빈.
 * - VectorStore: 파일 영속 SimpleVectorStore (부팅 시 기존 인덱스 로드)
 * - ChatClient: 시스템 프롬프트 + QuestionAnswerAdvisor(RAG) 기본 등록
 */
@Configuration
public class RagConfig {

    private static final String SYSTEM_PROMPT = """
            당신은 업로드된 PDF 문서를 근거로 답하는 AI 문서 비서입니다.
            - 항상 한국어 격식체로 답합니다.
            - 검색되어 제공된 문서 내용만을 근거로 답합니다.
            - 근거가 부족하면 "제공된 문서에서 해당 내용을 찾을 수 없습니다."라고 답하고 임의로 추측하지 않습니다.
            - 가능하면 답변 끝에 근거가 된 내용을 간단히 요약해 덧붙입니다.
            """;

    @Bean
    public VectorStore vectorStore(
            EmbeddingModel embeddingModel,
            @Value("${agent.rag.persist-path:./data/vector-store.json}") String persistPath) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File persistFile = new File(persistPath);
        if (persistFile.exists()) {
            store.load(persistFile);
        }
        return store;
    }

    @Bean
    public ChatClient pdfRagChatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(4)
                        .similarityThreshold(0.3)
                        .build())
                .build();
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(qaAdvisor)
                .build();
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `cd demo-pdf-rag && ./gradlew test --tests '*RagConfigTest'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/config demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/config
git commit -m "feat(demo-pdf-rag): SimpleVectorStore + QuestionAnswerAdvisor ChatClient 빈"
```

---

### Task 3: IngestManifest — SHA-256 인덱싱 이력 파일 관리

**Files:**
- Create: `demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/ingest/IngestManifest.java`
- Test: `demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/ingest/IngestManifestTest.java`

**Interfaces:**
- Produces:
  - `IngestManifest.Entry(String filename, int chunks, List<String> documentIds, String indexedAt)` (record)
  - `boolean contains(String hash)`
  - `void put(String hash, Entry entry)` (즉시 파일 저장)
  - `java.util.Collection<Entry> entries()`
  - `java.util.List<String> allDocumentIds()`
  - `boolean isEmpty()`
  - `void clear()` (즉시 파일 저장)

- [ ] **Step 1: 매니페스트 테스트 작성 (실패 확인용)**

`demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/ingest/IngestManifestTest.java`:

```java
package com.kosta.demo.pdfrag.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestManifestTest {

    @TempDir
    Path tempDir;

    private IngestManifest newManifest() {
        return new IngestManifest(tempDir.resolve("indexed-files.json").toString());
    }

    @Test
    void 처음엔_비어있다() {
        IngestManifest m = newManifest();
        assertThat(m.isEmpty()).isTrue();
        assertThat(m.contains("abc")).isFalse();
    }

    @Test
    void put_후_contains와_entries에_반영된다() {
        IngestManifest m = newManifest();
        m.put("hash1", new IngestManifest.Entry("a.pdf", 3, List.of("id1", "id2", "id3"), "2026-06-27T00:00:00Z"));

        assertThat(m.contains("hash1")).isTrue();
        assertThat(m.isEmpty()).isFalse();
        assertThat(m.entries()).hasSize(1);
        assertThat(m.allDocumentIds()).containsExactlyInAnyOrder("id1", "id2", "id3");
    }

    @Test
    void 파일에_저장되고_새_인스턴스에서_로드된다() {
        IngestManifest m1 = newManifest();
        m1.put("hash1", new IngestManifest.Entry("a.pdf", 2, List.of("id1", "id2"), "2026-06-27T00:00:00Z"));

        IngestManifest m2 = newManifest(); // 같은 경로로 재로드
        assertThat(m2.contains("hash1")).isTrue();
        assertThat(m2.allDocumentIds()).containsExactlyInAnyOrder("id1", "id2");
    }

    @Test
    void clear_후_비워지고_파일에도_반영된다() {
        IngestManifest m1 = newManifest();
        m1.put("hash1", new IngestManifest.Entry("a.pdf", 1, List.of("id1"), "2026-06-27T00:00:00Z"));
        m1.clear();

        assertThat(m1.isEmpty()).isTrue();
        IngestManifest m2 = newManifest();
        assertThat(m2.isEmpty()).isTrue();
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd demo-pdf-rag && ./gradlew test --tests '*IngestManifestTest'`
Expected: FAIL — `IngestManifest` 클래스 없음(컴파일 에러).

- [ ] **Step 3: IngestManifest 구현**

`demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/ingest/IngestManifest.java`:

```java
package com.kosta.demo.pdfrag.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 인덱싱 이력 관리.
 * key = 업로드 PDF 내용의 SHA-256(hex), value = {@link Entry}.
 * 변경 시마다 JSON 파일로 영속화하여 재시작 후에도 중복 판정과 reset이 가능하다.
 */
@Component
public class IngestManifest {

    /** 인덱싱된 파일 1건의 이력. documentIds는 reset 시 정확한 삭제에 사용. */
    public record Entry(String filename, int chunks, List<String> documentIds, String indexedAt) {}

    private final ObjectMapper mapper = new ObjectMapper();
    private final File file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public IngestManifest(@Value("${agent.rag.manifest-path:./data/indexed-files.json}") String manifestPath) {
        this.file = new File(manifestPath);
        load();
    }

    private void load() {
        if (!file.exists()) return;
        try {
            Map<String, Entry> loaded = mapper.readValue(file, new TypeReference<Map<String, Entry>>() {});
            entries.clear();
            entries.putAll(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("매니페스트 로드 실패: " + file, e);
        }
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, entries);
        } catch (IOException e) {
            throw new UncheckedIOException("매니페스트 저장 실패: " + file, e);
        }
    }

    public synchronized boolean contains(String hash) {
        return entries.containsKey(hash);
    }

    public synchronized void put(String hash, Entry entry) {
        entries.put(hash, entry);
        save();
    }

    public synchronized Collection<Entry> entries() {
        return new ArrayList<>(entries.values());
    }

    public synchronized List<String> allDocumentIds() {
        List<String> ids = new ArrayList<>();
        for (Entry e : entries.values()) ids.addAll(e.documentIds());
        return ids;
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    public synchronized void clear() {
        entries.clear();
        save();
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `cd demo-pdf-rag && ./gradlew test --tests '*IngestManifestTest'`
Expected: PASS (4건).

- [ ] **Step 5: 커밋**

```bash
git add demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/ingest demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/ingest
git commit -m "feat(demo-pdf-rag): SHA-256 인덱싱 매니페스트(IngestManifest)"
```

---

### Task 4: PdfIngestService — 파싱·청킹·중복방지·영속 오케스트레이션

**Files:**
- Create: `demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/ingest/PdfTextExtractor.java`
- Create: `demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/ingest/PdfIngestService.java`
- Test: `demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/ingest/PdfIngestServiceTest.java`

**Interfaces:**
- `PdfTextExtractor.extract(byte[] bytes, String filename) -> List<Document>` (PagePdfDocumentReader 얇은 어댑터, 단위 테스트에서 mock).
- `PdfIngestService`:
  - `IngestResult ingest(byte[] bytes, String filename)` → `IngestResult(String status, String filename, int pages, int chunks)` status ∈ {"indexed","duplicate"}.
  - `int reset()` → 제거된 청크 수.
  - `List<FileInfo> status()` → `FileInfo(String filename, int chunks, String indexedAt)`.
  - `boolean hasDocuments()` → 인덱싱된 문서 존재 여부(질의 가드용).
- Consumes: `VectorStore`, `PdfTextExtractor`, `IngestManifest`.

**참고:** 각 청크 메타데이터에 `content_hash`, `filename` 스탬프. 페이지 번호는 `PagePdfDocumentReader`가 넣는 `page_number` 키가 청킹 후에도 보존된다(TokenTextSplitter가 메타데이터 복사).

- [ ] **Step 1: PdfTextExtractor 어댑터 작성**

> 얇은 I/O 어댑터(라이브러리 위임)라 별도 단위 테스트 없이 런타임/수동 검증한다. 로직은 모두 PdfIngestService에 있고 그쪽을 TDD한다.

`demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/ingest/PdfTextExtractor.java`:

```java
package com.kosta.demo.pdfrag.ingest;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 업로드된 PDF 바이트를 페이지별 {@link Document}로 변환한다.
 * PagePdfDocumentReader(PDFBox)를 감싼 얇은 어댑터 — 테스트에서는 mock으로 대체된다.
 */
@Component
public class PdfTextExtractor {

    public List<Document> extract(byte[] bytes, String filename) {
        Resource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new PagePdfDocumentReader(resource).read();
    }
}
```

- [ ] **Step 2: PdfIngestService 테스트 작성 (실패 확인용)**

`demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/ingest/PdfIngestServiceTest.java`:

```java
package com.kosta.demo.pdfrag.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class PdfIngestServiceTest {

    @TempDir
    Path tempDir;

    private VectorStore vectorStore;
    private PdfTextExtractor extractor;
    private IngestManifest manifest;
    private PdfIngestService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        extractor = mock(PdfTextExtractor.class);
        manifest = new IngestManifest(tempDir.resolve("indexed-files.json").toString());
        // 두 페이지짜리 PDF를 흉내내는 고정 Document 반환 (page_number 메타 포함)
        when(extractor.extract(any(), anyString())).thenReturn(List.of(
                new Document("첫 번째 페이지 본문입니다.", Map.of("page_number", 1)),
                new Document("두 번째 페이지 본문입니다.", Map.of("page_number", 2))
        ));
        service = new PdfIngestService(
                vectorStore, extractor, manifest,
                tempDir.resolve("vector-store.json").toString());
    }

    @Test
    void 새_PDF는_인덱싱되고_청크에_content_hash가_스탬프된다() {
        byte[] bytes = "%PDF fake bytes A".getBytes(StandardCharsets.UTF_8);

        PdfIngestService.IngestResult result = service.ingest(bytes, "a.pdf");

        assertThat(result.status()).isEqualTo("indexed");
        assertThat(result.filename()).isEqualTo("a.pdf");
        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.chunks()).isGreaterThan(0);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(captor.capture());
        for (Document d : captor.getValue()) {
            assertThat(d.getMetadata()).containsKey("content_hash");
            assertThat(d.getMetadata().get("filename")).isEqualTo("a.pdf");
        }
        assertThat(manifest.isEmpty()).isFalse();
    }

    @Test
    void 동일_내용_PDF를_다시_올리면_중복으로_건너뛴다() {
        byte[] bytes = "%PDF fake bytes A".getBytes(StandardCharsets.UTF_8);
        service.ingest(bytes, "a.pdf");

        // 파일명이 달라도 내용(바이트)이 같으면 중복
        PdfIngestService.IngestResult again = service.ingest(bytes, "renamed.pdf");

        assertThat(again.status()).isEqualTo("duplicate");
        assertThat(again.chunks()).isZero();
        // add는 최초 1회만 호출, extractor도 최초 1회만
        verify(vectorStore, times(1)).add(anyList());
        verify(extractor, times(1)).extract(any(), anyString());
    }

    @Test
    void reset은_모든_documentId를_삭제하고_매니페스트를_비운다() {
        service.ingest("%PDF A".getBytes(StandardCharsets.UTF_8), "a.pdf");
        service.ingest("%PDF B".getBytes(StandardCharsets.UTF_8), "b.pdf");

        int removed = service.reset();

        assertThat(removed).isGreaterThan(0);
        assertThat(manifest.isEmpty()).isTrue();
        assertThat(service.hasDocuments()).isFalse();
        verify(vectorStore, times(1)).delete(anyList());
    }
}
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `cd demo-pdf-rag && ./gradlew test --tests '*PdfIngestServiceTest'`
Expected: FAIL — `PdfIngestService` 없음(컴파일 에러).

- [ ] **Step 4: PdfIngestService 구현**

`demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/ingest/PdfIngestService.java`:

```java
package com.kosta.demo.pdfrag.ingest;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 업로드 PDF를 인덱싱하는 오케스트레이션 서비스.
 * 흐름: SHA-256 해시 → 매니페스트 중복검사 → 추출 → 청킹 → 메타 스탬프 → add → 영속.
 */
@Service
public class PdfIngestService {

    /** status ∈ {"indexed","duplicate"}. duplicate면 pages·chunks는 0. */
    public record IngestResult(String status, String filename, int pages, int chunks) {}

    public record FileInfo(String filename, int chunks, String indexedAt) {}

    private final VectorStore vectorStore;
    private final PdfTextExtractor extractor;
    private final IngestManifest manifest;
    private final String persistPath;
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    public PdfIngestService(
            VectorStore vectorStore,
            PdfTextExtractor extractor,
            IngestManifest manifest,
            @Value("${agent.rag.persist-path:./data/vector-store.json}") String persistPath) {
        this.vectorStore = vectorStore;
        this.extractor = extractor;
        this.manifest = manifest;
        this.persistPath = persistPath;
    }

    public IngestResult ingest(byte[] bytes, String filename) {
        String hash = sha256(bytes);
        if (manifest.contains(hash)) {
            return new IngestResult("duplicate", filename, 0, 0);
        }

        List<Document> pages = extractor.extract(bytes, filename);
        List<Document> chunks = splitter.apply(pages);

        List<String> ids = new ArrayList<>();
        for (Document c : chunks) {
            c.getMetadata().put("content_hash", hash);
            c.getMetadata().put("filename", filename);
            ids.add(c.getId());
        }

        vectorStore.add(chunks);
        persist();
        manifest.put(hash, new IngestManifest.Entry(filename, chunks.size(), ids, Instant.now().toString()));

        return new IngestResult("indexed", filename, pages.size(), chunks.size());
    }

    public int reset() {
        List<String> ids = manifest.allDocumentIds();
        if (!ids.isEmpty()) {
            vectorStore.delete(ids);
        }
        manifest.clear();
        persist();
        return ids.size();
    }

    public List<FileInfo> status() {
        List<FileInfo> out = new ArrayList<>();
        for (IngestManifest.Entry e : manifest.entries()) {
            out.add(new FileInfo(e.filename(), e.chunks(), e.indexedAt()));
        }
        return out;
    }

    public boolean hasDocuments() {
        return !manifest.isEmpty();
    }

    private void persist() {
        if (vectorStore instanceof SimpleVectorStore svs) {
            File f = new File(persistPath);
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            svs.save(f);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }
}
```

> 주의: 테스트에서 `vectorStore`는 mock이라 `persist()`의 `instanceof SimpleVectorStore`가 false → 저장 스킵(파일 IO 없음). 런타임에서는 실제 SimpleVectorStore라 저장된다.

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `cd demo-pdf-rag && ./gradlew test --tests '*PdfIngestServiceTest'`
Expected: PASS (3건).

- [ ] **Step 6: 커밋**

```bash
git add demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/ingest demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/ingest
git commit -m "feat(demo-pdf-rag): PDF 인제스트 서비스(파싱·청킹·중복방지·영속)"
```

---

### Task 5: RagQueryService — 가드 + RAG 답변 + 근거(sources)

**Files:**
- Create: `demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/web/RagQueryService.java`
- Test: `demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/web/RagQueryServiceTest.java`

**Interfaces:**
- `RagQueryService.answer(String message) -> AnswerResult(String content, List<Source> sources)`, `Source(String filename, Integer page)`.
- 인덱싱된 문서가 없으면(`IngestManifest.isEmpty()`) ChatClient를 호출하지 않고 안내 메시지 반환.
- Consumes: `ChatClient`(이름 `pdfRagChatClient`), `VectorStore`, `IngestManifest`.

> 설계 결정: 답변은 `QuestionAnswerAdvisor`(ChatClient 기본 어드바이저)가 내부 검색으로 생성하고, 근거 표시는 동일 파라미터의 `vectorStore.similaritySearch`로 별도 조회한다. 임베딩 호출이 한 번 더 들지만(저비용) 어드바이저 내부 메타데이터 계약에 의존하지 않아 버전 견고성이 높다.

- [ ] **Step 1: RagQueryService 테스트 작성 (실패 확인용)**

`demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/web/RagQueryServiceTest.java`:

```java
package com.kosta.demo.pdfrag.web;

import com.kosta.demo.pdfrag.ingest.IngestManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RagQueryServiceTest {

    private ChatClient chatClient;
    private VectorStore vectorStore;
    private IngestManifest manifest;
    private RagQueryService service;

    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        vectorStore = mock(VectorStore.class);
        manifest = mock(IngestManifest.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        service = new RagQueryService(chatClient, vectorStore, manifest);
    }

    @Test
    void 인덱싱된_문서가_없으면_업로드_안내를_반환하고_LLM을_호출하지_않는다() {
        when(manifest.isEmpty()).thenReturn(true);

        RagQueryService.AnswerResult result = service.answer("질문");

        assertThat(result.content()).contains("PDF");
        assertThat(result.sources()).isEmpty();
        verify(chatClient, never()).prompt();
    }

    @Test
    void 문서가_있으면_LLM_답변과_근거를_반환한다() {
        when(manifest.isEmpty()).thenReturn(false);
        when(callSpec.content()).thenReturn("문서에 따르면 환불은 7일 이내 가능합니다.");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("환불 규정 ...", Map.of("filename", "policy.pdf", "page_number", 2)),
                new Document("환불 규정 계속 ...", Map.of("filename", "policy.pdf", "page_number", 2))
        ));

        RagQueryService.AnswerResult result = service.answer("환불 며칠 이내 가능?");

        assertThat(result.content()).contains("환불");
        // 동일 (filename, page) 는 중복 제거
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).filename()).isEqualTo("policy.pdf");
        assertThat(result.sources().get(0).page()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd demo-pdf-rag && ./gradlew test --tests '*RagQueryServiceTest'`
Expected: FAIL — `RagQueryService` 없음(컴파일 에러).

- [ ] **Step 3: RagQueryService 구현**

`demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/web/RagQueryService.java`:

```java
package com.kosta.demo.pdfrag.web;

import com.kosta.demo.pdfrag.ingest.IngestManifest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * RAG 질의 처리: 인덱싱 문서가 없으면 안내, 있으면 LLM 답변 + 근거(파일·페이지)를 반환한다.
 */
@Service
public class RagQueryService {

    private static final String NO_DOCS_GUIDE =
            "먼저 PDF 파일을 업로드해 주세요. 업로드된 문서를 근거로 답변해 드립니다.";

    public record Source(String filename, Integer page) {}

    public record AnswerResult(String content, List<Source> sources) {}

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final IngestManifest manifest;

    public RagQueryService(ChatClient pdfRagChatClient, VectorStore vectorStore, IngestManifest manifest) {
        this.chatClient = pdfRagChatClient;
        this.vectorStore = vectorStore;
        this.manifest = manifest;
    }

    public AnswerResult answer(String message) {
        if (manifest.isEmpty()) {
            return new AnswerResult(NO_DOCS_GUIDE, List.of());
        }

        // QuestionAnswerAdvisor(기본 어드바이저)가 검색→증강→생성을 수행한다.
        String content = chatClient.prompt()
                .user(message)
                .call()
                .content();

        return new AnswerResult(content, retrieveSources(message));
    }

    /** 근거 표시용 별도 검색(동일 파라미터). filename+page 중복 제거. */
    private List<Source> retrieveSources(String message) {
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(message)
                .topK(4)
                .similarityThreshold(0.3)
                .build());
        if (hits == null) return List.of();

        Set<String> seen = new LinkedHashSet<>();
        List<Source> sources = new ArrayList<>();
        for (Document d : hits) {
            String filename = asString(d.getMetadata().get("filename"));
            Integer page = asInteger(d.getMetadata().get("page_number"));
            String key = filename + "#" + page;
            if (seen.add(key)) {
                sources.add(new Source(filename, page));
            }
        }
        return sources;
    }

    private String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private Integer asInteger(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `cd demo-pdf-rag && ./gradlew test --tests '*RagQueryServiceTest'`
Expected: PASS (2건).

- [ ] **Step 5: 커밋**

```bash
git add demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/web/RagQueryService.java demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/web/RagQueryServiceTest.java
git commit -m "feat(demo-pdf-rag): RAG 질의 서비스(가드+답변+근거)"
```

---

### Task 6: ChatController — REST 엔드포인트 + 업로드 검증

**Files:**
- Create: `demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/web/ChatController.java`
- Test: `demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/web/ChatControllerTest.java`

**Interfaces:**
- `POST /api/pdf` (multipart `file`) → `IngestResult` JSON. 비-PDF/빈 파일 → 400.
- `POST /api/chat` (`{message}`) → `AnswerResult` JSON. message 공백 → 400.
- `POST /api/reset` → `{"status":"ok","removed":N}`.
- `GET /api/status` → `{"files":[...],"totalChunks":N}`.
- Consumes: `PdfIngestService`, `RagQueryService`.

- [ ] **Step 1: ChatController 테스트 작성 (실패 확인용)**

`demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/web/ChatControllerTest.java`:

```java
package com.kosta.demo.pdfrag.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.demo.pdfrag.ingest.PdfIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PdfIngestService ingestService;

    @MockBean
    private RagQueryService ragQueryService;

    @Test
    void PDF_업로드는_인제스트_결과를_반환한다() throws Exception {
        when(ingestService.ingest(any(), anyString()))
                .thenReturn(new PdfIngestService.IngestResult("indexed", "a.pdf", 3, 12));

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.pdf", "application/pdf", "%PDF-1.4 ...".getBytes());

        mvc.perform(multipart("/api/pdf").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("indexed"))
                .andExpect(jsonPath("$.chunks").value(12));
    }

    @Test
    void 비_PDF_업로드는_400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.txt", "text/plain", "hello".getBytes());

        mvc.perform(multipart("/api/pdf").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 채팅은_답변과_근거를_반환한다() throws Exception {
        when(ragQueryService.answer(anyString())).thenReturn(
                new RagQueryService.AnswerResult("답변입니다.",
                        List.of(new RagQueryService.Source("a.pdf", 2))));

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "질문"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("답변입니다."))
                .andExpect(jsonPath("$.sources[0].filename").value("a.pdf"))
                .andExpect(jsonPath("$.sources[0].page").value(2));
    }

    @Test
    void 빈_메시지_채팅은_400() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "  "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reset은_제거_개수를_반환한다() throws Exception {
        when(ingestService.reset()).thenReturn(5);

        mvc.perform(post("/api/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.removed").value(5));
    }

    @Test
    void status는_파일목록과_총청크를_반환한다() throws Exception {
        when(ingestService.status()).thenReturn(List.of(
                new PdfIngestService.FileInfo("a.pdf", 3, "2026-06-27T00:00:00Z"),
                new PdfIngestService.FileInfo("b.pdf", 4, "2026-06-27T00:01:00Z")));

        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChunks").value(7))
                .andExpect(jsonPath("$.files.length()").value(2));
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd demo-pdf-rag && ./gradlew test --tests '*ChatControllerTest'`
Expected: FAIL — `ChatController` 없음(컴파일 에러).

- [ ] **Step 3: ChatController 구현**

`demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/web/ChatController.java`:

```java
package com.kosta.demo.pdfrag.web;

import com.kosta.demo.pdfrag.ingest.PdfIngestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * PDF SimpleRAG 챗봇 엔드포인트.
 * - POST /api/pdf    : PDF 업로드 → 인덱싱(중복 자동 차단)
 * - POST /api/chat   : 업로드 문서 근거로 답변(+근거 표시)
 * - POST /api/reset  : 인덱스 초기화
 * - GET  /api/status : 인덱싱 현황
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final PdfIngestService ingestService;
    private final RagQueryService ragQueryService;

    public ChatController(PdfIngestService ingestService, RagQueryService ragQueryService) {
        this.ingestService = ingestService;
        this.ragQueryService = ragQueryService;
    }

    @PostMapping("/pdf")
    public PdfIngestService.IngestResult upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다.");
        }
        if (!isPdf(file)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 파일만 업로드할 수 있습니다.");
        }
        try {
            return ingestService.ingest(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 읽기 실패: " + e.getMessage(), e);
        }
    }

    @PostMapping("/chat")
    public RagQueryService.AnswerResult chat(@RequestBody ChatRequest req) {
        if (req == null || req.message() == null || req.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "질문 내용이 비어 있습니다.");
        }
        return ragQueryService.answer(req.message());
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        int removed = ingestService.reset();
        return Map.of("status", "ok", "removed", removed);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        List<PdfIngestService.FileInfo> files = ingestService.status();
        int totalChunks = files.stream().mapToInt(PdfIngestService.FileInfo::chunks).sum();
        return Map.of("files", files, "totalChunks", totalChunks);
    }

    private boolean isPdf(MultipartFile file) {
        String name = file.getOriginalFilename();
        String type = file.getContentType();
        boolean byName = name != null && name.toLowerCase().endsWith(".pdf");
        boolean byType = type != null && type.toLowerCase().contains("pdf");
        return byName || byType;
    }

    public record ChatRequest(String message) {}
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `cd demo-pdf-rag && ./gradlew test --tests '*ChatControllerTest'`
Expected: PASS (6건).

- [ ] **Step 5: 전체 테스트 실행**

Run: `cd demo-pdf-rag && ./gradlew test`
Expected: 전체 PASS (Application, RagConfig, IngestManifest, PdfIngestService, RagQueryService, ChatController).

- [ ] **Step 6: 커밋**

```bash
git add demo-pdf-rag/src/main/java/com/kosta/demo/pdfrag/web/ChatController.java demo-pdf-rag/src/test/java/com/kosta/demo/pdfrag/web/ChatControllerTest.java
git commit -m "feat(demo-pdf-rag): REST 컨트롤러(업로드·채팅·리셋·현황)"
```

---

### Task 7: 웹 UI — 단일 정적 페이지

**Files:**
- Create: `demo-pdf-rag/src/main/resources/static/index.html`

**Interfaces:**
- Consumes: `POST /api/pdf`, `POST /api/chat`, `POST /api/reset`, `GET /api/status`.

> 자동 테스트 없음(정적 프런트). Task 8에서 실제 기동 후 수동 검증.

- [ ] **Step 1: index.html 작성**

`demo-pdf-rag/src/main/resources/static/index.html`:

```html
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>PDF SimpleRAG 챗봇</title>
  <style>
    :root { --bg:#0f172a; --panel:#1e293b; --accent:#38bdf8; --text:#e2e8f0; --muted:#94a3b8; --user:#2563eb; }
    * { box-sizing: border-box; }
    body { margin:0; font-family: system-ui, "Apple SD Gothic Neo", sans-serif; background:var(--bg); color:var(--text); }
    header { padding:16px 24px; border-bottom:1px solid #334155; display:flex; align-items:center; gap:12px; }
    header h1 { font-size:18px; margin:0; }
    .wrap { display:flex; height:calc(100vh - 57px); }
    .sidebar { width:320px; border-right:1px solid #334155; padding:20px; overflow-y:auto; }
    .main { flex:1; display:flex; flex-direction:column; }
    .drop { border:2px dashed #475569; border-radius:12px; padding:24px; text-align:center; color:var(--muted); cursor:pointer; transition:.15s; }
    .drop.drag { border-color:var(--accent); color:var(--accent); background:#0b1220; }
    .files { margin-top:16px; }
    .file { background:var(--panel); border-radius:8px; padding:10px 12px; margin-bottom:8px; font-size:13px; }
    .file .meta { color:var(--muted); font-size:11px; margin-top:2px; }
    .btn { background:var(--accent); color:#04121f; border:0; border-radius:8px; padding:9px 14px; font-weight:600; cursor:pointer; }
    .btn.ghost { background:transparent; color:var(--muted); border:1px solid #475569; }
    .chat { flex:1; overflow-y:auto; padding:24px; display:flex; flex-direction:column; gap:14px; }
    .msg { max-width:760px; padding:12px 16px; border-radius:12px; line-height:1.6; white-space:pre-wrap; }
    .msg.user { align-self:flex-end; background:var(--user); }
    .msg.bot { align-self:flex-start; background:var(--panel); }
    .sources { margin-top:8px; font-size:12px; color:var(--muted); }
    .sources span { display:inline-block; background:#0b1220; border:1px solid #334155; border-radius:6px; padding:2px 8px; margin:2px 4px 0 0; }
    .composer { display:flex; gap:10px; padding:16px 24px; border-top:1px solid #334155; }
    .composer input { flex:1; background:var(--panel); border:1px solid #475569; border-radius:10px; color:var(--text); padding:12px 14px; font-size:14px; }
    .hint { color:var(--muted); font-size:12px; margin-top:6px; }
  </style>
</head>
<body>
  <header>
    <span style="font-size:22px">📄</span>
    <h1>PDF SimpleRAG 챗봇</h1>
    <span style="color:var(--muted);font-size:12px">Spring AI · SimpleVectorStore</span>
  </header>
  <div class="wrap">
    <aside class="sidebar">
      <div id="drop" class="drop">
        PDF를 끌어다 놓거나 클릭해 업로드<br/>
        <span class="hint">같은 파일은 자동으로 중복 인덱싱되지 않습니다</span>
      </div>
      <input id="fileInput" type="file" accept="application/pdf" hidden />
      <div style="display:flex;justify-content:space-between;align-items:center;margin-top:18px">
        <strong style="font-size:13px">인덱싱된 문서</strong>
        <button id="resetBtn" class="btn ghost" style="font-size:12px;padding:5px 10px">초기화</button>
      </div>
      <div id="files" class="files"></div>
    </aside>
    <main class="main">
      <div id="chat" class="chat">
        <div class="msg bot">안녕하세요. PDF를 업로드한 뒤 질문해 주세요. 업로드된 문서를 근거로 답변해 드립니다.</div>
      </div>
      <div class="composer">
        <input id="input" type="text" placeholder="질문을 입력하세요..." />
        <button id="sendBtn" class="btn">전송</button>
      </div>
    </main>
  </div>

  <script>
    const $ = (id) => document.getElementById(id);
    const chat = $("chat");

    function addMsg(text, who, sources) {
      const div = document.createElement("div");
      div.className = "msg " + who;
      div.textContent = text;
      if (sources && sources.length) {
        const s = document.createElement("div");
        s.className = "sources";
        s.innerHTML = "근거: " + sources.map(x =>
          `<span>${x.filename ?? "문서"}${x.page != null ? " p." + x.page : ""}</span>`).join("");
        div.appendChild(s);
      }
      chat.appendChild(div);
      chat.scrollTop = chat.scrollHeight;
      return div;
    }

    async function refreshStatus() {
      const r = await fetch("/api/status");
      const data = await r.json();
      const box = $("files");
      box.innerHTML = "";
      if (!data.files.length) {
        box.innerHTML = '<div class="hint">아직 업로드된 문서가 없습니다.</div>';
        return;
      }
      data.files.forEach(f => {
        const el = document.createElement("div");
        el.className = "file";
        el.innerHTML = `${f.filename}<div class="meta">${f.chunks} 청크 · ${f.indexedAt?.slice(0,19).replace("T"," ")}</div>`;
        box.appendChild(el);
      });
    }

    async function uploadFile(file) {
      if (!file) return;
      const note = addMsg(`'${file.name}' 업로드 중...`, "bot");
      const form = new FormData();
      form.append("file", file);
      try {
        const r = await fetch("/api/pdf", { method:"POST", body:form });
        if (!r.ok) { note.textContent = "업로드 실패: " + (await r.text()); return; }
        const d = await r.json();
        note.textContent = d.status === "duplicate"
          ? `'${d.filename}' 은 이미 인덱싱된 문서입니다. (중복 건너뜀)`
          : `'${d.filename}' 인덱싱 완료 — ${d.pages}페이지, ${d.chunks}청크.`;
        refreshStatus();
      } catch (e) { note.textContent = "업로드 오류: " + e.message; }
    }

    async function send() {
      const input = $("input");
      const text = input.value.trim();
      if (!text) return;
      addMsg(text, "user");
      input.value = "";
      const pending = addMsg("답변 생성 중...", "bot");
      try {
        const r = await fetch("/api/chat", {
          method:"POST", headers:{ "Content-Type":"application/json" },
          body: JSON.stringify({ message: text })
        });
        if (!r.ok) { pending.textContent = "오류: " + (await r.text()); return; }
        const d = await r.json();
        pending.textContent = d.content;
        if (d.sources && d.sources.length) {
          const s = document.createElement("div");
          s.className = "sources";
          s.innerHTML = "근거: " + d.sources.map(x =>
            `<span>${x.filename ?? "문서"}${x.page != null ? " p." + x.page : ""}</span>`).join("");
          pending.appendChild(s);
        }
      } catch (e) { pending.textContent = "오류: " + e.message; }
    }

    // 업로드 바인딩
    const drop = $("drop");
    $("fileInput").addEventListener("change", e => uploadFile(e.target.files[0]));
    drop.addEventListener("click", () => $("fileInput").click());
    ["dragover","dragenter"].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.add("drag"); }));
    ["dragleave","drop"].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.remove("drag"); }));
    drop.addEventListener("drop", e => uploadFile(e.dataTransfer.files[0]));

    // 채팅 바인딩
    $("sendBtn").addEventListener("click", send);
    $("input").addEventListener("keydown", e => { if (e.key === "Enter") send(); });

    // 초기화 바인딩
    $("resetBtn").addEventListener("click", async () => {
      const r = await fetch("/api/reset", { method:"POST" });
      const d = await r.json();
      addMsg(`인덱스를 초기화했습니다. (청크 ${d.removed}개 제거)`, "bot");
      refreshStatus();
    });

    refreshStatus();
  </script>
</body>
</html>
```

- [ ] **Step 2: 커밋**

```bash
git add demo-pdf-rag/src/main/resources/static/index.html
git commit -m "feat(demo-pdf-rag): PDF 업로드 + 채팅 웹 UI"
```

---

### Task 8: README + 최종 검증

**Files:**
- Create: `demo-pdf-rag/README.md`
- Modify: `README.md` (루트, demo 항목 한 줄 추가 — 선택)

**Interfaces:** 없음(문서·검증).

- [ ] **Step 1: demo-pdf-rag/README.md 작성**

`demo-pdf-rag/README.md`:

````markdown
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
````

- [ ] **Step 2: 전체 테스트 최종 실행**

Run: `cd demo-pdf-rag && ./gradlew clean test`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS.

- [ ] **Step 3: 수동 기동 검증 (OPENAI_API_KEY 필요)**

```bash
cd demo-pdf-rag
export OPENAI_API_KEY=sk-...   # 실제 키
./gradlew bootRun
```

검증 항목:
1. `http://localhost:8080` 접속 → UI 표시.
2. PDF 업로드 → "인덱싱 완료 — N페이지, M청크" 표시, 사이드바에 파일 등장.
3. **같은 PDF 재업로드 → "이미 인덱싱된 문서입니다. (중복 건너뜀)"** 확인.
4. 문서 내용 질문 → 답변 + 근거(파일·페이지) 표시.
5. 앱 재시작 후 `/api/status` 에 기존 파일 유지(파일 영속) 확인.
6. 초기화 버튼 → 목록 비워짐.

> 키가 없으면 1~3·5·6은 검증 가능하나(임베딩 호출은 실패), 4(LLM 답변)는 키가 있어야 한다.

- [ ] **Step 4: 루트 README에 demo 한 줄 추가 (선택)**

루트 `README.md`의 단계 구성 표 아래에 다음을 추가:

```markdown
> **부록 데모** — `demo-pdf-rag`: 업로드한 PDF를 SimpleVectorStore RAG로 인덱싱(SHA-256 중복 방지)해 답하는 독립 실행형 챗봇.
```

- [ ] **Step 5: 최종 커밋**

```bash
cd /Users/existmaster/frentis-repos/spring-ai-agent-kosta
git add demo-pdf-rag/README.md README.md
git commit -m "docs(demo-pdf-rag): README + 루트 데모 안내"
```

---

## 자체 검토 메모

- **스펙 커버리지:** 파일 영속(Task 2,4) · SHA-256 중복방지(Task 3,4) · PDF 파싱/청킹(Task 4) · QuestionAnswerAdvisor RAG(Task 2,5) · 근거 표시(Task 5) · 웹 UI(Task 7) · status/reset(Task 4,6) · 에러 처리 400/500(Task 6) · 테스트 전략(Task 3~6) 모두 매핑됨.
- **타입 일관성:** `IngestResult(status,filename,pages,chunks)`, `FileInfo(filename,chunks,indexedAt)`, `AnswerResult(content,sources)`, `Source(filename,page)`, `IngestManifest.Entry(filename,chunks,documentIds,indexedAt)` — 전 태스크에서 동일 시그니처 사용.
- **메타데이터 키:** `content_hash`, `filename`, `page_number`(PagePdfDocumentReader 제공) 일관 사용.
- **빈 이름:** ChatClient 빈 `pdfRagChatClient` — RagQueryService 생성자 파라미터명과 일치(by-name 주입).
- **외부 규격 확인 완료(context7, Spring AI 1.1.2 기준):** `PagePdfDocumentReader.read()`, `TokenTextSplitter().apply(List)`, `QuestionAnswerAdvisor.builder(vs).searchRequest(SearchRequest.builder().topK().similarityThreshold().build()).build()`, `SimpleVectorStore.builder(em).build()/load/save`, `VectorStore.delete(List<String>)`.
- **PDFBox 버전 위험 격리:** PDF 파싱을 `PdfTextExtractor` 어댑터로 분리해 단위 테스트가 PDFBox API에 의존하지 않음.
````
