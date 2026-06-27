package com.kosta.demo.pdfrag.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RagConfigTest {

    @Autowired
    private VectorStore vectorStore;

    @Autowired(required = false)
    @Qualifier("pdfRagChatClient")
    private ChatClient pdfRagChatClient;

    @Test
    void RAG_빈이_등록된다() {
        assertThat(vectorStore).isNotNull();
        assertThat(pdfRagChatClient).isNotNull();
    }
}
