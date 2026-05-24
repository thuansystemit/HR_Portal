import { Routes } from '@angular/router';

export const RECRUITMENT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/job-list/job-list.page').then(m => m.JobListPage),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./pages/job-form/job-form.page').then(m => m.JobFormPage),
  },
  {
    path: ':id/edit',
    loadComponent: () =>
      import('./pages/job-form/job-form.page').then(m => m.JobFormPage),
  },
  {
    path: ':id/board',
    loadComponent: () =>
      import('./pages/job-board/job-board.page').then(m => m.JobBoardPage),
  },
];
