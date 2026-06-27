package com.kosta.demo.pdfrag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PdfRagApplicationTest {

    @Test
    void contextLoads() {
        // 컨텍스트가 기동되면 통과 (OpenAI 더미 키로 빈만 생성, 네트워크 호출 없음)
    }
}
