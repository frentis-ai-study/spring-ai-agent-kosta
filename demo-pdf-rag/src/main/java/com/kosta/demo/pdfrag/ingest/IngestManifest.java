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
