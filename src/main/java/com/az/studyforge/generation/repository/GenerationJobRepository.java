package com.az.studyforge.generation.repository;

import com.az.studyforge.generation.entity.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {
}
