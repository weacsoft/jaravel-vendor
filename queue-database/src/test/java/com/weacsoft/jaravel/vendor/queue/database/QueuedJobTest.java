package com.weacsoft.jaravel.vendor.queue.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QueuedJob} 任务实体单元测试。
 */
class QueuedJobTest {

    @Test
    void sevenArgConstructorDefaultsExceptionToNull() {
        QueuedJob job = new QueuedJob(10, "emails", "payload", 2, 100L, 200L, 300L);

        assertEquals(10, job.getId());
        assertEquals("emails", job.getQueue());
        assertEquals("payload", job.getPayload());
        assertEquals(2, job.getAttempts());
        assertEquals(100L, job.getReservedAt());
        assertEquals(200L, job.getAvailableAt());
        assertEquals(300L, job.getCreatedAt());
        assertNull(job.getException());
    }

    @Test
    void eightArgConstructorCarriesException() {
        QueuedJob job = new QueuedJob(5, "default", "p", 3, 0L, 7L, 8L, "NPE");

        assertEquals(5, job.getId());
        assertEquals(3, job.getAttempts());
        assertEquals("NPE", job.getException());
    }

    @Test
    void toStringContainsIdAndQueueAndOmitsExceptionWhenNull() {
        QueuedJob ok = new QueuedJob(1, "q", "p", 1, 0, 0, 0);
        String s = ok.toString();
        assertTrue(s.contains("id=1"));
        assertTrue(s.contains("queue='q'"));
        assertFalse(s.contains("exception"));

        QueuedJob failed = new QueuedJob(1, "q", "p", 1, 0, 0, 0, "boom");
        assertTrue(failed.toString().contains("exception='boom'"));
    }
}
