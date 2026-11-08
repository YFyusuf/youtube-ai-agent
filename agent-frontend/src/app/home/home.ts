import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProjectService, ProjectResponse } from '../services/project';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss'
})
export class Home implements OnInit {
  private projectService = inject(ProjectService);

  projects = signal<ProjectResponse[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.projectService.getAllProjects().subscribe({
      next: (data) => {
        this.projects.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Projeler yüklenemedi. Backend çalışıyor mu?');
        this.loading.set(false);
        console.error(err);
      }
    });
  }
}