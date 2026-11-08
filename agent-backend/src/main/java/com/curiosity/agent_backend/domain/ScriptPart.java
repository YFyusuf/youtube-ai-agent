package com.curiosity.agent_backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Senaryonun TTS/görsel birimi.
 * Bir part = bir ses dosyası (voice_i.wav) + bir görsel (image_i).
 * orderIndex 1'den başlar ki dosya adlarıyla (voice_1.wav) hizalı olsun.
 */
@Entity
@Getter
@Setter
public class ScriptPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int orderIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(columnDefinition = "TEXT")
    private String imagePrompt;

    private String audioFile;

    private String imageFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private VideoProject project;
}
