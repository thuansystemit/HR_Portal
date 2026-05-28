import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, map, catchError, EMPTY } from 'rxjs';
import { AuthUser, LoginCredentials } from '../models/auth';
import { RolePermissions } from '../../features/roles/models/role.model';
import { environment } from '../../../environments/environment';

interface BackendUser {
  id:          string;
  fullName:    string;
  email:       string;
  roleId:      string;
  roleName:    string;
  permissions: string[];
}

interface LoginResponse {
  user?:                  BackendUser;
  mfaRequired?:           boolean;
  challengeToken?:        string;
  mfaEnrollmentRequired?: boolean;
  enrollmentToken?:       string;
}

export interface MfaChallenge {
  mfaRequired: true;
  challengeToken: string;
}

export interface MfaEnrollmentChallenge {
  mfaEnrollmentRequired: true;
  enrollmentToken: string;
}

const SESSION_KEY = 'demo_user';

const PERMISSION_MAP: Record<string, keyof RolePermissions> = {
  usersView: 'usersView', usersCreate: 'usersCreate', usersEdit: 'usersEdit', usersDelete: 'usersDelete',
  rolesView: 'rolesView', rolesEdit: 'rolesEdit',
  hiringRequestsSubmit: 'hiringRequestsSubmit', hiringRequestsViewOwn: 'hiringRequestsViewOwn',
  hiringRequestsViewAll: 'hiringRequestsViewAll', hiringRequestsManage: 'hiringRequestsManage',
  cvSharesSend: 'cvSharesSend', cvSharesReceive: 'cvSharesReceive',
  cvSharesSubmitImpression: 'cvSharesSubmitImpression',
  recruitmentBoardView: 'recruitmentBoardView', recruitmentManage: 'recruitmentManage',
  interviewFeedbackSubmit: 'interviewFeedbackSubmit',
  candidateSearch: 'candidateSearch',
  analyticsView: 'analyticsView',
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http   = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _user   = signal<AuthUser | null>(this.restoreSession());
  readonly user            = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);

  can(permission: keyof RolePermissions): boolean {
    return this._user()?.permissions[permission] ?? false;
  }

  permissionsFromArray(codes: string[]): RolePermissions {
    const perms: RolePermissions = {
      usersView: false, usersCreate: false, usersEdit: false, usersDelete: false,
      rolesView: false, rolesEdit: false,
      hiringRequestsSubmit: false, hiringRequestsViewOwn: false,
      hiringRequestsViewAll: false, hiringRequestsManage: false,
      cvSharesSend: false, cvSharesReceive: false, cvSharesSubmitImpression: false,
      recruitmentBoardView: false, recruitmentManage: false, interviewFeedbackSubmit: false,
      candidateSearch: false, analyticsView: false,
    };
    for (const code of codes) {
      const key = PERMISSION_MAP[code];
      if (key) perms[key] = true;
    }
    return perms;
  }

  private mapUser(b: BackendUser): AuthUser {
    return {
      id: b.id, name: b.fullName, email: b.email,
      roleId: b.roleId, roleName: b.roleName,
      permissions: this.permissionsFromArray(b.permissions),
    };
  }

  getBanner(): Observable<{ message: string }> {
    return this.http.get<{ message: string }>(`${environment.apiUrl}/auth/banner`);
  }

  login(creds: LoginCredentials): Observable<AuthUser | MfaChallenge | MfaEnrollmentChallenge> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, creds, { withCredentials: true })
      .pipe(
        map(res => {
          if (res.mfaEnrollmentRequired && res.enrollmentToken) {
            return { mfaEnrollmentRequired: true as const, enrollmentToken: res.enrollmentToken };
          }
          if (res.mfaRequired && res.challengeToken) {
            return { mfaRequired: true as const, challengeToken: res.challengeToken };
          }
          const user = this.mapUser(res.user!);
          this.persist(user);
          return user;
        }),
      );
  }

  verifyMfa(challengeToken: string, code: string): Observable<AuthUser> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/mfa/verify`,
        { challengeToken, code }, { withCredentials: true })
      .pipe(
        map(res => this.mapUser(res.user!)),
        tap(user => this.persist(user)),
      );
  }

  initMfaEnrollment(enrollmentToken: string): Observable<{ secret: string; qrCodeUri: string }> {
    return this.http.post<{ secret: string; qrCodeUri: string }>(
      `${environment.apiUrl}/auth/mfa/enroll/init`,
      { enrollmentToken },
    );
  }

  confirmMfaEnrollment(enrollmentToken: string, totpCode: string): Observable<{ user: AuthUser; backupCodes: string[] }> {
    return this.http
      .post<{ user: BackendUser; backupCodes: string[] }>(
        `${environment.apiUrl}/auth/mfa/enroll/confirm`,
        { enrollmentToken, totpCode },
        { withCredentials: true },
      )
      .pipe(
        map(res => {
          const user = this.mapUser(res.user);
          this.persist(user);
          return { user, backupCodes: res.backupCodes };
        }),
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

  updateName(name: string): void {
    const user = this._user();
    if (!user) return;
    this.persist({ ...user, name });
  }

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
