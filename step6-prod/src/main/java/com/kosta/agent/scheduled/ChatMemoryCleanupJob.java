package com.kosta.agent.scheduled;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * step6: ChatMemory TTL 잡.
 * SPRING_AI_CHAT_MEMORY 테이블에서 90일 이상 경과한 메시지를 매일 03:00에 삭제한다.
 *
 * Spring AI 1.1.5의 JDBC ChatMemoryRepository는 timestamp 컬럼이 있으므로
 * 단순 SQL DELETE로 정리할 수 있다.
 */
@Component
public class ChatMemoryCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryCleanupJob.class);
    private static final int RETENTION_DAYS = 90;

    @PersistenceContext
    private EntityManager em;

    /** 매일 03:00 실행. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanup() {
        try {
            int deleted = em.createNativeQuery(
                    "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE timestamp < NOW() - INTERVAL '" + RETENTION_DAYS + " days'"
            ).executeUpdate();
            log.info("[ChatMemoryCleanup] {}일 초과 메시지 {}건 삭제", RETENTION_DAYS, deleted);
        } catch (Exception e) {
            log.warn("[ChatMemoryCleanup] 정리 실패: {}", e.getMessage());
        }
    }
}
