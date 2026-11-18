package com.gak.aisubscription.sync;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.gak.aisubscription.service.AiAccessService;
import com.gak.aisubscription.service.AiAccountService;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AiSyncServiceTest {
    private final AiSyncRepository repository = mock(AiSyncRepository.class);
    private final AiSyncService service = new AiSyncService(mock(AiAccessService.class), mock(AiAccountService.class),
            repository, mock(AiSyncCrypto.class));
    private final AiSyncConnection connection = new AiSyncConnection(2L, 1L, "enc", "Asia/Shanghai", true, 1, null, null);
    @Test void manualRefreshMergesExistingInFlightJob() {
        when(repository.connection(1L, 2L, true)).thenReturn(connection);
        AiSyncJob active = new AiSyncJob(9L, 1L, 2L, LocalDate.now(), "DAILY", AiSyncStatus.RUNNING,
                1, "lease", 1, null, Instant.now(), null);
        when(repository.inFlight(1L, 2L)).thenReturn(active);
        assertEquals(9L, service.manual(1L, 2L).id());
        verify(repository, never()).insert(any(), any(), any(), any());
    }
    @Test void successOnSameDayDoesNotCreateAnotherDailyJob() {
        when(repository.enabledAccounts(1L)).thenReturn(java.util.List.of(2L));
        when(repository.connection(1L, 2L, true)).thenReturn(connection);
        AiSyncJob success = new AiSyncJob(9L, 1L, 2L, LocalDate.now(), "DAILY", AiSyncStatus.SUCCEEDED,
                1, "lease", 1, null, Instant.now(), Instant.now());
        when(repository.daily(eq(1L), eq(2L), any())).thenReturn(success);
        assertEquals(9L, service.dailyCheck(1L).get(0).id());
        verify(repository, never()).insert(any(), any(), any(), any());
    }
    @Test void foreignTaskIsNotDisclosed() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.job(2L, 9L)).getStatusCode().value());
    }
}
