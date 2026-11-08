package com.curiosity.agent_backend.dto;

import com.curiosity.agent_backend.domain.PipelineStep;
import com.curiosity.agent_backend.domain.StepStatus;
import com.curiosity.agent_backend.domain.StepType;

public record StepResponse(
        Long id,
        StepType type,
        StepStatus status,
        int orderIndex
) {
    public static StepResponse from(PipelineStep step) {
        return new StepResponse(
                step.getId(),
                step.getType(),
                step.getStatus(),
                step.getOrderIndex()
        );
    }
}