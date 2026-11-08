package com.curiosity.agent_backend.repository;

import com.curiosity.agent_backend.domain.VideoProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoProjectRepository extends JpaRepository<VideoProject, Long> {

    List<VideoProject> findAllByOrderByCreatedAtDesc();
}