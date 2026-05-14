import { Routes } from '@angular/router';

export const DOCUMENTS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/category-list/category-list.page').then(m => m.CategoryListPage),
  },
  {
    path: ':categoryId',
    loadComponent: () =>
      import('./pages/document-list/document-list.page').then(m => m.DocumentListPage),
  },
];
