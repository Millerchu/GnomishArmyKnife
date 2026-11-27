package com.gak.aisubscription.sync;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.gak.aisubscription.service.AiAccessService;
import com.gak.aisubscription.service.AiAccountService;
import com.gak.aisubscription.service.AiUsageService;
import com.gak.framework.message.MessagePublisher;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AiSyncWorkerTest {
    private final AiSyncRepository repository = mock(AiSyncRepository.class);
    private final AiSyncCrypto crypto = mock(AiSyncCrypto.class);
    private final OpenAiCostsClient client = mock(OpenAiCostsClient.class);
    private final AiAccessService access = mock(AiAccessService.class);
    private final AiAccountService accounts = mock(AiAccountService.class);
    private final MessagePublisher messages = mock(MessagePublisher.class);
    private final AiUsageService usage = mock(AiUsageService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final AiSyncJob job = new AiSyncJob(8L, 1L, 2L, LocalDate.now(), "DAILY", AiSyncStatus.RUNNING,
            1, "lease", 1, null, Instant.now(), null);
    private final AiSyncConnection connection = new AiSyncConnection(2L, 1L, "encrypted", "Asia/Shanghai", true, 1, null, null);
    private AiSyncWorker worker;
    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        worker = new AiSyncWorker(repository, crypto, client, mock(GrokCostsClient.class), access, accounts, usage, transactions, messages);
        when(repository.connection(anyLong(), anyLong(), anyBoolean())).thenReturn(connection);
        when(repository.claim(eq(job), anyString())).thenReturn(job);
        when(crypto.decrypt(1L, 2L, "encrypted")).thenReturn("key");
        when(client.fetch(eq("key"), any(), any())).thenReturn(List.of(new AiOfficialCost(
                LocalDate.of(2026,11,1), LocalDate.of(2026,11,2), "USD", new BigDecimal("1.23"))));
    }
    @Test void expiredLeaseCannotWriteAnyOfficialCost() {
        when(repository.complete(job)).thenReturn(false);
        worker.run(job);
        verifyNoInteractions(usage);
        verify(repository, never()).markSuccess(any());
    }
    @Test void currentLeaseWritesSnapshotAndCompletion() {
        when(repository.complete(job)).thenReturn(true);
        worker.run(job);
        verify(usage).replaceOfficialCostWindow(eq(1L), eq(2L), any(), any(), eq("OPENAI_COST:"),
                eq(List.of(new AiOfficialCost(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2),
                        "USD", new BigDecimal("1.23")))));
        verify(repository).markSuccess(job);
    }
    @Test void oldWorkerAuthenticationFailureCannotRevokeNewAuthorization() {
        when(client.fetch(anyString(), any(), any())).thenThrow(new AiSyncFailure("AUTH_REQUIRED", false, 0));
        when(repository.fail(eq(job), any())).thenReturn(false);
        worker.run(job);
        verify(repository, never()).revoke(anyLong(), anyLong());
    }
    @Test void authenticationFailureRevokesAndNotifiesOnlyOncePerAuthorization() {
        when(client.fetch(anyString(), any(), any())).thenThrow(new AiSyncFailure("AUTH_REQUIRED", false, 0));
        when(repository.fail(eq(job), any())).thenReturn(true);
        worker.run(job);
        verify(repository).revoke(1L, 2L);
        verify(messages).publish(argThat(command -> command.idempotencyKey().equals("ai-sync-auth:2:1")
                && command.userIds().equals(List.of(1L))));
    }
    @Test void revokedConnectionNeverCallsProvider() {
        when(repository.connection(anyLong(), anyLong(), anyBoolean())).thenReturn(
                new AiSyncConnection(2L, 1L, null, "UTC", false, 2, null, null));
        worker.run(job);
        verifyNoInteractions(client, usage);
    }
}
