package com.curiosity.agent_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePartRequest(
        @NotBlank(message = "Part metni boş olamaz") String text,
        String imagePrompt
) {
}
