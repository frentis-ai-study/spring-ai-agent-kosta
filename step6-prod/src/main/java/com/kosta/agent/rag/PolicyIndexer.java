package com.kosta.agent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * classpath:docs/*.txt 정책 문서를 토큰 단위로 청킹하여 pgvector에 적재한다.
 * POST /api/index 호출로 트리거한다.
 */
@Component
public class PolicyIndexer {

    private final VectorStore vectorStore;
    private final Resource[] docs;

    public PolicyIndexer(VectorStore vectorStore,
                         @Value("classpath:docs/*.txt") Resource[] docs) {
        this.vectorStore = vectorStore;
        this.docs = docs;
    }

    public int indexAll() {
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = Arrays.stream(docs)
                .flatMap(r -> {
                    TextReader reader = new TextReader(r);
                    reader.getCustomMetadata().put("filename", r.getFilename());
                    return splitter.apply(reader.get()).stream();
                })
                .toList();
        vectorStore.add(chunks);
        return chunks.size();
    }
}
