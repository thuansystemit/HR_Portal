import { Routes } from '@angular/router';

export const KNOWLEDGE_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/entity-list/entity-list.page').then(m => m.EntityListPage),
  },
  {
    path: ':entityId',
    loadComponent: () =>
      import('./pages/entity-detail/entity-detail.page').then(m => m.EntityDetailPage),
  },
];
