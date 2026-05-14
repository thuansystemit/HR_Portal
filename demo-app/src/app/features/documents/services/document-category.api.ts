import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { DocumentCategory, DocumentCategoryDto, DocumentType, CategoryRolePermission } from '../models/document.model';
import { environment } from '../../../../environments/environment';

/** Backend shape for a single category */
interface BackendCategory {
  id:            string;
  name:          string;
  description:   string;
  documentType:  string;
  documentCount: number;
  createdAt:     string;
  permissions: {
    roleId:    string;
    roleName:  string;
    canView:   boolean;
    canUpload: boolean;
    canDelete: boolean;
  }[];
}

function mapCategory(b: BackendCategory): DocumentCategory {
  return {
    id:            b.id,
    name:          b.name,
    description:   b.description,
    documentType:  b.documentType as DocumentType,
    documentCount: b.documentCount,
    createdAt:     b.createdAt,
    updatedAt:     b.createdAt,
    permissions:   b.permissions.map(p => ({
      roleId:    p.roleId,
      roleName:  p.roleName,
      canView:   p.canView,
      canUpload: p.canUpload,
      canDelete: p.canDelete,
    } as CategoryRolePermission)),
  };
}

@Injectable({ providedIn: 'root' })
export class DocumentCategoryApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/categories`;

  getAll(): Observable<DocumentCategory[]> {
    return this.http
      .get<BackendCategory[]>(this.base)
      .pipe(map(list => list.map(mapCategory)));
  }

  getById(id: string): Observable<DocumentCategory | undefined> {
    return this.http
      .get<BackendCategory>(`${this.base}/${id}`)
      .pipe(map(mapCategory));
  }

  create(dto: DocumentCategoryDto): Observable<DocumentCategory> {
    return this.http
      .post<BackendCategory>(this.base, dto)
      .pipe(map(mapCategory));
  }

  update(id: string, dto: DocumentCategoryDto): Observable<DocumentCategory> {
    return this.http
      .put<BackendCategory>(`${this.base}/${id}`, dto)
      .pipe(map(mapCategory));
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
