package com.curiosity.agent_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateFolderRequest(
        @NotBlank(message = "folderName boş olamaz") String folderName
) {
}
