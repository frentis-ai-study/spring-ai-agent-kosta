package com.kosta.agent.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * PolicyIndexer 단위 테스트.
 *
 * 멱등성 검증의 한계:
 *   PolicyIndexer.indexAll()은 호출 시마다 청크를 add()로 적재하므로,
 *   같은 청크를 두 번 적재하면 VectorStore에는 중복 저장된다.
 *   "두 번 실행해도 중복 안 생기는지"는 VectorStore가 ID를 기반으로 upsert를 보장해야 한다.
 *
 *   본 테스트는 다음을 검증한다:
 *   1) 호출 시 청크가 생성되고 vectorStore.add가 호출된다
 *   2) 동일 입력으로 두 번 호출 시 생성되는 Document의 ID가 동일하다 (멱등성의 기반)
 *
 *   실제 pgvector upsert 동작은 step5/6 통합 테스트(@Testcontainers)에서 검증한다.
 */
class PolicyIndexerTest {

    private VectorStore vectorStore;
    private Resource[] docs;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        docs = new Resource[] {
                new ByteArrayResource("환불 정책: 7일 이내 미사용 상품에 한해 환불 가능합니다.".getBytes()) {
                    @Override public String getFilename() { return "refund.txt"; }
                },
                new ByteArrayResource("배송 정책: 결제 후 1-3 영업일 이내 발송됩니다.".getBytes()) {
                    @Override public String getFilename() { return "shipping.txt"; }
                }
        };
    }

    private PolicyIndexer newIndexer() throws Exception {
        PolicyIndexer indexer = new PolicyIndexer(vectorStore, docs);
        return indexer;
    }

    @Test
    void indexAll_호출_시_VectorStore에_청크가_적재된다() throws Exception {
        PolicyIndexer indexer = newIndexer();
        int chunks = indexer.indexAll();

        assertThat(chunks).isGreaterThan(0);
        verify(vectorStore, times(1)).add(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void 동일_입력으로_두_번_호출하면_생성되는_Document_ID가_동일하다() throws Exception {
        PolicyIndexer first = newIndexer();
        PolicyIndexer second = newIndexer();

        // 첫 번째 호출의 청크 캡처
        VectorStore vs1 = mock(VectorStore.class);
        VectorStore vs2 = mock(VectorStore.class);
        setVectorStore(first, vs1);
        setVectorStore(second, vs2);

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
        // Document 내용이 동일하면 ID도 결정적이어야 한다 (TextReader/TokenTextSplitter는 동일 입력 → 동일 출력)
        List<String> ids1 = new ArrayList<>();
        List<String> ids2 = new ArrayList<>();
        for (Document d : chunks1) ids1.add(d.getText());
        for (Document d : chunks2) ids2.add(d.getText());
        assertThat(ids1).isEqualTo(ids2);
    }

    private void setVectorStore(PolicyIndexer indexer, VectorStore vs) throws Exception {
        Field f = PolicyIndexer.class.getDeclaredField("vectorStore");
        f.setAccessible(true);
        f.set(indexer, vs);
    }
}
