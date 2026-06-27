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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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

        @SuppressWarnings("unchecked")
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
