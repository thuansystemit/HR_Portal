import { Routes } from '@angular/router';

export const HR_ANALYTICS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/hr-analytics/hr-analytics.page').then(m => m.HrAnalyticsPage),
  },
];
