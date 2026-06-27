package com.kosta.demo.pdfrag.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.demo.pdfrag.ingest.PdfIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PdfIngestService ingestService;

    @MockBean
    private RagQueryService ragQueryService;

    @Test
    void PDF_업로드는_인제스트_결과를_반환한다() throws Exception {
        when(ingestService.ingest(any(), anyString()))
                .thenReturn(new PdfIngestService.IngestResult("indexed", "a.pdf", 3, 12));

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.pdf", "application/pdf", "%PDF-1.4 ...".getBytes());

        mvc.perform(multipart("/api/pdf").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("indexed"))
                .andExpect(jsonPath("$.chunks").value(12));
    }

    @Test
    void 비_PDF_업로드는_400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.txt", "text/plain", "hello".getBytes());

        mvc.perform(multipart("/api/pdf").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 채팅은_답변과_근거를_반환한다() throws Exception {
        when(ragQueryService.answer(anyString())).thenReturn(
                new RagQueryService.AnswerResult("답변입니다.",
                        List.of(new RagQueryService.Source("a.pdf", 2))));

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "질문"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("답변입니다."))
                .andExpect(jsonPath("$.sources[0].filename").value("a.pdf"))
                .andExpect(jsonPath("$.sources[0].page").value(2));
    }

    @Test
    void 빈_메시지_채팅은_400() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "  "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reset은_제거_개수를_반환한다() throws Exception {
        when(ingestService.reset()).thenReturn(5);

        mvc.perform(post("/api/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.removed").value(5));
    }

    @Test
    void status는_파일목록과_총청크를_반환한다() throws Exception {
        when(ingestService.status()).thenReturn(List.of(
                new PdfIngestService.FileInfo("a.pdf", 3, "2026-06-27T00:00:00Z"),
                new PdfIngestService.FileInfo("b.pdf", 4, "2026-06-27T00:01:00Z")));

        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChunks").value(7))
                .andExpect(jsonPath("$.files.length()").value(2));
    }
}
