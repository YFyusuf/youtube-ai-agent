import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ProjectService, StepDetailResponse } from '../services/project';

@Component({
  selector: 'app-step-detail',
  imports: [RouterLink, FormsModule],
  templateUrl: './step-detail.html',
  styleUrl: './step-detail.scss'
})
export class StepDetail implements OnInit {
  private projectService = inject(ProjectService);
  private route = inject(ActivatedRoute);

  step = signal<StepDetailResponse | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  scriptText = '';
  saving = signal(false);
  saved = signal(false);

  private stepId!: number;

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('stepId');
    this.stepId = Number(idParam);
    this.loadStep();
  }

  loadStep(): void {
    this.projectService.getStep(this.stepId).subscribe({
      next: (data) => {
        this.step.set(data);
        this.scriptText = data.rawScript ?? '';
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Adım yüklenemedi.');
        this.loading.set(false);
        console.error(err);
      }
    });
  }

  saveScript(): void {
    const text = this.scriptText.trim();
    if (!text) {
      this.error.set('Senaryo boş olamaz.');
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    this.saved.set(false);

    this.projectService.saveScript(this.stepId, text).subscribe({
      next: (data) => {
        this.step.set(data);
        this.scriptText = data.rawScript ?? '';
        this.saving.set(false);
        this.saved.set(true);
      },
      error: (err) => {
        this.error.set('Senaryo kaydedilemedi.');
        this.saving.set(false);
        console.error(err);
      }
    });
  }
  generating = signal(false);
  running = signal(false);

  // Adımın ne yaptığını sayfada özetleyen açıklamalar
  private static readonly HINTS: Record<string, string> = {
    SCRIPT_REVIEW:
      'Senaryoyu iki katmanda inceler: kural kontrolleri (kapanış sorusu, 250 karakter limiti, tekrar kalıpları) + Gemini\'nin editör gözüyle ton/ritim değerlendirmesi. Sonuç ve gerekçeler Log alanında görünür.',
    SCRIPT_SPLIT:
      'Senaryoyu part\'lara böler; her part = 1 ses + 1 görsel. Senaryoda tek başına bir satırda --- varsa part\'lar oradan alınır, yoksa ~100 kelimede bir otomatik bölünür.',
    TTS_GENERATE:
      'Her part için XTTS v2 ile seslendirme üretir (ses/voice_i.wav). Üretilmiş part\'lar atlanır.',
    IMAGE_PROMPT:
      'Her part için Gemini\'den İngilizce görsel prompt\'u ister. VHS/analog stil eki üretimde otomatik eklenir.',
    IMAGE_GENERATE:
      'Her part için fal.ai Flux Schnell ile görsel üretir (gorseller/image_i.jpg). Üretilmiş part\'lar atlanır.',
  };

  stepHint(): string {
    const type = this.step()?.type ?? '';
    return StepDetail.HINTS[type] ?? 'Bu adım henüz uygulanmadı; çalıştırılırsa FAILED döner.';
  }

  runThisStep(): void {
    this.running.set(true);
    this.error.set(null);

    this.projectService.runStep(this.stepId).subscribe({
      next: () => {
        // runStep özet döner; log dahil güncel hali ayrıca çekilir
        this.projectService.getStep(this.stepId).subscribe({
          next: (data) => {
            this.step.set(data);
            this.running.set(false);
            if (data.status === 'FAILED') {
              this.error.set('Adım FAILED ile bitti. Ayrıntılar Log alanında.');
            }
          },
          error: () => {
            this.running.set(false);
          }
        });
      },
      error: (err) => {
        this.error.set('Adım çalıştırılamadı. Backend/Python ayakta mı?');
        this.running.set(false);
        console.error(err);
      }
    });
  }

  generateWithAI(): void {
    this.generating.set(true);
    this.error.set(null);
    this.saved.set(false);

    this.projectService.runStep(this.stepId).subscribe({
      next: () => {
        // runStep senaryoyu üretip kaydetti; güncel halini çekelim
        this.projectService.getStep(this.stepId).subscribe({
          next: (data) => {
            this.step.set(data);
            this.scriptText = data.rawScript ?? '';
            this.generating.set(false);
            // Doğrulama döngüsünden geçemeyen senaryo FAILED döner
            if (data.status === 'FAILED') {
              this.error.set('Senaryo üretildi ama doğrulamadan geçemedi. Ayrıntılar Log alanında.');
            }
          },
          error: () => {
            this.generating.set(false);
          }
        });
      },
      error: (err) => {
        this.error.set('Otomatik üretim başarısız. Gemini/Python çalışıyor mu?');
        this.generating.set(false);
        console.error(err);
      }
    });
  }
}