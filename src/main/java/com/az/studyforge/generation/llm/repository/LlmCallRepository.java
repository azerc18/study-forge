package com.az.studyforge.generation.llm.repository;

import com.az.studyforge.generation.llm.dto.ProviderUsage;
import com.az.studyforge.generation.llm.entity.LlmCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {
    @Query("""
            select new com.az.studyforge.generation.llm.dto.ProviderUsage(
                       c.provider, coalesce(sum(c.promptTokens), 0), coalesce(sum(c.completionTokens), 0))
            from LlmCall c
            where c.createdAt >= :from
            group by c.provider
            """)
    List<ProviderUsage> sumTokensByProviderSince(@Param("from") Instant from);
}