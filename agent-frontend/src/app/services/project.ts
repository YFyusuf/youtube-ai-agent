import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ProjectResponse {
  id: number;
  topic: string;
  overallStatus: string;
  createdAt: string;
  stepCount: number;
}

export interface StepResponse {
  id: number;
  type: string;
  status: string;
  orderIndex: number;
}

export interface ProjectDetailResponse {
  id: number;
  topic: string;
  folderName: string | null;
  overallStatus: string;
  createdAt: string;
  steps: StepResponse[];
}
export interface PartResponse {
  id: number;
  orderIndex: number;
  text: string;
  imagePrompt: string | null;
  audioFile: string | null;
  imageFile: string | null;
}

export interface StepDetailResponse {
  id: number;
  type: string;
  status: string;
  orderIndex: number;
  log: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  projectId: number;
  projectTopic: string;
  rawScript: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class ProjectService {
  private http = inject(HttpClient);
  private baseUrl = 'http://localhost:8080/api/projects';

  getAllProjects(): Observable<ProjectResponse[]> {
    return this.http.get<ProjectResponse[]>(this.baseUrl);
  }

  getProject(id: number): Observable<ProjectDetailResponse> {
    return this.http.get<ProjectDetailResponse>(`${this.baseUrl}/${id}`);
  }
  createProject(topic: string, folderName?: string): Observable<ProjectDetailResponse> {
    return this.http.post<ProjectDetailResponse>(this.baseUrl, {
      topic,
      folderName: folderName || null
    });
  }
  updateFolder(projectId: number, folderName: string): Observable<ProjectDetailResponse> {
    return this.http.put<ProjectDetailResponse>(
      `${this.baseUrl}/${projectId}/folder`,
      { folderName }
    );
  }
  runStep(stepId: number): Observable<StepResponse> {
    return this.http.post<StepResponse>(`${this.baseUrl}/steps/${stepId}/run`, {});
  }
  getStep(stepId: number): Observable<StepDetailResponse> {
    return this.http.get<StepDetailResponse>(`${this.baseUrl}/steps/${stepId}`);
  }
  saveScript(stepId: number, script: string): Observable<StepDetailResponse> {
    return this.http.post<StepDetailResponse>(
      `${this.baseUrl}/steps/${stepId}/script`,
      { script }
    );
  }
  getParts(projectId: number): Observable<PartResponse[]> {
    return this.http.get<PartResponse[]>(`${this.baseUrl}/${projectId}/parts`);
  }
  updatePart(partId: number, text: string, imagePrompt: string | null): Observable<PartResponse> {
    return this.http.put<PartResponse>(`${this.baseUrl}/parts/${partId}`, { text, imagePrompt });
  }
  regeneratePartAudio(partId: number): Observable<PartResponse> {
    return this.http.post<PartResponse>(`${this.baseUrl}/parts/${partId}/tts`, {});
  }
  regeneratePartImage(partId: number): Observable<PartResponse> {
    return this.http.post<PartResponse>(`${this.baseUrl}/parts/${partId}/image`, {});
  }
}