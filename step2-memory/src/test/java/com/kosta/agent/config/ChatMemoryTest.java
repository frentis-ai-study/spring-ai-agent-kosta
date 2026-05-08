package com.kosta.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MessageWindowChatMemory 동작 검증.
 * - conversationId별로 분리됨
 * - maxMessages 윈도우를 유지함
 * (실제 LLM 호출 없이 메모리 구현만 검증)
 */
class ChatMemoryTest {

    @Test
    void conversationId별로_메시지가_분리되어_저장된다() {
        ChatMemoryRepository repo = new InMemoryChatMemoryRepository();
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repo)
                .maxMessages(10)
                .build();

        memory.add("conv-A", new UserMessage("내 이름은 앨리스"));
        memory.add("conv-A", new AssistantMessage("앨리스 님, 반갑습니다"));
        memory.add("conv-B", new UserMessage("내 이름은 밥"));

        List<Message> a = memory.get("conv-A");
        List<Message> b = memory.get("conv-B");

        assertThat(a).hasSize(2);
        assertThat(b).hasSize(1);
        assertThat(a.get(0).getText()).contains("앨리스");
        assertThat(b.get(0).getText()).contains("밥");
    }

    @Test
    void maxMessages를_초과하면_오래된_메시지가_제거된다() {
        ChatMemoryRepository repo = new InMemoryChatMemoryRepository();
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repo)
                .maxMessages(4)
                .build();

        for (int i = 0; i < 10; i++) {
            memory.add("conv", new UserMessage("msg-" + i));
        }
        List<Message> kept = memory.get("conv");

        assertThat(kept).hasSize(4);
        // 가장 최근 4개만 남는다
        assertThat(kept.get(kept.size() - 1).getText()).isEqualTo("msg-9");
    }
}
