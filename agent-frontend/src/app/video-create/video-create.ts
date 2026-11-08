import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ProjectService } from '../services/project';

@Component({
  selector: 'app-video-create',
  imports: [FormsModule],
  templateUrl: './video-create.html',
  styleUrl: './video-create.scss'
})
export class VideoCreate {
  private projectService = inject(ProjectService);
  private router = inject(Router);

  topicValue = '';
  folderValue = '';
  submitting = signal(false);
  error = signal<string | null>(null);

  create(): void {
    const value = this.topicValue.trim();
    if (!value) {
      this.error.set('Lütfen bir konu gir.');
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.projectService.createProject(value, this.folderValue.trim()).subscribe({
      next: (project) => {
        this.router.navigate(['/projects', project.id]);
      },
      error: (err) => {
        this.error.set('Proje oluşturulamadı. Backend çalışıyor mu?');
        this.submitting.set(false);
        console.error(err);
      }
    });
  }
}