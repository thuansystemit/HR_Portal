import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

export const authGuard: CanActivateFn = () => {
  // Replace with real auth check (e.g. inject AuthService)
  const isAuthenticated = true;
  if (!isAuthenticated) {
    inject(Router).navigate(['/login']);
    return false;
  }
  return true;
};
