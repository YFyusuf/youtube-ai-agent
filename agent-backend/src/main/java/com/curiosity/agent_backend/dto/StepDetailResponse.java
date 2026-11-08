package com.curiosity.agent_backend.dto;

import com.curiosity.agent_backend.domain.PipelineStep;
import com.curiosity.agent_backend.domain.StepStatus;
import com.curiosity.agent_backend.domain.StepType;

import java.time.Instant;

public record StepDetailResponse(
        Long id,
        StepType type,
        StepStatus status,
        int orderIndex,
        String log,
        Instant startedAt,
        Instant finishedAt,
        Long projectId,
        String projectTopic,
        String rawScript
) {
    public static StepDetailResponse from(PipelineStep step) {
        var project = step.getProject();
        return new StepDetailResponse(
                step.getId(),
                step.getType(),
                step.getStatus(),
                step.getOrderIndex(),
                step.getLog(),
                step.getStartedAt(),
                step.getFinishedAt(),
                project.getId(),
                project.getTopic(),
                project.getRawScript()
        );
    }
}