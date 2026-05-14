import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth';
import { RolePermissions } from '../../features/roles/models/role.model';

export const authGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? true : router.createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);
  return !auth.isAuthenticated() ? true : router.createUrlTree(['/']);
};

export const permGuard = (permission: keyof RolePermissions): CanActivateFn =>
  () => {
    const auth   = inject(AuthService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
    return auth.can(permission) ? true : router.createUrlTree(['/access-denied']);
  };
