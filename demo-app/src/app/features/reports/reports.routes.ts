import { Routes } from '@angular/router';

export const REPORTS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/reports/reports').then(m => m.ReportsPage),
  },
];
