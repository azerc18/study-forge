package com.az.studyforge.generation.llm.repository;

import com.az.studyforge.generation.llm.entity.LlmCall;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {}