package com.curiosity.agent_backend.dto;

import com.curiosity.agent_backend.domain.StepStatus;
import com.curiosity.agent_backend.domain.VideoProject;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        String topic,
        StepStatus overallStatus,
        Instant createdAt,
        int stepCount
) {
    public static ProjectResponse from(VideoProject project) {
        return new ProjectResponse(
                project.getId(),
                project.getTopic(),
                project.getOverallStatus(),
                project.getCreatedAt(),
                project.getSteps().size()
        );
    }
}