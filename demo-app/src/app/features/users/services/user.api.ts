import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { User, UserDto, UserRole } from '../models/user.model';
import { environment } from '../../../../environments/environment';

/** Shape of a single user returned by the backend */
interface BackendUser {
  id:        string;
  fullName:  string;
  email:     string;
  roleId:    string;
  roleName:  string;
  status:    'active' | 'inactive' | 'pending';
  createdAt: string;
}

/** Paginated list wrapper */
interface PagedResponse<T> {
  content:       T[];
  page:          number;
  size:          number;
  totalElements: number;
  totalPages:    number;
}

function roleNameToUserRole(roleName: string): UserRole {
  const lower = roleName.toLowerCase();
  if (lower === 'administrator') return 'admin';
  if (lower === 'manager')       return 'manager';
  return 'viewer';
}

function mapUser(b: BackendUser): User {
  return {
    id:        b.id,
    name:      b.fullName,
    email:     b.email,
    role:      roleNameToUserRole(b.roleName),
    roleId:    b.roleId,
    status:    b.status,
    createdAt: b.createdAt,
  };
}

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/users`;

  getAll(page = 0, size = 100): Observable<{ users: User[]; total: number }> {
    return this.http
      .get<PagedResponse<BackendUser>>(`${this.base}?page=${page}&size=${size}`)
      .pipe(
        map(res => ({
          users: res.content.map(mapUser),
          total: res.totalElements,
        })),
      );
  }

  getByRoleName(roleName: string): Observable<User[]> {
    return this.http
      .get<BackendUser[]>(`${this.base}?roleName=${encodeURIComponent(roleName)}`)
      .pipe(map(list => list.map(mapUser)));
  }

  getById(id: string): Observable<User | undefined> {
    return this.http
      .get<BackendUser>(`${this.base}/${id}`)
      .pipe(map(mapUser));
  }

  create(dto: UserDto): Observable<User> {
    const body: Record<string, unknown> = {
      fullName: dto.name,
      email:    dto.email,
      roleId:   dto.roleId,
      status:   dto.status,
      password: dto.password,
    };
    return this.http.post<BackendUser>(this.base, body).pipe(map(mapUser));
  }

  update(id: string, dto: UserDto): Observable<User> {
    const body: Record<string, unknown> = {
      fullName: dto.name,
      email:    dto.email,
      roleId:   dto.roleId,
      status:   dto.status,
    };
    return this.http.put<BackendUser>(`${this.base}/${id}`, body).pipe(map(mapUser));
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
