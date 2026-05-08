package com.kosta.agent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 정책 문서를 토큰 단위로 청킹하여 VectorStore에 적재한다.
 * 우선순위:
 *   1) 외부 폴더 (agent.rag.policies-path, 기본 ./data/policies) 안의 *.txt, *.md
 *   2) 외부 폴더가 비어 있으면 classpath:docs/*.txt 로 fallback
 * 학생이 외부 폴더에 자기 파일을 두고 POST /api/index 를 호출하면 즉시 반영된다.
 */
@Component
public class PolicyIndexer {

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private final String externalPath;
    private final String persistPath;

    public PolicyIndexer(
            VectorStore vectorStore,
            @Value("${agent.rag.policies-path:./data/policies}") String externalPath,
            @Value("${agent.rag.persist-path:./data/vector-store.json}") String persistPath) {
        this.vectorStore = vectorStore;
        this.externalPath = externalPath;
        this.persistPath = persistPath;
    }

    public int indexAll() {
        Resource[] sources;
        try {
            sources = resolveSources();
        } catch (IOException e) {
            throw new RuntimeException("정책 문서 로딩 실패: " + e.getMessage(), e);
        }

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = Arrays.stream(sources)
                .flatMap(r -> {
                    TextReader reader = new TextReader(r);
                    reader.getCustomMetadata().put("filename", r.getFilename());
                    return splitter.apply(reader.get()).stream();
                })
                .toList();

        vectorStore.add(chunks);

        if (vectorStore instanceof SimpleVectorStore svs) {
            File f = new File(persistPath);
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            svs.save(f);
        }
        return chunks.size();
    }

    private Resource[] resolveSources() throws IOException {
        File dir = new File(externalPath);
        if (dir.isDirectory()) {
            List<Resource> all = new ArrayList<>();
            File[] txt = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));
            File[] md = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".md"));
            if (txt != null) for (File f : txt) all.add(new FileSystemResource(f));
            if (md != null) for (File f : md) all.add(new FileSystemResource(f));
            if (!all.isEmpty()) return all.toArray(new Resource[0]);
        }
        return resolver.getResources("classpath:docs/*.txt");
    }
}
