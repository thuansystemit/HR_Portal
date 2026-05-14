import { Routes } from '@angular/router';

export const CV_CANDIDATES_ROUTES: Routes = [
  {
    path: ':categoryId',
    loadComponent: () =>
      import('./pages/candidate-list/candidate-list.page').then(m => m.CandidateListPage),
  },
  {
    path: ':categoryId/:candidateId',
    loadComponent: () =>
      import('./pages/candidate-detail/candidate-detail.page').then(m => m.CandidateDetailPage),
  },
];
