package com.kosta.agent.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * PolicyIndexer 단위 테스트.
 *
 * 외부 폴더 패턴 검증:
 *   1) 외부 폴더에 .txt/.md 파일이 있으면 우선 사용한다
 *   2) 외부 폴더가 비어 있거나 없으면 classpath:docs/*.txt 로 fallback한다
 *   3) 동일 입력으로 두 번 실행해도 생성되는 Document 내용이 결정적이다
 */
class PolicyIndexerTest {

    @TempDir
    Path tempDir;

    private VectorStore vectorStore;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
    }

    private PolicyIndexer newIndexer(Path policiesPath) {
        return new PolicyIndexer(
                vectorStore,
                policiesPath.toString(),
                tempDir.resolve("vector-store.json").toString());
    }

    @Test
    void 외부_폴더에_파일이_있으면_우선_사용한다() throws Exception {
        Files.writeString(tempDir.resolve("refund.txt"),
                "환불 정책: 7일 이내 미사용 상품에 한해 환불 가능합니다.");
        Files.writeString(tempDir.resolve("shipping.md"),
                "# 배송 정책\n결제 후 1-3 영업일 이내 발송됩니다.");

        PolicyIndexer indexer = newIndexer(tempDir);
        int chunks = indexer.indexAll();

        assertThat(chunks).isGreaterThan(0);
        verify(vectorStore, times(1)).add(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void 외부_폴더가_비어있으면_classpath로_fallback한다() {
        // tempDir 자체는 존재하나 정책 파일이 없는 상태
        PolicyIndexer indexer = newIndexer(tempDir);
        int chunks = indexer.indexAll();

        // classpath:docs/*.txt 의 기본 정책 3개가 인덱싱되어야 함 (chunks > 0)
        assertThat(chunks).isGreaterThan(0);
        verify(vectorStore, times(1)).add(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void 동일_입력으로_두_번_호출하면_생성되는_Document가_결정적이다() throws Exception {
        Files.writeString(tempDir.resolve("policy.txt"),
                "환불 정책: 7일 이내 미사용 상품에 한해 환불 가능합니다.");

        VectorStore vs1 = mock(VectorStore.class);
        VectorStore vs2 = mock(VectorStore.class);

        PolicyIndexer first = new PolicyIndexer(
                vs1, tempDir.toString(), tempDir.resolve("v1.json").toString());
        PolicyIndexer second = new PolicyIndexer(
                vs2, tempDir.toString(), tempDir.resolve("v2.json").toString());

        first.indexAll();
        second.indexAll();

        @SuppressWarnings("unchecked")
        var captor1 = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vs1).add(captor1.capture());

        @SuppressWarnings("unchecked")
        var captor2 = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vs2).add(captor2.capture());

        @SuppressWarnings("unchecked")
        List<Document> chunks1 = (List<Document>) captor1.getValue();
        @SuppressWarnings("unchecked")
        List<Document> chunks2 = (List<Document>) captor2.getValue();

        assertThat(chunks1).hasSameSizeAs(chunks2);
        List<String> texts1 = new ArrayList<>();
        List<String> texts2 = new ArrayList<>();
        for (Document d : chunks1) texts1.add(d.getText());
        for (Document d : chunks2) texts2.add(d.getText());
        assertThat(texts1).isEqualTo(texts2);
    }
}
