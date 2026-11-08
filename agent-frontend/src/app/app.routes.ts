import { Routes } from '@angular/router';
import { Dashboard } from './dashboard/dashboard';
import { Home } from './home/home';
import { ProjectDetail } from './project-detail/project-detail';
import { VideoCreate } from './video-create/video-create';
import { Settings } from './settings/settings';
import { StepDetail } from './step-detail/step-detail';

export const routes: Routes = [
  { path: '', component: Dashboard },
  { path: 'videos', component: Home },
  { path: 'projects/:id', component: ProjectDetail },
  { path: 'new', component: VideoCreate },
  { path: 'settings', component: Settings },
  { path: 'steps/:stepId', component: StepDetail },
];