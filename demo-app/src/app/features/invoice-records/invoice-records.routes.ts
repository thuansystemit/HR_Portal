import { Routes } from '@angular/router';

export const INVOICE_RECORDS_ROUTES: Routes = [
  {
    path: ':categoryId',
    loadComponent: () =>
      import('./pages/invoice-list/invoice-list.page').then(m => m.InvoiceListPage),
  },
  {
    path: ':categoryId/:recordId',
    loadComponent: () =>
      import('./pages/invoice-detail/invoice-detail.page').then(m => m.InvoiceDetailPage),
  },
];
