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
