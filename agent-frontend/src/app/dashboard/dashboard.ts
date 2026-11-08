import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProjectService, ProjectResponse } from '../services/project';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard implements OnInit {
  private projectService = inject(ProjectService);

  projects = signal<ProjectResponse[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  total = computed(() => this.projects().length);
  shared = computed(() => this.projects().filter(p => p.overallStatus === 'DONE').length);
  stock = computed(() => this.projects().filter(p => p.overallStatus !== 'DONE').length);

  recent = computed(() => this.projects().slice(0, 5));

  ngOnInit(): void {
    this.projectService.getAllProjects().subscribe({
      next: (data) => {
        this.projects.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Veriler yüklenemedi. Backend çalışıyor mu?');
        this.loading.set(false);
        console.error(err);
      }
    });
  }
}