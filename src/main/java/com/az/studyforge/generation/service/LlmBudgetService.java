package com.az.studyforge.generation.service;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.generation.config.GenerationProperties;
import com.az.studyforge.generation.llm.config.LlmProperties;
import com.az.studyforge.generation.llm.config.LlmProperties.Provider;
import com.az.studyforge.generation.llm.dto.ProviderUsage;
import com.az.studyforge.generation.llm.repository.LlmCallRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LlmBudgetService {

    static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final LlmCallRepository repository;
    private final double dailyBudgetUsd;
    private final Map<String, Provider> providers;
    private final double fallbackInputPrice;
    private final double fallbackOutputPrice;
    private final ZoneId zone;
    private final Clock clock;

    private volatile Snapshot cache;

    /** Số tiền đã tính cho 1 ngày, kèm lúc tính để biết cache còn hạn không. */
    private record Snapshot(LocalDate day, double spentUsd, Instant computedAt) {}

    /** Trạng thái để hiển thị (admin dashboard ở M6.3). */
    public record BudgetStatus(double spentUsd, double budgetUsd, boolean exceeded, Instant resetsAt) {}

    public LlmBudgetService(LlmCallRepository repository, LlmProperties llmProps,
                            GenerationProperties generationProps, Clock clock) {
        this.repository = repository;
        this.dailyBudgetUsd = llmProps.dailyBudgetUsd();
        this.providers = llmProps.providers().stream()
                .collect(Collectors.toMap(Provider::name, Function.identity(), (a, b) -> a));
        // Provider đã bị xoá khỏi config nhưng còn log hôm nay → tính theo giá ĐẮT nhất cho chắc
        this.fallbackInputPrice = llmProps.providers().stream().mapToDouble(Provider::inputPricePerMillion).max().orElse(0);
        this.fallbackOutputPrice = llmProps.providers().stream().mapToDouble(Provider::outputPricePerMillion).max().orElse(0);
        this.zone = generationProps.quotaZone();
        this.clock = clock;
    }

    /** 0 = tắt chặn ngân sách. */
    public boolean enabled() {
        return dailyBudgetUsd > 0;
    }

    public boolean isExceeded() {
        return enabled() && spentTodayUsd() >= dailyBudgetUsd;
    }

    /** Gọi ở đầu các API tốn AI: vượt ngân sách → 503 AI_BUDGET_EXCEEDED (không trừ quota của user). */
    public void check() {
        if (!isExceeded()) return;
        Instant resetsAt = resetsAt();
        throw new AppException(ErrorCode.AI_BUDGET_EXCEEDED, ErrorCode.AI_BUDGET_EXCEEDED.getDefaultMessage(),
                Map.of("resetsAt", resetsAt.toString()), null);
    }

    public BudgetStatus status() {
        double spent = spentTodayUsd();
        return new BudgetStatus(spent, dailyBudgetUsd, enabled() && spent >= dailyBudgetUsd, resetsAt());
    }

    public double spentTodayUsd() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        Snapshot s = cache;
        if (s != null && s.day().equals(today) && now.isBefore(s.computedAt().plus(CACHE_TTL))) {
            return s.spentUsd();
        }
        double spent = cost(repository.sumTokensByProviderSince(today.atStartOfDay(zone).toInstant()));
        cache = new Snapshot(today, spent, now);
        if (enabled() && spent >= dailyBudgetUsd) {
            log.warn("Daily AI budget reached: spent ${} of ${}", "%.4f".formatted(spent), dailyBudgetUsd);
        }
        return spent;
    }

    /** Bỏ cache (dùng sau khi admin đổi cấu hình, hoặc trong test). */
    public void invalidate() {
        cache = null;
    }

    /** public để admin tính chi phí theo ngày bằng đúng công thức giá này. */
    public double cost(List<ProviderUsage> usages) {
        double total = 0;
        for (ProviderUsage u : usages) {
            Provider p = providers.get(u.provider());
            total += p != null
                    ? p.costUsd(u.promptTokens(), u.completionTokens())
                    : (fallbackInputPrice * u.promptTokens() + fallbackOutputPrice * u.completionTokens()) / 1_000_000.0;
        }
        return total;
    }

    private Instant resetsAt() {
        return LocalDate.ofInstant(clock.instant(), zone).plusDays(1).atStartOfDay(zone).toInstant();
    }
}
