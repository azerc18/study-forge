package com.az.studyforge.generation.service;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.generation.config.GenerationProperties;
import com.az.studyforge.generation.llm.config.LlmProperties;
import com.az.studyforge.generation.llm.dto.ProviderUsage;
import com.az.studyforge.generation.llm.repository.LlmCallRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmBudgetServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 2026-10-23 10:00 giờ VN; đầu ngày VN = 2026-10-22T17:00Z; reset lúc 2026-10-23T17:00Z. */
    private static final Instant NOW = Instant.parse("2026-10-23T03:00:00Z");
    private static final Instant START_OF_DAY = Instant.parse("2026-10-22T17:00:00Z");

    @Mock LlmCallRepository repository;

    /** Đồng hồ chỉnh được để test cache và qua ngày. */
    private static final class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock(NOW);

    private static LlmProperties.Provider provider(String name, double in, double out) {
        return new LlmProperties.Provider(name, "https://x", "key", "m", Duration.ofSeconds(90), 8000, 0.7,
                null, in, out);
    }

    private LlmBudgetService service(double budget) {
        LlmProperties props = new LlmProperties(2, List.of(
                provider("deepseek", 0.66, 1.98),
                provider("groq", 1.0, 3.0)), budget);
        GenerationProperties gen = new GenerationProperties(VN, 2, 3, 2, Duration.ofMinutes(15), 2);
        return new LlmBudgetService(repository, props, gen, clock);
    }

    // ===== cost =====

    @Test
    void cost_sumsEachProviderWithItsOwnPrice() {
        LlmBudgetService s = service(0.5);

        double cost = s.cost(List.of(
                new ProviderUsage("deepseek", 1_000_000, 500_000),       // 0.66 + 0.99 = 1.65
                new ProviderUsage("groq", 100_000, 100_000)));           // 0.10 + 0.30 = 0.40

        assertThat(cost).isCloseTo(2.05, within(1e-9));
    }

    @Test
    void cost_unknownProvider_usesMostExpensivePrice() {
        LlmBudgetService s = service(0.5);

        double cost = s.cost(List.of(new ProviderUsage("removed-provider", 1_000_000, 1_000_000)));

        assertThat(cost).isCloseTo(1.0 + 3.0, within(1e-9));    // max input 1.0, max output 3.0
    }

    // ===== spentToday / isExceeded =====

    @Test
    void spentToday_queriesFromStartOfDayInQuotaZone() {
        when(repository.sumTokensByProviderSince(START_OF_DAY))
                .thenReturn(List.of(new ProviderUsage("deepseek", 100_000, 50_000)));

        assertThat(service(0.5).spentTodayUsd()).isCloseTo(0.066 + 0.099, within(1e-9));
    }

    @Test
    void isExceeded_belowBudget_false_atOrAbove_true() {
        when(repository.sumTokensByProviderSince(any()))
                .thenReturn(List.of(new ProviderUsage("groq", 500_000, 0)))      // 0.50
                .thenReturn(List.of(new ProviderUsage("groq", 500_000, 0)));

        assertThat(service(0.51).isExceeded()).isFalse();
        assertThat(service(0.50).isExceeded()).isTrue();             // đúng bằng ngân sách → chặn
    }

    @Test
    void zeroBudget_disablesCheck_withoutQueryingDb() {
        LlmBudgetService s = service(0);

        assertThat(s.enabled()).isFalse();
        assertThat(s.isExceeded()).isFalse();
        s.check();                                                   // không ném lỗi
        verifyNoInteractions(repository);
    }

    // ===== check =====

    @Test
    void check_exceeded_throwsWithResetTime() {
        when(repository.sumTokensByProviderSince(any())).thenReturn(List.of(new ProviderUsage("groq", 0, 1_000_000)));

        assertThatThrownBy(() -> service(1.0).check()).isInstanceOfSatisfying(AppException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_BUDGET_EXCEEDED);
            assertThat(e.getDetails()).isEqualTo(Map.of("resetsAt", "2026-10-23T17:00:00Z"));
        });
    }

    @Test
    void check_underBudget_passes() {
        when(repository.sumTokensByProviderSince(any())).thenReturn(List.of());

        service(0.5).check();
    }

    // ===== cache =====

    @Test
    void cache_reusedWithin30s_refreshedAfter() {
        when(repository.sumTokensByProviderSince(any())).thenReturn(List.of());
        LlmBudgetService s = service(0.5);

        s.spentTodayUsd();
        clock.now = NOW.plusSeconds(29);
        s.spentTodayUsd();
        verify(repository, times(1)).sumTokensByProviderSince(any());

        clock.now = NOW.plusSeconds(30);
        s.spentTodayUsd();
        verify(repository, times(2)).sumTokensByProviderSince(any());
    }

    @Test
    void newDay_ignoresCache_andQueriesFromNewStartOfDay() {
        when(repository.sumTokensByProviderSince(any()))
                .thenReturn(List.of(new ProviderUsage("groq", 1_000_000, 0)))   // hôm qua: $1 → vượt
                .thenReturn(List.of());                                          // hôm nay: chưa dùng
        LlmBudgetService s = service(0.5);
        assertThat(s.isExceeded()).isTrue();

        clock.now = Instant.parse("2026-10-23T17:00:05Z");                       // 00:00:05 ngày 24 giờ VN

        assertThat(s.isExceeded()).isFalse();
        verify(repository).sumTokensByProviderSince(Instant.parse("2026-10-23T17:00:00Z"));
    }

    @Test
    void invalidate_forcesRecalculation() {
        when(repository.sumTokensByProviderSince(any())).thenReturn(List.of());
        LlmBudgetService s = service(0.5);

        s.spentTodayUsd();
        s.invalidate();
        s.spentTodayUsd();

        verify(repository, times(2)).sumTokensByProviderSince(any());
    }

    @Test
    void status_reportsSpentBudgetAndReset() {
        when(repository.sumTokensByProviderSince(any())).thenReturn(List.of(new ProviderUsage("groq", 200_000, 0)));

        LlmBudgetService.BudgetStatus st = service(0.5).status();

        assertThat(st.spentUsd()).isCloseTo(0.2, within(1e-9));
        assertThat(st.budgetUsd()).isEqualTo(0.5);
        assertThat(st.exceeded()).isFalse();
        assertThat(st.resetsAt()).isEqualTo(Instant.parse("2026-10-23T17:00:00Z"));
    }
}
