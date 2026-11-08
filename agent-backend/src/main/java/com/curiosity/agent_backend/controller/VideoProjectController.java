package com.curiosity.agent_backend.controller;

import com.curiosity.agent_backend.domain.PipelineStep;
import com.curiosity.agent_backend.domain.VideoProject;
import com.curiosity.agent_backend.dto.CreateProjectRequest;
import com.curiosity.agent_backend.dto.PartResponse;
import com.curiosity.agent_backend.dto.ProjectDetailResponse;
import com.curiosity.agent_backend.dto.ProjectResponse;
import com.curiosity.agent_backend.dto.SaveScriptRequest;
import com.curiosity.agent_backend.dto.StepDetailResponse;
import com.curiosity.agent_backend.dto.StepResponse;
import com.curiosity.agent_backend.dto.UpdateFolderRequest;
import com.curiosity.agent_backend.dto.UpdatePartRequest;
import com.curiosity.agent_backend.service.PipelineService;
import com.curiosity.agent_backend.service.VideoProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class VideoProjectController {

    private final VideoProjectService projectService;
    private final PipelineService pipelineService;

    public VideoProjectController(VideoProjectService projectService,
                                  PipelineService pipelineService) {
        this.projectService = projectService;
        this.pipelineService = pipelineService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request) {
        VideoProject project = projectService.createProject(request.topic(), request.folderName());
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @PutMapping("/{id}/folder")
    public ProjectDetailResponse updateFolder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFolderRequest request) {
        VideoProject project = projectService.updateFolderName(id, request.folderName());
        return ProjectDetailResponse.from(project);
    }

    @GetMapping
    public List<ProjectResponse> getAllProjects() {
        return projectService.getAllProjects().stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ProjectDetailResponse getProject(@PathVariable Long id) {
        VideoProject project = projectService.getProject(id);
        return ProjectDetailResponse.from(project);
    }
    @PostMapping("/steps/{stepId}/run")
    public StepResponse runStep(@PathVariable Long stepId) {
        PipelineStep step = pipelineService.runStep(stepId);
        return StepResponse.from(step);
    }
    @GetMapping("/steps/{stepId}")
    public StepDetailResponse getStep(@PathVariable Long stepId) {
        PipelineStep step = pipelineService.getStep(stepId);
        return StepDetailResponse.from(step);
    }
    @PostMapping("/steps/{stepId}/script")
    public StepDetailResponse saveScript(
            @PathVariable Long stepId,
            @Valid @RequestBody SaveScriptRequest request) {
        PipelineStep step = pipelineService.saveScript(stepId, request.script());
        return StepDetailResponse.from(step);
    }

    @GetMapping("/{id}/parts")
    public List<PartResponse> getParts(@PathVariable Long id) {
        return pipelineService.getParts(id).stream()
                .map(PartResponse::from)
                .toList();
    }

    @PutMapping("/parts/{partId}")
    public PartResponse updatePart(
            @PathVariable Long partId,
            @Valid @RequestBody UpdatePartRequest request) {
        return PartResponse.from(
                pipelineService.updatePart(partId, request.text(), request.imagePrompt()));
    }

    @PostMapping("/parts/{partId}/tts")
    public PartResponse regeneratePartAudio(@PathVariable Long partId) {
        return PartResponse.from(pipelineService.regeneratePartAudio(partId));
    }

    @PostMapping("/parts/{partId}/image")
    public PartResponse regeneratePartImage(@PathVariable Long partId) {
        return PartResponse.from(pipelineService.regeneratePartImage(partId));
    }
}