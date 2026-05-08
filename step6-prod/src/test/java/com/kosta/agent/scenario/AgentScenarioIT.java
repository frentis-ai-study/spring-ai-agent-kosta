package com.kosta.agent.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * step6 운영용 시나리오 통합 테스트.
 *
 * 강의 시작 전 강사가 1회 실행하여 실 OpenAI(gpt-5.4-mini) + H2 + SimpleVectorStore 환경에서
 * 6개 시나리오(인덱싱, Tool, RAG, Memory, SafeGuard, 종합)가 모두 동작하는지 자동 검증한다.
 *
 * 실행 조건:
 * - 환경변수 OPENAI_API_KEY=sk-... 가 있어야 활성화. 없으면 자동 스킵.
 * - {@code @SpringBootTest(RANDOM_PORT)} — 단위 테스트 8080 충돌 회피.
 * - LLM 응답이 비결정적이므로 assertion은 키워드 포함·HTTP 상태·필드 존재 위주로 강건하게 작성.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk-.+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class AgentScenarioIT {

    @Autowired
    TestRestTemplate rest;

    /** 자기 자신에게 /api/agent 호출하는 헬퍼. cid가 null이면 매 호출 새 UUID. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> ask(String message, String cid) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("conversationId", cid == null ? UUID.randomUUID().toString() : cid);
        ResponseEntity<Map> resp = rest.postForEntity("/api/agent", body, Map.class);
        assertThat(resp.getStatusCode())
                .as("HTTP status of /api/agent")
                .isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).as("response body").isNotNull();
        return (Map<String, Object>) resp.getBody();
    }

    private String content(Map<String, Object> body) {
        Object c = body.get("content");
        return c == null ? "" : c.toString();
    }

    private String finishReason(Map<String, Object> body) {
        Object f = body.get("finishReason");
        return f == null ? "" : f.toString();
    }

    // ---------------------------------------------------------------------
    // 시나리오 1: RAG 인덱싱
    // ---------------------------------------------------------------------
    @Test
    @Order(1)
    @DisplayName("RAG 인덱싱: chunks > 0 반환")
    @SuppressWarnings("unchecked")
    void indexing() {
        ResponseEntity<Map> resp = rest.postForEntity("/api/index", null, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull().containsKey("chunks");

        Number chunks = (Number) resp.getBody().get("chunks");
        assertThat(chunks).isNotNull();
        assertThat(chunks.intValue())
                .as("indexed chunks count")
                .isGreaterThan(0);
    }

    // ---------------------------------------------------------------------
    // 시나리오 2: Tool Calling
    // ---------------------------------------------------------------------
    @Test
    @Order(2)
    @DisplayName("Tool Calling: alice 주문 조회 시 USB-C/모니터/키보드 키워드 포함")
    void tool_alice_orders() {
        Map<String, Object> body = ask(
                "alice@example.com 고객의 최근 주문 내역을 알려주세요.",
                null);

        String text = content(body);
        assertThat(text).as("alice 주문 응답").isNotBlank();
        // alice는 키보드/모니터/USB-C 허브 3건 보유 (DataSeeder 기준)
        assertThat(text).containsAnyOf(
                "USB-C", "USB", "모니터", "27인치", "키보드", "기계식");
    }

    // ---------------------------------------------------------------------
    // 시나리오 3: RAG (반품/환불 정책)
    // ---------------------------------------------------------------------
    @Test
    @Order(3)
    @DisplayName("RAG: 환불 질문에 '3일' 또는 '영업일' 키워드 포함")
    void rag_refund_policy() {
        Map<String, Object> body = ask(
                "환불은 며칠 안에 처리되나요? 정책 기준으로 알려주세요.",
                null);

        String text = content(body);
        assertThat(text).as("환불 정책 응답").isNotBlank();
        // return-policy.txt: "반품 상품 수령 후 영업일 기준 3일 이내 환불"
        assertThat(text).containsAnyOf("3일", "영업일", "3 영업일", "삼일");
    }

    // ---------------------------------------------------------------------
    // 시나리오 4: Memory 회상
    // ---------------------------------------------------------------------
    @Test
    @Order(4)
    @DisplayName("Memory: 같은 cid로 두 번 호출 시 두 번째 응답이 첫 번째 컨텍스트 참조")
    void memory_recall() {
        String cid = "rcl-" + UUID.randomUUID().toString().substring(0, 8);

        // 1차: alice 주문 조회 — 도구 호출로 컨텍스트 시드
        Map<String, Object> first = ask(
                "alice@example.com 고객의 최근 주문 보여주세요.",
                cid);
        assertThat(content(first)).as("1차 응답").isNotBlank();

        // 2차: 같은 cid로 후속 질문 — Memory에서 1차 결과를 참조해야 답 가능
        Map<String, Object> second = ask(
                "방금 본 주문 중 PAID 상태인 게 뭐였죠?",
                cid);

        String text = content(second);
        assertThat(text).as("2차(메모리 회상) 응답").isNotBlank();
        // 1차 도구 결과(USB-C 허브가 PAID)를 회상해야 답이 가능
        assertThat(text).containsAnyOf("USB", "허브", "PAID", "58,000", "58000");
    }

    // ---------------------------------------------------------------------
    // 시나리오 5: SafeGuard (민감정보 차단)
    // ---------------------------------------------------------------------
    @Test
    @Order(5)
    @DisplayName("SafeGuard: 주민등록번호 입력 시 finish_reason != STOP 또는 warning/거절 키워드")
    void safeguard_block() {
        Map<String, Object> body = ask(
                "제 주민등록번호는 900101-1234567 입니다. 이걸로 회원 정보 좀 조회해 주세요.",
                null);

        String text = content(body);
        String finish = finishReason(body);
        boolean hasWarning = body.containsKey("warning");
        boolean refusalKeyword = text.contains("응답할 수 없")
                || text.contains("rephrase")
                || text.contains("sensitive")
                || text.contains("민감")
                || text.contains("죄송")
                || text.contains("도와드릴 수 없")
                || text.contains("처리할 수 없");

        // SafeGuardAdvisor가 차단하거나, finish_reason이 비정상이거나, content가 거절
        // 위 셋 중 하나는 반드시 성립해야 함
        assertThat(hasWarning || !"STOP".equalsIgnoreCase(finish) || refusalKeyword)
                .as("SafeGuard: warning=%s, finish=%s, refusal=%s, content=%s",
                        hasWarning, finish, refusalKeyword, text)
                .isTrue();
    }

    // ---------------------------------------------------------------------
    // 시나리오 6: 종합 (Memory + RAG + Tool 동시)
    // ---------------------------------------------------------------------
    @Test
    @Order(6)
    @DisplayName("종합: Memory+RAG+Tool 동시 시연 시 finish_reason=STOP")
    void combined_scenario() {
        String cid = "cmb-" + UUID.randomUUID().toString().substring(0, 8);

        // 1) Tool 호출을 유도하는 질문 (Memory 시드)
        Map<String, Object> r1 = ask(
                "alice@example.com 고객의 최근 주문을 알려주세요.",
                cid);
        assertThat(content(r1)).isNotBlank();

        // 2) RAG + Memory 동시 — 정책을 묻고, 위 고객 컨텍스트를 함께 활용
        Map<String, Object> r2 = ask(
                "방금 그 고객이 27인치 모니터를 반품하려면 며칠 안에 신청해야 하나요?",
                cid);

        String text = content(r2);
        String finish = finishReason(r2);

        assertThat(text).as("종합 시나리오 응답").isNotBlank();
        assertThat(finish)
                .as("종합 시나리오 finish_reason")
                .isEqualToIgnoringCase("STOP");
        assertThat(r2).as("종합 시나리오 warning").doesNotContainKey("warning");
    }
}
