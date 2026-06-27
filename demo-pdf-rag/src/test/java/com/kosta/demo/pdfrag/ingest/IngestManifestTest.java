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
