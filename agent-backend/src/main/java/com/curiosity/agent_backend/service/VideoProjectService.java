package com.curiosity.agent_backend.service;

import com.curiosity.agent_backend.domain.PipelineStep;
import com.curiosity.agent_backend.domain.StepType;
import com.curiosity.agent_backend.domain.VideoProject;
import com.curiosity.agent_backend.repository.VideoProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class VideoProjectService {

    private final VideoProjectRepository projectRepository;

    public VideoProjectService(VideoProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public VideoProject createProject(String topic, String folderName) {
        VideoProject project = new VideoProject();
        project.setTopic(topic);
        project.setFolderName(sanitizeFolderName(
                folderName == null || folderName.isBlank() ? topic : folderName));

        int index = 0;
        for (StepType type : StepType.values()) {
            PipelineStep step = new PipelineStep();
            step.setType(type);
            step.setOrderIndex(index++);
            project.addStep(step);
        }

        return projectRepository.save(project);
    }

    @Transactional
    public VideoProject updateFolderName(Long id, String folderName) {
        VideoProject project = getProject(id);
        project.setFolderName(sanitizeFolderName(folderName));
        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public List<VideoProject> getAllProjects() {
        return projectRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public VideoProject getProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proje bulunamadı: " + id));
    }

    // Klasör adını dosya sistemine güvenli hale getirir:
    // küçük harf, harf/rakam dışı her şey tire, path kaçışı yok
    private String sanitizeFolderName(String raw) {
        String cleaned = raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9çğıöşü]+", "-")
                .replaceAll("^-+|-+$", "");
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Geçerli bir klasör adı üretilemedi: " + raw);
        }
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }
}
