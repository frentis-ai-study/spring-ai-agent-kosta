package com.kosta.agent.rag;

import org.springframework.ai.document.Document;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 정책 문서를 "섹션 단위"로 청킹하여 VectorStore에 적재한다.
 *
 * <p>왜 섹션 청킹인가:
 * <ul>
 *   <li>토큰 기반 분할({@code TokenTextSplitter})은 의미 경계를 무시한다.
 *       정책 문서는 "1. 반품 신청 가능 기간", "2. 반품 비용" 처럼 번호 항목 자체가
 *       하나의 의미 단위이므로 그 경계로 자르는 것이 검색 정밀도가 가장 높다.</li>
 *   <li>각 청크의 본문 앞에 문서 제목과 섹션 제목을 prefix로 붙여
 *       청크가 작아져도 자체 컨텍스트를 잃지 않게 한다 (제목 토큰이 임베딩에 반영됨).</li>
 *   <li>섹션 번호/제목을 metadata로 보관하여 인용·디버깅·후처리(reranker)에 활용한다.</li>
 * </ul>
 *
 * <p>지원 형식:
 * <ul>
 *   <li>번호형 섹션: {@code "1. 제목"}, {@code "2. 제목"} ...</li>
 *   <li>FAQ형: {@code "Q1. 질문"} (다음 줄의 "A." 까지 한 청크)</li>
 *   <li>그 외 일반 텍스트: 헤더가 없으면 파일 전체를 단일 청크로 처리</li>
 * </ul>
 *
 * <p>로딩 우선순위:
 * <ol>
 *   <li>외부 폴더({@code agent.rag.policies-path}, 기본 {@code ./data/policies})의 *.txt, *.md</li>
 *   <li>비어 있으면 {@code classpath:docs/*.txt} 로 fallback</li>
 * </ol>
 */
@Component
public class PolicyIndexer {

    /** 번호형 섹션 헤더: 줄 시작에서 "1.", "2.", ... 형태. */
    private static final Pattern NUMBERED_HEADER = Pattern.compile("^(\\d+)\\.\\s+(.+)$");

    /** FAQ 헤더: "Q1.", "Q2.", ... 형태. */
    private static final Pattern FAQ_HEADER = Pattern.compile("^(Q\\d+)\\.\\s+(.+)$");

    /** 문서 제목 라인: "[제목]" 형태(첫 줄 한정 사용). */
    private static final Pattern DOC_TITLE = Pattern.compile("^\\[(.+)\\]$");

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

        List<Document> chunks = new ArrayList<>();
        for (Resource r : sources) {
            try {
                String raw = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                chunks.addAll(splitBySection(raw, r.getFilename()));
            } catch (IOException e) {
                throw new RuntimeException("문서 읽기 실패: " + r.getFilename(), e);
            }
        }

        vectorStore.add(chunks);

        if (vectorStore instanceof SimpleVectorStore svs) {
            File f = new File(persistPath);
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            svs.save(f);
        }
        return chunks.size();
    }

    /**
     * 텍스트를 섹션 헤더 기준으로 분할한다.
     *
     * <p>알고리즘:
     * <ol>
     *   <li>첫 줄이 {@code [제목]} 형태면 문서 제목으로 추출(이후 모든 청크에 prefix).</li>
     *   <li>줄 단위로 스캔하며 헤더 정규식과 매칭되는 줄을 만나면 새 섹션 시작.</li>
     *   <li>헤더 직전까지 누적된 라인을 한 청크로 flush.</li>
     *   <li>각 청크 본문은 {@code "[문서제목] / 섹션제목\n섹션내용"} 형태로 구성하여
     *       검색 시 제목 토큰이 임베딩에 함께 반영되도록 한다.</li>
     * </ol>
     */
    private List<Document> splitBySection(String text, String filename) {
        String[] lines = text.split("\\r?\\n");
        String docTitle = null;
        int startIdx = 0;

        if (lines.length > 0) {
            Matcher m = DOC_TITLE.matcher(lines[0].trim());
            if (m.matches()) {
                docTitle = m.group(1).trim();
                startIdx = 1;
            }
        }

        List<Document> out = new ArrayList<>();
        String currentSection = null;     // 현재 섹션 헤더(예: "1. 반품 신청 가능 기간")
        String currentSectionId = null;   // 메타데이터용 ID(예: "1", "Q3")
        StringBuilder buf = new StringBuilder();

        for (int i = startIdx; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            String[] header = matchHeader(trimmed);  // [id, title] or null
            if (header != null) {
                // 새 헤더를 만났으니 직전 섹션을 먼저 flush
                flush(out, buf, docTitle, currentSection, currentSectionId, filename);
                currentSectionId = header[0];
                currentSection = trimmed;
                buf.setLength(0);
            } else {
                if (!trimmed.isEmpty()) {
                    if (buf.length() > 0) buf.append('\n');
                    buf.append(line);
                }
            }
        }
        // 마지막 섹션 flush
        flush(out, buf, docTitle, currentSection, currentSectionId, filename);

        // 헤더가 하나도 없는 문서는 fallback으로 전체를 1청크로 보존
        if (out.isEmpty() && text.trim().length() > 0) {
            out.add(buildDoc(text.trim(), docTitle, null, null, filename));
        }
        return out;
    }

    /** 라인이 섹션 헤더면 {id, title}을 반환, 아니면 null. */
    private String[] matchHeader(String line) {
        Matcher mNum = NUMBERED_HEADER.matcher(line);
        if (mNum.matches()) return new String[]{mNum.group(1), mNum.group(2).trim()};
        Matcher mFaq = FAQ_HEADER.matcher(line);
        if (mFaq.matches()) return new String[]{mFaq.group(1), mFaq.group(2).trim()};
        return null;
    }

    private void flush(List<Document> out, StringBuilder buf, String docTitle,
                       String sectionHeader, String sectionId, String filename) {
        if (sectionHeader == null && buf.length() == 0) return;
        if (buf.length() == 0) return;  // 헤더만 있고 본문 없는 경우 스킵
        out.add(buildDoc(buf.toString().trim(), docTitle, sectionHeader, sectionId, filename));
    }

    /**
     * 청크 본문 = "[문서제목] / 섹션헤더\n본문" 형태.
     * 제목/헤더 토큰이 임베딩에 포함되어 "반품 비용 얼마야?" 같은 질의가
     * "2. 반품 비용" 섹션과 더 잘 매칭된다.
     */
    private Document buildDoc(String body, String docTitle, String sectionHeader,
                              String sectionId, String filename) {
        StringBuilder content = new StringBuilder();
        if (docTitle != null) content.append('[').append(docTitle).append(']');
        if (sectionHeader != null) {
            if (content.length() > 0) content.append(" / ");
            content.append(sectionHeader);
        }
        if (content.length() > 0) content.append('\n');
        content.append(body);

        Map<String, Object> meta = new HashMap<>();
        meta.put("filename", filename);
        if (docTitle != null) meta.put("doc_title", docTitle);
        if (sectionId != null) meta.put("section_id", sectionId);
        if (sectionHeader != null) meta.put("section", sectionHeader);
        return new Document(content.toString(), meta);
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
