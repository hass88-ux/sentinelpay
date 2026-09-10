package com.sentinelpay.backend.monitoring;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitoringServiceTest {
    @Test void failuresStayVisibleUntilSuccessfulRecovery() {
        var store=mock(MonitoringStore.class);
        var service=new MonitoringService(store);
        assertEquals("NOT_RUN",service.status().state());
        var run=new MonitoringStore.Run("NO_DATA",Instant.now(),Instant.now(),0,0,0);
        when(store.evaluate(any())).thenReturn(run).thenThrow(new DataAccessResourceFailureException("offline"))
                .thenReturn(run);
        service.run();
        assertThrows(DataAccessResourceFailureException.class,service::run);
        assertEquals("FAILED",service.status().state());
        assertSame(run,service.status().lastSuccessfulRun());
        assertNotNull(service.status().lastFailureAt());
        service.run();
        assertEquals("NO_DATA",service.status().state());
        assertNull(service.status().lastFailureAt());
    }
}
