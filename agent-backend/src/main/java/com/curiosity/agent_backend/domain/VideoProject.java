package com.curiosity.agent_backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class VideoProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    private String folderName;

    @Column(columnDefinition = "TEXT")
    private String rawScript;

    @Column(columnDefinition = "TEXT")
    private String finalScript;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepStatus overallStatus = StepStatus.PENDING;

    private Instant createdAt;

    @OneToMany(
        mappedBy = "project",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderBy("orderIndex ASC")
    private List<PipelineStep> steps = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
    }

    public void addStep(PipelineStep step) {
        step.setProject(this);
        this.steps.add(step);
    }
}