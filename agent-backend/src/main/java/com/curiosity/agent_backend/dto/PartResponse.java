package com.curiosity.agent_backend.dto;

import com.curiosity.agent_backend.domain.ScriptPart;

public record PartResponse(
        Long id,
        int orderIndex,
        String text,
        String imagePrompt,
        String audioFile,
        String imageFile
) {
    public static PartResponse from(ScriptPart part) {
        return new PartResponse(
                part.getId(),
                part.getOrderIndex(),
                part.getText(),
                part.getImagePrompt(),
                part.getAudioFile(),
                part.getImageFile()
        );
    }
}
