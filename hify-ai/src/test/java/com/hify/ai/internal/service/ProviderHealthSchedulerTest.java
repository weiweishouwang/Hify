package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.entity.ProviderHealthObservation;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.shared.http.LlmApiException;
import com.hify.shared.http.LlmErrorKind;
import com.hify.shared.redis.DistributedLock;
import com.hify.shared.redis.IdempotentGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProviderHealthSchedulerTest {
    ModelProviderMapper providers = mock(ModelProviderMapper.class);
    ProviderConnectivityService connectivity = mock(ProviderConnectivityService.class);
    ProviderHealthWriter writer = mock(ProviderHealthWriter.class);
    DistributedLock lock = mock(DistributedLock.class);
    IdempotentGuard guard = mock(IdempotentGuard.class);
    Clock clock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    ProviderHealthScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = scheduler(tasks::add, clock);
        when(lock.tryLock(anyString(), any(Duration.class))).thenReturn("lock-token");
        when(guard.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
    }

    ProviderHealthScheduler scheduler(Executor executor, Clock clock) {
        return new ProviderHealthScheduler(providers, connectivity, writer, lock, guard, executor, clock);
    }

    ModelProviderDO provider(long id) {
        var provider = new ModelProviderDO();
        provider.setId(id);
        provider.setEnabled(true);
        provider.setDeleted(0);
        provider.setConfigVersion(1L);
        return provider;
    }

    void enqueueOne() {
        when(providers.selectList(any(Wrapper.class))).thenReturn(List.of(provider(1)));
        when(providers.selectById(1L)).thenReturn(provider(1));
        scheduler.schedule();
        tasks.remove().run();
    }

    @Test
    void runsEveryMinuteAndOnlyDispatchesFromSchedulerThread() throws Exception {
        assertThat(ProviderHealthScheduler.class.getMethod("schedule").getAnnotation(Scheduled.class).fixedRate())
                .isEqualTo(60_000L);
        scheduler.schedule();
        verifyNoInteractions(providers, connectivity, writer);
        when(providers.selectList(any(Wrapper.class))).thenReturn(List.of());
        tasks.remove().run();
        var query = ArgumentCaptor.forClass(Wrapper.class);
        verify(providers).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("enabled", "id", "LIMIT");
    }

    @Test
    void successRecordsOneObservationAndAlwaysReleasesLock() {
        enqueueOne();
        tasks.remove().run();
        var observation = ArgumentCaptor.forClass(ProviderHealthObservation.class);
        verify(writer).record(observation.capture());
        assertThat(observation.getValue().successful()).isTrue();
        assertThat(observation.getValue().providerId()).isEqualTo(1L);
        assertThat(observation.getValue().latencyMs()).isNotNegative();
        verify(lock).unlock(anyString(), eq("lock-token"));
    }

    @Test
    void failureIsSanitizedAndDoesNotPreventOtherProviders() {
        when(providers.selectList(any(Wrapper.class))).thenReturn(List.of(provider(1), provider(2)));
        when(providers.selectById(1L)).thenReturn(provider(1));
        when(providers.selectById(2L)).thenReturn(provider(2));
        doThrow(new LlmApiException(LlmErrorKind.AUTH_FAILED, "secret-from-upstream"))
                .when(connectivity).testConnection(argThat(row -> row.getId().equals(1L)));
        scheduler.schedule();
        while (!tasks.isEmpty()) { tasks.remove().run(); }
        var observation = ArgumentCaptor.forClass(ProviderHealthObservation.class);
        verify(writer, times(2)).record(observation.capture());
        assertThat(observation.getAllValues().getFirst().successful()).isFalse();
        assertThat(observation.getAllValues().getFirst().errorCode()).isEqualTo("AUTH_FAILED");
        assertThat(observation.getAllValues().getFirst().errorMessage()).doesNotContain("secret-from-upstream");
        assertThat(observation.getAllValues().getLast().successful()).isTrue();
    }

    @Test
    void duplicateWindowAndOverlappingProviderAreSkipped() {
        enqueueOne();
        when(guard.tryAcquire(anyString(), any(Duration.class))).thenReturn(false);
        tasks.remove().run();
        verifyNoInteractions(connectivity, writer);
        verify(lock).unlock(anyString(), eq("lock-token"));
        when(lock.tryLock(anyString(), any(Duration.class))).thenReturn(null);
        enqueueOne();
        tasks.remove().run();
        verifyNoInteractions(connectivity, writer);
    }

    @Test
    void disabledProviderAndExpiredQueuedTaskAreSkipped() {
        enqueueOne();
        var disabled = provider(1);
        disabled.setEnabled(false);
        when(providers.selectById(1L)).thenReturn(disabled);
        tasks.remove().run();
        verifyNoInteractions(connectivity, writer);
        tasks.clear();
        scheduler = scheduler(tasks::add, Clock.offset(clock, Duration.ofMinutes(2)));
        scheduler.schedule();
        // 任务创建后时间继续前进；旧窗口的队列任务不能把排队延迟算成上游失败。
        var movingClock = mock(Clock.class);
        when(movingClock.instant()).thenReturn(clock.instant(), clock.instant().plusSeconds(120));
        scheduler = scheduler(tasks::add, movingClock);
        tasks.clear();
        scheduler.schedule();
        tasks.remove().run();
        verifyNoInteractions(connectivity, writer);
    }

    @Test
    void rejectedTaskOrRedisFailureDoesNotMarkProviderDown() {
        scheduler = scheduler(task -> { throw new RejectedExecutionException("full"); }, clock);
        assertThatCode(scheduler::schedule).doesNotThrowAnyException();
        verifyNoInteractions(providers, writer);
        scheduler = scheduler(tasks::add, clock);
        enqueueOne();
        when(lock.tryLock(anyString(), any(Duration.class))).thenThrow(new IllegalStateException("redis unavailable"));
        assertThatCode(() -> tasks.remove().run()).doesNotThrowAnyException();
        verifyNoInteractions(connectivity, writer);
    }
}
