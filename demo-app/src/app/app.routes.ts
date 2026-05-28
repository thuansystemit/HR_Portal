import { Routes } from '@angular/router';
import { Shell } from './layout/shell/shell';
import { authGuard, guestGuard, permGuard } from './auth/guards/auth-guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/pages/login/login').then(m => m.Login),
    canActivate: [guestGuard],
  },
  {
    path: 'mfa-verify',
    loadComponent: () => import('./auth/pages/mfa-verify/mfa-verify').then(m => m.MfaVerify),
    canActivate: [guestGuard],
  },
  {
    path: 'mfa-setup',
    loadComponent: () => import('./auth/pages/mfa-setup/mfa-setup').then(m => m.MfaSetup),
    canActivate: [guestGuard],
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadChildren: () =>
          import('./features/dashboard/dashboard.routes').then(m => m.DASHBOARD_ROUTES),
      },
      {
        path: 'users',
        canActivate: [permGuard('usersView')],
        loadChildren: () =>
          import('./features/users/users.routes').then(m => m.USERS_ROUTES),
      },
      {
        path: 'roles',
        canActivate: [permGuard('rolesView')],
        loadChildren: () =>
          import('./features/roles/roles.routes').then(m => m.ROLES_ROUTES),
      },
      {
        path: 'access-denied',
        loadComponent: () =>
          import('./shared/pages/access-denied/access-denied').then(m => m.AccessDenied),
      },
      {
        path: 'reports',
        canActivate: [permGuard('rolesView')],
        loadChildren: () =>
          import('./features/reports/reports.routes').then(m => m.REPORTS_ROUTES),
      },
      {
        path: 'documents',
        loadChildren: () =>
          import('./features/documents/documents.routes').then(m => m.DOCUMENTS_ROUTES),
      },
      {
        path: 'hiring-requests',
        loadChildren: () =>
          import('./features/hiring-requests/hiring-requests.routes').then(m => m.HIRING_REQUESTS_ROUTES),
      },
      {
        path: 'cv-candidates',
        canActivate: [permGuard('candidateSearch')],
        loadChildren: () =>
          import('./features/cv-candidates/cv-candidates.routes').then(m => m.CV_CANDIDATES_ROUTES),
      },
      {
        path: 'cv-shares',
        canActivate: [permGuard('cvSharesReceive')],
        loadChildren: () =>
          import('./features/cv-shares/cv-shares.routes').then(m => m.CV_SHARES_ROUTES),
      },
      {
        path: 'recruitment',
        canActivate: [permGuard('recruitmentManage')],
        loadChildren: () =>
          import('./features/recruitment/recruitment.routes').then(m => m.RECRUITMENT_ROUTES),
      },
      {
        path: 'knowledge',
        loadChildren: () =>
          import('./features/knowledge/knowledge.routes').then(m => m.KNOWLEDGE_ROUTES),
      },
      {
        path: 'hr-analytics',
        loadChildren: () =>
          import('./features/hr-analytics/hr-analytics.routes').then(m => m.HR_ANALYTICS_ROUTES),
      },
      {
        path: 'profile',
        loadChildren: () =>
          import('./features/profile/profile-routes.routes').then(m => m.PROFILE_ROUTES),
      },
      {
        path: 'settings',
        canActivate: [permGuard('rolesEdit')],
        loadChildren: () =>
          import('./features/settings/settings.routes').then(m => m.SETTINGS_ROUTES),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: '' },
];
