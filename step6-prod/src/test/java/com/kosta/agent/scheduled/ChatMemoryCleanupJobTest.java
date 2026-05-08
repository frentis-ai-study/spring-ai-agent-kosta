package com.kosta.agent.scheduled;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * ChatMemoryCleanupJob 단위 테스트.
 * - 정상 케이스: DELETE SQL이 실행되고 deleted 카운트가 로그에 반영된다
 * - 예외 케이스: 쿼리 실패 시 throw하지 않고 warn 로그만 남긴다 (try/catch 보호)
 */
class ChatMemoryCleanupJobTest {

    private void inject(ChatMemoryCleanupJob job, EntityManager em) throws Exception {
        Field f = ChatMemoryCleanupJob.class.getDeclaredField("em");
        f.setAccessible(true);
        f.set(job, em);
    }

    @Test
    void cleanup은_네이티브_DELETE_쿼리를_실행한다() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(contains("DELETE FROM SPRING_AI_CHAT_MEMORY"))).thenReturn(q);
        when(q.executeUpdate()).thenReturn(7);

        ChatMemoryCleanupJob job = new ChatMemoryCleanupJob();
        inject(job, em);

        job.cleanup();

        verify(em).createNativeQuery(contains("90 days"));
        verify(q).executeUpdate();
    }

    @Test
    void cleanup은_DB_예외가_발생해도_throw하지_않는다() throws Exception {
        EntityManager em = mock(EntityManager.class);
        when(em.createNativeQuery(contains("DELETE"))).thenThrow(new RuntimeException("DB down"));

        ChatMemoryCleanupJob job = new ChatMemoryCleanupJob();
        inject(job, em);

        // 예외가 잡혀서 호출자에게 전파되지 않아야 한다 (스케줄러 안정성)
        assertThatCode(job::cleanup).doesNotThrowAnyException();
    }

    @Test
    void RETENTION_DAYS_상수가_90으로_설정되어_있다() throws Exception {
        Field f = ChatMemoryCleanupJob.class.getDeclaredField("RETENTION_DAYS");
        f.setAccessible(true);
        int retention = (int) f.get(null);
        assertThat(retention).isEqualTo(90);
    }
}
