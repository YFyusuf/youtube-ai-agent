package com.curiosity.agent_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveScriptRequest(
        @NotBlank(message = "script boş olamaz") String script
) {
}