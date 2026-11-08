package com.curiosity.agent_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
        @NotBlank(message = "topic boş olamaz") String topic,
        String folderName // boş bırakılırsa topic'ten türetilir
) {
}
