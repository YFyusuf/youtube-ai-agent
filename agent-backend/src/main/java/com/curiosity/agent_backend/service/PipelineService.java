package com.curiosity.agent_backend.service;

import com.curiosity.agent_backend.domain.PipelineStep;
import com.curiosity.agent_backend.domain.ScriptPart;
import com.curiosity.agent_backend.domain.StepStatus;
import com.curiosity.agent_backend.domain.StepType;
import com.curiosity.agent_backend.domain.VideoProject;
import com.curiosity.agent_backend.repository.PipelineStepRepository;
import com.curiosity.agent_backend.repository.ScriptPartRepository;
import com.curiosity.agent_backend.repository.VideoProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PipelineService {

    private final PipelineStepRepository stepRepository;
    private final VideoProjectRepository projectRepository;
    private final ScriptPartRepository partRepository;
    private final AgentClient agentClient;
    private final String contentBaseDir;

    public PipelineService(PipelineStepRepository stepRepository,
                           VideoProjectRepository projectRepository,
                           ScriptPartRepository partRepository,
                           AgentClient agentClient,
                           @Value("${content.base-dir}") String contentBaseDir) {
        this.stepRepository = stepRepository;
        this.projectRepository = projectRepository;
        this.partRepository = partRepository;
        this.agentClient = agentClient;
        this.contentBaseDir = contentBaseDir;
    }

    // Bilerek @Transactional DEĞİL: agent çağrıları dakikalar sürebilir,
    // DB transaction'ını HTTP isteği boyunca açık tutamayız.
    // Her repository.save kendi kısa transaction'ında çalışır.
    public PipelineStep runStep(Long stepId) {
        PipelineStep step = stepRepository.findWithProjectById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Adım bulunamadı: " + stepId));

        VideoProject project = step.getProject();

        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(Instant.now());
        step.setLog(null);
        step = stepRepository.save(step);
        refreshOverallStatus(project);

        try {
            switch (step.getType()) {
                case SCRIPT_GENERATE -> runScriptGenerate(step, project);
                case SCRIPT_REVIEW -> runScriptReview(step, project);
                case SCRIPT_SPLIT -> runScriptSplit(step, project);
                case IMAGE_PROMPT -> runImagePrompt(step, project);
                case TTS_GENERATE -> runTtsGenerate(step, project);
                case IMAGE_GENERATE -> runImageGenerate(step, project);
                default -> {
                    // Uygulanmamış adım DONE görünmemeli; pipeline yanlışlıkla ilerlemesin
                    step.setStatus(StepStatus.FAILED);
                    step.setLog("Bu adım türü henüz uygulanmadı: " + step.getType());
                }
            }
        } catch (Exception e) {
            step.setStatus(StepStatus.FAILED);
            step.setLog("Hata: " + e.getMessage());
        }

        step.setFinishedAt(Instant.now());
        step = stepRepository.save(step);
        refreshOverallStatus(project);

        return step;
    }

    private void runScriptGenerate(PipelineStep step, VideoProject project) {
        Map<String, Object> result = agentClient.generateScript(project.getTopic());
        String script = (String) result.get("script");
        boolean valid = Boolean.TRUE.equals(result.get("valid"));
        Object attempts = result.get("attempts");

        // Geçersiz senaryo da kaydedilir ki kullanıcı inceleyebilsin; ama adım DONE olmaz
        project.setRawScript(script);
        projectRepository.save(project);

        if (valid) {
            step.setStatus(StepStatus.DONE);
            step.setLog("Senaryo üretildi ve doğrulandı ("
                    + attempts + " deneme, " + script.length() + " karakter).");
        } else {
            step.setStatus(StepStatus.FAILED);
            step.setLog("Senaryo " + attempts
                    + " denemede doğrulamadan geçemedi:\n" + formatErrors(result.get("errors")));
        }
    }

    private void runScriptReview(PipelineStep step, VideoProject project) {
        if (project.getRawScript() == null || project.getRawScript().isBlank()) {
            step.setStatus(StepStatus.FAILED);
            step.setLog("Senaryo yok. Önce SCRIPT_GENERATE çalıştırılmalı (veya senaryoyu elle gir).");
            return;
        }

        Map<String, Object> result = agentClient.reviewScript(project.getTopic(), project.getRawScript());
        boolean approved = Boolean.TRUE.equals(result.get("approved"));
        String verdict = String.valueOf(result.getOrDefault("verdict", ""));
        String issues = formatErrors(result.get("issues"));
        String hardErrors = formatErrors(result.get("hardErrors"));

        StringBuilder log = new StringBuilder("Editör değerlendirmesi: " + verdict);
        if (!hardErrors.isBlank()) {
            log.append("\n\nKural ihlalleri:\n").append(hardErrors);
        }
        if (!issues.isBlank()) {
            log.append("\n\nNotlar:\n").append(issues);
        }

        step.setStatus(approved ? StepStatus.DONE : StepStatus.FAILED);
        step.setLog(log.toString());
    }

    private void runScriptSplit(PipelineStep step, VideoProject project) {
        if (project.getRawScript() == null || project.getRawScript().isBlank()) {
            step.setStatus(StepStatus.FAILED);
            step.setLog("Senaryo yok. Önce SCRIPT_GENERATE çalıştırılmalı.");
            return;
        }

        Map<String, Object> result = agentClient.splitScript(project.getRawScript());
        List<String> parts = stringList(result.get("parts"));

        // Yeniden bölme = eski partlar (ve bağlı prompt/dosya kayıtları) geçersiz
        partRepository.deleteAll(partRepository.findByProjectIdOrderByOrderIndexAsc(project.getId()));

        int index = 1;
        for (String text : parts) {
            ScriptPart part = new ScriptPart();
            part.setProject(project);
            part.setOrderIndex(index++);
            part.setText(text);
            partRepository.save(part);
        }

        step.setStatus(StepStatus.DONE);
        step.setLog("manual".equals(result.get("mode"))
                ? "Elle konan ayraçlardan (---) " + parts.size() + " part alındı."
                : "Senaryo otomatik " + parts.size() + " parta bölündü.");
    }

    private void runImagePrompt(PipelineStep step, VideoProject project) {
        List<ScriptPart> parts = partRepository.findByProjectIdOrderByOrderIndexAsc(project.getId());
        if (parts.isEmpty()) {
            step.setStatus(StepStatus.FAILED);
            step.setLog("Part yok. Önce SCRIPT_SPLIT çalıştırılmalı.");
            return;
        }

        List<String> texts = parts.stream().map(ScriptPart::getText).toList();
        Map<String, Object> result = agentClient.generateImagePrompts(project.getTopic(), texts);
        List<String> prompts = stringList(result.get("prompts"));

        if (prompts.size() != parts.size()) {
            step.setStatus(StepStatus.FAILED);
            step.setLog("Prompt sayısı part sayısıyla uyuşmuyor: "
                    + prompts.size() + " prompt, " + parts.size() + " part.");
            return;
        }

        for (int i = 0; i < parts.size(); i++) {
            parts.get(i).setImagePrompt(prompts.get(i));
        }
        partRepository.saveAll(parts);

        step.setStatus(StepStatus.DONE);
        step.setLog(parts.size() + " part için görsel prompt üretildi.");
    }

    private void runTtsGenerate(PipelineStep step, VideoProject project) {
        List<ScriptPart> parts = partRepository.findByProjectIdOrderByOrderIndexAsc(project.getId());
        if (parts.isEmpty()) {
            step.setStatus(StepStatus.FAILED);
            step.setLog("Part yok. Önce SCRIPT_SPLIT çalıştırılmalı.");
            return;
        }

        String outputDir = projectDir(project).resolve("ses").toString();
        int produced = 0;
        int skipped = 0;

        for (ScriptPart part : parts) {
            // Daha önce üretilmiş part atlanır: uzun TTS koşusu yarıda kesilirse
            // adım yeniden çalıştırıldığında kaldığı yerden devam eder
            if (part.getAudioFile() != null) {
                skipped++;
                continue;
            }
            try {
                Map<String, Object> result =
                        agentClient.generateTts(part.getText(), outputDir, part.getOrderIndex());
                part.setAudioFile((String) result.get("file"));
                partRepository.save(part);
                produced++;
            } catch (Exception e) {
                step.setStatus(StepStatus.FAILED);
                step.setLog("Part " + part.getOrderIndex() + " seslendirilirken hata: " + e.getMessage()
                        + "\nÖnceki " + produced + " part kaydedildi; adım yeniden çalıştırılınca kaldığı yerden devam eder.");
                return;
            }
        }

        step.setStatus(StepStatus.DONE);
        step.setLog(produced + " ses üretildi"
                + (skipped > 0 ? ", " + skipped + " part zaten hazırdı" : "")
                + ". Klasör: " + outputDir);
    }

    private void runImageGenerate(PipelineStep step, VideoProject project) {
        List<ScriptPart> parts = partRepository.findByProjectIdOrderByOrderIndexAsc(project.getId());
        if (parts.isEmpty()) {
            step.setStatus(StepStatus.FAILED);
            step.setLog("Part yok. Önce SCRIPT_SPLIT çalıştırılmalı.");
            return;
        }
        if (parts.stream().anyMatch(p -> p.getImagePrompt() == null || p.getImagePrompt().isBlank())) {
            step.setStatus(StepStatus.FAILED);
            step.setLog("Görsel prompt'ları eksik. Önce IMAGE_PROMPT çalıştırılmalı.");
            return;
        }

        String outputDir = projectDir(project).resolve("gorseller").toString();
        int produced = 0;
        int skipped = 0;

        for (ScriptPart part : parts) {
            if (part.getImageFile() != null) {
                skipped++;
                continue;
            }
            try {
                Map<String, Object> result =
                        agentClient.generateImage(part.getImagePrompt(), outputDir, part.getOrderIndex());
                part.setImageFile((String) result.get("file"));
                partRepository.save(part);
                produced++;
            } catch (Exception e) {
                step.setStatus(StepStatus.FAILED);
                step.setLog("Part " + part.getOrderIndex() + " görseli üretilirken hata: " + e.getMessage()
                        + "\nÖnceki " + produced + " görsel kaydedildi; adım yeniden çalıştırılınca kaldığı yerden devam eder.");
                return;
            }
        }

        step.setStatus(StepStatus.DONE);
        step.setLog(produced + " görsel üretildi"
                + (skipped > 0 ? ", " + skipped + " part zaten hazırdı" : "")
                + ". Klasör: " + outputDir);
    }

    // Projenin çıktı klasörü: content.base-dir + folderName
    private Path projectDir(VideoProject project) {
        if (project.getFolderName() == null || project.getFolderName().isBlank()) {
            throw new IllegalStateException(
                    "Proje klasörü tanımlı değil. Proje sayfasından bir klasör adı belirle.");
        }
        return Path.of(contentBaseDir, project.getFolderName());
    }

    // ---- Part işlemleri: listeleme, düzenleme, tek part yeniden üretim ----

    // XTTS v2 limiti: 250 karakteri aşan cümle seslendirmede patlar.
    // Elle düzenlenen part metni de bu kuraldan muaf değil (CLAUDE.md 4.2).
    private static final int MAX_SENTENCE_LENGTH = 250;

    @Transactional(readOnly = true)
    public List<ScriptPart> getParts(Long projectId) {
        return partRepository.findByProjectIdOrderByOrderIndexAsc(projectId);
    }

    @Transactional
    public ScriptPart updatePart(Long partId, String text, String imagePrompt) {
        ScriptPart part = partRepository.findById(partId)
                .orElseThrow(() -> new IllegalArgumentException("Part bulunamadı: " + partId));

        for (String sentence : text.trim().split("(?<=[.!?])\\s+")) {
            if (sentence.length() > MAX_SENTENCE_LENGTH) {
                throw new IllegalArgumentException(
                        sentence.length() + " karakterlik cümle var, limit " + MAX_SENTENCE_LENGTH
                        + ". Bu cümleyi bölmeden kaydedemezsin: \""
                        + sentence.substring(0, Math.min(80, sentence.length())) + "...\"");
            }
        }

        part.setText(text.trim());
        part.setImagePrompt(imagePrompt == null || imagePrompt.isBlank() ? null : imagePrompt.trim());
        return partRepository.save(part);
    }

    // Bilerek @Transactional değil: TTS dakikalar sürebilir (bkz. runStep notu)
    public ScriptPart regeneratePartAudio(Long partId) {
        ScriptPart part = partRepository.findWithProjectById(partId)
                .orElseThrow(() -> new IllegalArgumentException("Part bulunamadı: " + partId));

        String outputDir = projectDir(part.getProject()).resolve("ses").toString();
        Map<String, Object> result =
                agentClient.generateTts(part.getText(), outputDir, part.getOrderIndex());
        part.setAudioFile((String) result.get("file"));
        return partRepository.save(part);
    }

    // Bilerek @Transactional değil: görsel üretimi uzun sürebilir
    public ScriptPart regeneratePartImage(Long partId) {
        ScriptPart part = partRepository.findWithProjectById(partId)
                .orElseThrow(() -> new IllegalArgumentException("Part bulunamadı: " + partId));

        if (part.getImagePrompt() == null || part.getImagePrompt().isBlank()) {
            throw new IllegalStateException(
                    "Bu part'ın görsel prompt'u yok. Önce IMAGE_PROMPT çalıştır ya da prompt'u elle girip kaydet.");
        }

        String outputDir = projectDir(part.getProject()).resolve("gorseller").toString();
        Map<String, Object> result =
                agentClient.generateImage(part.getImagePrompt(), outputDir, part.getOrderIndex());
        part.setImageFile((String) result.get("file"));
        return partRepository.save(part);
    }

    @Transactional(readOnly = true)
    public PipelineStep getStep(Long stepId) {
        return stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Adım bulunamadı: " + stepId));
    }

    @Transactional
    public PipelineStep saveScript(Long stepId, String script) {
        PipelineStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Adım bulunamadı: " + stepId));

        if (step.getType() != StepType.SCRIPT_GENERATE) {
            throw new IllegalArgumentException("Bu adım bir senaryo adımı değil.");
        }

        VideoProject project = step.getProject();
        project.setRawScript(script);
        projectRepository.save(project);

        step.setStatus(StepStatus.DONE);
        step.setLog("Senaryo elle girildi (" + script.length() + " karakter).");
        step.setStartedAt(Instant.now());
        step.setFinishedAt(Instant.now());
        stepRepository.save(step);
        refreshOverallStatus(project);

        return step;
    }

    // Projenin genel durumunu adımlardan türetir:
    // biri koşuyorsa RUNNING, biri patladıysa FAILED,
    // hepsi bittiyse DONE, aksi halde PENDING.
    private void refreshOverallStatus(VideoProject project) {
        List<PipelineStep> steps =
                stepRepository.findByProjectIdOrderByOrderIndexAsc(project.getId());

        StepStatus overall;
        if (steps.stream().anyMatch(s -> s.getStatus() == StepStatus.RUNNING)) {
            overall = StepStatus.RUNNING;
        } else if (steps.stream().anyMatch(s -> s.getStatus() == StepStatus.FAILED)) {
            overall = StepStatus.FAILED;
        } else if (steps.stream().allMatch(s -> s.getStatus() == StepStatus.DONE
                || s.getStatus() == StepStatus.SKIPPED)) {
            overall = StepStatus.DONE;
        } else {
            overall = StepStatus.PENDING;
        }

        project.setOverallStatus(overall);
        projectRepository.save(project);
    }

    private String formatErrors(Object errors) {
        if (errors instanceof List<?> list) {
            return list.stream()
                    .map(e -> "- " + e)
                    .collect(Collectors.joining("\n"));
        }
        return String.valueOf(errors);
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        throw new IllegalStateException("Agent'tan beklenen liste gelmedi: " + value);
    }
}
