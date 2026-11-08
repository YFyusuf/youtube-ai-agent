package com.curiosity.agent_backend.dto;

import com.curiosity.agent_backend.domain.StepStatus;
import com.curiosity.agent_backend.domain.VideoProject;

import java.time.Instant;
import java.util.List;

public record ProjectDetailResponse(
        Long id,
        String topic,
        String folderName,
        StepStatus overallStatus,
        Instant createdAt,
        List<StepResponse> steps
) {
    public static ProjectDetailResponse from(VideoProject project) {
        List<StepResponse> stepList = project.getSteps().stream()
                .map(StepResponse::from)
                .toList();

        return new ProjectDetailResponse(
                project.getId(),
                project.getTopic(),
                project.getFolderName(),
                project.getOverallStatus(),
                project.getCreatedAt(),
                stepList
        );
    }
}