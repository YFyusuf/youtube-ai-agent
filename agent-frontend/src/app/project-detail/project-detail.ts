import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ProjectService, ProjectDetailResponse, PartResponse } from '../services/project';

@Component({
  selector: 'app-project-detail',
  imports: [RouterLink, FormsModule],
  templateUrl: './project-detail.html',
  styleUrl: './project-detail.scss'
})
export class ProjectDetail implements OnInit {
  private projectService = inject(ProjectService);
  private route = inject(ActivatedRoute);

  project = signal<ProjectDetailResponse | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  runningStepId = signal<number | null>(null);

  private projectId!: number;

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.projectId = Number(idParam);
    this.loadProject();
  }

  folderValue = '';
  savingFolder = signal(false);

  loadProject(): void {
    this.projectService.getProject(this.projectId).subscribe({
      next: (data) => {
        this.project.set(data);
        this.folderValue = data.folderName ?? '';
        this.loading.set(false);
        // Part'lar ayrı endpoint'ten gelir; SPLIT koşulduysa burada listelenir
        this.loadParts();
      },
      error: (err) => {
        this.error.set('Proje yüklenemedi.');
        this.loading.set(false);
        console.error(err);
      }
    });
  }

  saveFolder(): void {
    const value = this.folderValue.trim();
    if (!value) {
      this.error.set('Klasör adı boş olamaz.');
      return;
    }
    this.savingFolder.set(true);
    this.error.set(null);
    this.projectService.updateFolder(this.projectId, value).subscribe({
      next: (data) => {
        this.project.set(data);
        this.folderValue = data.folderName ?? '';
        this.savingFolder.set(false);
      },
      error: (err) => {
        this.error.set('Klasör adı kaydedilemedi.');
        this.savingFolder.set(false);
        console.error(err);
      }
    });
  }

  // ---- Part listesi: görüntüleme, düzenleme, tek part yeniden üretim ----

  parts = signal<PartResponse[]>([]);
  // Aynı anda tek part işlemi: hangi part hangi işte meşgul
  busyPartId = signal<number | null>(null);
  busyAction = signal<'save' | 'tts' | 'image' | null>(null);
  partMessage = signal<string | null>(null);

  loadParts(): void {
    this.projectService.getParts(this.projectId).subscribe({
      next: (data) => this.parts.set(data),
      error: (err) => console.error(err)
    });
  }

  private replacePart(updated: PartResponse): void {
    this.parts.update(list => list.map(p => p.id === updated.id ? updated : p));
  }

  private handlePartError(err: any, fallback: string): void {
    // GlobalExceptionHandler 400'de {message: ...} döndürür
    this.partMessage.set(err?.error?.message ?? fallback);
    this.busyPartId.set(null);
    this.busyAction.set(null);
  }

  savePart(part: PartResponse): void {
    this.busyPartId.set(part.id);
    this.busyAction.set('save');
    this.partMessage.set(null);
    this.projectService.updatePart(part.id, part.text, part.imagePrompt).subscribe({
      next: (data) => {
        this.replacePart(data);
        this.busyPartId.set(null);
        this.busyAction.set(null);
        this.partMessage.set(`Part ${data.orderIndex} kaydedildi.`);
      },
      error: (err) => this.handlePartError(err, 'Part kaydedilemedi.')
    });
  }

  regenAudio(part: PartResponse): void {
    this.busyPartId.set(part.id);
    this.busyAction.set('tts');
    this.partMessage.set(null);
    this.projectService.regeneratePartAudio(part.id).subscribe({
      next: (data) => {
        this.replacePart(data);
        this.busyPartId.set(null);
        this.busyAction.set(null);
        this.partMessage.set(`Part ${data.orderIndex} sesi yeniden üretildi.`);
      },
      error: (err) => this.handlePartError(err, 'Ses üretilemedi. Python agent ayakta mı?')
    });
  }

  regenImage(part: PartResponse): void {
    this.busyPartId.set(part.id);
    this.busyAction.set('image');
    this.partMessage.set(null);
    this.projectService.regeneratePartImage(part.id).subscribe({
      next: (data) => {
        this.replacePart(data);
        this.busyPartId.set(null);
        this.busyAction.set(null);
        this.partMessage.set(`Part ${data.orderIndex} görseli yeniden üretildi.`);
      },
      error: (err) => this.handlePartError(err, 'Görsel üretilemedi. Python agent ayakta mı?')
    });
  }

  runStep(stepId: number): void {
    this.runningStepId.set(stepId);
    this.projectService.runStep(stepId).subscribe({
      next: () => {
        this.runningStepId.set(null);
        this.loadProject();
      },
      error: (err) => {
        this.runningStepId.set(null);
        this.error.set('Adım çalıştırılamadı.');
        console.error(err);
      }
    });
  }
}