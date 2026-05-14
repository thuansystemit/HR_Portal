import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, map, catchError, EMPTY } from 'rxjs';
import { AuthUser, LoginCredentials } from '../models/auth';
import { RolePermissions } from '../../features/roles/models/role.model';
import { environment } from '../../../environments/environment';

/** Shape returned by the backend for /auth/login and /auth/me */
interface BackendUser {
  id:          string;
  fullName:    string;
  email:       string;
  roleId:      string;
  roleName:    string;
  permissions: string[];
}

interface LoginResponse {
  user: BackendUser;
}

const SESSION_KEY = 'demo_user';

const PERMISSION_MAP: Record<string, keyof RolePermissions> = {
  usersView:   'usersView',
  usersCreate: 'usersCreate',
  usersEdit:   'usersEdit',
  usersDelete: 'usersDelete',
  rolesView:   'rolesView',
  rolesEdit:   'rolesEdit',
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http   = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _user   = signal<AuthUser | null>(this.restoreSession());
  readonly user            = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);

  // ─── Permission helper ────────────────────────────────────────────────────

  can(permission: keyof RolePermissions): boolean {
    return this._user()?.permissions[permission] ?? false;
  }

  permissionsFromArray(codes: string[]): RolePermissions {
    const perms: RolePermissions = {
      usersView: false, usersCreate: false, usersEdit: false, usersDelete: false,
      rolesView: false, rolesEdit: false,
    };
    for (const code of codes) {
      const key = PERMISSION_MAP[code];
      if (key) perms[key] = true;
    }
    return perms;
  }

  // ─── Backend mapping ──────────────────────────────────────────────────────

  private mapUser(b: BackendUser): AuthUser {
    return {
      id:          b.id,
      name:        b.fullName,
      email:       b.email,
      roleId:      b.roleId,
      roleName:    b.roleName,
      permissions: this.permissionsFromArray(b.permissions),
    };
  }

  // ─── Auth actions ─────────────────────────────────────────────────────────

  login(creds: LoginCredentials): Observable<AuthUser> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, creds, { withCredentials: true })
      .pipe(
        map(res => this.mapUser(res.user)),
        tap(user => this.persist(user)),
      );
  }

  logout(): void {
    this.http
      .post(`${environment.apiUrl}/auth/logout`, {}, { withCredentials: true })
      .pipe(catchError(() => EMPTY))
      .subscribe(() => {
        this._user.set(null);
        sessionStorage.removeItem(SESSION_KEY);
        this.router.navigate(['/login']);
      });
  }

  me(): Observable<AuthUser> {
    return this.http
      .get<BackendUser>(`${environment.apiUrl}/auth/me`, { withCredentials: true })
      .pipe(
        map(b => this.mapUser(b)),
        tap(user => this.persist(user)),
      );
  }

  changePassword(current: string, next: string): Observable<void> {
    return this.http.put<void>(
      `${environment.apiUrl}/auth/change-password`,
      { currentPassword: current, newPassword: next },
      { withCredentials: true },
    );
  }

  /** Updates the displayed name locally (no dedicated profile-update endpoint yet). */
  updateName(name: string): void {
    const user = this._user();
    if (!user) return;
    this.persist({ ...user, name });
  }

  // ─── Session helpers ──────────────────────────────────────────────────────

  private persist(user: AuthUser): void {
    this._user.set(user);
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(user));
  }

  private restoreSession(): AuthUser | null {
    try {
      const raw = sessionStorage.getItem(SESSION_KEY);
      return raw ? (JSON.parse(raw) as AuthUser) : null;
    } catch {
      return null;
    }
  }
}
