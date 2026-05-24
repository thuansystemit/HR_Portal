import { Routes } from '@angular/router';

export const HIRING_REQUESTS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/request-list/request-list.page').then(m => m.RequestListPage),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./pages/request-form/request-form.page').then(m => m.RequestFormPage),
  },
  {
    path: ':requestId',
    loadComponent: () =>
      import('./pages/request-detail/request-detail.page').then(m => m.RequestDetailPage),
  },
];
