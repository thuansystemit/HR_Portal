import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Role, RoleDto, RolePermissions } from '../models/role.model';
import { environment } from '../../../../environments/environment';

/** Shape returned by the backend */
interface BackendRole {
  id:              string;
  name:            string;
  description:     string;
  isBuiltin:       boolean;
  permissionCodes: string[];
  userCount:       number;
  createdAt:       string;
}

interface PagedResponse<T> {
  content: T[];
  totalElements: number;
}

const PERMISSION_MAP: Record<string, keyof RolePermissions> = {
  usersView:   'usersView',
  usersCreate: 'usersCreate',
  usersEdit:   'usersEdit',
  usersDelete: 'usersDelete',
  rolesView:   'rolesView',
  rolesEdit:   'rolesEdit',
};

function codesFromPermissions(p: RolePermissions): string[] {
  return (Object.keys(p) as (keyof RolePermissions)[]).filter(k => p[k]);
}

function permissionsFromCodes(codes: string[]): RolePermissions {
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

function mapRole(b: BackendRole): Role {
  return {
    id:          b.id,
    name:        b.name,
    description: b.description,
    builtIn:     b.isBuiltin,
    userCount:   b.userCount,
    createdAt:   b.createdAt,
    permissions: permissionsFromCodes(b.permissionCodes),
  };
}

@Injectable({ providedIn: 'root' })
export class RoleApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/roles`;

  getAll(): Observable<Role[]> {
    return this.http
      .get<PagedResponse<BackendRole>>(this.base)
      .pipe(map(res => res.content.map(mapRole)));
  }

  getById(id: string): Observable<Role | undefined> {
    return this.http
      .get<BackendRole>(`${this.base}/${id}`)
      .pipe(map(mapRole));
  }

  create(dto: RoleDto): Observable<Role> {
    const body = {
      name:            dto.name,
      description:     dto.description,
      permissionCodes: codesFromPermissions(dto.permissions),
    };
    return this.http.post<BackendRole>(this.base, body).pipe(map(mapRole));
  }

  update(id: string, dto: RoleDto): Observable<Role> {
    const body = {
      name:            dto.name,
      description:     dto.description,
      permissionCodes: codesFromPermissions(dto.permissions),
    };
    return this.http.put<BackendRole>(`${this.base}/${id}`, body).pipe(map(mapRole));
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
