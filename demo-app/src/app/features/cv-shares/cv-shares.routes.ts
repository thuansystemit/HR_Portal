import { Routes } from '@angular/router';

export const CV_SHARES_ROUTES: Routes = [
  {
    path: 'inbox',
    loadComponent: () =>
      import('./pages/cv-share-inbox/cv-share-inbox.page').then(m => m.CvShareInboxPage),
  },
  {
    path: ':shareId',
    loadComponent: () =>
      import('./pages/cv-share-detail/cv-share-detail.page').then(m => m.CvShareDetailPage),
  },
];
