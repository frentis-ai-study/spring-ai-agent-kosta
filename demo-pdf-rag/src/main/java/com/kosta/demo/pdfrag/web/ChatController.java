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
