package com.curiosity.agent_backend.repository;

import com.curiosity.agent_backend.domain.PipelineStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PipelineStepRepository extends JpaRepository<PipelineStep, Long> {

    List<PipelineStep> findByProjectIdOrderByOrderIndexAsc(Long projectId);

    // Transaction dışında da project'e erişebilmek için join fetch
    @Query("select s from PipelineStep s join fetch s.project where s.id = :stepId")
    Optional<PipelineStep> findWithProjectById(@Param("stepId") Long stepId);
}