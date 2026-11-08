package com.curiosity.agent_backend.domain;

public enum StepType {
    SCRIPT_GENERATE,
    SCRIPT_REVIEW,
    SCRIPT_SPLIT,
    TTS_GENERATE,
    TTS_QC,
    IMAGE_PROMPT,
    IMAGE_GENERATE,
    IMAGE_QC,
    EDIT,
    THUMBNAIL,
    METADATA,
    UPLOAD
}